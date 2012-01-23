/* 
    JSPWiki - a JSP-based WikiWiki clone.

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.  
 */
package org.apache.wiki.ui.admin;

import java.lang.management.ManagementFactory;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.management.*;

import org.apache.wiki.Release;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.event.WikiEngineEvent;
import org.apache.wiki.event.WikiEvent;
import org.apache.wiki.event.WikiEventListener;
import org.apache.wiki.log.Logger;
import org.apache.wiki.log.LoggerFactory;
import org.apache.wiki.modules.WikiModuleInfo;
import org.apache.wiki.ui.admin.beans.CoreBean;
import org.apache.wiki.ui.admin.beans.PluginBean;
import org.apache.wiki.ui.admin.beans.SearchManagerBean;
import org.apache.wiki.ui.admin.beans.UserBean;



/**
 *  Provides a manager class for all AdminBeans within JSPWiki.  This class
 *  also manages registration for any AdminBean which is also a JMX bean.
 *
 *  @since  2.5.52
 */
public class AdminBeanManager implements WikiEventListener
{
    private WikiEngine m_engine;
    private ArrayList<AdminBean>  m_allBeans;

    private MBeanServer m_mbeanServer = null;

    private static final Logger log = LoggerFactory.getLogger(AdminBeanManager.class);

    public AdminBeanManager( WikiEngine engine )
    {
        m_mbeanServer = ManagementFactory.getPlatformMBeanServer();

        m_engine = engine;

        if( m_mbeanServer != null )
        {
            log.info( m_mbeanServer.getClass().getName() );
            log.info( m_mbeanServer.getDefaultDomain() );
        }

        m_engine.addWikiEventListener( this );
        initialize();
    }

    public void initialize()
    {
        reload();
    }

    private String getJMXTitleString( int title )
    {
        switch( title )
        {
            case AdminBean.CORE:
                return "Core";

            case AdminBean.EDITOR:
                return "Editors";

            case AdminBean.UNKNOWN:
            default:
                return "Unknown";
        }
    }


    /**
     *  Register an AdminBean.  If the AdminBean is also a JMX MBean, it
     *  also gets registered to the MBeanServer we've found.
     *
     *  @param ab AdminBean to register.
     */
    private void registerAdminBean( AdminBean ab )
    {
        try
        {
            if( ab instanceof DynamicMBean && m_mbeanServer != null )
            {
                ObjectName name = getObjectName(ab);

                if( !m_mbeanServer.isRegistered(name))
                {
                    m_mbeanServer.registerMBean( ab, name );
                    log.info("Registered new admin bean "+ab.getTitle());
                }
                else
                {
                    log.error( "JMX bean named " + name + 
                               " is already registered. Hint: are you running 2 webapps with the same " +
                               WikiEngine.PROP_APPNAME + "?" );
                }
            }

            m_allBeans.add( ab );

        }
        catch( InstanceAlreadyExistsException e )
        {
            log.error("Admin bean already registered to JMX",e);
        }
        catch( MBeanRegistrationException e )
        {
            log.error("Admin bean cannot be registered to JMX",e);
        }
        catch( NotCompliantMBeanException e )
        {
            log.error("Your admin bean is not very good",e);
        }
        catch( MalformedObjectNameException e )
        {
            log.error("Your admin bean name is not very good",e);
        }
        catch( NullPointerException e )
        {
            log.error("Evil NPE occurred",e);
        }
        catch( AccessControlException e )
        {
            //FIXME catching this unchecked exception seems necessary when running webtests in Jetty 
            log.error( "Evil AccessControlException occurred", e );
        }
    }

    private ObjectName getObjectName(AdminBean ab) throws MalformedObjectNameException
    {
        String component = getJMXTitleString( ab.getType() );
        String title     = ab.getTitle();

        ObjectName name = new ObjectName( Release.APPNAME + ":wiki=" + m_engine.getApplicationName() + ",component="+component+",name="+title );
        return name;
    }

    /**
     *  Registers all the beans from a collection of WikiModuleInfos.  If some of the beans
     *  fail, logs the message and keeps going to the next bean.
     *
     *  @param modules Collection of WikiModuleInfo instances
     */
    private void registerBeans( Collection<? extends WikiModuleInfo> modules )
    {
        for( WikiModuleInfo module : modules )
        {
            String abname = module.getAdminBeanClass();

            try
            {
                if( abname != null && abname.length() > 0 )
                {
                    Class<?> abclass = Class.forName(abname);

                    AdminBean ab = (AdminBean) abclass.newInstance();

                    registerAdminBean( ab );
                }
            }
            catch (ClassNotFoundException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (InstantiationException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (IllegalAccessException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    // FIXME: Should unload the beans first.
    private void reload()
    {
        m_allBeans = new ArrayList<AdminBean>();

        try
        {
            registerAdminBean( new CoreBean(m_engine) );
            registerAdminBean( new UserBean(m_engine) );
            registerAdminBean( new SearchManagerBean(m_engine) );
            registerAdminBean( new PluginBean(m_engine) );
        }
        catch (NotCompliantMBeanException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        registerBeans( m_engine.getEditorManager().modules() );
        registerBeans( m_engine.getPluginManager().modules() );
    }

    /**
     *  Lists all administration beans which are currently known
     *  and instantiated.
     *
     *  @return all AdminBeans known to the manager
     */
    public List<AdminBean> getAllBeans()
    {
        if( m_allBeans == null ) reload();

        return m_allBeans;
    }

    /**
     *  Locates a bean based on the AdminBean.getId() out of all
     *  the registered beans.
     *
     *  @param id ID
     *  @return An AdminBean, or null, if no such bean is found.
     */
    public AdminBean findBean( String id )
    {
        for( AdminBean ab : m_allBeans )
        {
            if( ab.getId().equals(id) )
                return ab;
        }
        return null;
    }


    /**
     *  Returns the type identifier for a string type.
     *
     *  @param type A type string.
     *  @return A type value.
     */
    public static int getTypeFromString(String type)
    {
        if( type.equals("core") )
            return AdminBean.CORE;
        else if( type.equals("editors") )
            return AdminBean.EDITOR;

        return AdminBean.UNKNOWN;
    }

    /**
     *  Unregisters AdminBeans upon SHUTDOWN event.
     *
     *  @param event {@inheritDoc}
     */
    public void actionPerformed(WikiEvent event)
    {
        if( event instanceof WikiEngineEvent )
        {
            if( ((WikiEngineEvent)event).getType() == WikiEngineEvent.SHUTDOWN )
            {
                for( AdminBean ab : m_allBeans )
                {
                    try
                    {
                        ObjectName on = getObjectName( ab );
                        if( m_mbeanServer.isRegistered( on ) )
                        {
                            m_mbeanServer.unregisterMBean(on);
                            log.info("Unregistered AdminBean "+ab.getTitle());
                        }
                    }
                    catch( MalformedObjectNameException e )
                    {
                        log.error("Malformed object name when unregistering",e);
                    }
                    catch (InstanceNotFoundException e)
                    {
                        log.error("Object was registered; yet claims that it's not there",e);
                    }
                    catch (MBeanRegistrationException e)
                    {
                        log.error("Registration exception while unregistering",e);
                    }
                }
            }
        }
    }
}
