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
import kotlinx.coroutines.future.await
import org.hibernate.LockMode
import org.hibernate.graph.RootGraph
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.common.AffectedEntities
import org.hibernate.reactive.common.ResultSetMapping
import org.hibernate.reactive.context.Context
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.internal.withHibernateContext
import org.hibernate.reactive.pool.ReactiveConnection
import org.hibernate.reactive.session.ReactiveStatelessSession
import java.util.concurrent.CompletableFuture
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class CoroutinesStatelessSessionImpl(
    private val delegate: ReactiveStatelessSession,
    private val context: Context,
    private val factory: CoroutinesSessionFactoryImpl,
) : Coroutines.StatelessSession {
    private var currentTransaction: CoroutinesStatelessTransaction<*>? = null

    override suspend fun <T> get(
        entityClass: Class<T>,
        id: Any?,
    ): T? =
        withHibernateContext(context) {
            delegate.reactiveGet(entityClass, id).await()
        }

    override suspend fun <T> get(
        entityClass: Class<T>,
        vararg ids: Any,
    ): List<T?> =
        withHibernateContext(context) {
            delegate.reactiveGet(entityClass, *ids).await()
        }

    override suspend fun <T> get(
        entityClass: Class<T>,
        id: Any,
        lockMode: LockMode,
    ): T? =
        withHibernateContext(context) {
            delegate.reactiveGet(entityClass, id, lockMode, null).await()
        }

    override suspend fun <T> get(
        entityGraph: EntityGraph<T>,
        id: Any,
    ): T? =
        withHibernateContext(context) {
            delegate.reactiveGet((entityGraph as RootGraph<T>).graphedType.javaType, id, null, entityGraph).await()
        }

    override suspend fun insert(entity: Any?) {
        withHibernateContext(context) {
            delegate.reactiveInsert(entity).await()
        }
    }

    override suspend fun insertAll(vararg entities: Any?) {
        withHibernateContext(context) {
            delegate.reactiveInsertAll(entities.size, *entities).await()
        }
    }

    override suspend fun insertAll(
        batchSize: Int,
        vararg entities: Any,
    ) {
        withHibernateContext(context) {
            delegate.reactiveInsertAll(batchSize, *entities).await()
        }
    }

    override suspend fun insertMultiple(entities: List<*>) = insertAll(entities.toTypedArray())

    override suspend fun delete(entity: Any) {
        withHibernateContext(context) {
            delegate.reactiveDelete(entity).await()
        }
    }

    override suspend fun deleteAll(vararg entities: Any?) {
        withHibernateContext(context) {
            delegate.reactiveDeleteAll(entities.size, entities).await()
        }
    }

    override suspend fun deleteAll(
        batchSize: Int,
        vararg entities: Any?,
    ) {
        withHibernateContext(context) {
            delegate.reactiveDeleteAll(batchSize, entities).await()
        }
    }

    override suspend fun deleteMultiple(entities: List<*>) = deleteAll(entities.toTypedArray())

    override suspend fun update(entity: Any?) {
        withHibernateContext(context) {
            delegate.reactiveUpdate(entity).await()
        }
    }

    override suspend fun updateAll(vararg entities: Any?) {
        withHibernateContext(context) {
            delegate.reactiveUpdateAll(entities.size, entities).await()
        }
    }

    override suspend fun updateAll(
        batchSize: Int,
        vararg entities: Any?,
    ) {
        withHibernateContext(context) {
            delegate.reactiveUpdateAll(batchSize, entities).await()
        }
    }

    override suspend fun updateMultiple(entities: List<*>) = updateAll(entities.toTypedArray())

    override suspend fun upsert(entity: Any?) {
        withHibernateContext(context) {
            delegate.reactiveUpsert(entity).await()
        }
    }

    override suspend fun upsertAll(vararg entities: Any?) {
        withHibernateContext(context) {
            delegate.reactiveUpsertAll(entities.size, entities).await()
        }
    }

    override suspend fun upsertAll(
        batchSize: Int,
        vararg entities: Any?,
    ) {
        withHibernateContext(context) {
            delegate.reactiveUpsertAll(batchSize, entities).await()
        }
    }

    override suspend fun upsertMultiple(entities: List<*>) = upsertAll(entities.toTypedArray())

    override suspend fun refresh(entity: Any?) {
        withHibernateContext(context) {
            delegate.reactiveRefresh(entity).await()
        }
    }

    override suspend fun refreshAll(vararg entities: Any?) {
        withHibernateContext(context) {
            delegate.reactiveRefreshAll(entities.size, entities).await()
        }
    }

    override suspend fun refreshAll(
        batchSize: Int,
        vararg entities: Any?,
    ) {
        withHibernateContext(context) {
            delegate.reactiveRefreshAll(batchSize, entities).await()
        }
    }

    override suspend fun refreshMultiple(entities: List<*>) = refreshAll(entities.toTypedArray())

    override suspend fun refresh(
        entity: Any?,
        lockMode: LockMode?,
    ) {
        withHibernateContext(context) {
            delegate.reactiveRefresh(entity, lockMode).await()
        }
    }

    override suspend fun <T> fetch(association: T?): T? =
        withHibernateContext(context) {
            delegate.reactiveFetch(association, false).await()
        }

    override fun getIdentifier(entity: Any?): Any? = delegate.getIdentifier(entity)

    override suspend fun <T> withTransaction(work: suspend (Coroutines.Transaction) -> T): T =
        withHibernateContext(context) {
            if (currentTransaction == null) {
                CoroutinesStatelessTransaction<T>().execute(work)
            } else {
                work(currentTransaction!!)
            }
        }

    override suspend fun close() {
        withHibernateContext(context) {
            val closing = CompletableFuture<Void>()
            delegate.close(closing)
            closing.await()
        }
    }

    override fun currentTransaction(): Coroutines.Transaction? = currentTransaction

    override fun getFactory(): Coroutines.SessionFactory = factory

    // -- Query --
    override fun <R> createQuery(typedQueryReference: TypedQueryReference<R>): Coroutines.Query<R> =
        CoroutinesQueryImpl(delegate.createReactiveQuery(typedQueryReference), context)

    @Deprecated(
        "See explanation in [org.hibernate.query.QueryProducer.createSelectionQuery(string)]",
        replaceWith = ReplaceWith("createSelectionQuery(queryString, resultType)"),
        level = DeprecationLevel.WARNING,
    )
    override fun <R> createQuery(queryString: String?): Coroutines.Query<R> =
        CoroutinesQueryImpl(delegate.createReactiveQuery(queryString), context)

    override fun <R> createNamedQuery(queryName: String?): Coroutines.Query<R> =
        CoroutinesQueryImpl(delegate.createReactiveNamedQuery(queryName), context)

    override fun <R> createNativeQuery(queryString: String?): Coroutines.Query<R> =
        CoroutinesQueryImpl(delegate.createReactiveNativeQuery(queryString), context)

    override fun <R> createNativeQuery(
        queryString: String?,
        affectedEntities: AffectedEntities,
    ): Coroutines.Query<R> = CoroutinesQueryImpl(delegate.createReactiveNativeQuery(queryString, affectedEntities), context)

    // -- Selection --
    override fun <R> createSelectionQuery(
        queryString: String?,
        resultType: Class<R>?,
    ): Coroutines.SelectionQuery<R> = CoroutinesSelectionQueryImpl(delegate.createReactiveSelectionQuery(queryString, resultType), context)

    override fun <R> createQuery(
        queryString: String?,
        resultType: Class<R>?,
    ): Coroutines.SelectionQuery<R> = CoroutinesSelectionQueryImpl(delegate.createReactiveQuery(queryString, resultType), context)

    override fun <R> createQuery(criteriaQuery: CriteriaQuery<R>): Coroutines.SelectionQuery<R> =
        CoroutinesSelectionQueryImpl(delegate.createReactiveQuery(criteriaQuery), context)

    override fun <R> createNamedQuery(
        queryName: String?,
        resultType: Class<R>,
    ): Coroutines.SelectionQuery<R> = CoroutinesSelectionQueryImpl(delegate.createReactiveNamedQuery(queryName, resultType), context)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultType: Class<R>,
    ): Coroutines.SelectionQuery<R> = CoroutinesSelectionQueryImpl(delegate.createReactiveNativeQuery(queryString, resultType), context)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultType: Class<R>,
        affectedEntities: AffectedEntities,
    ): Coroutines.SelectionQuery<R> =
        CoroutinesSelectionQueryImpl(delegate.createReactiveNativeQuery(queryString, resultType, affectedEntities), context)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
    ): Coroutines.SelectionQuery<R> =
        CoroutinesSelectionQueryImpl(delegate.createReactiveNativeQuery(queryString, resultSetMapping), context)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
        affectedEntities: AffectedEntities,
    ): Coroutines.SelectionQuery<R> =
        CoroutinesSelectionQueryImpl(delegate.createReactiveNativeQuery(queryString, resultSetMapping, affectedEntities), context)

    // -- Mutation --
    override fun createMutationQuery(queryString: String?): Coroutines.MutationQuery =
        CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery<Any>(queryString), context)

    override fun createMutationQuery(updateQuery: CriteriaUpdate<*>): Coroutines.MutationQuery =
        CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(updateQuery), context)

    override fun createMutationQuery(deleteQuery: CriteriaDelete<*>): Coroutines.MutationQuery =
        CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(deleteQuery), context)

    override fun createMutationQuery(insert: JpaCriteriaInsert<*>): Coroutines.MutationQuery =
        CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(insert), context)

    override fun <R> createQuery(criteriaUpdate: CriteriaUpdate<R>): Coroutines.MutationQuery =
        CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(criteriaUpdate), context)

    override fun <R> createQuery(criteriaDelete: CriteriaDelete<R>): Coroutines.MutationQuery =
        CoroutinesMutationQueryImpl(delegate.createReactiveMutationQuery(criteriaDelete), context)

    override fun <T> getResultSetMapping(
        resultType: Class<T>?,
        mappingName: String?,
    ): ResultSetMapping<T> = delegate.getResultSetMapping(resultType, mappingName)

    override fun <T> getEntityGraph(
        rootType: Class<T>?,
        graphName: String?,
    ): EntityGraph<T> = delegate.getEntityGraph(rootType, graphName)

    override fun <T> createEntityGraph(rootType: Class<T>?): EntityGraph<T> = delegate.createEntityGraph(rootType)

    override fun <T> createEntityGraph(
        rootType: Class<T>?,
        graphName: String?,
    ): EntityGraph<T> = delegate.createEntityGraph(rootType, graphName)

    override fun isOpen(): Boolean = delegate.isOpen

    override fun getCriteriaBuilder(): CriteriaBuilder = getFactory().getCriteriaBuilder()

    // Need the correct event loop context
    suspend fun getReactiveConnection(): ReactiveConnection = withHibernateContext(context) { delegate.reactiveConnection }

    @OptIn(ExperimentalContracts::class)
    private inner class CoroutinesStatelessTransaction<T> : Coroutines.Transaction {
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

        suspend fun executeInTransaction(work: suspend (Coroutines.Transaction) -> T): T {
            contract {
                callsInPlace(work, InvocationKind.EXACTLY_ONCE)
            }
            return try {
                val result = work(this)
                if (rollback) {
                    rollback()
                } else {
                    commit()
                }
                result
            } catch (e: Throwable) {
                rollback()
                throw e
            }
        }

        suspend fun begin() {
            delegate.reactiveConnection.beginTransaction().await()
        }

        suspend fun rollback() {
            delegate.reactiveConnection.rollbackTransaction().await()
        }

        suspend fun commit() {
            delegate.reactiveConnection.commitTransaction().await()
        }
    }
}
