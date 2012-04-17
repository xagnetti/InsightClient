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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.zip.DataFormatException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.BlockingCache;
import net.sf.ehcache.constructs.blocking.LockTimeoutException;
import net.sf.ehcache.constructs.web.AlreadyCommittedException;
import net.sf.ehcache.constructs.web.AlreadyGzippedException;
import net.sf.ehcache.constructs.web.GenericResponseWrapper;
import net.sf.ehcache.constructs.web.Header;
import net.sf.ehcache.constructs.web.PageInfo;
import net.sf.ehcache.constructs.web.ResponseHeadersNotModifiableException;
import net.sf.ehcache.constructs.web.ResponseUtil;
import net.sf.ehcache.constructs.web.SerializableCookie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CachingFilter extends Filter
{

   private static class VisitLog extends ThreadLocal< Boolean >
   {
      public void clear()
      {
         super.remove();
      }

      public boolean hasVisited()
      {
         return get();
      }

      public void markAsVisited()
      {
         set( true );
      }

      @Override
      protected Boolean initialValue()
      {
         return false;
      }
   }
   private static final Logger LOG                     = LoggerFactory.getLogger( CachingFilter.class );
   private static final String BLOCKING_TIMEOUT_MILLIS = "blockingTimeoutMillis";

   private static final String CACHE_NAME              = "cacheName";

   protected String            cacheName;

   protected BlockingCache     blockingCache;

   private final VisitLog      visitLog                = new VisitLog();

   @Override
   public void doInit( final FilterConfig filterConfig ) throws CacheException
   {
      synchronized ( this.getClass() )
      {
         if ( blockingCache == null )
         {
            setCacheNameIfAnyConfigured( filterConfig );
            final String localCacheName = getCacheName();
            final Ehcache cache = getCacheManager().getEhcache( localCacheName );
            if ( cache == null )
               throw new CacheException( "cache '"
                     + localCacheName + "' not found in configuration" );
            if ( !( cache instanceof BlockingCache ) )
            {
               // decorate and substitute
               final BlockingCache newBlockingCache = new BlockingCache( cache );
               getCacheManager().replaceCacheWithDecoratedCache( cache,
                                                                 newBlockingCache );
            }
            blockingCache = ( BlockingCache ) getCacheManager().getEhcache( localCacheName );
            final Integer blockingTimeoutMillis = parseBlockingCacheTimeoutMillis( filterConfig );
            if ( ( blockingTimeoutMillis != null )
                  && ( blockingTimeoutMillis > 0 ) )
               blockingCache.setTimeoutMillis( blockingTimeoutMillis );
         }
      }
   }

   protected PageInfo buildPage( final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final FilterChain chain ) throws AlreadyGzippedException,
                                                          Exception
   {

      // Invoke the next entity in the chain
      final ByteArrayOutputStream outstr = new ByteArrayOutputStream();
      final GenericResponseWrapper wrapper = new GenericResponseWrapper( response, outstr );
      chain.doFilter( request,
                      wrapper );
      wrapper.flush();

      final long timeToLiveSeconds = blockingCache.getCacheConfiguration().getTimeToLiveSeconds();

      // Return the page info
      return new PageInfo( wrapper.getStatus(),
                           wrapper.getContentType(),
                           wrapper.getCookies(),
                           outstr.toByteArray(),
                           true,
                           timeToLiveSeconds,
                           wrapper.getAllHeaders() );
   }

   protected PageInfo buildPageInfo( final HttpServletRequest request,
                                     final HttpServletResponse response,
                                     final FilterChain chain ) throws Exception
   {
      // Look up the cached page
      final String key = calculateKey( request );
      PageInfo pageInfo = null;
      try
      {
         checkNoReentry( request );
         final Element element = blockingCache.get( key );
         if ( ( element == null )
               || ( element.getObjectValue() == null ) )
            try
            {
               // Page is not cached - build the response, cache it, and
               // send to client
               pageInfo = buildPage( request,
                                     response,
                                     chain );
               if ( pageInfo.isOk() )
               {
                  if ( LOG.isDebugEnabled() )
                     LOG.debug( "PageInfo ok. Adding to cache "
                           + blockingCache.getName() + " with key " + key );
                  blockingCache.put( new Element( key, pageInfo ) );
               }
               else
               {
                  if ( LOG.isDebugEnabled() )
                     LOG.debug( "PageInfo was not ok(200). Putting null into cache "
                           + blockingCache.getName() + " with key " + key );
                  blockingCache.put( new Element( key, null ) );
               }
            }
            catch ( final Throwable throwable )
            {
               // Must unlock the cache if the above fails. Will be logged
               // at Filter
               blockingCache.put( new Element( key, null ) );
               throw new Exception( throwable );
            }
         else
            pageInfo = ( PageInfo ) element.getObjectValue();
      }
      catch ( final LockTimeoutException e )
      {
         // do not release the lock, because you never acquired it
         throw e;
      }
      finally
      {
         // all done building page, reset the re-entrant flag
         visitLog.clear();
      }
      return pageInfo;
   }

   protected abstract String calculateKey( final HttpServletRequest httpRequest );

   protected void checkNoReentry( final HttpServletRequest httpRequest ) throws FilterNonReentrantException
   {
      final String filterName = getClass().getName();
      if ( visitLog.hasVisited() )
         throw new FilterNonReentrantException( "The request thread is attempting to reenter"
               + " filter " + filterName + ". URL: " + httpRequest.getRequestURL() );
      else
      {
         // mark this thread as already visited
         visitLog.markAsVisited();
         if ( LOG.isDebugEnabled() )
            LOG.debug( "Thread {}  has been marked as visited.",
                       Thread.currentThread().getName() );
      }
   }

   @Override
   protected void doDestroy()
   {
      // noop
   }

   @Override
   protected void doFilter( final HttpServletRequest request,
                            final HttpServletResponse response,
                            final FilterChain chain ) throws AlreadyGzippedException,
                                                     AlreadyCommittedException,
                                                     FilterNonReentrantException,
                                                     LockTimeoutException,
                                                     Exception
   {
      if ( response.isCommitted() )
         throw new AlreadyCommittedException( "Response already committed before doing buildPage." );
      logRequestHeaders( request );
      final PageInfo pageInfo = buildPageInfo( request,
                                               response,
                                               chain );

      if ( pageInfo.isOk() )
      {
         if ( response.isCommitted() )
            throw new AlreadyCommittedException( "Response already committed after doing buildPage"
                  + " but before writing response from PageInfo." );
         writeResponse( request,
                        response,
                        pageInfo );
      }
   }

   protected abstract CacheManager getCacheManager();

   protected String getCacheName()
   {
      return cacheName;
   }

   protected void setCacheNameIfAnyConfigured( final FilterConfig filterConfig )
   {
      this.cacheName = filterConfig.getInitParameter( CACHE_NAME );

   }

   protected void setContentType( final HttpServletResponse response,
                                  final PageInfo pageInfo )
   {
      final String contentType = pageInfo.getContentType();
      if ( ( contentType != null )
            && ( contentType.length() > 0 ) )
         response.setContentType( contentType );
   }

   protected void setCookies( final PageInfo pageInfo,
                              final HttpServletResponse response )
   {

      final Collection cookies = pageInfo.getSerializableCookies();
      for ( final Iterator iterator = cookies.iterator(); iterator.hasNext(); )
      {
         final Cookie cookie = ( ( SerializableCookie ) iterator.next() ).toCookie();
         response.addCookie( cookie );
      }
   }

   protected void setHeaders( final PageInfo pageInfo,
                              final boolean requestAcceptsGzipEncoding,
                              final HttpServletResponse response )
   {

      final Collection< Header< ? extends Serializable >> headers = pageInfo.getHeaders();

      // Track which headers have been set so all headers of the same name
      // after the first are added
      final TreeSet< String > setHeaders = new TreeSet< String >( String.CASE_INSENSITIVE_ORDER );

      for ( final Header< ? extends Serializable > header : headers )
      {
         final String name = header.getName();

         switch ( header.getType() )
         {
         case STRING:
            if ( setHeaders.contains( name ) )
               response.addHeader( name,
                                   ( String ) header.getValue() );
            else
            {
               setHeaders.add( name );
               response.setHeader( name,
                                   ( String ) header.getValue() );
            }
            break;
         case DATE:
            if ( setHeaders.contains( name ) )
               response.addDateHeader( name,
                                       ( Long ) header.getValue() );
            else
            {
               setHeaders.add( name );
               response.setDateHeader( name,
                                       ( Long ) header.getValue() );
            }
            break;
         case INT:
            if ( setHeaders.contains( name ) )
               response.addIntHeader( name,
                                      ( Integer ) header.getValue() );
            else
            {
               setHeaders.add( name );
               response.setIntHeader( name,
                                      ( Integer ) header.getValue() );
            }
            break;
         default:
            throw new IllegalArgumentException( "No mapping for Header: "
                  + header );
         }
      }
   }

   protected void setStatus( final HttpServletResponse response,
                             final PageInfo pageInfo )
   {
      response.setStatus( pageInfo.getStatusCode() );
   }

   protected void writeContent( final HttpServletRequest request,
                                final HttpServletResponse response,
                                final PageInfo pageInfo ) throws IOException,
                                                         ResponseHeadersNotModifiableException
   {
      byte[] body;

      final boolean shouldBodyBeZero = ResponseUtil.shouldBodyBeZero( request,
                                                                      pageInfo.getStatusCode() );
      if ( shouldBodyBeZero )
         body = new byte[ 0 ];
      else if ( acceptsGzipEncoding( request ) )
      {
         body = pageInfo.getGzippedBody();
         if ( ResponseUtil.shouldGzippedBodyBeZero( body,
                                                    request ) )
            body = new byte[ 0 ];
         else
            ResponseUtil.addGzipHeader( response );

      }
      else
         body = pageInfo.getUngzippedBody();

      response.setContentLength( body.length );
      final OutputStream out = new BufferedOutputStream( response.getOutputStream() );
      out.write( body );
      out.flush();
   }

   protected void writeResponse( final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final PageInfo pageInfo ) throws IOException,
                                                          DataFormatException,
                                                          ResponseHeadersNotModifiableException
   {
      final boolean requestAcceptsGzipEncoding = acceptsGzipEncoding( request );

      setStatus( response,
                 pageInfo );
      setContentType( response,
                      pageInfo );
      setCookies( pageInfo,
                  response );
      // do headers last so that users can override with their own header sets
      setHeaders( pageInfo,
                  requestAcceptsGzipEncoding,
                  response );
      writeContent( request,
                    response,
                    pageInfo );
   }

   Integer parseBlockingCacheTimeoutMillis( final FilterConfig filterConfig )
   {

      final String timeout = filterConfig.getInitParameter( BLOCKING_TIMEOUT_MILLIS );
      try
      {
         return Integer.parseInt( timeout );
      }
      catch ( final NumberFormatException e )
      {
         return null;
      }

   }
}
