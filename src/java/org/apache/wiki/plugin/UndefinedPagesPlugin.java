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
package org.apache.wiki.plugin;

import java.util.*;

import javax.jcr.RepositoryException;

import org.apache.wiki.WikiContext;
import org.apache.wiki.api.PluginException;
import org.apache.wiki.content.ReferenceManager;
import org.apache.wiki.content.WikiPath;


/**
 *  Plugin that enumerates the pages in the wiki that have not yet been defined.
 *  
 *  Parameters  (from AbstractFilteredPlugin):
 *  <ul>
 *  <li><b>separator</b> - how to separate generated links; default is a wikitext line break,  producing a vertical list</li>
 * <li><b> maxwidth</b> - maximum width, in chars, of generated links.</li>
 * </ul>
 *
 */
public class UndefinedPagesPlugin extends AbstractFilteredPlugin
{
    /**
     *  {@inheritDoc}
     */
    public String execute( WikiContext context, Map<String,Object> params )
        throws PluginException
    {
        ReferenceManager refmgr = context.getEngine().getReferenceManager();
        List<WikiPath> links;
        try
        {
            links = refmgr.findUncreated();
        }
        catch( RepositoryException e )
        {
            e.printStackTrace();
            throw new PluginException( "Could not find uncreated pages.", e );
        }

        super.initialize( context, params );

        links = filterCollection( links );
        Collections.sort( links );
        
        String wikitext = null;
        
        if (m_lastModified)
        {
            throw new PluginException( context.getBundle( WikiPlugin.CORE_PLUGINS_RESOURCEBUNDLE )
                .getString( "plugin.undefined.parm.invalid" )
                                       + " : " + PARAM_LASTMODIFIED );
        }
        
        if (m_show.equals(PARAM_SHOW_VALUE_COUNT))
        {
            wikitext = "" + links.size();
        }
        else
        {
            wikitext = wikitizeCollection(links, m_separator, ALL_ITEMS);
        }
        
        return makeHTML( context, wikitext );
    }
}
