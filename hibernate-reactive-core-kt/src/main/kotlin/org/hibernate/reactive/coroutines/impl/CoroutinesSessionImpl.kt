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
import jakarta.persistence.metamodel.Attribute
import kotlinx.coroutines.future.await
import org.hibernate.CacheMode
import org.hibernate.Filter
import org.hibernate.FlushMode
import org.hibernate.LockMode
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.common.AffectedEntities
import org.hibernate.reactive.common.Identifier
import org.hibernate.reactive.common.ResultSetMapping
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.DelicateHibernateReactiveCoroutineApi
import org.hibernate.reactive.coroutines.ExperimentalHibernateReactiveCoroutineApi
import org.hibernate.reactive.coroutines.HibernateReactiveOpen
import org.hibernate.reactive.session.ReactiveSession
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@HibernateReactiveOpen
@ExperimentalHibernateReactiveCoroutineApi
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(DelicateHibernateReactiveCoroutineApi::class)
class CoroutinesSessionImpl(private val delegate: ReactiveSession) : Coroutines.Session {
    private var currentTransaction: Coroutines.Transaction? = null

    override suspend fun <T> find(entityClass: Class<T>, id: Any?): T? {
        TODO()
    }

    override suspend fun <T> find(entityClass: Class<T>, id: Any?, lockMode: LockMode?): T? {
        TODO()
    }

    override suspend fun <T> find(entityGraph: EntityGraph<T>, id: Any?): T? {
        TODO()
    }

    override suspend fun <T> find(entityClass: Class<T>, vararg ids: Any?): List<T?> {
        TODO()
    }

    override suspend fun <T> find(entityClass: Class<T>, naturalId: Identifier<T>): T? {
        TODO()
    }

    override fun <T> getReference(entityClass: Class<T?>?, id: Any?): T? = delegate.getReference(entityClass, id)

    override fun <T> getReference(entity: T?): T? =
        delegate.getReference(delegate.getEntityClass(entity), delegate.getEntityId(entity))

    override suspend fun persist(instance: Any?) {
        TODO()
    }

    override suspend fun persist(entityName: String?, instance: Any?) {
        TODO()
    }

    override suspend fun persistAll(vararg entities: Any?) {
        TODO()
    }

    override suspend fun remove(entity: Any?) {
        TODO()
    }

    override suspend fun removeAll(vararg entities: Any?) {
        TODO()
    }

    override suspend fun <T> merge(entity: T?): T? {
        TODO()
    }

    override suspend fun mergeAll(vararg entities: Any?) {
        TODO()
    }

    override suspend fun refresh(entity: Any?) {
        TODO()
    }

    override suspend fun refresh(entity: Any?, lockMode: LockMode?) {
        TODO()
    }

    override suspend fun refreshAll(vararg entities: Any?) {
        TODO()
    }

    override suspend fun lock(entity: Any?, lockMode: LockMode?) {
        TODO()
    }

    override suspend fun flush() {
        TODO()
    }

    override suspend fun <T> fetch(association: T?): T? {
        TODO()
    }

    override suspend fun <E, T> fetch(entity: E?, field: Attribute<E, T>): T? {
        TODO()
    }

    override suspend fun <T> unproxy(association: T?): T? {
        TODO()
    }

    override fun getLockMode(entity: Any?): LockMode? = delegate.getCurrentLockMode(entity)

    override fun contains(entity: Any?): Boolean = delegate.contains(entity)

    override fun setFlushMode(flushMode: FlushMode): Coroutines.Session = apply {
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
    override fun getFlushMode(): FlushMode? = when (delegate.getHibernateFlushMode()) {
        FlushMode.AUTO -> FlushMode.AUTO
        FlushMode.COMMIT -> FlushMode.COMMIT
        FlushMode.MANUAL -> FlushMode.MANUAL
        FlushMode.ALWAYS -> FlushMode.ALWAYS
    }

    override fun detach(entity: Any?): Coroutines.Session = apply { delegate.detach(entity) }

    override fun clear(): Coroutines.Session = apply { delegate.clear() }

    override fun enableFetchProfile(name: String?): Coroutines.Session = apply { delegate.enableFetchProfile(name) }

    override fun disableFetchProfile(name: String?): Coroutines.Session = apply { delegate.disableFetchProfile(name) }

    override fun isFetchProfileEnabled(name: String?): Boolean = delegate.isFetchProfileEnabled(name)

    override fun setDefaultReadOnly(readOnly: Boolean): Coroutines.Session = apply {
        delegate.isDefaultReadOnly = readOnly
    }

    override fun isDefaultReadOnly(): Boolean = delegate.isDefaultReadOnly

    override fun setReadOnly(entityOrProxy: Any, readOnly: Boolean): Coroutines.Session = apply {
        delegate.setReadOnly(entityOrProxy, readOnly)
    }

    override fun isReadOnly(entityOrProxy: Any): Boolean = delegate.isReadOnly(entityOrProxy)

    override fun setCacheMode(cacheMode: CacheMode?): Coroutines.Session = apply { delegate.cacheMode = cacheMode }

    override fun getCacheMode(): CacheMode? = delegate.cacheMode

    override fun setBatchSize(batchSize: Int?): Coroutines.Session = apply { delegate.batchSize = batchSize }

    override fun getBatchSize(): Int? = delegate.batchSize

    override fun enableFilter(filterName: String?): Filter? = delegate.enableFilter(filterName)

    override fun disableFilter(filterName: String?) {
        delegate.disableFilter(filterName)
    }

    override fun getEnabledFilter(filterName: String?): Filter? = delegate.getEnabledFilter(filterName)

    override fun getFetchBatchSize(): Int = delegate.fetchBatchSize

    override fun setFetchBatchSize(batchSize: Int): Coroutines.Session = apply { delegate.fetchBatchSize = batchSize }

    override fun isSubselectFetchingEnabled(): Boolean = delegate.isSubselectFetchingEnabled

    override fun setSubselectFetchingEnabled(enabled: Boolean): Coroutines.Session = apply {
        delegate.isSubselectFetchingEnabled = enabled
    }

    // TODO this is safe context?
    override suspend fun <T> withTransaction(work: suspend (Coroutines.Transaction) -> T): T =
        if (currentTransaction == null) {
            CoroutinesTransactionImpl<T>().execute(work)
        } else {
            work(currentTransaction!!)
        }

    override fun currentTransaction(): Coroutines.Transaction? = currentTransaction

    override fun isOpen(): Boolean = delegate.isOpen

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

    override fun <T> getResultSetMapping(resultType: Class<T>?, mappingName: String?): ResultSetMapping<T> {
        TODO()
    }

    override fun <T> getEntityGraph(rootType: Class<T>?, graphName: String?): EntityGraph<T> {
        TODO()
    }

    override fun <T> createEntityGraph(rootType: Class<T>?): EntityGraph<T> {
        TODO()
    }

    override fun <T> createEntityGraph(rootType: Class<T>?, graphName: String?): EntityGraph<T> {
        TODO()
    }

    override fun getCriteriaBuilder(): CriteriaBuilder = delegate.factory.criteriaBuilder

    override suspend fun close() {
        TODO()
    }

    // Are safe to call await in the correct context?
    // Private because don't have access outside this class. Inner to have behavior similar to protected
    private inner class CoroutinesTransactionImpl<T> : Coroutines.Transaction {
        private var rollback = false

        override fun markForRollback() {
            rollback = true
        }

        override fun isMarkedForRollback(): Boolean = rollback

        suspend fun execute(work: suspend (Coroutines.Transaction) -> T): T {
            contract { callsInPlace(work, InvocationKind.EXACTLY_ONCE) }
            return try {
                currentTransaction = this
                begin()
                executeInTransaction(work)
            } finally {
                currentTransaction = null
            }
        }

        private suspend inline fun executeInTransaction(work: suspend (Coroutines.Transaction) -> T): T {
            contract { callsInPlace(work, InvocationKind.EXACTLY_ONCE) }
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

        private suspend inline fun flush() {
            delegate.reactiveAutoflush().await()
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

        private suspend inline fun beforeCompletion() {
            delegate.reactiveActionQueue.beforeTransactionCompletion().await()
        }

        private suspend inline fun afterCompletion() {
            delegate.reactiveActionQueue.afterTransactionCompletion(!rollback).await()
        }
    }
}
