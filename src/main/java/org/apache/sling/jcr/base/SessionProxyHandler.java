/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.base;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Credentials;
import javax.jcr.Session;

/**
 * The session proxy handler creates session proxies to handle
 * the namespace mapping support if impersonate is called on
 * the session.
 */
public class SessionProxyHandler  {

    /** The array of proxied interfaces. */
    private Class<?>[] interfaces;

    /** The repository */
    private final AbstractSlingRepository repository;

    public SessionProxyHandler(final AbstractSlingRepository repo) {
        this.repository = repo;
    }

    /** Calculate the interfaces.
     * This is done only once - we simply assume that the same repository is
     * emitting session from the same class.
     */
    private Class<?>[] getInterfaces(final Class<?> sessionClass) {
        if ( interfaces == null ) {
            synchronized ( SessionProxyHandler.class ) {
                if ( interfaces == null ) {
                    final HashSet<Class<?>> workInterfaces = new HashSet<Class<?>>();

                    // Get *all* interfaces
                    guessWorkInterfaces( sessionClass, workInterfaces );

                    this.interfaces = workInterfaces.toArray( new Class[workInterfaces.size()] );

                }
            }
        }
        return interfaces;
    }

    /**
     * Create a proxy for the session.
     */
    public Session createProxy(final Session session) {
        final Class<?> sessionClass = session.getClass();
        return (Session)Proxy.newProxyInstance(sessionClass.getClassLoader(),
                getInterfaces(sessionClass),
                new SessionProxy(session, this.repository));

    }


    public static final class SessionProxy implements InvocationHandler {
        private final Session delegatee;
        private final AbstractSlingRepository repository;

        public SessionProxy(final Session delegatee, final AbstractSlingRepository repo) {
            this.delegatee = delegatee;
            this.repository = repo;
        }

        /**
         * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
         */
        public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable {
            if ( method.getName().equals("impersonate") && args != null && args.length == 1) {
                final Session session = this.delegatee.impersonate((Credentials)args[0]);
                this.repository.defineNamespacePrefixes(session);
                return new SessionProxy(session, this.repository);
            }
            try {
                return method.invoke(this.delegatee, args);
            } catch (InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        }
    }

    /**
     * Get a list of interfaces to proxy by scanning through
     * all interfaces a class implements.
     *
     * @param clazz           the class
     * @param workInterfaces  the set of current work interfaces
     */
    private void guessWorkInterfaces( final Class<?> clazz,
                                      final Set<Class<?>> workInterfaces ) {
        if ( null != clazz ) {
            addInterfaces( clazz.getInterfaces(), workInterfaces );

            guessWorkInterfaces( clazz.getSuperclass(), workInterfaces );
        }
    }

    /**
     * Get a list of interfaces to proxy by scanning through
     * all interfaces a class implements.
     *
     * @param classInterfaces the array of interfaces
     * @param workInterfaces  the set of current work interfaces
     */
    private void addInterfaces( final Class<?>[] classInterfaces,
                                final Set<Class<?>> workInterfaces ) {
        for ( int i = 0; i < classInterfaces.length; i++ ) {
            // to avoid problems we simply ignore all pre jsr 283 interfaces - once we
            // moved to jcr 2.0 completly we can remove this check
            if ( !classInterfaces[i].getName().startsWith("org.apache.jackrabbit.api.jsr283")) {
                workInterfaces.add( classInterfaces[i] );
            }
            addInterfaces(classInterfaces[i].getInterfaces(), workInterfaces);
        }
    }
}
