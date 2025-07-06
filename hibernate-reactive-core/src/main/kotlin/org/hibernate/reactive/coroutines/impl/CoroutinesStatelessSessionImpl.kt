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
import kotlinx.coroutines.CoroutineDispatcher
import org.hibernate.LockMode
import org.hibernate.graph.RootGraph
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.common.AffectedEntities
import org.hibernate.reactive.common.ResultSetMapping
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.internal.RequireHibernateReactiveContext
import org.hibernate.reactive.coroutines.internal.safeAwait
import org.hibernate.reactive.coroutines.internal.withHibernateContext
import org.hibernate.reactive.pool.ReactiveConnection
import org.hibernate.reactive.session.ReactiveStatelessSession
import java.util.concurrent.CompletableFuture
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class CoroutinesStatelessSessionImpl(
    private val delegate: ReactiveStatelessSession,
    private val factory: CoroutinesSessionFactoryImpl,
    val dispatcher: CoroutineDispatcher,
) : Coroutines.StatelessSession {
    // This need synchronized?
    private var currentTransaction: CoroutinesStatelessTransaction<*>? = null

    override suspend fun <T> get(
        entityClass: Class<T>,
        id: Any,
    ): T? = withHibernateContext(dispatcher) { delegate.reactiveGet(entityClass, id) }

    override suspend fun <T> get(
        entityClass: Class<T>,
        vararg ids: Any,
    ): List<T?> = withHibernateContext(dispatcher) { delegate.reactiveGet(entityClass, *ids) }

    override suspend fun <T> get(
        entityClass: Class<T>,
        id: Any,
        lockMode: LockMode,
    ): T? = withHibernateContext(dispatcher) { delegate.reactiveGet(entityClass, id, lockMode, null) }

    override suspend fun <T> get(
        entityGraph: EntityGraph<T>,
        id: Any,
    ): T? =
        withHibernateContext(dispatcher) {
            delegate.reactiveGet((entityGraph as RootGraph<T>).graphedType.javaType, id, null, entityGraph)
        }

    override suspend fun insert(entity: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveInsert(entity) }
    }

    override suspend fun insertAll(vararg entities: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveInsertAll(entities.size, *entities) }
    }

    override suspend fun insertAll(
        batchSize: Int,
        vararg entities: Any,
    ) {
        withHibernateContext(dispatcher) { delegate.reactiveInsertAll(batchSize, *entities) }
    }

    override suspend fun delete(entity: Any) {
        withHibernateContext(dispatcher) { delegate.reactiveDelete(entity) }
    }

    override suspend fun deleteAll(vararg entities: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveDeleteAll(entities.size, *entities) }
    }

    override suspend fun deleteAll(
        batchSize: Int,
        vararg entities: Any?,
    ) {
        withHibernateContext(dispatcher) { delegate.reactiveDeleteAll(batchSize, *entities) }
    }

    override suspend fun deleteMultiple(entities: List<*>) = deleteAll(*entities.toTypedArray())

    override suspend fun update(entity: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveUpdate(entity) }
    }

    override suspend fun updateAll(vararg entities: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveUpdateAll(entities.size, *entities) }
    }

    override suspend fun updateAll(
        batchSize: Int,
        vararg entities: Any?,
    ) {
        withHibernateContext(dispatcher) { delegate.reactiveUpdateAll(batchSize, *entities) }
    }

    override suspend fun upsert(entity: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveUpsert(entity) }
    }

    override suspend fun upsertAll(vararg entities: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveUpsertAll(entities.size, *entities) }
    }

    override suspend fun upsertAll(
        batchSize: Int,
        vararg entities: Any?,
    ) {
        withHibernateContext(dispatcher) { delegate.reactiveUpsertAll(batchSize, *entities) }
    }

    override suspend fun refresh(entity: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveRefresh(entity) }
    }

    override suspend fun refreshAll(vararg entities: Any?) {
        withHibernateContext(dispatcher) { delegate.reactiveRefreshAll(entities.size, *entities) }
    }

    override suspend fun refreshAll(
        batchSize: Int,
        vararg entities: Any?,
    ) {
        withHibernateContext(dispatcher) { delegate.reactiveRefreshAll(batchSize, *entities) }
    }

    override suspend fun refresh(
        entity: Any?,
        lockMode: LockMode?,
    ) {
        withHibernateContext(dispatcher) { delegate.reactiveRefresh(entity, lockMode) }
    }

    override suspend fun <T> fetch(association: T?): T? = withHibernateContext(dispatcher) { delegate.reactiveFetch(association, false) }

    override fun getIdentifier(entity: Any?): Any? = delegate.getIdentifier(entity)

    override suspend fun <T> withTransaction(work: suspend (Coroutines.Transaction) -> T): T =
        withHibernateContext(dispatcher) {
            if (currentTransaction == null) {
                CoroutinesStatelessTransaction<T>()
                    .execute(work)
            } else {
                work(currentTransaction!!)
            }
        }

    override suspend fun close() {
        withHibernateContext(dispatcher) {
            val closing = CompletableFuture<Void>()
            delegate.close(closing)
            closing
        }
    }

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
        withHibernateContext(dispatcher) {
            CoroutinesSelectionQueryImpl(delegate.createReactiveQuery(queryString, resultType), dispatcher)
        }

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

    override fun isOpen(): Boolean = delegate.isOpen

    override fun getCriteriaBuilder(): CriteriaBuilder = getFactory().getCriteriaBuilder()

    override fun currentTransaction(): Coroutines.Transaction? = currentTransaction

    override fun getFactory(): Coroutines.SessionFactory = factory

    // Need the correct event loop context
    @OptIn(RequireHibernateReactiveContext::class)
    @org.jetbrains.annotations.VisibleForTesting
    suspend fun getReactiveConnection(): ReactiveConnection = withHibernateContext(dispatcher, delegate::getReactiveConnection)

    @OptIn(ExperimentalContracts::class)
    private inner class CoroutinesStatelessTransaction<T> : Coroutines.Transaction {
        private var rollback = false

        override fun markForRollback() {
            rollback = true
        }

        override fun isMarkedForRollback(): Boolean = rollback

        suspend fun execute(work: suspend (Coroutines.Transaction) -> T): T {
            @Suppress("WRONG_INVOCATION_KIND")
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

        suspend fun executeInTransaction(work: suspend (Coroutines.Transaction) -> T): T {
            contract {
                callsInPlace(work, InvocationKind.EXACTLY_ONCE)
            }
            return try {
                val result = work(this)
                // finally, when there was no exception, commit or rollback the transaction
                if (rollback) {
                    rollback()
                } else {
                    commit()
                }
                result
            } catch (e: Throwable) {
                // in the case of an exception or cancellation, we need to roll back the transaction
                rollback()
                throw e
            }
        }

        suspend fun begin() {
            delegate.reactiveConnection.beginTransaction().safeAwait()
        }

        suspend fun rollback() {
            delegate.reactiveConnection.rollbackTransaction().safeAwait()
        }

        suspend fun commit() {
            delegate.reactiveConnection.commitTransaction().safeAwait()
        }
    }
}
