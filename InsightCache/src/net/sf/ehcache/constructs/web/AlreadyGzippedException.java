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

import net.sf.ehcache.CacheException;

public class AlreadyGzippedException extends CacheException
{

   /**
    * 
    */
   private static final long serialVersionUID = 1L;

   /**
    * Constructor for the exception
    */
   public AlreadyGzippedException()
   {
      super();
   }

   /**
    * Constructs an exception with the message given
    * 
    * @param message the message
    */
   public AlreadyGzippedException( final String message )
   {
      super( message );
   }
}
