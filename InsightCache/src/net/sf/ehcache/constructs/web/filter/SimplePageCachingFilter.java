/**
 *  Copyright 2003-2009 Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.constructs.web.filter;

import javax.servlet.http.HttpServletRequest;

import net.sf.ehcache.CacheManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimplePageCachingFilter extends CachingFilter
{
   public static final String  DEFAULT_CACHE_NAME = "SimplePageCachingFilter";
   private static final Logger LOG                = LoggerFactory.getLogger( SimplePageCachingFilter.class );

   @Override
   protected String calculateKey( final HttpServletRequest httpRequest )
   {
      final StringBuffer stringBuffer = new StringBuffer();
      stringBuffer.append( httpRequest.getMethod() )
                  .append( httpRequest.getRequestURI() )
                  .append( httpRequest.getQueryString() )
                  .append( "_" );

      final String key = stringBuffer.append( ( ( InsightRequestWrapper ) httpRequest ).getBody() )
                                     .toString();

      return key;
   }

   @Override
   protected CacheManager getCacheManager()
   {
      return CacheManager.getInstance();
   }

   @Override
   protected String getCacheName()
   {
      if ( ( cacheName != null )
            && ( cacheName.length() > 0 ) )
      {
         LOG.debug( "Using configured cacheName of {}.",
                    cacheName );
         return cacheName;
      }
      else
      {
         LOG.debug( "No cacheName configured. Using default of {}.",
                    DEFAULT_CACHE_NAME );
         return DEFAULT_CACHE_NAME;
      }
   }
}
