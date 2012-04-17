package com.adobe.insight;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

@WebServlet(value = "/ProxyServlet", initParams =
{ @WebInitParam(name = "proxyHost", value = "adobead-6tm1ol6.eur.adobe.com"),
 @WebInitParam(name = "proxyPort", value = "80"),
 @WebInitParam(name = "proxyPath", value = "/Profiles/Custom/API.query") })
public class ProxyServlet extends HttpServlet
{
   static MultiThreadedHttpConnectionManager connectionManager                 = new MultiThreadedHttpConnectionManager();
   static HttpClient                         client                            = new HttpClient( connectionManager );
   private static final long                 serialVersionUID                  = 1L;
   private static final String               STRING_CONTENT_LENGTH_HEADER_NAME = "Content-Length";
   private static final String               STRING_HOST_HEADER_NAME           = "Host";
   private static final String               STRING_LOCATION_HEADER            = "Location";
   private int                               intProxyPort                      = 80;
   private boolean                           lockedProxyPort                   = false;
   private String                            stringProxyHost;
   private String                            stringProxyPath                   = "";

   @Override
   public void doGet( final HttpServletRequest httpServletRequest,
                      final HttpServletResponse httpServletResponse ) throws IOException,
                                                                     ServletException
   {
      if ( !lockedProxyPort )
      {
         final Integer proxyPort = ( Integer ) httpServletRequest.getSession().getAttribute( "proxyport" );
         if ( proxyPort != null )
            this.intProxyPort = proxyPort;
         else
         {
            httpServletResponse.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                           "Invalid civclient session. Please log in again." );
            return;
         }
      }

      final GetMethod getMethodProxyRequest = new GetMethod( this.getProxyURL( httpServletRequest ) );
      setProxyRequestHeaders( httpServletRequest,
                              getMethodProxyRequest );

      httpServletResponse.setHeader( "Cache-Control",
                                     "no-cache" );
      httpServletResponse.setHeader( "Pragma",
                                     "no-cache" );
      httpServletResponse.setDateHeader( "Expires",
                                         0 );
      this.executeProxyRequest( getMethodProxyRequest,
                                httpServletRequest,
                                httpServletResponse );
   }

   @Override
   public void doPost( final HttpServletRequest httpServletRequest,
                       final HttpServletResponse httpServletResponse ) throws IOException,
                                                                      ServletException
   {
      final PostMethod postMethodProxyRequest = new PostMethod( this.getProxyURL( httpServletRequest ) );
      setProxyRequestHeaders( httpServletRequest,
                              postMethodProxyRequest );

      postMethodProxyRequest.setRequestBody( httpServletRequest.getInputStream() );

      httpServletResponse.setHeader( "Cache-Control",
                                     "no-cache" );
      httpServletResponse.setHeader( "Pragma",
                                     "no-cache" );
      httpServletResponse.setDateHeader( "Expires",
                                         0 );

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
         final String stringLocation = httpMethodProxyRequest.getResponseHeader( STRING_LOCATION_HEADER )
                                                             .getValue();
         if ( stringLocation == null )
         {
            httpMethodProxyRequest.releaseConnection();
            throw new ServletException( "Recieved status code: "
                  + stringStatusCode + " but no " + STRING_LOCATION_HEADER
                  + " header was found in the response" );
         }
         String stringMyHostName = httpServletRequest.getServerName();
         if ( httpServletRequest.getServerPort() != 80 )
            stringMyHostName += ":"
                  + httpServletRequest.getServerPort();
         stringMyHostName += httpServletRequest.getContextPath();
         httpServletResponse.sendRedirect( stringLocation.replace( getProxyHostAndPort()
                                                                         + this.getProxyPath(),
                                                                   stringMyHostName ) );
         httpMethodProxyRequest.releaseConnection();
         return;
      }
      else if ( intProxyResponseCode == HttpServletResponse.SC_NOT_MODIFIED )
      {
         httpServletResponse.setIntHeader( STRING_CONTENT_LENGTH_HEADER_NAME,
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
      String stringProxyURL = "http://"
            + this.getProxyHostAndPort();
      if ( !this.getProxyPath().equalsIgnoreCase( "" ) )
         stringProxyURL += this.getProxyPath();

      final String username = ""
            + httpServletRequest.getSession().getAttribute( "username" );
      final String password = ""
            + httpServletRequest.getSession().getAttribute( "password" );

      if ( httpServletRequest.getQueryString() != null )
         stringProxyURL += "?"
               + httpServletRequest.getQueryString() + "&username=" + username + "&password=" + password;
      return stringProxyURL;
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
         if ( stringHeaderName.equalsIgnoreCase( STRING_CONTENT_LENGTH_HEADER_NAME ) )
            continue;
         final Enumeration< String > enumerationOfHeaderValues = httpServletRequest.getHeaders( stringHeaderName );
         while ( enumerationOfHeaderValues.hasMoreElements() )
         {
            String stringHeaderValue = enumerationOfHeaderValues.nextElement();
            if ( stringHeaderName.equalsIgnoreCase( STRING_HOST_HEADER_NAME ) )
               stringHeaderValue = getProxyHostAndPort();
            final Header header = new Header( stringHeaderName, stringHeaderValue );
            httpMethodProxyRequest.setRequestHeader( header );
         }
      }
   }
}
