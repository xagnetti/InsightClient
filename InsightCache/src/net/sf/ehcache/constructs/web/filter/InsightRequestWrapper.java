package net.sf.ehcache.constructs.web.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class InsightRequestWrapper extends HttpServletRequestWrapper
{
   private final String body;

   public InsightRequestWrapper( final HttpServletRequest request ) throws IOException
   {
      super( request );
      final StringBuilder stringBuilder = new StringBuilder();
      BufferedReader bufferedReader = null;
      try
      {
         final InputStream inputStream = request.getInputStream();
         if ( inputStream != null )
         {
            bufferedReader = new BufferedReader( new InputStreamReader( inputStream ) );
            final char[] charBuffer = new char[ 128 ];
            int bytesRead = -1;
            while ( ( bytesRead = bufferedReader.read( charBuffer ) ) > 0 )
               stringBuilder.append( charBuffer,
                                     0,
                                     bytesRead );
         }
         else
            stringBuilder.append( "" );
      }
      catch ( final IOException ex )
      {
         throw ex;
      }
      finally
      {
         if ( bufferedReader != null )
            try
            {
               bufferedReader.close();
            }
            catch ( final IOException ex )
            {
               throw ex;
            }
      }
      body = stringBuilder.toString();
   }

   public String getBody()
   {
      return this.body;
   }

   @Override
   public ServletInputStream getInputStream() throws IOException
   {
      final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream( body.getBytes() );
      final ServletInputStream servletInputStream = new ServletInputStream()
      {
         @Override
         public int read() throws IOException
         {
            return byteArrayInputStream.read();
         }
      };
      return servletInputStream;
   }

   @Override
   public BufferedReader getReader() throws IOException
   {
      return new BufferedReader( new InputStreamReader( this.getInputStream() ) );
   }
}