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

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Filter implements javax.servlet.Filter
{
   public static final String  NO_FILTER = "NO_FILTER";
   private static final Logger LOG       = LoggerFactory.getLogger( Filter.class );
   protected FilterConfig      filterConfig;
   protected String            exceptionsToLogDifferently;
   protected boolean           suppressStackTraces;

   @Override
   public final void destroy()
   {
      this.filterConfig = null;
      doDestroy();
   }

   @Override
   public final void doFilter( final ServletRequest request,
                               final ServletResponse response,
                               final FilterChain chain ) throws ServletException,
                                                        IOException
   {
      final HttpServletRequest httpRequest = ( HttpServletRequest ) request;
      final HttpServletResponse httpResponse = ( HttpServletResponse ) response;
      try
      {
         // NO_FILTER set for RequestDispatcher forwards to avoid double
         // gzipping
         if ( filterNotDisabled( httpRequest ) )
            doFilter( httpRequest,
                      httpResponse,
                      chain );
         else
            chain.doFilter( request,
                            response );
      }
      catch ( final Throwable throwable )
      {
         logThrowable( throwable,
                       httpRequest );
      }
   }

   /**
    * Returns the filter config.
    */
   public FilterConfig getFilterConfig()
   {
      return filterConfig;
   }

   @Override
   public final void init( final FilterConfig filterConfig ) throws ServletException
   {
      try
      {

         this.filterConfig = filterConfig;
         processInitParams( filterConfig );

         // Attempt to initialise this filter
         doInit( filterConfig );
      }
      catch ( final Exception e )
      {
         LOG.error( "Could not initialise servlet filter.",
                    e );
         throw new ServletException( "Could not initialise servlet filter.", e );
      }
   }

   protected boolean acceptsEncoding( final HttpServletRequest request,
                                      final String name )
   {
      final boolean accepts = headerContains( request,
                                              "Accept-Encoding",
                                              name );
      return accepts;
   }

   protected boolean acceptsGzipEncoding( final HttpServletRequest request )
   {
      return acceptsEncoding( request,
                              "gzip" );
   }

   protected abstract void doDestroy();

   protected abstract void doFilter( final HttpServletRequest httpRequest,
                                     final HttpServletResponse httpResponse,
                                     final FilterChain chain ) throws Throwable;

   protected abstract void doInit( FilterConfig filterConfig ) throws Exception;

   protected boolean filterNotDisabled( final HttpServletRequest httpRequest )
   {
      return httpRequest.getAttribute( NO_FILTER ) == null;
   }

   protected void logRequestHeaders( final HttpServletRequest request )
   {
      if ( LOG.isDebugEnabled() )
      {
         final Map< String, String > headers = new HashMap< String, String >();
         final Enumeration< String > enumeration = request.getHeaderNames();
         final StringBuffer logLine = new StringBuffer();
         logLine.append( "Request Headers" );
         while ( enumeration.hasMoreElements() )
         {
            final String name = enumeration.nextElement();
            final String headerValue = request.getHeader( name );
            headers.put( name,
                         headerValue );
            logLine.append( ": " ).append( name ).append( " -> " ).append( headerValue );
         }
         LOG.debug( logLine.toString() );
      }
   }

   protected void processInitParams( final FilterConfig config ) throws ServletException
   {
      final String exceptions = config.getInitParameter( "exceptionsToLogDifferently" );
      final String level = config.getInitParameter( "exceptionsToLogDifferentlyLevel" );
      final String suppressStackTracesString = config.getInitParameter( "suppressStackTraces" );
      suppressStackTraces = Boolean.valueOf( suppressStackTracesString ).booleanValue();
      if ( LOG.isDebugEnabled() )
         LOG.debug( "Suppression of stack traces enabled for "
               + this.getClass().getName() );

      if ( exceptions != null )
      {
         validateMandatoryParameters( exceptions,
                                      level );
         exceptionsToLogDifferently = exceptions;
         if ( LOG.isDebugEnabled() )
            LOG.debug( "Different logging levels configured for "
                  + this.getClass().getName() );
      }
   }

   private boolean headerContains( final HttpServletRequest request,
                                   final String header,
                                   final String value )
   {

      logRequestHeaders( request );

      final Enumeration< String > accepted = request.getHeaders( header );
      while ( accepted.hasMoreElements() )
      {
         final String headerValue = accepted.nextElement();
         if ( headerValue.indexOf( value ) != -1 )
            return true;
      }
      return false;
   }

   private void logThrowable( final Throwable throwable,
                              final HttpServletRequest httpRequest ) throws ServletException,
                                                                    IOException
   {
      final StringBuffer messageBuffer = new StringBuffer( "Throwable thrown during doFilter on request with URI: " ).append( httpRequest.getRequestURI() )
                                                                                                                     .append( " and Query: " )
                                                                                                                     .append( httpRequest.getQueryString() );
      final String message = messageBuffer.toString();
      final boolean matchFound = matches( throwable );
      if ( matchFound )
      {
         try
         {
            if ( suppressStackTraces )
               LOG.error( throwable.getMessage() );
            else
               LOG.error( throwable.getMessage(),
                          throwable );
         }
         catch ( final Exception e )
         {
            LOG.error( "Could not invoke Log method",
                       e );
         }
         if ( throwable instanceof IOException )
            throw ( IOException ) throwable;
         else
            throw new ServletException( message, throwable );
      }
      else
      {

         if ( suppressStackTraces )
            LOG.warn( messageBuffer.append( throwable.getMessage() )
                                   .append( "\nTop StackTraceElement: " )
                                   .append( throwable.getStackTrace()[ 0 ].toString() )
                                   .toString() );
         else
            LOG.warn( messageBuffer.append( throwable.getMessage() ).toString(),
                      throwable );
         if ( throwable instanceof IOException )
            throw ( IOException ) throwable;
         else
            throw new ServletException( throwable );
      }
   }

   private boolean matches( final Throwable throwable )
   {
      if ( exceptionsToLogDifferently == null )
         return false;
      if ( exceptionsToLogDifferently.indexOf( throwable.getClass().getName() ) != -1 )
         return true;
      if ( throwable instanceof ServletException )
      {
         final Throwable rootCause = ( ( ( ServletException ) throwable ).getRootCause() );
         if ( exceptionsToLogDifferently.indexOf( rootCause.getClass().getName() ) != -1 )
            return true;
      }
      if ( throwable.getCause() != null )
      {
         final Throwable cause = throwable.getCause();
         if ( exceptionsToLogDifferently.indexOf( cause.getClass().getName() ) != -1 )
            return true;
      }
      return false;
   }

   private void validateMandatoryParameters( final String exceptions,
                                             final String level ) throws ServletException
   {
      if ( ( ( exceptions != null ) && ( level == null ) )
            || ( ( level != null ) && ( exceptions == null ) ) )
         throw new ServletException( "Invalid init-params. Both exceptionsToLogDifferently"
               + " and exceptionsToLogDifferentlyLevelvalue should be specified if one is" + " specified." );
   }

}
