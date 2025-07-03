/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.reactive.coroutines.impl

import jakarta.persistence.metamodel.Metamodel
import kotlinx.coroutines.future.await
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
class CoroutinesSessionFactoryImpl(
    private val delegate: SessionFactoryImpl,
) : Coroutines.SessionFactory,
    Implementor {
    private companion object {
        private const val REUSING_SESSION = "Reusing existing open Coroutines.Session which was found in the current Vert.x context"
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

    private val context = delegate.serviceRegistry.getService(Context::class.java)
    private val connectionPool = delegate.serviceRegistry.getService(ReactiveConnectionPool::class.java)
    private val contextKeyForSession = BaseKey(Coroutines.Session::class.java, delegate.uuid)
    private val contextKeyForStatelessSession =
        BaseKey(Coroutines.StatelessSession::class.java, delegate.uuid)
    private val log =
        LoggerFactory.make(
            Log::class.java,
            java.lang.invoke.MethodHandles
                .lookup(),
        )

    override fun getUuid(): String? = delegate.uuid

    override fun getServiceRegistry(): ServiceRegistry? = delegate.serviceRegistry

    override fun getContext(): Context? = context

    override suspend fun openSession(): Coroutines.Session = openSessionInternal(null, false)

    override suspend fun openSession(tenantId: String?): Coroutines.Session = openSessionInternal(tenantId, true)

    private suspend fun openSessionInternal(
        tenantId: String?,
        withTenant: Boolean,
    ): Coroutines.Session {
        val options = options(tenantId, withTenant)
        val tenantIdentifier = if (withTenant) tenantId else options.tenantIdentifier
        return withHibernateContext(checkNotNull(context)) {
            val reactiveConnection = connection(tenantIdentifier)
            val session =
                create(reactiveConnection) {
                    ReactiveSessionImpl(delegate, options, reactiveConnection)
                }
            CoroutinesSessionImpl(session, context, this)
        }
    }

    override suspend fun openStatelessSession(): Coroutines.StatelessSession = openSessionStatelessInternal(null, false)

    override suspend fun openStatelessSession(tenantId: String?): Coroutines.StatelessSession = openSessionStatelessInternal(tenantId, true)

    private suspend fun openSessionStatelessInternal(
        tenantId: String?,
        withTenant: Boolean,
    ): Coroutines.StatelessSession {
        val options = options(tenantId, withTenant)
        val tenantIdentifier = if (withTenant) tenantId else options.tenantIdentifier
        return withHibernateContext(checkNotNull(context)) {
            val reactiveConnection = connection(tenantIdentifier)
            val session =
                create(reactiveConnection) {
                    ReactiveStatelessSessionImpl(delegate, options, reactiveConnection)
                }
            CoroutinesStatelessSessionImpl(session, context, this)
        }
    }

    override suspend fun <T> withSession(work: suspend (Coroutines.Session) -> T): T {
        val current = context?.get(contextKeyForSession)
        return if (current != null && current.isOpen()) {
            log.debug(REUSING_SESSION)
            work(current)
        } else {
            log.debug(OPENING_NEW_SESSION)
            // Force a nonnull context?
            withHibernateContext(checkNotNull(context)) {
                withSession(openSession(), work, contextKeyForSession)
            }
        }
    }

    override suspend fun <T> withSession(
        tenantId: String,
        work: suspend (Coroutines.Session) -> T,
    ): T {
        val key: Context.Key<Coroutines.Session> = MultitenantKey(contextKeyForSession, tenantId)
        val current = context?.get(key)
        return if (current != null && current.isOpen()) {
            log.debugf(REUSING_TENANT_SESSION, tenantId)
            work(current)
        } else {
            log.debugf(OPENING_NEW_TENANT_SESSION, tenantId)
            withHibernateContext(checkNotNull(context)) {
                withSession(openSession(tenantId), work, key)
            }
        }
    }

    override suspend fun <T> withTransaction(work: suspend (Coroutines.Session, Coroutines.Transaction) -> T): T =
        withSession { s -> s.withTransaction { t -> work(s, t) } }

    override suspend fun <T> withTransaction(
        tenantId: String,
        work: suspend (Coroutines.Session, Coroutines.Transaction) -> T,
    ): T = withSession(tenantId) { s -> s.withTransaction { t -> work(s, t) } }

    override suspend fun <T> withStatelessTransaction(work: suspend (Coroutines.StatelessSession, Coroutines.Transaction) -> T): T =
        withStatelessSession { s -> s.withTransaction { t -> work(s, t) } }

    override suspend fun <T> withStatelessTransaction(
        tenantId: String?,
        work: suspend (Coroutines.StatelessSession, Coroutines.Transaction) -> T,
    ): T = withStatelessSession(tenantId) { s -> s.withTransaction { t -> work(s, t) } }

    override suspend fun <T> withStatelessSession(work: suspend (Coroutines.StatelessSession) -> T): T {
        val current = context?.get(contextKeyForStatelessSession)
        return if (current != null && current.isOpen()) {
            log.debug(REUSING_STATELESS_SESSION)
            work(current)
        } else {
            log.debug(OPENING_NEW_STATELESS_SESSION)
            withHibernateContext(checkNotNull(context)) {
                withSession(openStatelessSession(), work, contextKeyForStatelessSession)
            }
        }
    }

    override suspend fun <T> withStatelessSession(
        tenantId: String?,
        work: suspend (Coroutines.StatelessSession) -> T,
    ): T {
        val key: Context.Key<Coroutines.StatelessSession> = MultitenantKey(contextKeyForStatelessSession, tenantId)
        val current = context?.get(key)
        return if (current != null && current.isOpen()) {
            log.debugf(REUSING_TENANT_STATELESS_SESSION, tenantId)
            work(current)
        } else {
            log.debugf(OPENING_NEW_TENANT_STATELESS_SESSION, tenantId)
            withHibernateContext(checkNotNull(context)) {
                withSession(openStatelessSession(tenantId), work, key)
            }
        }
    }

    override fun getCriteriaBuilder(): HibernateCriteriaBuilder = delegate.criteriaBuilder

    override fun getMetamodel(): Metamodel? = delegate.metamodel

    override fun getCache(): Cache? = delegate.cache

    override fun getStatistics(): Statistics? = delegate.statistics

    override fun getCurrentSession(): Coroutines.Session? = context?.get(contextKeyForSession)

    override fun getCurrentStatelessSession(): Coroutines.StatelessSession? = context?.get(contextKeyForStatelessSession)

    override fun close() = delegate.close()

    override fun isOpen(): Boolean = delegate.isOpen

    @Suppress("DEPRECATION", "removal")
    private fun options(
        tenantIdentifier: String? = null,
        withTenant: Boolean = false,
    ): SessionCreationOptions =
        SessionFactoryImpl.SessionBuilderImpl(delegate).apply {
            if (withTenant) {
                this.tenantIdentifier(tenantIdentifier)
            }
        }

    private suspend fun connection(tenantId: String?): ReactiveConnection {
        assertUseOnEventLoop()
        // force checkNotNull?
        return if (tenantId == null) {
            checkNotNull(connectionPool).getConnection().await()
        } else {
            checkNotNull(connectionPool).getConnection(tenantId).await()
        }
    }

    private suspend fun <S> create(
        connection: ReactiveConnection,
        supplier: () -> S,
    ): S {
        contract {
            callsInPlace(supplier, InvocationKind.EXACTLY_ONCE)
        }
        return try {
            supplier()
        } catch (e: Throwable) {
            close(connection)
            throw e
        }
    }

    private suspend fun close(connection: ReactiveConnection) {
        connection.close().await()
    }

    private suspend fun <S : Coroutines.Closeable, T> withSession(
        session: S,
        work: suspend (S) -> T,
        contextKey: Context.Key<S>,
    ): T {
        contract {
            callsInPlace(work, InvocationKind.EXACTLY_ONCE)
        }
        context?.put(contextKey, session)

        try {
            return work(session)
        } finally {
            context?.remove(contextKey)
            try {
                session.close()
            } catch (_: Throwable) {
                // Only throw the original exception in case an error occurs while closing the session
            }
        }
    }
}
