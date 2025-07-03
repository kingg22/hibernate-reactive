/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.reactive.coroutines.delegation

import jakarta.persistence.EntityGraph
import jakarta.persistence.TypedQueryReference
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import org.hibernate.LockMode
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.common.AffectedEntities
import org.hibernate.reactive.common.ResultSetMapping
import org.hibernate.reactive.coroutines.Coroutines

/** Wraps a [CoroutinesStatelessSessionDelegator.delegate] stateless session. */
abstract class CoroutinesStatelessSessionDelegator : Coroutines.StatelessSession {
    abstract fun delegate(): Coroutines.StatelessSession

    override suspend fun <T> get(
        entityClass: Class<T>,
        id: Any?,
    ): T? = delegate().get(entityClass, id)

    override suspend fun <T> get(
        entityClass: Class<T>,
        vararg ids: Any,
    ): List<T?> = delegate().get(entityClass, *ids)

    override suspend fun <T> get(
        entityClass: Class<T>,
        id: Any,
        lockMode: LockMode,
    ): T? = delegate().get(entityClass, id, lockMode)

    override suspend fun <T> get(
        entityGraph: EntityGraph<T>,
        id: Any,
    ): T? = delegate().get(entityGraph, id)

    override suspend fun insert(entity: Any?) = delegate().insert(entity)

    override suspend fun insertAll(vararg entities: Any?) = delegate().insertAll(*entities)

    override suspend fun insertAll(
        batchSize: Int,
        vararg entities: Any,
    ) = delegate().insertAll(batchSize, *entities)

    override suspend fun insertMultiple(entities: List<*>) = delegate().insertMultiple(entities)

    override suspend fun delete(entity: Any) = delegate().delete(entity)

    override suspend fun deleteAll(vararg entities: Any?) = delegate().deleteAll(*entities)

    override suspend fun deleteAll(
        batchSize: Int,
        vararg entities: Any?,
    ) = delegate().deleteAll(batchSize, *entities)

    override suspend fun deleteMultiple(entities: List<*>) = delegate().deleteMultiple(entities)

    override suspend fun update(entity: Any?) = delegate().update(entity)

    override suspend fun updateAll(vararg entities: Any?) = delegate().updateAll(*entities)

    override suspend fun updateAll(
        batchSize: Int,
        vararg entities: Any?,
    ) = delegate().updateAll(batchSize, *entities)

    override suspend fun updateMultiple(entities: List<*>) = delegate().updateMultiple(entities)

    override suspend fun upsert(entity: Any?) = delegate().upsert(entity)

    override suspend fun upsertAll(vararg entities: Any?) = delegate().upsertAll(*entities)

    override suspend fun upsertAll(
        batchSize: Int,
        vararg entities: Any?,
    ) = delegate().upsertAll(batchSize, *entities)

    override suspend fun upsertMultiple(entities: List<*>) = delegate().upsertMultiple(entities)

    override suspend fun refresh(entity: Any?) = delegate().refresh(entity)

    override suspend fun refreshAll(vararg entities: Any?) = delegate().refreshAll(*entities)

    override suspend fun refreshAll(
        batchSize: Int,
        vararg entities: Any?,
    ) = delegate().refreshAll(batchSize, *entities)

    override suspend fun refreshMultiple(entities: List<*>) = delegate().refreshMultiple(entities)

    override suspend fun refresh(
        entity: Any?,
        lockMode: LockMode?,
    ) = delegate().refresh(entity, lockMode)

    override suspend fun <T> fetch(association: T?): T? = delegate().fetch(association)

    override fun getIdentifier(entity: Any?): Any? = delegate().getIdentifier(entity)

    override suspend fun <T> withTransaction(work: suspend (Coroutines.Transaction) -> T): T = delegate().withTransaction(work)

    override fun currentTransaction(): Coroutines.Transaction? = delegate().currentTransaction()

    override fun isOpen(): Boolean = delegate().isOpen()

    override suspend fun close() = delegate().close()

    override fun getFactory(): Coroutines.SessionFactory = delegate().getFactory()

    override fun <R> createSelectionQuery(
        queryString: String?,
        resultType: Class<R>?,
    ): Coroutines.SelectionQuery<R> = delegate().createSelectionQuery(queryString, resultType)

    override fun <R> createQuery(typedQueryReference: TypedQueryReference<R>): Coroutines.Query<R> =
        delegate().createQuery(typedQueryReference)

    override fun createMutationQuery(queryString: String?): Coroutines.MutationQuery = delegate().createMutationQuery(queryString)

    override fun createMutationQuery(updateQuery: CriteriaUpdate<*>): Coroutines.MutationQuery = delegate().createMutationQuery(updateQuery)

    override fun createMutationQuery(deleteQuery: CriteriaDelete<*>): Coroutines.MutationQuery = delegate().createMutationQuery(deleteQuery)

    override fun createMutationQuery(insert: JpaCriteriaInsert<*>): Coroutines.MutationQuery = delegate().createMutationQuery(insert)

    @Deprecated(
        "See explanation in [org.hibernate.query.QueryProducer.createSelectionQuery(string)]",
        replaceWith = ReplaceWith("createSelectionQuery(queryString, resultType)"),
        level = DeprecationLevel.WARNING,
    )
    override fun <R> createQuery(queryString: String?): Coroutines.Query<R> = delegate().createQuery(queryString)

    override fun <R> createQuery(
        queryString: String?,
        resultType: Class<R>?,
    ): Coroutines.SelectionQuery<R> = delegate().createQuery(queryString, resultType)

    override fun <R> createQuery(criteriaQuery: CriteriaQuery<R>): Coroutines.SelectionQuery<R> = delegate().createQuery(criteriaQuery)

    override fun <R> createQuery(criteriaUpdate: CriteriaUpdate<R>): Coroutines.MutationQuery = delegate().createQuery(criteriaUpdate)

    override fun <R> createQuery(criteriaDelete: CriteriaDelete<R>): Coroutines.MutationQuery = delegate().createQuery(criteriaDelete)

    override fun <R> createNamedQuery(queryName: String?): Coroutines.Query<R> = delegate().createNamedQuery(queryName)

    override fun <R> createNamedQuery(
        queryName: String?,
        resultType: Class<R>,
    ): Coroutines.SelectionQuery<R> = delegate().createNamedQuery(queryName, resultType)

    override fun <R> createNativeQuery(queryString: String?): Coroutines.Query<R> = delegate().createNativeQuery(queryString)

    override fun <R> createNativeQuery(
        queryString: String?,
        affectedEntities: AffectedEntities,
    ): Coroutines.Query<R> = delegate().createNativeQuery(queryString, affectedEntities)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultType: Class<R>,
    ): Coroutines.SelectionQuery<R> = delegate().createNativeQuery(queryString, resultType)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultType: Class<R>,
        affectedEntities: AffectedEntities,
    ): Coroutines.SelectionQuery<R> = delegate().createNativeQuery(queryString, resultType, affectedEntities)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
    ): Coroutines.SelectionQuery<R> = delegate().createNativeQuery(queryString, resultSetMapping)

    override fun <R> createNativeQuery(
        queryString: String?,
        resultSetMapping: ResultSetMapping<R>?,
        affectedEntities: AffectedEntities,
    ): Coroutines.SelectionQuery<R> = delegate().createNativeQuery(queryString, resultSetMapping, affectedEntities)

    override fun <T> getResultSetMapping(
        resultType: Class<T>?,
        mappingName: String?,
    ): ResultSetMapping<T> = delegate().getResultSetMapping(resultType, mappingName)

    override fun <T> getEntityGraph(
        rootType: Class<T>?,
        graphName: String?,
    ): EntityGraph<T> = delegate().getEntityGraph(rootType, graphName)

    override fun <T> createEntityGraph(rootType: Class<T>?): EntityGraph<T> = delegate().createEntityGraph(rootType)

    override fun <T> createEntityGraph(
        rootType: Class<T>?,
        graphName: String?,
    ): EntityGraph<T> = delegate().createEntityGraph(rootType, graphName)

    override fun getCriteriaBuilder(): CriteriaBuilder = delegate().getCriteriaBuilder()
}
