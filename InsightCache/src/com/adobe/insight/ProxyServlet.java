package com.adobe.insight;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;

public class ProxyServlet extends HttpServlet
{
   private static MultiThreadedHttpConnectionManager connectionManager          = new MultiThreadedHttpConnectionManager();
   private static HttpClient                         client                     = new HttpClient( connectionManager );
   private static final long                         serialVersionUID           = 1L;
   private static final String                       CONTENT_LENGTH_HEADER_NAME = "Content-Length";
   private static final String                       HOST_HEADER_NAME           = "Host";
   private static final String                       LOCATION_HEADER            = "Location";
   private int                                       intProxyPort               = 80;
   private boolean                                   lockedProxyPort            = false;
   private String                                    stringProxyHost;
   private String                                    stringProxyPath            = "";

   @Override
   public void doGet( final HttpServletRequest request,
                      final HttpServletResponse response ) throws IOException,
                                                          ServletException
   {
      if ( !lockedProxyPort )
      {
         final Integer proxyPort = ( Integer ) request.getSession().getAttribute( "proxyport" );
         if ( proxyPort != null )
            this.intProxyPort = proxyPort;
         else
         {
            response.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                "Invalid civclient session. Please log in again." );
            return;
         }
      }

      final GetMethod proxyRequest = new GetMethod( this.getProxyURL( request ) );
      setProxyRequestHeaders( request,
                              proxyRequest );

      response.setHeader( "Cache-Control",
                          "no-cache" );
      response.setHeader( "Pragma",
                          "no-cache" );
      response.setDateHeader( "Expires",
                              0 );
      this.executeProxyRequest( proxyRequest,
                                request,
                                response );
   }

   @SuppressWarnings("deprecation")
   @Override
   public void doPost( final HttpServletRequest httpServletRequest,
                       final HttpServletResponse httpServletResponse ) throws IOException,
                                                                      ServletException
   {
      final PostMethod postMethodProxyRequest = new PostMethod( this.getProxyURL( httpServletRequest ) );
      setProxyRequestHeaders( httpServletRequest,
                              postMethodProxyRequest );

      final StringBuffer jb = new StringBuffer();
      String line = null;
      final BufferedReader reader = httpServletRequest.getReader();
      while ( ( line = reader.readLine() ) != null )
         jb.append( line );

      postMethodProxyRequest.setRequestEntity( new StringRequestEntity( jb.toString() ) );

      this.executeProxyRequest( postMethodProxyRequest,
                                httpServletRequest,
                                httpServletResponse );
   }

   @Override
   public void init( final ServletConfig servletConfig )
   {
      final String stringProxyHostNew = servletConfig.getInitParameter( "proxyHost" );
      if ( ( stringProxyHostNew == null )
            || ( stringProxyHostNew.length() == 0 ) )
         throw new IllegalArgumentException( "Proxy host not set, please set init-param 'proxyHost' in web.xml" );
      this.setProxyHost( stringProxyHostNew );
      final String stringProxyPortNew = servletConfig.getInitParameter( "proxyPort" );
      if ( ( stringProxyPortNew != null )
            && ( stringProxyPortNew.length() > 0 ) )
         this.setProxyPort( Integer.parseInt( stringProxyPortNew ) );
      final String stringProxyPathNew = servletConfig.getInitParameter( "proxyPath" );
      if ( ( stringProxyPathNew != null )
            && ( stringProxyPathNew.length() > 0 ) )
         this.setProxyPath( stringProxyPathNew );
   }

   private void executeProxyRequest( final HttpMethod httpMethodProxyRequest,
                                     final HttpServletRequest httpServletRequest,
                                     final HttpServletResponse httpServletResponse ) throws IOException,
                                                                                    ServletException
   {

      httpMethodProxyRequest.setFollowRedirects( false );
      int intProxyResponseCode = 0;
      try
      {
         intProxyResponseCode = client.executeMethod( httpMethodProxyRequest );
      }
      catch ( final IOException ioErr )
      {
         final OutputStream outputStreamClientResponse = httpServletResponse.getOutputStream();
         httpServletResponse.setStatus( 502 );
         outputStreamClientResponse.write( ioErr.getMessage().getBytes() );
         httpMethodProxyRequest.releaseConnection();
         return;
      }

      if ( ( intProxyResponseCode >= HttpServletResponse.SC_MULTIPLE_CHOICES /* 300 */)
            && ( intProxyResponseCode < HttpServletResponse.SC_NOT_MODIFIED /* 304 */) )
      {
         final String stringStatusCode = Integer.toString( intProxyResponseCode );
         final String stringLocation = httpMethodProxyRequest.getResponseHeader( LOCATION_HEADER ).getValue();
         if ( stringLocation == null )
         {
            httpMethodProxyRequest.releaseConnection();
            throw new ServletException( "Recieved status code: "
                  + stringStatusCode + " but no " + LOCATION_HEADER + " header was found in the response" );
         }
         final StringBuffer buffer = new StringBuffer();

         buffer.append( httpServletRequest.getServerName() );
         if ( httpServletRequest.getServerPort() != 80 )
         {
            buffer.append( ':' );
            buffer.append( httpServletRequest.getServerPort() );
         }
         buffer.append( httpServletRequest.getContextPath() );
         httpServletResponse.sendRedirect( stringLocation.replace( getProxyHostAndPort()
                                                                         + this.getProxyPath(),
                                                                   buffer.toString() ) );
         httpMethodProxyRequest.releaseConnection();
         return;
      }
      else if ( intProxyResponseCode == HttpServletResponse.SC_NOT_MODIFIED )
      {
         httpServletResponse.setIntHeader( CONTENT_LENGTH_HEADER_NAME,
                                           0 );
         httpServletResponse.setStatus( HttpServletResponse.SC_NOT_MODIFIED );
         httpMethodProxyRequest.releaseConnection();
         return;
      }

      httpServletResponse.setStatus( intProxyResponseCode );
      final Header[] headerArrayResponse = httpMethodProxyRequest.getResponseHeaders();
      for ( final Header header : headerArrayResponse )
         httpServletResponse.setHeader( header.getName(),
                                        header.getValue() );

      final InputStream inputStreamProxyResponse = httpMethodProxyRequest.getResponseBodyAsStream();
      final BufferedInputStream bufferedInputStream = new BufferedInputStream( inputStreamProxyResponse );
      final OutputStream outputStreamClientResponse = httpServletResponse.getOutputStream();
      int intNextByte;
      while ( ( intNextByte = bufferedInputStream.read() ) != -1 )
         outputStreamClientResponse.write( intNextByte );
      httpMethodProxyRequest.releaseConnection();

   }

   private String getProxyHost()
   {
      return this.stringProxyHost;
   }

   private String getProxyHostAndPort()
   {
      if ( this.getProxyPort() == 80 )
         return this.getProxyHost();
      else
         return this.getProxyHost()
               + ":" + this.getProxyPort();
   }

   private String getProxyPath()
   {
      return this.stringProxyPath;
   }

   private int getProxyPort()
   {
      return this.intProxyPort;
   }

   private String getProxyURL( final HttpServletRequest httpServletRequest )
   {
      final StringBuffer buffer = new StringBuffer();

      buffer.append( "http://" );
      buffer.append( this.getProxyHostAndPort() );
      if ( !this.getProxyPath().equalsIgnoreCase( "" ) )
         buffer.append( this.getProxyPath() );

      if ( httpServletRequest.getQueryString() != null )
      {
         buffer.append( '?' );
         buffer.append( httpServletRequest.getQueryString() );
      }
      return buffer.toString();
   }

   private void setProxyHost( final String stringProxyHostNew )
   {
      this.stringProxyHost = stringProxyHostNew;
   }

   private void setProxyPath( final String stringProxyPathNew )
   {
      this.stringProxyPath = stringProxyPathNew;
   }

   private void setProxyPort( final int intProxyPortNew )
   {
      this.intProxyPort = intProxyPortNew;
      this.lockedProxyPort = true;
   }

   private void setProxyRequestHeaders( final HttpServletRequest httpServletRequest,
                                        final HttpMethod httpMethodProxyRequest )
   {
      final Enumeration< String > enumerationOfHeaderNames = httpServletRequest.getHeaderNames();
      while ( enumerationOfHeaderNames.hasMoreElements() )
      {
         final String stringHeaderName = enumerationOfHeaderNames.nextElement();
         if ( stringHeaderName.equalsIgnoreCase( CONTENT_LENGTH_HEADER_NAME ) )
            continue;
         final Enumeration< String > enumerationOfHeaderValues = httpServletRequest.getHeaders( stringHeaderName );
         while ( enumerationOfHeaderValues.hasMoreElements() )
         {
            String stringHeaderValue = enumerationOfHeaderValues.nextElement();
            if ( stringHeaderName.equalsIgnoreCase( HOST_HEADER_NAME ) )
               stringHeaderValue = getProxyHostAndPort();
            final Header header = new Header( stringHeaderName, stringHeaderValue );
            httpMethodProxyRequest.setRequestHeader( header );
         }
      }
   }
}
