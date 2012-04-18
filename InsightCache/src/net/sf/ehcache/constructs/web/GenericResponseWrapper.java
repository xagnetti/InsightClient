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

package net.sf.ehcache.constructs.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import net.sf.ehcache.constructs.web.Header.Type;
import net.sf.ehcache.constructs.web.filter.FilterServletOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a wrapper for {@link javax.servlet.http.HttpServletResponseWrapper}.
 * <p/>
 * It is used to wrap the real Response so that we can modify it after that the
 * target of the request has delivered its response.
 * <p/>
 * It uses the Wrapper pattern.
 * 
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 * @version $Id: GenericResponseWrapper.java 793 2008-10-07 07:28:03Z gregluck $
 */
public class GenericResponseWrapper extends HttpServletResponseWrapper implements Serializable
{

   private static final long                        serialVersionUID   = -5976708169031065497L;

   private static final Logger                      LOG                = LoggerFactory.getLogger( GenericResponseWrapper.class );
   private int                                      statusCode         = SC_OK;
   private int                                      contentLength;
   private String                                   contentType;
   private final Map< String, List< Serializable >> headersMap         = new TreeMap< String, List< Serializable >>( String.CASE_INSENSITIVE_ORDER );
   private final List< Cookie >                     cookies            = new ArrayList< Cookie >();
   private final ServletOutputStream                outstr;
   private PrintWriter                              writer;
   private boolean                                  disableFlushBuffer = true;
   private transient HttpDateFormatter              httpDateFormatter;

   /**
    * Creates a GenericResponseWrapper
    */
   public GenericResponseWrapper( final HttpServletResponse response,
                                  final OutputStream outstr )
   {
      super( response );
      this.outstr = new FilterServletOutputStream( outstr );
   }

   /**
    * Adds a cookie.
    */
   @Override
   public void addCookie( final Cookie cookie )
   {
      cookies.add( cookie );
      super.addCookie( cookie );
   }

   /**
    * @see javax.servlet.http.HttpServletResponseWrapper#addDateHeader(java.lang.String,
    *      long)
    */
   @Override
   public void addDateHeader( final String name,
                              final long date )
   {
      List< Serializable > values = this.headersMap.get( name );
      if ( values == null )
      {
         values = new LinkedList< Serializable >();
         this.headersMap.put( name,
                              values );
      }
      values.add( date );

      super.addDateHeader( name,
                           date );
   }

   /**
    * @see javax.servlet.http.HttpServletResponseWrapper#addHeader(java.lang.String,
    *      java.lang.String)
    */
   @Override
   public void addHeader( final String name,
                          final String value )
   {
      List< Serializable > values = this.headersMap.get( name );
      if ( values == null )
      {
         values = new LinkedList< Serializable >();
         this.headersMap.put( name,
                              values );
      }
      values.add( value );

      super.addHeader( name,
                       value );
   }

   /**
    * @see javax.servlet.http.HttpServletResponseWrapper#addIntHeader(java.lang.String,
    *      int)
    */
   @Override
   public void addIntHeader( final String name,
                             final int value )
   {
      List< Serializable > values = this.headersMap.get( name );
      if ( values == null )
      {
         values = new LinkedList< Serializable >();
         this.headersMap.put( name,
                              values );
      }
      values.add( value );

      super.addIntHeader( name,
                          value );
   }

   /**
    * Flushes all the streams for this response.
    */
   public void flush() throws IOException
   {
      if ( writer != null )
         writer.flush();
      outstr.flush();
   }

   /**
    * Flushes buffer and commits response to client.
    */
   @Override
   public void flushBuffer() throws IOException
   {
      flush();

      // doing this might leads to response already committed exception
      // when the PageInfo has not yet built but the buffer already flushed
      // Happens in Weblogic when a servlet forward to a JSP page and the
      // forward
      // method trigger a flush before it forwarded to the JSP
      // disableFlushBuffer for that purpose is 'true' by default
      // EHC-447
      if ( !disableFlushBuffer )
         super.flushBuffer();
   }

   /**
    * @return All of the headersMap set/added on the response
    */
   public Collection< Header< ? extends Serializable >> getAllHeaders()
   {
      final List< Header< ? extends Serializable >> headers = new LinkedList< Header< ? extends Serializable >>();

      for ( final Map.Entry< String, List< Serializable >> headerEntry : this.headersMap.entrySet() )
      {
         final String name = headerEntry.getKey();
         for ( final Serializable value : headerEntry.getValue() )
         {
            final Type type = Header.Type.determineType( value.getClass() );
            switch ( type )
            {
            case STRING:
               headers.add( new Header< String >( name, ( String ) value ) );
               break;
            case DATE:
               headers.add( new Header< Long >( name, ( Long ) value ) );
               break;
            case INT:
               headers.add( new Header< Integer >( name, ( Integer ) value ) );
               break;
            default:
               throw new IllegalArgumentException( "No mapping for Header.Type: "
                     + type );
            }
         }
      }

      return headers;
   }

   /**
    * Gets the content length.
    */
   public int getContentLength()
   {
      return contentLength;
   }

   /**
    * Gets the content type.
    */
   @Override
   public String getContentType()
   {
      return contentType;
   }

   /**
    * Gets all the cookies.
    */
   public Collection< Cookie > getCookies()
   {
      return cookies;
   }

   /**
    * Gets the headersMap.
    * 
    * @deprecated use {@link #getAllHeaders()} instead
    */
   @SuppressWarnings("rawtypes")
   @Deprecated
   public Collection getHeaders()
   {
      final Collection< String[] > headers = new ArrayList< String[] >( this.headersMap.size() );

      for ( final Map.Entry< String, List< Serializable >> headerEntry : this.headersMap.entrySet() )
      {
         final String name = headerEntry.getKey();
         for ( final Serializable value : headerEntry.getValue() )
         {
            final Type type = Header.Type.determineType( value.getClass() );
            switch ( type )
            {
            case STRING:
               headers.add( new String[]
               { name,
                           ( String ) value } );
               break;
            case DATE:
               final HttpDateFormatter localHttpDateFormatter = this.getHttpDateFormatter();
               final String formattedValue = localHttpDateFormatter.formatHttpDate( new Date( ( Long ) value ) );
               headers.add( new String[]
               { name,
                           formattedValue } );
               break;
            case INT:
               headers.add( new String[]
               { name,
                           ( ( Integer ) value ).toString() } );
               break;
            default:
               throw new IllegalArgumentException( "No mapping for Header.Type: "
                     + type );
            }
         }
      }

      return Collections.unmodifiableCollection( headers );
   }

   /**
    * Gets the outputstream.
    */
   @Override
   public ServletOutputStream getOutputStream()
   {
      return outstr;
   }

   /**
    * Returns the status code for this response.
    */
   @Override
   public int getStatus()
   {
      return statusCode;
   }

   /**
    * Gets the print writer.
    */
   @Override
   public PrintWriter getWriter() throws IOException
   {
      if ( writer == null )
         writer = new PrintWriter( new OutputStreamWriter( outstr, getCharacterEncoding() ), true );
      return writer;
   }

   /**
    * Is the wrapped reponse's buffer flushing disabled?
    * 
    * @return true if the wrapped reponse's buffer flushing disabled
    */
   public boolean isDisableFlushBuffer()
   {
      return disableFlushBuffer;
   }

   /**
    * Resets the response.
    */
   @Override
   public void reset()
   {
      super.reset();
      cookies.clear();
      headersMap.clear();
      statusCode = SC_OK;
      contentType = null;
      contentLength = 0;
   }

   /**
    * Resets the buffers.
    */
   @Override
   public void resetBuffer()
   {
      super.resetBuffer();
   }

   /**
    * Send the error. If the response is not ok, most of the logic is bypassed
    * and the error is sent raw Also, the content is not cached.
    * 
    * @param i the status code
    * @throws IOException
    */
   @Override
   public void sendError( final int i ) throws IOException
   {
      statusCode = i;
      super.sendError( i );
   }

   /**
    * Send the error. If the response is not ok, most of the logic is bypassed
    * and the error is sent raw Also, the content is not cached.
    * 
    * @param i the status code
    * @param string the error message
    * @throws IOException
    */
   @Override
   public void sendError( final int i,
                          final String string ) throws IOException
   {
      statusCode = i;
      super.sendError( i,
                       string );
   }

   /**
    * Send the redirect. If the response is not ok, most of the logic is
    * bypassed and the error is sent raw. Also, the content is not cached.
    * 
    * @param string the URL to redirect to
    * @throws IOException
    */
   @Override
   public void sendRedirect( final String string ) throws IOException
   {
      statusCode = HttpServletResponse.SC_MOVED_TEMPORARILY;
      super.sendRedirect( string );
   }

   /**
    * Sets the content length.
    */
   @Override
   public void setContentLength( final int length )
   {
      this.contentLength = length;
      super.setContentLength( length );
   }

   /**
    * Sets the content type.
    */
   @Override
   public void setContentType( final String type )
   {
      this.contentType = type;
      super.setContentType( type );
   }

   /**
    * @see javax.servlet.http.HttpServletResponseWrapper#setDateHeader(java.lang.String,
    *      long)
    */
   @Override
   public void setDateHeader( final String name,
                              final long date )
   {
      final LinkedList< Serializable > values = new LinkedList< Serializable >();
      values.add( date );
      this.headersMap.put( name,
                           values );

      super.setDateHeader( name,
                           date );
   }

   /**
    * Set if the wrapped reponse's buffer flushing should be disabled.
    * 
    * @param disableFlushBuffer true if the wrapped reponse's buffer flushing
    *           should be disabled
    */
   public void setDisableFlushBuffer( final boolean disableFlushBuffer )
   {
      this.disableFlushBuffer = disableFlushBuffer;
   }

   /**
    * @see javax.servlet.http.HttpServletResponseWrapper#setHeader(java.lang.String,
    *      java.lang.String)
    */
   @Override
   public void setHeader( final String name,
                          final String value )
   {
      final LinkedList< Serializable > values = new LinkedList< Serializable >();
      values.add( value );
      this.headersMap.put( name,
                           values );

      super.setHeader( name,
                       value );
   }

   /**
    * @see javax.servlet.http.HttpServletResponseWrapper#setIntHeader(java.lang.String,
    *      int)
    */
   @Override
   public void setIntHeader( final String name,
                             final int value )
   {
      final LinkedList< Serializable > values = new LinkedList< Serializable >();
      values.add( value );
      this.headersMap.put( name,
                           values );

      super.setIntHeader( name,
                          value );
   }

   /**
    * Sets the status code for this response.
    */
   @Override
   public void setStatus( final int code )
   {
      statusCode = code;
      super.setStatus( code );
   }

   /**
    * Sets the status code for this response.
    */
   @Override
   public void setStatus( final int code,
                          final String msg )
   {
      statusCode = code;
      LOG.warn( "Discarding message because this method is deprecated." );
      super.setStatus( code );
   }

   private HttpDateFormatter getHttpDateFormatter()
   {
      if ( this.httpDateFormatter == null )
         this.httpDateFormatter = new HttpDateFormatter();

      return this.httpDateFormatter;
   }
}
