/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.impl

import jakarta.persistence.metamodel.Metamodel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.hibernate.Cache
import org.hibernate.internal.SessionCreationOptions
import org.hibernate.internal.SessionFactoryImpl
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.reactive.common.InternalStateAssertions.assertUseOnEventLoop
import org.hibernate.reactive.common.spi.Implementor
import org.hibernate.reactive.context.Context
import org.hibernate.reactive.context.impl.BaseKey
import org.hibernate.reactive.context.impl.MultitenantKey
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.internal.RequireHibernateReactiveContext
import org.hibernate.reactive.coroutines.internal.safeAwait
import org.hibernate.reactive.coroutines.internal.safeGet
import org.hibernate.reactive.coroutines.internal.withHibernateContext
import org.hibernate.reactive.logging.impl.Log
import org.hibernate.reactive.logging.impl.LoggerFactory
import org.hibernate.reactive.pool.ReactiveConnection
import org.hibernate.reactive.pool.ReactiveConnectionPool
import org.hibernate.reactive.session.impl.ReactiveSessionImpl
import org.hibernate.reactive.session.impl.ReactiveStatelessSessionImpl
import org.hibernate.service.ServiceRegistry
import org.hibernate.stat.Statistics
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
class CoroutinesSessionFactoryImpl
    @JvmOverloads
    constructor(
        private val delegate: SessionFactoryImpl,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : Coroutines.SessionFactory,
        Implementor {
        companion object {
            private const val REUSING_SESSION =
                "Reusing existing open Coroutines.Session which was found in the current Vert.x context"
            private const val REUSING_STATELESS_SESSION =
                "Reusing existing open Coroutines.StatelessSession which was found in the current Vert.x context"
            private const val REUSING_TENANT_SESSION =
                "Reusing existing open Coroutines.Session which was found in the current Vert.x context for current tenant '%s'"
            private const val REUSING_TENANT_STATELESS_SESSION =
                "Reusing existing open Coroutines.StatelessSession which was found in the current Vert.x context for current tenant '%s'"
            private const val OPENING_NEW_SESSION =
                "No existing open Coroutines.Session was found in the current Vert.x context: opening a new instance"
            private const val OPENING_NEW_TENANT_SESSION =
                "No existing open Coroutines.Session was found in the current Vert.x context for current tenant '%s': opening a new instance"
            private const val OPENING_NEW_TENANT_STATELESS_SESSION =
                "No existing open Coroutines.Session was found in the current Vert.x context for current tenant '%s': opening a new instance"
            private const val OPENING_NEW_STATELESS_SESSION =
                "No existing open Coroutines.StatelessSession was found in the current Vert.x context: opening a new instance"
        }

        @RequireHibernateReactiveContext
        private val context = delegate.serviceRegistry.getService(Context::class.java)
        private val connectionPool = delegate.serviceRegistry.getService(ReactiveConnectionPool::class.java)
        private val contextKeyForSession: BaseKey<Coroutines.Session> =
            BaseKey(Coroutines.Session::class.java, delegate.uuid)
        private val contextKeyForStatelessSession: BaseKey<Coroutines.StatelessSession> =
            BaseKey(Coroutines.StatelessSession::class.java, delegate.uuid)
        private val log: Log =
            LoggerFactory.make(
                Log::class.java,
                java.lang.invoke.MethodHandles
                    .lookup(),
            )

        @OptIn(RequireHibernateReactiveContext::class)
        private val dispatcher = context?.asCoroutineDispatcher()

        // delegate and getters of properties
        override fun getUuid(): String? = delegate.uuid

        override fun getServiceRegistry(): ServiceRegistry? = delegate.serviceRegistry

        @RequireHibernateReactiveContext
        override fun getContext(): Context? = context

        override fun getCriteriaBuilder(): HibernateCriteriaBuilder = delegate.criteriaBuilder

        override fun getMetamodel(): Metamodel? = delegate.metamodel

        override fun getCache(): Cache? = delegate.cache

        override fun getStatistics(): Statistics? = delegate.statistics

        @OptIn(RequireHibernateReactiveContext::class)
        override suspend fun getCurrentSession(): Coroutines.Session? = context?.safeGet(contextKeyForSession, dispatcher)

        @OptIn(RequireHibernateReactiveContext::class)
        override suspend fun getCurrentStatelessSession(): Coroutines.StatelessSession? =
            context?.safeGet(contextKeyForStatelessSession, dispatcher)

        override fun close() = delegate.close()

        override fun isOpen(): Boolean = delegate.isOpen

        // Implementations
        override suspend fun openSession(): Coroutines.Session = openSessionInternal(null, false)

        override suspend fun openSession(tenantId: String?): Coroutines.Session = openSessionInternal(tenantId, true)

        private suspend fun openSessionInternal(
            tenantId: String?,
            withTenant: Boolean,
        ): Coroutines.Session {
            val options = options(tenantId, withTenant)
            val tenantIdentifier = if (withTenant) tenantId else options.tenantIdentifier
            return withContext(ioDispatcher) {
                // First change to io, after change to vertx thread, this prevents block when await
                withHibernateContext(checkNotNull(dispatcher)) {
                    val reactiveConnection = connection(tenantIdentifier)
                    val session =
                        create(reactiveConnection) {
                            // THIS is core of problem, session impl save the associatedWorkThread and verify it,
                            // coroutines don't guarantee the thread
                            ReactiveSessionImpl(delegate, options, reactiveConnection)
                        }
                    @OptIn(RequireHibernateReactiveContext::class) // Is safe pass as argument
                    CoroutinesSessionImpl(session, this@CoroutinesSessionFactoryImpl, dispatcher)
                }
            }
        }

        // stateless
        override suspend fun openStatelessSession(): Coroutines.StatelessSession = openSessionStatelessInternal(null, false)

        override suspend fun openStatelessSession(tenantId: String?): Coroutines.StatelessSession =
            openSessionStatelessInternal(tenantId, true)

        private suspend fun openSessionStatelessInternal(
            tenantId: String?,
            withTenant: Boolean,
        ): Coroutines.StatelessSession {
            val options = options(tenantId, withTenant)
            val tenantIdentifier = if (withTenant) tenantId else options.tenantIdentifier
            return withContext(ioDispatcher) {
                withHibernateContext(checkNotNull(dispatcher)) {
                    val reactiveConnection = connection(tenantIdentifier)
                    val session =
                        create(reactiveConnection) {
                            // May be converted a problem because is a task check the thread
                            ReactiveStatelessSessionImpl(delegate, options, reactiveConnection)
                        }
                    @OptIn(RequireHibernateReactiveContext::class) // Is safe pass as argument
                    CoroutinesStatelessSessionImpl(session, this@CoroutinesSessionFactoryImpl, dispatcher)
                }
            }
        }

        // with session
        override suspend fun <T> withSession(work: suspend (Coroutines.Session) -> T): T =
            withContext(ioDispatcher) {
                withHibernateContext(checkNotNull(dispatcher)) {
                    @OptIn(RequireHibernateReactiveContext::class)
                    val current = context?.get(contextKeyForSession)
                    if (current != null && current.isOpen()) {
                        log.debug(REUSING_SESSION)
                        // Resume the work in the hibernate context because
                        // use of the reactive Session from a different Thread than the one which was used to open the reactive Session
                        // See it on InternalStateAssertions.assertCurrentThreadMatches, used on ReactiveSessionImpl
                        withActiveSession(current, work, contextKeyForSession)
                    } else {
                        log.debug(OPENING_NEW_SESSION)
                        // Force a nonnull context?
                        withSession(openSession(), work, contextKeyForSession)
                    }
                }
            }

        override suspend fun <T> withSession(
            tenantId: String,
            work: suspend (Coroutines.Session) -> T,
        ): T =
            withContext(ioDispatcher) {
                withHibernateContext(checkNotNull(dispatcher)) {
                    val key: Context.Key<Coroutines.Session> = MultitenantKey(contextKeyForSession, tenantId)

                    @OptIn(RequireHibernateReactiveContext::class)
                    val current = context?.get(key)
                    if (current != null && current.isOpen()) {
                        log.debugf(REUSING_TENANT_SESSION, tenantId)
                        withActiveSession(current, work, key)
                    } else {
                        log.debugf(OPENING_NEW_TENANT_SESSION, tenantId)
                        withSession(openSession(tenantId), work, key)
                    }
                }
            }

        // with stateless
        override suspend fun <T> withStatelessSession(work: suspend (Coroutines.StatelessSession) -> T): T =
            withContext(ioDispatcher) {
                withHibernateContext(checkNotNull(dispatcher)) {
                    @OptIn(RequireHibernateReactiveContext::class)
                    val current = context?.get(contextKeyForStatelessSession)
                    if (current != null && current.isOpen()) {
                        log.debug(REUSING_STATELESS_SESSION)
                        withActiveSession(current, work, contextKeyForStatelessSession)
                    } else {
                        log.debug(OPENING_NEW_STATELESS_SESSION)
                        withSession(openStatelessSession(), work, contextKeyForStatelessSession)
                    }
                }
            }

        override suspend fun <T> withStatelessSession(
            tenantId: String?,
            work: suspend (Coroutines.StatelessSession) -> T,
        ): T =
            withContext(ioDispatcher) {
                withHibernateContext(checkNotNull(dispatcher)) {
                    val key: Context.Key<Coroutines.StatelessSession> = MultitenantKey(contextKeyForStatelessSession, tenantId)

                    @OptIn(RequireHibernateReactiveContext::class)
                    val current = context?.get(key)
                    if (current != null && current.isOpen()) {
                        log.debugf(REUSING_TENANT_STATELESS_SESSION, tenantId)
                        withActiveSession(current, work, key)
                    } else {
                        log.debugf(OPENING_NEW_TENANT_STATELESS_SESSION, tenantId)
                        withSession(openStatelessSession(tenantId), work, key)
                    }
                }
            }

        // private helpers
        private fun options(
            tenantIdentifier: String? = null,
            withTenant: Boolean = false,
        ): SessionCreationOptions =
            SessionFactoryImpl.SessionBuilderImpl(delegate).apply {
                if (withTenant) {
                    @Suppress("DEPRECATION", "removal")
                    this.tenantIdentifier(tenantIdentifier)
                }
            }

        @OptIn(RequireHibernateReactiveContext::class)
        private suspend fun connection(tenantId: String?): ReactiveConnection {
            // force checkNotNull?
            // Workaround to await in different thread to prevent blocking the event loop
            assertUseOnEventLoop()
            val pool = checkNotNull(connectionPool)
            return if (tenantId == null) {
                pool.getConnection().safeAwait()
            } else {
                pool.getConnection(tenantId).safeAwait()
            }
        }

        @OptIn(RequireHibernateReactiveContext::class)
        private suspend fun <S> create(
            connection: ReactiveConnection,
            supplier: () -> S,
        ): S {
            contract {
                callsInPlace(supplier, InvocationKind.EXACTLY_ONCE)
            }
            return try {
                // ReactiveSessionImpl and ReactiveStatelessSessionImpl need to be called in event loop
                withContext(checkNotNull(dispatcher)) { supplier() }
            } catch (e: Throwable) {
                // This use NonCancellable job, don't change the dispatcher
                // At 4 july 2025 is safe call this outside of hibernate context
                withContext(NonCancellable) { connection.close().safeAwait() }
                throw e
            }
        }

        /** Put in context the session and delegate the rest to [withActiveSession] */
        @OptIn(RequireHibernateReactiveContext::class)
        private suspend fun <S : Coroutines.Closeable, T> withSession(
            session: S,
            work: suspend (S) -> T,
            contextKey: Context.Key<S>,
        ): T {
            @Suppress("WRONG_INVOCATION_KIND")
            contract {
                callsInPlace(work, InvocationKind.EXACTLY_ONCE)
            }
            // Use hibernate context because operation with it may fail if No Vert.x context active
            return withContext(checkNotNull(dispatcher)) {
                checkNotNull(context).put(contextKey, session)
                withActiveSession(session, work, contextKey)
            }
        }

        /**
         * Prevent resource leak and blocking the event loop with an active session.
         * This method doesn't put in the context the key, only remove it.
         */
        @OptIn(RequireHibernateReactiveContext::class)
        private suspend fun <S : Coroutines.Closeable, T> withActiveSession(
            session: S,
            work: suspend (S) -> T,
            contextKey: Context.Key<S>,
        ): T {
            @Suppress("WRONG_INVOCATION_KIND")
            contract {
                callsInPlace(work, InvocationKind.EXACTLY_ONCE)
            }
            checkNotNull(context)
            checkNotNull(context[contextKey])
            return withHibernateContext(checkNotNull(dispatcher)) {
                try {
                    work(session)
                } finally {
                    context.remove(contextKey)
                    try {
                        withContext(NonCancellable) { session.close() }
                    } catch (_: Throwable) {
                        // Only throw the original exception in case an error occurs while closing the session
                    }
                }
            }
        }
    }
