/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.impl

import jakarta.persistence.metamodel.Metamodel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.hibernate.Cache
import org.hibernate.engine.creation.internal.SessionBuilderImpl
import org.hibernate.engine.creation.internal.SessionCreationOptions
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.internal.SessionFactoryImpl
import org.hibernate.internal.SessionImpl
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.reactive.common.InternalStateAssertions.assertUseOnEventLoop
import org.hibernate.reactive.common.spi.Implementor
import org.hibernate.reactive.context.Context
import org.hibernate.reactive.context.impl.BaseKey
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.DelicateHibernateReactiveCoroutineApi
import org.hibernate.reactive.coroutines.ExperimentalHibernateReactiveCoroutineApi
import org.hibernate.reactive.coroutines.HibernateReactiveOpen
import org.hibernate.reactive.logging.impl.Log
import org.hibernate.reactive.logging.impl.LoggerFactory
import org.hibernate.reactive.pool.ReactiveConnection
import org.hibernate.reactive.pool.ReactiveConnectionPool
import org.hibernate.service.ServiceRegistry
import org.hibernate.stat.Statistics
import java.lang.invoke.MethodHandles
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@HibernateReactiveOpen
@ExperimentalHibernateReactiveCoroutineApi
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(DelicateHibernateReactiveCoroutineApi::class)
class CoroutinesSessionFactoryImpl(private val delegate: SessionFactoryImpl) :
    Coroutines.SessionFactory,
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

    private val log: Log = LoggerFactory.make(Log::class.java, MethodHandles.lookup())

    private val context: Context = delegate.serviceRegistry.requireService(Context::class.java)

    private val connectionPool: ReactiveConnectionPool = delegate.serviceRegistry.requireService(
        ReactiveConnectionPool::class.java,
    )

    private val contextKeyForSession: BaseKey<Coroutines.Session> =
        BaseKey(Coroutines.Session::class.java, delegate.uuid)

    private val contextKeyForStatelessSession: BaseKey<Coroutines.StatelessSession> =
        BaseKey(Coroutines.StatelessSession::class.java, delegate.uuid)

    // session
    override suspend fun openSession(): Coroutines.Session = TODO(
        """
            val options = options(tenantId, withTenant)
            val tenantIdentifier = if (withTenant) tenantId else options.tenantIdentifier
            val reactiveConnection = connection(tenantIdentifier)
            val session = create(reactiveConnection) {
                // THIS is core of problem, session impl save the associatedWorkThread and verify it,
                // coroutines don't guarantee the thread
                ReactiveSessionImpl(delegate, options, reactiveConnection)
            }
            CoroutinesSessionImpl(session, this),
        """.trimMargin(),
    )

    override suspend fun openSession(tenantId: String?): Coroutines.Session = TODO()

    // stateless
    override suspend fun openStatelessSession(): Coroutines.StatelessSession = TODO(
        """
            val options = options(tenantId, withTenant)
            val tenantIdentifier = if (withTenant) tenantId else options.tenantIdentifier
            val reactiveConnection = connection(tenantIdentifier)
            val session = create(reactiveConnection) {
                // May be converted a problem because is a task check the thread
                ReactiveStatelessSessionImpl(delegate, options, reactiveConnection)
            }
            CoroutinesStatelessSessionImpl(session, this)
        """.trimIndent(),
    )

    override suspend fun openStatelessSession(tenantId: String?): Coroutines.StatelessSession = TODO()

    // with session
    override suspend fun <T> withSession(work: suspend (Coroutines.Session) -> T): T = TODO(
        """
            val current = getCurrentSession()
            return if (current != null && current.isOpen()) {
                log.debug(REUSING_SESSION)
                // Resume the work in the hibernate context because
                // use of the reactive Session from a different Thread than the one which was used to open the reactive Session
                // See it on InternalStateAssertions.assertCurrentThreadMatches, used on ReactiveSessionImpl
                // Resume the session with his dispatcher
                withActiveSession(current, work, contextKeyForSession)
            } else {
                log.debug(OPENING_NEW_SESSION)
                val session = openSession()
                withSession(session, work, contextKeyForSession)
            }
        """.trimIndent(),
    )

    override suspend fun <T> withSession(tenantId: String, work: suspend (Coroutines.Session) -> T): T = TODO(
        """
            val key: Context.Key<Coroutines.Session> = MultitenantKey(contextKeyForSession, tenantId)
            val current: Coroutines.Session? = context[key]
            return if (current != null && current.isOpen()) {
                log.debugf(REUSING_TENANT_SESSION, tenantId)
                withActiveSession(current, work, key)
            } else {
                log.debugf(OPENING_NEW_TENANT_SESSION, tenantId)
                val session = openSession(tenantId)
                withSession(session, work, key)
            }
        """.trimIndent(),
    )

    // with stateless
    override suspend fun <T> withStatelessSession(work: suspend (Coroutines.StatelessSession) -> T): T = TODO(
        """
            val current = getCurrentStatelessSession()
            return if (current != null && current.isOpen()) {
                log.debug(REUSING_STATELESS_SESSION)
                withActiveSession(current, work, contextKeyForStatelessSession)
            } else {
                log.debug(OPENING_NEW_STATELESS_SESSION)
                val statelessSession = openStatelessSession()
                withSession(statelessSession, work, contextKeyForStatelessSession)
            }
        """.trimIndent(),
    )

    override suspend fun <T> withStatelessSession(
        tenantId: String?,
        work: suspend (Coroutines.StatelessSession) -> T,
    ): T = TODO(
        """
            val key: Context.Key<Coroutines.StatelessSession> = MultitenantKey(contextKeyForStatelessSession, tenantId)
            val current: Coroutines.StatelessSession? = context[key]
            return if (current != null && current.isOpen()) {
                log.debugf(REUSING_TENANT_STATELESS_SESSION, tenantId)
                withActiveSession(current, work, key)
            } else {
                log.debugf(OPENING_NEW_TENANT_STATELESS_SESSION, tenantId)
                val statelessSession = openStatelessSession(tenantId)
                withSession(statelessSession, work, key)
            }
        """.trimIndent(),
    )

    override fun getCriteriaBuilder(): HibernateCriteriaBuilder = delegate.criteriaBuilder

    override fun getMetamodel(): Metamodel? = delegate.metamodel

    override fun getCache(): Cache? = delegate.cache

    override fun getStatistics(): Statistics? = delegate.statistics

    override fun getCurrentSession(): Coroutines.Session? = context[contextKeyForSession]

    override fun getCurrentStatelessSession(): Coroutines.StatelessSession? = context[contextKeyForStatelessSession]

    override fun close() {
        delegate.close()
    }

    override fun isOpen(): Boolean = delegate.isOpen

    override fun getUuid(): String? = delegate.uuid

    override fun getServiceRegistry(): ServiceRegistry? = delegate.serviceRegistry

    override fun getContext(): Context = context

    // private helpers
    private fun options(): SessionCreationOptions = object : SessionBuilderImpl(delegate) {
        override fun createSession(): SessionImplementor = SessionImpl(delegate, this)
    }

    private fun options(tenantId: String?): SessionCreationOptions = object : SessionBuilderImpl(delegate) {
        override fun createSession(): SessionImplementor = SessionImpl(delegate, this)
        override fun getTenantIdentifierValue(): Any? = tenantId
    }

    private suspend fun connection(tenantId: String?): ReactiveConnection {
        assertUseOnEventLoop()
        // TODO this is await of kotlinx.coroutines.jvm check if apply
        return if (tenantId == null) {
            connectionPool.getConnection().await()
        } else {
            connectionPool.getConnection(tenantId).await()
        }
    }

    private final suspend fun <S> create(connection: ReactiveConnection, supplier: () -> S): S {
        contract { callsInPlace(supplier, InvocationKind.EXACTLY_ONCE) }
        return try {
            // ReactiveSessionImpl and ReactiveStatelessSessionImpl need to be called in event loop
            supplier()
        } catch (e: Throwable) {
            // This use NonCancellable job, don't change the dispatcher
            // At 4 july 2025 is safe call this outside of hibernate context
            withContext(NonCancellable) { connection.close() }
            throw e
        }
    }

    /** Put in context the session and delegate await to close and remove from the context */
    private final suspend fun <S : Coroutines.Closeable, T> withSession(
        session: S,
        work: suspend (S) -> T,
        contextKey: Context.Key<S>,
    ): T {
        contract { callsInPlace(work, InvocationKind.EXACTLY_ONCE) }
        context[contextKey] = session

        return try {
            work(session)
        } finally {
            context.remove(contextKey)
            try {
                session.close()
            } catch (_: Throwable) {
                // TODO need to check if is safe catch CancellationException of coroutines
                // currentCoroutineContext().ensureActive()
                // Only throw the original exception in case an error occurs while closing the session
            }
        }
    }

    /**
     * Prevent resource leak and blocking the event loop with an active session.
     * This method doesn't put in the context the key, only remove it.
     */
    private final suspend fun <S : Coroutines.Closeable, T> withActiveSession(
        session: S,
        work: suspend (S) -> T,
        contextKey: Context.Key<S>,
    ): T {
        contract { callsInPlace(work, InvocationKind.EXACTLY_ONCE) }
        checkNotNull(context[contextKey])

        return try {
            work(session)
        } finally {
            context.remove(contextKey)
            try {
                session.close()
            } catch (_: Throwable) {
                // TODO need to check if is safe catch CancellationException of coroutines
                // currentCoroutineContext().ensureActive()
                // Only throw the original exception in case an error occurs while closing the session
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline operator fun <T> Context.set(key: Context.Key<T>, instance: T) = put(key, instance)
}
