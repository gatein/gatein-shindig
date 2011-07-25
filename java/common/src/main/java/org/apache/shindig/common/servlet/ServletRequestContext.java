/**
 * Copyright (C) 2011 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.apache.shindig.common.servlet;

import javax.servlet.ServletRequest;

/**
 * @author <a href="kien.nguyen@exoplatform.com">Kien Nguyen</a>
 * @version $Revision$
 */
public class ServletRequestContext
{

   public static void setRequestInfo(ServletRequest req)
   {
      String auth = req.getServerName() + ":" + req.getServerPort();
      String fullAuth = req.getScheme() + "://" + auth;
      authority.set(auth);
      fullAuthority.set(fullAuth);

      System.setProperty("authority", auth);
      System.setProperty("fullAuthority", fullAuth);
   }

   /**
    * A Thread Local holder for authority -- host + port
    */
   private static ThreadLocal<String> authority = new ThreadLocal<String>();

   /**
    * A Thread Local holder for full authority -- scheme + host + port
    */
   private static ThreadLocal<String> fullAuthority = new ThreadLocal<String>();

   public static String getAuthority()
   {

      String retVal = authority.get();
      if (retVal == null)
      {
         retVal = System.getProperty("authority");
         if (retVal == null)
         {
            retVal = getDefaultAuthority();
         }
      }
      return retVal;
   }

   private static String getDefaultAuthority()
   {

      String retVal = System.getProperty("defaultAuthority");
      if (retVal == null)
      {
         retVal = getServerHostname() + ":" + getServerPort();
         System.setProperty("defaultAuthority", retVal);
      }
      return retVal;

   }

   public static String getFullAuthority()
   {

      String retVal = fullAuthority.get();
      if (retVal == null)
      {
         retVal = System.getProperty("fullAuthority");
         if (retVal == null)
         {
            retVal = getDefaultFullAuthority();
         }
      }
      return retVal;

   }

   private static String getDefaultFullAuthority()
   {

      String retVal = System.getProperty("defaultFullAuthority");
      if (retVal != null)
      {
         retVal = "http://" + getServerHostname() + ":" + getServerPort();
         System.setProperty("defaultFullAuthority", retVal);
      }
      return retVal;

   }

   private static String getServerPort()
   {
      return System.getProperty("shindig.port") != null ? System.getProperty("shindig.port") : System
         .getProperty("jetty.port") != null ? System.getProperty("jetty.port") : "8080";
   }

   private static String getServerHostname()
   {
      return System.getProperty("shindig.host") != null ? System.getProperty("shindig.host") : System
         .getProperty("jetty.host") != null ? System.getProperty("jetty.host") : "localhost";
   }

}
