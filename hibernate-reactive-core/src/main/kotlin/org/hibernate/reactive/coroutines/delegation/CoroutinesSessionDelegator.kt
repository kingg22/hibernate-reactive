/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.reactive.coroutines.delegation

import jakarta.persistence.EntityGraph
import jakarta.persistence.TypedQueryReference
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.metamodel.Attribute
import org.hibernate.CacheMode
import org.hibernate.FlushMode
import org.hibernate.LockMode
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.common.AffectedEntities
import org.hibernate.reactive.common.Identifier
import org.hibernate.reactive.common.ResultSetMapping
import org.hibernate.reactive.coroutines.Coroutines

/** Wraps a [CoroutinesSessionDelegator.delegate] session. */
abstract class CoroutinesSessionDelegator : Coroutines.Session {
    abstract fun delegate(): Coroutines.Session

    override suspend fun <T> find(
        entityClass: Class<T>,
        id: Any?,
    ) = delegate().find(entityClass, id)

    override suspend fun <T> find(
        entityClass: Class<T>,
        id: Any?,
        lockMode: LockMode?,
    ) = delegate().find(entityClass, id, lockMode)

    override suspend fun <T> find(
        entityGraph: EntityGraph<T>,
        id: Any?,
    ) = delegate().find(entityGraph, id)

    override suspend fun <T> find(
        entityClass: Class<T>,
        vararg ids: Any?,
    ) = delegate().find(entityClass, *ids)

    override suspend fun <T> find(
        entityClass: Class<T>,
        naturalId: Identifier<T>,
    ) = delegate().find(entityClass, naturalId)

    override fun <T> getReference(
        entityClass: Class<T?>?,
        id: Any?,
    ) = delegate().getReference(entityClass, id)

    override fun <T> getReference(entity: T?) = delegate().getReference(entity)

    override suspend fun persist(instance: Any?) = delegate().persist(instance)

    override suspend fun persist(
        entityName: String?,
        instance: Any?,
    ) = delegate().persist(entityName, instance)

    override suspend fun persistAll(vararg entities: Any?) = delegate().persistAll(*entities)

    override suspend fun remove(entity: Any?) = delegate().remove(entity)

    override suspend fun removeAll(vararg entities: Any?) = delegate().removeAll(*entities)

    override suspend fun <T> merge(entity: T?) = delegate().merge(entity)

    override suspend fun mergeAll(vararg entities: Any?) = delegate().mergeAll(*entities)

    override suspend fun refresh(entity: Any?) = delegate().refresh(entity)

    override suspend fun refresh(
        entity: Any?,
        lockMode: LockMode?,
    ) = delegate().refresh(entity, lockMode)

    override suspend fun refreshAll(vararg entities: Any?) = delegate().refreshAll(*entities)

    override suspend fun lock(
        entity: Any?,
        lockMode: LockMode?,
    ) = delegate().lock(entity, lockMode)

    override suspend fun flush() = delegate().flush()

    override suspend fun <T> fetch(association: T?) = delegate().fetch(association)

    override suspend fun <E, T> fetch(
        entity: E?,
        field: Attribute<E, T>,
    ) = delegate().fetch(entity, field)

    override suspend fun <T> unproxy(association: T?) = delegate().unproxy(association)

    override fun getLockMode(entity: Any?) = delegate().getLockMode(entity)

    override fun contains(entity: Any?) = delegate().contains(entity)

    override fun setFlushMode(flushMode: FlushMode) = delegate().setFlushMode(flushMode)

    override fun getFlushMode() = delegate().getFlushMode()

    override fun detach(entity: Any?) = delegate().detach(entity)

    override fun clear() = delegate().clear()

    override fun enableFetchProfile(name: String?) = delegate().enableFetchProfile(name)

    override fun disableFetchProfile(name: String?) = delegate().disableFetchProfile(name)

    override fun isFetchProfileEnabled(name: String?) = delegate().isFetchProfileEnabled(name)

    override fun setDefaultReadOnly(readOnly: Boolean) = delegate().setDefaultReadOnly(readOnly)

    override fun isDefaultReadOnly() = delegate().isDefaultReadOnly()

    override fun setReadOnly(
        entityOrProxy: Any?,
        readOnly: Boolean,
    ) = delegate().setReadOnly(entityOrProxy, readOnly)

    override fun isReadOnly(entityOrProxy: Any?) = delegate().isReadOnly(entityOrProxy)

    override fun setCacheMode(cacheMode: CacheMode?) = delegate().setCacheMode(cacheMode)

    override fun getCacheMode() = delegate().getCacheMode()

    override fun setBatchSize(batchSize: Int?) = delegate().setBatchSize(batchSize)

    override fun getBatchSize() = delegate().getBatchSize()

    override fun enableFilter(filterName: String?) = delegate().enableFilter(filterName)

    override fun disableFilter(filterName: String?) = delegate().disableFilter(filterName)

    override fun getEnabledFilter(filterName: String?) = delegate().getEnabledFilter(filterName)

    override fun getFetchBatchSize() = delegate().getFetchBatchSize()

    override fun setFetchBatchSize(batchSize: Int) = delegate().setFetchBatchSize(batchSize)

    override fun isSubselectFetchingEnabled() = delegate().isSubselectFetchingEnabled()

    override fun setSubselectFetchingEnabled(enabled: Boolean) = delegate().setSubselectFetchingEnabled(enabled)

    override suspend fun <T> withTransaction(work: suspend (Coroutines.Transaction) -> T) = delegate().withTransaction(work)

    override fun currentTransaction() = delegate().currentTransaction()

    override fun isOpen() = delegate().isOpen()

    override fun getFactory() = delegate().getFactory()

    override fun <R> createSelectionQuery(
        queryString: String?,
        resultType: Class<R>?,
    ) = delegate().createSelectionQuery(queryString, resultType)

    override fun <R> createQuery(typedQueryReference: TypedQueryReference<R>) = delegate().createQuery(typedQueryReference)

    override fun createMutationQuery(queryString: String?) = delegate().createMutationQuery(queryString)

    override fun createMutationQuery(updateQuery: CriteriaUpdate<*>) = delegate().createMutationQuery(updateQuery)

    override fun createMutationQuery(deleteQuery: CriteriaDelete<*>) = delegate().createMutationQuery(deleteQuery)

    override fun createMutationQuery(insert: JpaCriteriaInsert<*>) = delegate().createMutationQuery(insert)

    @Deprecated(
        "See explanation in [org.hibernate.query.QueryProducer.createSelectionQuery(string)]",
        replaceWith = ReplaceWith("createSelectionQuery(queryString, resultType)"),
        level = DeprecationLevel.WARNING,
    )
    override fun <R> createQuery(queryString: String?) = delegate().createQuery<R>(queryString)

    override fun <R> createQuery(
        queryString: String?,
        resultType: Class<R>?,
    ) = delegate().createQuery(queryString, resultType)

    override fun <R> createQuery(criteriaQuery: CriteriaQuery<R>) = delegate().createQuery(criteriaQuery)

    override fun <R> createQuery(criteriaUpdate: CriteriaUpdate<R>) = delegate().createQuery(criteriaUpdate)

    override fun <R> createQuery(criteriaDelete: CriteriaDelete<R>) = delegate().createQuery(criteriaDelete)

    override fun <R> createNamedQuery(queryName: String?) = delegate().createNamedQuery<R>(queryName)

    override fun <R> createNamedQuery(
        queryName: String?,
        resultType: Class<R>,
    ) = delegate().createNamedQuery(queryName, resultType)

    override fun <R> createNativeQuery(queryString: String?) = delegate().createNativeQuery<R>(queryString)

    override fun <R> createNativeQuery(
        queryString: String?,
        affectedEntities: AffectedEntities,
    ) = delegate().createNativeQuery<R>(queryString, affectedEntities)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultType: Class<R>,
    ) = delegate().createNativeQuery(queryString, resultType)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultType: Class<R>,
        affectedEntities: AffectedEntities,
    ) = delegate().createNativeQuery(queryString, resultType, affectedEntities)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
    ) = delegate().createNativeQuery(queryString, resultSetMapping)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
        affectedEntities: AffectedEntities,
    ) = delegate().createNativeQuery(queryString, resultSetMapping, affectedEntities)

    override fun <T> getResultSetMapping(
        resultType: Class<T>?,
        mappingName: String?,
    ) = delegate().getResultSetMapping(resultType, mappingName)

    override fun <T> getEntityGraph(
        rootType: Class<T>?,
        graphName: String?,
    ) = delegate().getEntityGraph(rootType, graphName)

    override fun <T> createEntityGraph(rootType: Class<T>?) = delegate().createEntityGraph(rootType)

    override fun <T> createEntityGraph(
        rootType: Class<T>?,
        graphName: String?,
    ) = delegate().createEntityGraph(rootType, graphName)

    override fun getCriteriaBuilder() = delegate().getCriteriaBuilder()

    override suspend fun close() = delegate().close()
}
