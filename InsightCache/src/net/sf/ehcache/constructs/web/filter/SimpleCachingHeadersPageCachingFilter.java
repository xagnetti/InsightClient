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

/*
 * Based on a contribution from Craig Andrews which has been released also under the Apache 2 license at
 * http://candrews.integralblue.com/2009/02/http-caching-header-aware-servlet-filter/. Copyright notice follows.
 *
 * Copyright 2009 Craig Andrews
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

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

import javax.servlet.FilterChain;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.constructs.web.AlreadyGzippedException;
import net.sf.ehcache.constructs.web.Header;
import net.sf.ehcache.constructs.web.HttpDateFormatter;
import net.sf.ehcache.constructs.web.PageInfo;
import net.sf.ehcache.constructs.web.ResponseHeadersNotModifiableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebFilter(urlPatterns =
{ "/ProxyServlet" })
public class SimpleCachingHeadersPageCachingFilter extends SimplePageCachingFilter
{

   public static final String  NAME                     = "SimpleCachingHeadersPageCachingFilter";
   private static final Logger LOG                      = LoggerFactory.getLogger( SimpleCachingHeadersPageCachingFilter.class );
   private static final long   ONE_YEAR_IN_MILLISECONDS = 60
                                                              * 60 * 24 * 365 * 1000L;
   private static final int    MILLISECONDS_PER_SECOND  = 1000;
   private HttpDateFormatter   httpDateFormatter;

   @Override
   protected PageInfo buildPage( final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final FilterChain chain ) throws AlreadyGzippedException,
                                                          Exception
   {
      final PageInfo pageInfo = super.buildPage( request,
                                                 response,
                                                 chain );

      final List< Header< ? extends Serializable >> headers = pageInfo.getHeaders();

      final long ttlMilliseconds = calculateTimeToLiveMilliseconds();

      // Remove any conflicting headers
      for ( final Iterator< Header< ? extends Serializable >> headerItr = headers.iterator(); headerItr.hasNext(); )
      {
         final Header< ? extends Serializable > header = headerItr.next();

         final String name = header.getName();
         if ( "Last-Modified".equalsIgnoreCase( name )
               || "Expires".equalsIgnoreCase( name ) || "Cache-Control".equalsIgnoreCase( name )
               || "ETag".equalsIgnoreCase( name ) )
            headerItr.remove();
      }
      long lastModified = pageInfo.getCreated().getTime();
      lastModified = TimeUnit.MILLISECONDS.toSeconds( lastModified );
      lastModified = TimeUnit.SECONDS.toMillis( lastModified );

      headers.add( new Header< Long >( "Last-Modified", lastModified ) );
      headers.add( new Header< Long >( "Expires", System.currentTimeMillis()
            + ttlMilliseconds ) );
      headers.add( new Header< String >( "Cache-Control", "max-age="
            + ( ttlMilliseconds / MILLISECONDS_PER_SECOND ) ) );
      headers.add( new Header< String >( "ETag", generateEtag( ttlMilliseconds ) ) );

      return pageInfo;
   }

   protected long calculateTimeToLiveMilliseconds()
   {
      if ( blockingCache.isDisabled() )
         return -1;
      else
      {
         final CacheConfiguration cacheConfiguration = blockingCache.getCacheConfiguration();
         if ( cacheConfiguration.isEternal() )
            return ONE_YEAR_IN_MILLISECONDS;
         else
            return cacheConfiguration.getTimeToLiveSeconds()
                  * MILLISECONDS_PER_SECOND;
      }
   }

   protected final HttpDateFormatter getHttpDateFormatter()
   {
      if ( httpDateFormatter == null )
         httpDateFormatter = new HttpDateFormatter();
      return this.httpDateFormatter;
   }

   @Override
   protected void writeResponse( final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final PageInfo pageInfo ) throws IOException,
                                                          DataFormatException,
                                                          ResponseHeadersNotModifiableException
   {

      final List< Header< ? extends Serializable >> headers = pageInfo.getHeaders();
      for ( final Header< ? extends Serializable > header : headers )
      {
         if ( "ETag".equals( header.getName() ) )
         {
            final String requestIfNoneMatch = request.getHeader( "If-None-Match" );
            if ( header.getValue().equals( requestIfNoneMatch ) )
            {
               response.sendError( HttpServletResponse.SC_NOT_MODIFIED );
               return;
            }
            break;
         }
         if ( "Last-Modified".equals( header.getName() ) )
         {
            final long requestIfModifiedSince = request.getDateHeader( "If-Modified-Since" );
            if ( requestIfModifiedSince != -1 )
            {
               final Date requestDate = new Date( requestIfModifiedSince );
               final Date pageInfoDate;
               switch ( header.getType() )
               {
               case STRING:
                  pageInfoDate = this.getHttpDateFormatter()
                                     .parseDateFromHttpDate( ( String ) header.getValue() );
                  break;
               case DATE:
                  pageInfoDate = new Date( ( Long ) header.getValue() );
                  break;
               default:
                  throw new IllegalArgumentException( "Header "
                        + header + " is not supported as type: " + header.getType() );
               }

               if ( !requestDate.before( pageInfoDate ) )
               {
                  response.sendError( HttpServletResponse.SC_NOT_MODIFIED );
                  response.setHeader( "Last-Modified",
                                      request.getHeader( "If-Modified-Since" ) );
                  return;
               }
            }
         }
      }

      super.writeResponse( request,
                           response,
                           pageInfo );
   }

   private String generateEtag( final long ttlMilliseconds )
   {
      final StringBuffer stringBuffer = new StringBuffer();
      final Long eTagRaw = System.currentTimeMillis()
            + ttlMilliseconds;
      final String eTag = stringBuffer.append( "\"" ).append( eTagRaw ).append( "\"" ).toString();
      return eTag;
   }
}
