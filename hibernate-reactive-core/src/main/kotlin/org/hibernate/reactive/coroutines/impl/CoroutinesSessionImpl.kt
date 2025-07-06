/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.impl

import jakarta.persistence.EntityGraph
import jakarta.persistence.TypedQueryReference
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.metamodel.Attribute
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.hibernate.CacheMode
import org.hibernate.Filter
import org.hibernate.FlushMode
import org.hibernate.LockMode
import org.hibernate.graph.RootGraph
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.common.AffectedEntities
import org.hibernate.reactive.common.Identifier
import org.hibernate.reactive.common.ResultSetMapping
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.internal.RequireHibernateReactiveContext
import org.hibernate.reactive.coroutines.internal.safeAwait
import org.hibernate.reactive.coroutines.internal.withHibernateContext
import org.hibernate.reactive.pool.ReactiveConnection
import org.hibernate.reactive.session.ReactiveSession
import org.hibernate.reactive.util.impl.CompletionStages.applyToAll
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

class CoroutinesSessionImpl(
    private val delegate: ReactiveSession,
    private val factory: CoroutinesSessionFactoryImpl,
    val dispatcher: CoroutineContext,
) : Coroutines.Session {
    // This need synchronized?
    private var currentTransaction: CoroutinesTransactionImpl<*>? = null

    override suspend fun <T> find(
        entityClass: Class<T>,
        id: Any?,
    ): T? = withHibernateContext(dispatcher) { delegate.reactiveFind(entityClass, id, null, null) }

    override suspend fun <T> find(
        entityClass: Class<T>,
        id: Any?,
        lockMode: LockMode?,
    ): T? =
        withHibernateContext(dispatcher) {
            @Suppress("DEPRECATION", "removal")
            delegate.reactiveFind(entityClass, id, org.hibernate.LockOptions(lockMode), null)
        }

    override suspend fun <T> find(
        entityGraph: EntityGraph<T>,
        id: Any?,
    ): T? =
        withHibernateContext(dispatcher) {
            delegate.reactiveFind((entityGraph as RootGraph<T>).graphedType.javaType, id, null, entityGraph)
        }

    override suspend fun <T> find(
        entityClass: Class<T>,
        vararg ids: Any?,
    ): List<T?> = withHibernateContext(dispatcher) { delegate.reactiveFind(entityClass, *ids) }

    override suspend fun <T> find(
        entityClass: Class<T>,
        naturalId: Identifier<T>,
    ): T? = withHibernateContext(dispatcher) { delegate.reactiveFind(entityClass, naturalId.namedValues()) }

    override fun <T> getReference(
        entityClass: Class<T?>?,
        id: Any?,
    ): T? = delegate.getReference(entityClass, id)

    override fun <T> getReference(entity: T?): T? = delegate.getReference(delegate.getEntityClass(entity), delegate.getEntityId(entity))

    override suspend fun persist(instance: Any?) {
        withHibernateContext(dispatcher) { delegate.reactivePersist(instance) }
    }

    override suspend fun persist(
        entityName: String?,
        instance: Any?,
    ) {
        withHibernateContext(dispatcher) { delegate.reactivePersist(entityName, instance) }
    }

    override suspend fun persistAll(vararg entities: Any?) {
        withHibernateContext(dispatcher) { applyToAll(delegate::reactivePersist, entities) }
    }

    override suspend fun remove(entity: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveRemove(entity) }
    }

    override suspend fun removeAll(vararg entities: Any?) {
        withHibernateContext(dispatcher) { applyToAll(delegate::reactiveRemove, entities) }
    }

    override suspend fun <T> merge(entity: T?): T? = withHibernateContext(dispatcher) { delegate.reactiveMerge(entity) }

    override suspend fun mergeAll(vararg entities: Any?) {
        withHibernateContext(dispatcher) { applyToAll(delegate::reactiveMerge, entities) }
    }

    override suspend fun refresh(entity: Any?) {
        withHibernateContext(dispatcher) {
            @Suppress("DEPRECATION", "removal")
            delegate.reactiveRefresh(entity, org.hibernate.LockOptions.NONE)
        }
    }

    override suspend fun refresh(
        entity: Any?,
        lockMode: LockMode?,
    ) {
        withHibernateContext(dispatcher) {
            @Suppress("DEPRECATION", "removal")
            delegate.reactiveRefresh(entity, org.hibernate.LockOptions(lockMode))
        }
    }

    override suspend fun refreshAll(vararg entities: Any?) {
        withHibernateContext(dispatcher) {
            applyToAll({ e ->
                @Suppress("DEPRECATION", "removal")
                delegate.reactiveRefresh(e, org.hibernate.LockOptions.NONE)
            }, entities)
        }
    }

    override suspend fun lock(
        entity: Any?,
        lockMode: LockMode?,
    ) {
        withHibernateContext(dispatcher) {
            @Suppress("DEPRECATION", "removal")
            delegate.reactiveLock(entity, org.hibernate.LockOptions(lockMode))
        }
    }

    override suspend fun flush() {
        withHibernateContext(dispatcher, delegate::reactiveFlush)
    }

    override suspend fun <T> fetch(association: T?): T? = withHibernateContext(dispatcher) { delegate.reactiveFetch(association, false) }

    override suspend fun <E, T> fetch(
        entity: E?,
        field: Attribute<E, T>,
    ): T? = withHibernateContext(dispatcher) { delegate.reactiveFetch(entity, field) }

    override suspend fun <T> unproxy(association: T?): T? = withHibernateContext(dispatcher) { delegate.reactiveFetch(association, true) }

    override suspend fun <T> withTransaction(work: suspend (Coroutines.Transaction) -> T): T =
        withHibernateContext(dispatcher) {
            // apply context (dispatcher) in the root of the operation, all child coroutines have the correct dispatcher
            if (currentTransaction == null) {
                CoroutinesTransactionImpl<T>()
                    .execute(work)
            } else {
                work(currentTransaction!!)
            }
        }

    override suspend fun close() {
        withContext(dispatcher + NonCancellable) { safeAwait(delegate.reactiveClose()) }
    }

    override fun currentTransaction(): Coroutines.Transaction? = currentTransaction

    override fun getFactory(): Coroutines.SessionFactory = factory

    // builder and delegation

    // Special function for test. We need to change to suspend because required event loop context
    @OptIn(RequireHibernateReactiveContext::class)
    @org.jetbrains.annotations.VisibleForTesting
    suspend fun getReactiveConnection(): ReactiveConnection = withHibernateContext(dispatcher, delegate::getReactiveConnection)

    override fun getLockMode(entity: Any?): LockMode? = delegate.getCurrentLockMode(entity)

    override fun contains(entity: Any?): Boolean = delegate.contains(entity)

    override fun setFlushMode(flushMode: FlushMode): Coroutines.Session =
        apply {
            // Why?
            delegate.setHibernateFlushMode(
                when (flushMode) {
                    FlushMode.AUTO -> FlushMode.AUTO
                    FlushMode.COMMIT -> FlushMode.COMMIT
                    FlushMode.MANUAL -> FlushMode.MANUAL
                    FlushMode.ALWAYS -> FlushMode.ALWAYS
                },
            )
        }

    // why?
    override fun getFlushMode(): FlushMode? =
        when (delegate.getHibernateFlushMode()) {
            FlushMode.AUTO -> FlushMode.AUTO
            FlushMode.COMMIT -> FlushMode.COMMIT
            FlushMode.MANUAL -> FlushMode.MANUAL
            FlushMode.ALWAYS -> FlushMode.ALWAYS
        }

    override fun detach(entity: Any?): Coroutines.Session =
        apply {
            delegate.detach(entity)
        }

    override fun clear(): Coroutines.Session =
        apply {
            delegate.clear()
        }

    override fun enableFetchProfile(name: String?): Coroutines.Session =
        apply {
            delegate.enableFetchProfile(name)
        }

    override fun disableFetchProfile(name: String?): Coroutines.Session =
        apply {
            delegate.disableFetchProfile(name)
        }

    override fun isFetchProfileEnabled(name: String?): Boolean = delegate.isFetchProfileEnabled(name)

    override fun setDefaultReadOnly(readOnly: Boolean): Coroutines.Session =
        apply {
            delegate.isDefaultReadOnly = readOnly
        }

    override fun isDefaultReadOnly(): Boolean = delegate.isDefaultReadOnly

    override fun setReadOnly(
        entityOrProxy: Any,
        readOnly: Boolean,
    ): Coroutines.Session =
        apply {
            delegate.setReadOnly(entityOrProxy, readOnly)
        }

    override fun isReadOnly(entityOrProxy: Any): Boolean = delegate.isReadOnly(entityOrProxy)

    override fun setCacheMode(cacheMode: CacheMode?): Coroutines.Session =
        apply {
            delegate.cacheMode = cacheMode
        }

    override fun getCacheMode(): CacheMode? = delegate.cacheMode

    override fun setBatchSize(batchSize: Int?): Coroutines.Session =
        apply {
            delegate.batchSize = batchSize
        }

    override fun getBatchSize(): Int? = delegate.batchSize

    override fun enableFilter(filterName: String?): Filter? = delegate.enableFilter(filterName)

    override fun disableFilter(filterName: String?) = delegate.disableFilter(filterName)

    override fun getEnabledFilter(filterName: String?): Filter? = delegate.getEnabledFilter(filterName)

    override fun getFetchBatchSize(): Int = delegate.fetchBatchSize

    override fun setFetchBatchSize(batchSize: Int): Coroutines.Session =
        apply {
            delegate.fetchBatchSize = batchSize
        }

    override fun isSubselectFetchingEnabled(): Boolean = delegate.isSubselectFetchingEnabled

    override fun setSubselectFetchingEnabled(enabled: Boolean): Coroutines.Session =
        apply {
            delegate.isSubselectFetchingEnabled = enabled
        }

    override fun isOpen(): Boolean = delegate.isOpen

    // -- Query --
    override suspend fun <R> createQuery(typedQueryReference: TypedQueryReference<R>): Coroutines.Query<R> =
        withHibernateContext(dispatcher) { CoroutinesQueryImpl(delegate.createReactiveQuery(typedQueryReference), dispatcher) }

    @Deprecated(
        "See explanation in [org.hibernate.query.QueryProducer.createSelectionQuery(string)]",
        replaceWith = ReplaceWith("createSelectionQuery(queryString, resultType)"),
        level = DeprecationLevel.WARNING,
    )
    override suspend fun <R> createQuery(queryString: String?): Coroutines.Query<R> =
        withHibernateContext(dispatcher) { CoroutinesQueryImpl(delegate.createReactiveQuery(queryString), dispatcher) }

    override suspend fun <R> createNamedQuery(queryName: String?): Coroutines.Query<R> =
        withHibernateContext(dispatcher) { CoroutinesQueryImpl(delegate.createReactiveNamedQuery(queryName), dispatcher) }

    override suspend fun <R> createNativeQuery(queryString: String?): Coroutines.Query<R> =
        withHibernateContext(dispatcher) { CoroutinesQueryImpl(delegate.createReactiveNativeQuery(queryString), dispatcher) }

    override suspend fun <R> createNativeQuery(
        queryString: String?,
        affectedEntities: AffectedEntities,
    ): Coroutines.Query<R> =
        withHibernateContext(dispatcher) {
            CoroutinesQueryImpl(delegate.createReactiveNativeQuery(queryString, affectedEntities), dispatcher)
        }

    // -- Selection --
    override suspend fun <R> createSelectionQuery(
        queryString: String?,
        resultType: Class<R>?,
    ): Coroutines.SelectionQuery<R> =
        withHibernateContext(dispatcher) {
            CoroutinesSelectionQueryImpl(delegate.createReactiveSelectionQuery(queryString, resultType), dispatcher)
        }

    override suspend fun <R> createQuery(
        queryString: String?,
        resultType: Class<R>?,
    ): Coroutines.SelectionQuery<R> =
        withHibernateContext(dispatcher) { CoroutinesSelectionQueryImpl(delegate.createReactiveQuery(queryString, resultType), dispatcher) }

    override suspend fun <R> createQuery(criteriaQuery: CriteriaQuery<R>): Coroutines.SelectionQuery<R> =
        withHibernateContext(dispatcher) { CoroutinesSelectionQueryImpl(delegate.createReactiveQuery(criteriaQuery), dispatcher) }

    override suspend fun <R> createNamedQuery(
        queryName: String?,
        resultType: Class<R>,
    ): Coroutines.SelectionQuery<R> =
        withHibernateContext(dispatcher) {
            CoroutinesSelectionQueryImpl(delegate.createReactiveNamedQuery(queryName, resultType), dispatcher)
        }

    override suspend fun <R> createNativeQuery(
        queryString: String?,
        resultType: Class<R>,
    ): Coroutines.SelectionQuery<R> =
        withHibernateContext(dispatcher) {
            CoroutinesSelectionQueryImpl(delegate.createReactiveNativeQuery(queryString, resultType), dispatcher)
        }

    override suspend fun <R> createNativeQuery(
        queryString: String?,
        resultType: Class<R>,
        affectedEntities: AffectedEntities,
    ): Coroutines.SelectionQuery<R> =
        withHibernateContext(dispatcher) {
            CoroutinesSelectionQueryImpl(delegate.createReactiveNativeQuery(queryString, resultType, affectedEntities), dispatcher)
        }

    override suspend fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
    ): Coroutines.SelectionQuery<R> =
        withHibernateContext(dispatcher) {
            CoroutinesSelectionQueryImpl(delegate.createReactiveNativeQuery(queryString, resultSetMapping), dispatcher)
        }

    override suspend fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
        affectedEntities: AffectedEntities,
    ): Coroutines.SelectionQuery<R> =
        withHibernateContext(dispatcher) {
            CoroutinesSelectionQueryImpl(delegate.createReactiveNativeQuery(queryString, resultSetMapping, affectedEntities), dispatcher)
        }

    // -- Mutation --
    override suspend fun createMutationQuery(queryString: String?): Coroutines.MutationQuery =
        withHibernateContext(dispatcher) {
            CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery<Any>(queryString), dispatcher)
        }

    override suspend fun createMutationQuery(updateQuery: CriteriaUpdate<*>): Coroutines.MutationQuery =
        withHibernateContext(dispatcher) {
            CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(updateQuery), dispatcher)
        }

    override suspend fun createMutationQuery(deleteQuery: CriteriaDelete<*>): Coroutines.MutationQuery =
        withHibernateContext(dispatcher) {
            CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(deleteQuery), dispatcher)
        }

    override suspend fun createMutationQuery(insert: JpaCriteriaInsert<*>): Coroutines.MutationQuery =
        withHibernateContext(dispatcher) {
            CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(insert), dispatcher)
        }

    override suspend fun <R> createQuery(criteriaUpdate: CriteriaUpdate<R>): Coroutines.MutationQuery =
        withHibernateContext(dispatcher) {
            CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(criteriaUpdate), dispatcher)
        }

    override suspend fun <R> createQuery(criteriaDelete: CriteriaDelete<R>): Coroutines.MutationQuery =
        withHibernateContext(dispatcher) {
            CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(criteriaDelete), dispatcher)
        }

    override fun <T> getResultSetMapping(
        resultType: Class<T>?,
        mappingName: String?,
    ): ResultSetMapping<T> = delegate.getResultSetMapping(resultType, mappingName)

    override suspend fun <T> getEntityGraph(
        rootType: Class<T>?,
        graphName: String?,
    ): EntityGraph<T> = withHibernateContext(dispatcher) { delegate.getEntityGraph(rootType, graphName) }

    override suspend fun <T> createEntityGraph(rootType: Class<T>?): EntityGraph<T> =
        withHibernateContext(dispatcher) { delegate.createEntityGraph(rootType) }

    override suspend fun <T> createEntityGraph(
        rootType: Class<T>?,
        graphName: String?,
    ): EntityGraph<T> = withHibernateContext(dispatcher) { delegate.createEntityGraph(rootType, graphName) }

    override fun getCriteriaBuilder(): CriteriaBuilder = getFactory().getCriteriaBuilder()

    // Private because don't have access outside this class. Inner to have behavior similar to protected
    @OptIn(ExperimentalContracts::class)
    private inner class CoroutinesTransactionImpl<T> : Coroutines.Transaction {
        private var rollback = false

        override fun markForRollback() {
            rollback = true
        }

        override fun isMarkedForRollback(): Boolean = rollback

        suspend fun execute(work: suspend (Coroutines.Transaction) -> T): T {
            contract {
                callsInPlace(work, InvocationKind.EXACTLY_ONCE)
            }
            return try {
                currentTransaction = this
                begin()
                executeInTransaction(work)
            } finally {
                currentTransaction = null
            }
        }

        private suspend fun executeInTransaction(work: suspend (Coroutines.Transaction) -> T): T {
            contract {
                callsInPlace(work, InvocationKind.EXACTLY_ONCE)
            }
            return try {
                val result = work(this)
                // only flush() if the work completed with no exception
                flush()
                beforeCompletion()
                // finally, when there was no exception, commit or rollback the transaction
                if (rollback) {
                    rollback()
                } else {
                    commit()
                }
                afterCompletion()
                result
            } catch (e: Throwable) {
                // in the case of an exception or cancellation, we need to roll back the transaction
                rollback()
                afterCompletion()
                throw e
            }
        }

        private suspend fun flush() {
            delegate.reactiveAutoflush().safeAwait()
        }

        private suspend fun begin() {
            delegate.reactiveConnection.beginTransaction().safeAwait()
        }

        private suspend fun rollback() {
            delegate.reactiveConnection.rollbackTransaction().safeAwait()
        }

        private suspend fun commit() {
            delegate.reactiveConnection.commitTransaction().safeAwait()
        }

        private suspend fun beforeCompletion() {
            delegate.reactiveActionQueue.beforeTransactionCompletion().safeAwait()
        }

        private suspend fun afterCompletion() {
            delegate.reactiveActionQueue.afterTransactionCompletion(!rollback).safeAwait()
        }
    }
}
