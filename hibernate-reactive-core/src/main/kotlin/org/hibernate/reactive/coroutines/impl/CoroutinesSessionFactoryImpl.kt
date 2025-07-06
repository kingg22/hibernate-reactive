/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.impl

import io.vertx.kotlin.coroutines.dispatcher
import jakarta.persistence.metamodel.Metamodel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.hibernate.Cache
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
import org.hibernate.reactive.coroutines.internal.set
import org.hibernate.reactive.coroutines.internal.withHibernateContext
import org.hibernate.reactive.logging.impl.Log
import org.hibernate.reactive.logging.impl.LoggerFactory
import org.hibernate.reactive.pool.ReactiveConnection
import org.hibernate.reactive.pool.ReactiveConnectionPool
import org.hibernate.reactive.session.impl.ReactiveSessionImpl
import org.hibernate.reactive.session.impl.ReactiveStatelessSessionImpl
import org.hibernate.reactive.vertx.VertxInstance
import org.hibernate.service.ServiceRegistry
import org.hibernate.stat.Statistics
import java.lang.invoke.MethodHandles
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

class CoroutinesSessionFactoryImpl(
    private val delegate: SessionFactoryImpl,
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
    @JvmSynthetic
    internal val context = delegate.serviceRegistry.requireService(Context::class.java)
    private val connectionPool = delegate.serviceRegistry.requireService(ReactiveConnectionPool::class.java)
    private val contextKeyForSession: BaseKey<Coroutines.Session> =
        BaseKey(Coroutines.Session::class.java, delegate.uuid)
    private val contextKeyForStatelessSession: BaseKey<Coroutines.StatelessSession> =
        BaseKey(Coroutines.StatelessSession::class.java, delegate.uuid)
    private val log: Log =
        LoggerFactory.make(
            Log::class.java,
            MethodHandles
                .lookup(),
        )

    /** Run all coroutines with Vertx dispatcher, assume is in event loop and vertx propagate it context. */
    @OptIn(RequireHibernateReactiveContext::class)
    private val dispatcher: CoroutineDispatcher =
        delegate.serviceRegistry
            .requireService(VertxInstance::class.java)
            .vertx
            .dispatcher()

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
    override suspend fun getCurrentSession(): Coroutines.Session? = context.safeGet(contextKeyForSession, dispatcher)

    @OptIn(RequireHibernateReactiveContext::class)
    override suspend fun getCurrentStatelessSession() = context.safeGet(contextKeyForStatelessSession, dispatcher)

    override fun close() = delegate.close()

    override fun isOpen(): Boolean = delegate.isOpen

    // Implementations
    override suspend fun openSession(): Coroutines.Session = openSessionInternal(null, false)

    override suspend fun openSession(tenantId: String?): Coroutines.Session = openSessionInternal(tenantId, true)

    private suspend fun openSessionInternal(
        tenantId: String?,
        withTenant: Boolean,
    ): Coroutines.Session =
        withContext(dispatcher) {
            val options = options(tenantId, withTenant)
            val tenantIdentifier = if (withTenant) tenantId else options.tenantIdentifier
            val reactiveConnection = connection(tenantIdentifier)
            val session =
                create(reactiveConnection) {
                    // THIS is core of problem, session impl save the associatedWorkThread and verify it,
                    // coroutines don't guarantee the thread
                    ReactiveSessionImpl(delegate, options, reactiveConnection)
                }
            CoroutinesSessionImpl(session, this@CoroutinesSessionFactoryImpl, dispatcher)
        }

    // stateless
    override suspend fun openStatelessSession(): Coroutines.StatelessSession = openSessionStatelessInternal(null, false)

    override suspend fun openStatelessSession(tenantId: String?): Coroutines.StatelessSession = openSessionStatelessInternal(tenantId, true)

    @OptIn(RequireHibernateReactiveContext::class)
    private suspend fun openSessionStatelessInternal(
        tenantId: String?,
        withTenant: Boolean,
    ): Coroutines.StatelessSession =
        withContext(dispatcher) {
            val options = options(tenantId, withTenant)
            val tenantIdentifier = if (withTenant) tenantId else options.tenantIdentifier
            val reactiveConnection = connection(tenantIdentifier)
            val session =
                create(reactiveConnection) {
                    // May be converted a problem because is a task check the thread
                    ReactiveStatelessSessionImpl(delegate, options, reactiveConnection)
                }
            CoroutinesStatelessSessionImpl(session, this@CoroutinesSessionFactoryImpl, dispatcher)
        }

    // with session
    @OptIn(RequireHibernateReactiveContext::class)
    override suspend fun <T> withSession(work: suspend (Coroutines.Session) -> T): T {
        val current: Coroutines.Session? = withHibernateContext(dispatcher) { context[contextKeyForSession] }
        return if (current != null && current.isOpen()) {
            log.debug(REUSING_SESSION)
            // Resume the work in the hibernate context because
            // use of the reactive Session from a different Thread than the one which was used to open the reactive Session
            // See it on InternalStateAssertions.assertCurrentThreadMatches, used on ReactiveSessionImpl
            // Resume the session with his dispatcher
            withActiveSession(current, work, contextKeyForSession, (current as CoroutinesSessionImpl).dispatcher)
        } else {
            log.debug(OPENING_NEW_SESSION)
            // Force a nonnull context?
            val session = openSession()
            withSession(session, work, contextKeyForSession, (session as CoroutinesSessionImpl).dispatcher)
        }
    }

    @OptIn(RequireHibernateReactiveContext::class)
    override suspend fun <T> withSession(
        tenantId: String,
        work: suspend (Coroutines.Session) -> T,
    ): T {
        val key: Context.Key<Coroutines.Session> = MultitenantKey(contextKeyForSession, tenantId)
        val current: Coroutines.Session? = withHibernateContext(dispatcher) { context[key] }
        return if (current != null && current.isOpen()) {
            log.debugf(REUSING_TENANT_SESSION, tenantId)
            withActiveSession(current, work, key, (current as CoroutinesSessionImpl).dispatcher)
        } else {
            log.debugf(OPENING_NEW_TENANT_SESSION, tenantId)
            val session = openSession(tenantId)
            withSession(session, work, key, (session as CoroutinesSessionImpl).dispatcher)
        }
    }

    // with stateless
    @OptIn(RequireHibernateReactiveContext::class)
    override suspend fun <T> withStatelessSession(work: suspend (Coroutines.StatelessSession) -> T): T {
        val current: Coroutines.StatelessSession? = withContext(dispatcher) { context[contextKeyForStatelessSession] }
        return if (current != null && current.isOpen()) {
            log.debug(REUSING_STATELESS_SESSION)
            withActiveSession(
                current,
                work,
                contextKeyForStatelessSession,
                (current as CoroutinesStatelessSessionImpl).dispatcher,
            )
        } else {
            log.debug(OPENING_NEW_STATELESS_SESSION)
            val statelessSession = openStatelessSession()
            withSession(
                statelessSession,
                work,
                contextKeyForStatelessSession,
                (statelessSession as CoroutinesStatelessSessionImpl).dispatcher,
            )
        }
    }

    @OptIn(RequireHibernateReactiveContext::class)
    override suspend fun <T> withStatelessSession(
        tenantId: String?,
        work: suspend (Coroutines.StatelessSession) -> T,
    ): T {
        val key: Context.Key<Coroutines.StatelessSession> = MultitenantKey(contextKeyForStatelessSession, tenantId)
        val current: Coroutines.StatelessSession? = withHibernateContext(dispatcher) { context[key] }
        return if (current != null && current.isOpen()) {
            log.debugf(REUSING_TENANT_STATELESS_SESSION, tenantId)
            withActiveSession(current, work, key, (current as CoroutinesStatelessSessionImpl).dispatcher)
        } else {
            log.debugf(OPENING_NEW_TENANT_STATELESS_SESSION, tenantId)
            val statelessSession = openStatelessSession(tenantId)
            withSession(
                statelessSession,
                work,
                key,
                (statelessSession as CoroutinesStatelessSessionImpl).dispatcher,
            )
        }
    }

    // private helpers
    private fun options(
        tenantIdentifier: String? = null,
        withTenant: Boolean = false,
    ) = SessionFactoryImpl.SessionBuilderImpl(delegate).apply {
        if (withTenant) {
            @Suppress("DEPRECATION", "removal")
            this.tenantIdentifier(tenantIdentifier)
        }
    }

    @OptIn(RequireHibernateReactiveContext::class)
    private suspend fun connection(tenantId: String?): ReactiveConnection {
        assertUseOnEventLoop()
        return if (tenantId == null) {
            connectionPool.getConnection().safeAwait()
        } else {
            connectionPool.getConnection(tenantId).safeAwait()
        }
    }

    @OptIn(RequireHibernateReactiveContext::class, ExperimentalContracts::class)
    private suspend fun <S> create(
        connection: ReactiveConnection,
        supplier: () -> S,
    ): S {
        contract { callsInPlace(supplier, InvocationKind.EXACTLY_ONCE) }
        return try {
            // ReactiveSessionImpl and ReactiveStatelessSessionImpl need to be called in event loop
            supplier()
        } catch (e: Throwable) {
            // This use NonCancellable job, don't change the dispatcher
            // At 4 july 2025 is safe call this outside of hibernate context
            withContext(NonCancellable) { connection.close().safeAwait() }
            throw e
        }
    }

    /** Put in context the session and delegate await to close and remove from the context */
    @OptIn(RequireHibernateReactiveContext::class, ExperimentalContracts::class)
    private suspend fun <S : Coroutines.Closeable, T> withSession(
        session: S,
        work: suspend (S) -> T,
        contextKey: Context.Key<S>,
        contextDispatcher: CoroutineContext,
    ): T {
        contract { callsInPlace(work, InvocationKind.EXACTLY_ONCE) }
        // Use hibernate context because operation with it may fail if No Vert.x context active
        withContext(contextDispatcher) { context[contextKey] = session }
        return try {
            work(session)
        } finally {
            withContext(contextDispatcher) { context.remove(contextKey) }
            try {
                session.close()
            } catch (_: Throwable) {
                // Only throw the original exception in case an error occurs while closing the session
            }
        }
    }

    /**
     * Prevent resource leak and blocking the event loop with an active session.
     * This method doesn't put in the context the key, only remove it.
     */
    @OptIn(RequireHibernateReactiveContext::class, ExperimentalContracts::class)
    private suspend fun <S : Coroutines.Closeable, T> withActiveSession(
        session: S,
        work: suspend (S) -> T,
        contextKey: Context.Key<S>,
        contextDispatcher: CoroutineContext,
    ): T {
        contract { callsInPlace(work, InvocationKind.EXACTLY_ONCE) }
        withContext(contextDispatcher) { checkNotNull(context[contextKey]) }

        return try {
            work(session)
        } finally {
            withContext(contextDispatcher) { context.remove(contextKey) }
            try {
                session.close()
            } catch (_: Throwable) {
                // Only throw the original exception in case an error occurs while closing the session
            }
        }
    }
}
