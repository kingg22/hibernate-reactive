/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.impl

import jakarta.persistence.EntityGraph
import jakarta.persistence.TypedQueryReference
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.hibernate.LockMode
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.common.AffectedEntities
import org.hibernate.reactive.common.ResultSetMapping
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.DelicateHibernateReactiveCoroutineApi
import org.hibernate.reactive.coroutines.ExperimentalHibernateReactiveCoroutineApi
import org.hibernate.reactive.coroutines.HibernateReactiveOpen
import org.hibernate.reactive.session.ReactiveStatelessSession
import java.util.concurrent.CompletableFuture
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

@HibernateReactiveOpen
@ExperimentalHibernateReactiveCoroutineApi
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(DelicateHibernateReactiveCoroutineApi::class)
class CoroutinesStatelessSessionImpl(
    private val delegate: ReactiveStatelessSession,
    /** The dispatcher used when the [delegate] was created, needs to be a [single thead context][kotlinx.coroutines.newSingleThreadContext] */
    val dispatcher: CoroutineContext,
) : Coroutines.StatelessSession {
    private var currentTransaction: Coroutines.Transaction? = null

    override suspend fun <T> get(entityClass: Class<T>, id: Any?): T? {
        TODO()
    }

    override suspend fun <T> get(entityClass: Class<T>, vararg ids: Any): List<T?> {
        TODO()
    }

    override suspend fun <T> get(entityClass: Class<T>, id: Any, lockMode: LockMode): T? {
        TODO()
    }

    override suspend fun <T> get(entityGraph: EntityGraph<T>, id: Any): T? {
        TODO()
    }

    override suspend fun insert(entity: Any) {
        TODO()
    }

    override suspend fun insertAll(vararg entities: Any) {
        TODO()
    }

    override suspend fun insertAll(batchSize: Int, vararg entities: Any) {
        TODO()
    }

    override suspend fun delete(entity: Any) {
        TODO()
    }

    override suspend fun deleteAll(vararg entities: Any?) {
        TODO()
    }

    override suspend fun deleteAll(batchSize: Int, vararg entities: Any?) {
        TODO()
    }

    override suspend fun update(entity: Any?) {
        TODO()
    }

    override suspend fun updateAll(vararg entities: Any?) {
        TODO()
    }

    override suspend fun updateAll(batchSize: Int, vararg entities: Any?) {
        TODO()
    }

    override suspend fun upsert(entity: Any?) {
        TODO()
    }

    override suspend fun upsertAll(vararg entities: Any?) {
        TODO()
    }

    override suspend fun upsertAll(batchSize: Int, vararg entities: Any?) {
        TODO()
    }

    override suspend fun upsertMultiple(entities: List<*>) {
        TODO()
    }

    override suspend fun refresh(entity: Any?) {
        TODO()
    }

    override suspend fun refreshAll(vararg entities: Any?) {
        TODO()
    }

    override suspend fun refreshAll(batchSize: Int, vararg entities: Any?) {
        TODO()
    }

    override suspend fun refresh(entity: Any?, lockMode: LockMode?) {
        TODO()
    }

    override suspend fun <T> fetch(association: T?): T? {
        TODO()
    }

    override fun getIdentifier(entity: Any?): Any? = delegate.getIdentifier(entity)

    override suspend fun <T> withTransaction(work: suspend (Coroutines.Transaction) -> T): T =
        if (currentTransaction == null) {
            CoroutinesStatelessTransaction<T>().execute(work)
        } else {
            work(currentTransaction!!)
        }

    override fun currentTransaction(): Coroutines.Transaction? = currentTransaction

    override fun isOpen(): Boolean = delegate.isOpen

    override suspend fun close() {
        withContext(dispatcher + NonCancellable) {
            val closing = CompletableFuture<Void>()
            delegate.close(closing)
            closing
        }.await()
    }

    override fun getFactory(): Coroutines.SessionFactory =
        delegate.factory.unwrap(Coroutines.SessionFactory::class.java)

    override fun <R> createSelectionQuery(queryString: String?, resultType: Class<R>?): Coroutines.SelectionQuery<R> {
        TODO()
    }

    override fun <R> createQuery(typedQueryReference: TypedQueryReference<R>): Coroutines.Query<R> {
        TODO()
    }

    override fun createMutationQuery(queryString: String?): Coroutines.MutationQuery {
        TODO()
    }

    override fun createMutationQuery(updateQuery: CriteriaUpdate<*>): Coroutines.MutationQuery {
        TODO()
    }

    override fun createMutationQuery(deleteQuery: CriteriaDelete<*>): Coroutines.MutationQuery {
        TODO()
    }

    override fun createMutationQuery(insert: JpaCriteriaInsert<*>): Coroutines.MutationQuery {
        TODO()
    }

    @Deprecated(
        "See explanation in [org.hibernate.query.QueryProducer.createSelectionQuery(string)]",
        replaceWith = ReplaceWith("createSelectionQuery(queryString, resultType)"),
        level = DeprecationLevel.WARNING,
    )
    override fun <R> createQuery(queryString: String?): Coroutines.Query<R> {
        TODO()
    }

    override fun <R> createQuery(queryString: String?, resultType: Class<R>?): Coroutines.SelectionQuery<R> {
        TODO()
    }

    override fun <R> createQuery(criteriaQuery: CriteriaQuery<R>): Coroutines.SelectionQuery<R> {
        TODO()
    }

    override fun <R> createQuery(criteriaUpdate: CriteriaUpdate<R>): Coroutines.MutationQuery {
        TODO()
    }

    override fun <R> createQuery(criteriaDelete: CriteriaDelete<R>): Coroutines.MutationQuery {
        TODO()
    }

    override fun <R> createNamedQuery(queryName: String?): Coroutines.Query<R> {
        TODO()
    }

    override fun <R> createNamedQuery(queryName: String?, resultType: Class<R>): Coroutines.SelectionQuery<R> {
        TODO()
    }

    override fun <R> createNativeQuery(queryString: String?): Coroutines.Query<R> {
        TODO()
    }

    override fun <R> createNativeQuery(queryString: String?, affectedEntities: AffectedEntities): Coroutines.Query<R> {
        TODO()
    }

    override fun <R> createNativeQuery(queryString: String?, resultType: Class<R>): Coroutines.SelectionQuery<R> {
        TODO()
    }

    override fun <R> createNativeQuery(
        queryString: String?,
        resultType: Class<R>,
        affectedEntities: AffectedEntities,
    ): Coroutines.SelectionQuery<R> {
        TODO()
    }

    override fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
    ): Coroutines.SelectionQuery<R> {
        TODO()
    }

    override fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
        affectedEntities: AffectedEntities,
    ): Coroutines.SelectionQuery<R> {
        TODO()
    }

    override fun <T> getResultSetMapping(resultType: Class<T>?, mappingName: String?): ResultSetMapping<T> =
        delegate.getResultSetMapping(resultType, mappingName)

    override fun <T> getEntityGraph(rootType: Class<T>?, graphName: String?): EntityGraph<T> {
        TODO()
    }

    override fun <T> createEntityGraph(rootType: Class<T>?): EntityGraph<T> {
        TODO()
    }

    override fun <T> createEntityGraph(rootType: Class<T>?, graphName: String?): EntityGraph<T> {
        TODO()
    }

    override fun getCriteriaBuilder(): CriteriaBuilder = getFactory().getCriteriaBuilder()

    private inner class CoroutinesStatelessTransaction<T> : Coroutines.Transaction {
        private var rollback = false

        override fun markForRollback() {
            rollback = true
        }

        override fun isMarkedForRollback(): Boolean = rollback

        suspend fun execute(work: suspend (Coroutines.Transaction) -> T): T {
            contract { callsInPlace(work, InvocationKind.EXACTLY_ONCE) }
            return try {
                currentTransaction = this
                withContext(dispatcher) {
                    begin()
                }
                try {
                    val result = work(this)
                    // finally, when there was no exception, commit or rollback the transaction
                    withContext(dispatcher) {
                        if (rollback) {
                            rollback()
                        } else {
                            commit()
                        }
                    }
                    result
                } catch (e: Throwable) {
                    // in the case of an exception or cancellation, we need to roll back the transaction
                    withContext(dispatcher) {
                        rollback()
                    }
                    throw e
                }
            } finally {
                currentTransaction = null
            }
        }

        private suspend inline fun begin() {
            delegate.reactiveConnection.beginTransaction().await()
        }

        private suspend inline fun rollback() {
            delegate.reactiveConnection.rollbackTransaction().await()
        }

        private suspend inline fun commit() {
            delegate.reactiveConnection.commitTransaction().await()
        }
    }
}
