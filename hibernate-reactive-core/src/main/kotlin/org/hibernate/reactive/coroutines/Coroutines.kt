/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.reactive.coroutines

import jakarta.persistence.CacheRetrieveMode
import jakarta.persistence.CacheStoreMode
import jakarta.persistence.EntityGraph
import jakarta.persistence.FlushModeType
import jakarta.persistence.LockModeType
import jakarta.persistence.Parameter
import jakarta.persistence.TypedQueryReference
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.Metamodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.hibernate.Cache
import org.hibernate.CacheMode
import org.hibernate.Filter
import org.hibernate.FlushMode
import org.hibernate.Incubating
import org.hibernate.LockMode
import org.hibernate.bytecode.enhance.spi.interceptor.EnhancementAsProxyLazinessInterceptor
import org.hibernate.collection.spi.AbstractPersistentCollection
import org.hibernate.engine.internal.ManagedTypeHelper
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.internal.util.LockModeConverter
import org.hibernate.jpa.internal.util.CacheModeHelper
import org.hibernate.jpa.internal.util.FlushModeTypeHelper
import org.hibernate.proxy.HibernateProxy
import org.hibernate.query.Page
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.common.AffectedEntities
import org.hibernate.reactive.common.Identifier
import org.hibernate.reactive.common.ResultSetMapping
import org.hibernate.reactive.session.impl.ReactiveQueryExecutorLookup
import org.hibernate.stat.Statistics
import java.util.concurrent.CompletionStage
import kotlin.coroutines.EmptyCoroutineContext

/**
 * An API for Hibernate Reactive where non-blocking operations are represented as suspend functions.
 *
 * The [Query], [Session], and [SessionFactory] interfaces declared here are simply non-blocking counterparts to
 * the similarly named interfaces in Hibernate ORM.
 */
interface Coroutines {
    /**
     * A non-blocking counterpart to the Hibernate
     * [org.hibernate.query.Query] interface, allowing reactive
     * execution of HQL and JPQL queries.
     *
     * The semantics of operations on this interface are identical to the
     * semantics of the similarly named operations of `Query`, except
     * that the operations are performed asynchronously, returning without blocking the calling thread.
     *
     * Note that [jakarta.persistence.TemporalType] is not supported
     * as an argument for parameter bindings, and so parameters of type
     * [java.util.Date] or [java.util.Calendar] should not be
     * used. Instead, datetime types from `java.time` should be used
     * as parameters.
     *
     * @see jakarta.persistence.Query
     */
    interface AbstractQuery {
        /**
         * Set the value of an ordinal parameter.
         * Ordinal parameters are numbered from 1,
         * and are specified in the query using placeholder tokens of form `?1`, `?2`, etc.
         *
         * @param parameter an integer identifying the ordinal parameter
         * @param argument the argument to set
         */
        fun setParameter(
            parameter: Int,
            argument: Any,
        ): AbstractQuery

        /**
         * Set the value of a named parameter.
         * Named parameters are specified in the query using placeholder tokens of form `:name`.
         *
         * @param parameter the name of the parameter
         * @param argument the argument to set
         */
        fun setParameter(
            parameter: String,
            argument: Any,
        ): AbstractQuery

        /**
         * Set the value of a typed parameter.
         * Typed parameters are obtained from the JPA [CriteriaBuilder],
         * which may itself be obtained by calling [SessionFactory.getCriteriaBuilder].
         *
         * @param parameter the parameter
         * @param argument the argument to set
         *
         * @see CriteriaBuilder.parameter
         */
        fun <T> setParameter(
            parameter: Parameter<T>,
            argument: T,
        ): AbstractQuery

        /**
         * Set the comment for this query.
         * This comment will be prepended to the SQL query sent to the database.
         *
         * @param comment The human-readable comment
         */
        fun setComment(comment: String?): AbstractQuery

        fun getComment(): String?
    }

    interface SelectionQuery<R> : AbstractQuery {
        /**
         * Set the maximum number of results that may be returned by this
         * query when executed.
         */
        fun setMaxResults(maxResults: Int): SelectionQuery<R>

        /**
         * Set the position of the first result that may be returned by
         * this query when executed, where the results are numbered from
         * 0.
         */
        fun setFirstResult(firstResult: Int): SelectionQuery<R>

        /**
         * Set the [page][Page] of results to return.
         *
         * @see Page
         *
         * @since 2.1
         */
        @Incubating
        fun setPage(page: Page): SelectionQuery<R>

        /** @return the maximum number results, or [Integer.MAX_VALUE] if not set */
        fun getMaxResults(): Int

        /** @return the first result, or 0 if not set */
        fun getFirstResult(): Int

        /**
         * Asynchronously execute this query, returning a single row that
         * matches the query, throwing an exception if the query returns
         * zero rows or more than one matching row. If the query has multiple
         * results per row, the results are returned in an instance of
         * `Object[]`.
         *
         * @return the single resulting row
         * @throws jakarta.persistence.NoResultException if there is no result
         * @throws jakarta.persistence.NonUniqueResultException if there are multiple results
         *
         * @see jakarta.persistence.Query.getSingleResult
         */
        @JvmSynthetic
        suspend fun getSingleResult(): R

        /**
         * Asynchronously execute this query, returning a single row that
         * matches the query, or `null` if the query returns no results,
         * throwing an exception if the query returns more than one matching
         * row. If the query has multiple results per row, the results are
         * returned in an instance of `Object[]`.
         *
         * @return the single resulting row or `null`
         * @throws jakarta.persistence.NonUniqueResultException if there are multiple results
         *
         * @see Coroutines.SelectionQuery.getSingleResult
         */
        @JvmSynthetic
        suspend fun getSingleResultOrNull(): R?

        /**
         * Determine the size of the query result list that would be
         * returned by calling [Coroutines.SelectionQuery.getResultList] with no
         * [offset][Coroutines.SelectionQuery.getFirstResult] or
         * [limit][Coroutines.SelectionQuery.getMaxResults] applied to the query.
         *
         * @return the size of the list that would be returned
         */
        @Incubating
        @JvmSynthetic
        suspend fun getResultCount(): Long?

        /**
         * Asynchronously execute this query, returning the query results as a [List], suspending.
         * If the query has multiple results per row, the results are returned in an instance of `Object[]`.
         *
         * @return the resulting rows as a [List]
         *
         * @see jakarta.persistence.Query.getResultList
         */
        @JvmSynthetic
        suspend fun getResultList(): List<R>

        /**
         * Set the read-only/modifiable mode for entities and proxies
         * loaded by this Query. This setting overrides the default setting
         * for the persistence context.
         *
         * @see Session.setDefaultReadOnly
         */
        fun setReadOnly(readOnly: Boolean): SelectionQuery<R>

        /**
         * @return the read-only/modifiable mode
         *
         * @see Session.isDefaultReadOnly
         */
        fun isReadOnly(): Boolean

        /**
         * Enable or disable caching of this query result set in the second-level query cache.
         *
         * @param cacheable `true` if this query is cacheable
         */
        fun setCacheable(cacheable: Boolean): SelectionQuery<R>

        /**
         * @return `true` if this query is cacheable
         *
         * @see Coroutines.SelectionQuery.setCacheable
         */
        fun isCacheable(): Boolean

        /**
         * Set the name of the cache region in which to store this query result set
         * if [caching is enabled] [Coroutines.SelectionQuery.setCacheable].
         *
         * @param cacheRegion the name of the cache region
         */
        fun setCacheRegion(cacheRegion: String?): SelectionQuery<R>

        /**
         * @return the name of the cache region
         *
         * @see [Coroutines.SelectionQuery.setCacheRegion]
         */
        fun getCacheRegion(): String?

        /** Set the current [CacheMode] in effect while this query is being executed. */
        fun setCacheMode(cacheMode: CacheMode?): SelectionQuery<R>

        /** Set the current [CacheStoreMode] in effect while this query is being executed. */
        fun setCacheStoreMode(cacheStoreMode: CacheStoreMode?): SelectionQuery<R> =
            setCacheMode(
                CacheModeHelper.interpretCacheMode(
                    cacheStoreMode,
                    CacheModeHelper.interpretCacheRetrieveMode(this.getCacheMode()),
                ),
            )

        /** Set the current [CacheRetrieveMode] in effect while this query is being executed. */
        fun setCacheRetrieveMode(cacheRetrieveMode: CacheRetrieveMode?): SelectionQuery<R> =
            setCacheMode(
                CacheModeHelper.interpretCacheMode(
                    CacheModeHelper.interpretCacheStoreMode(this.getCacheMode()),
                    cacheRetrieveMode,
                ),
            )

        fun getCacheStoreMode(): CacheStoreMode?

        fun getCacheRetrieveMode(): CacheRetrieveMode?

        /**
         * Obtain the [CacheMode] in effect for this query
         * By default, the query inherits the `CacheMode` of the [Session] from which is originates.
         *
         * @see Session.getCacheMode
         */
        fun getCacheMode(): CacheMode?

        /** Set the current [FlushMode] in effect while this query is being executed. */
        fun setFlushMode(flushMode: FlushMode?): SelectionQuery<R>

        /** Set the current [FlushModeType] in effect while this query is being executed. */
        fun setFlushMode(flushModeType: FlushModeType?): SelectionQuery<R> = setFlushMode(FlushModeTypeHelper.getFlushMode(flushModeType))

        /**
         * Obtain the [FlushMode] in effect for this query.
         * By default, the query inherits the `FlushMode` of the [Session] from which it originates.
         *
         * @see Session.getFlushMode
         */
        fun getFlushMode(): FlushMode?

        /**
         * Set the [LockMode] to use for the whole query.
         */
        fun setLockMode(lockMode: LockMode?): SelectionQuery<R>

        /**
         * Set the [LockModeType] to use for the whole query.
         */
        fun setLockMode(lockModeType: LockModeType): SelectionQuery<R> = setLockMode(LockModeConverter.convertToLockMode(lockModeType))

        /**
         * Set the [LockMode] to use for specified alias (as defined in the query's `from` clause).
         *
         * @param alias the from clause alias
         * @param lockMode the requested [LockMode]
         *
         * @see org.hibernate.query.Query.setLockMode
         */
        fun setLockMode(
            alias: String?,
            lockMode: LockMode?,
        ): SelectionQuery<R>

        /**
         * Set the [LockModeType] to use for specified alias (as defined in the query's `from` clause).
         *
         * @param alias the from clause alias
         * @param lockModeType the requested [LockModeType]
         *
         * @see org.hibernate.query.Query.setLockMode
         */
        fun setLockMode(
            alias: String?,
            lockModeType: LockModeType,
        ): SelectionQuery<R> = setLockMode(alias, LockModeConverter.convertToLockMode(lockModeType))

        /** Set the [EntityGraph] that will be used as a fetch plan for the root entity returned by this query. */
        fun setPlan(entityGraph: EntityGraph<R>?): SelectionQuery<R>

        /**
         * Enable a [fetch][org.hibernate.annotations.FetchProfile] which will be in effect during execution of this query.
         */
        fun enableFetchProfile(profileName: String?): SelectionQuery<R>
    }

    interface MutationQuery : AbstractQuery {
        /**
         * Asynchronously execute this delete, update, or insert query,
         * returning the updated row count.
         *
         * @return the row count as an integer
         *
         * @see jakarta.persistence.Query.executeUpdate
         */
        @JvmSynthetic
        suspend fun executeUpdate(): Int
    }

    interface Query<R> :
        SelectionQuery<R>,
        MutationQuery {
        override fun setCacheStoreMode(cacheStoreMode: CacheStoreMode?): Query<R> =
            apply { super<SelectionQuery>.setCacheStoreMode(cacheStoreMode) }

        override fun setCacheRetrieveMode(cacheRetrieveMode: CacheRetrieveMode?): Query<R> =
            apply { super<SelectionQuery>.setCacheRetrieveMode(cacheRetrieveMode) }

        override fun setFlushMode(flushModeType: FlushModeType?): Query<R> = apply { super<SelectionQuery>.setFlushMode(flushModeType) }

        override fun setLockMode(lockModeType: LockModeType): Query<R> = apply { super<SelectionQuery>.setLockMode(lockModeType) }

        override fun setLockMode(
            alias: String?,
            lockModeType: LockModeType,
        ): Query<R> = apply { super<SelectionQuery>.setLockMode(alias, lockModeType) }
    }

    /**
     * Operations common to objects which act as factories for instances of
     * [Query]. This is a common supertype of [Session] and
     * [StatelessSession].
     *
     * @since 3.0
     */
    interface QueryProducer {
        /**
         * Create an instance of [SelectionQuery] for the given HQL/JPQL query string.
         *
         * @param queryString The HQL/JPQL query
         *
         * @return The [SelectionQuery] instance for manipulation and execution
         *
         * @see jakarta.persistence.EntityManager.createQuery
         */
        fun <R> createSelectionQuery(
            queryString: String?,
            resultType: Class<R>?,
        ): SelectionQuery<R>

        /**
         * Create a typed [org.hibernate.query.Query] instance for the given typed query reference.
         *
         * @param typedQueryReference the type query reference
         *
         * @return The [org.hibernate.query.Query] instance for execution
         *
         * @throws IllegalArgumentException if a query has not been defined with the name of the typed query reference or
         * if the query result is found to not be assignable to result class of the typed query reference
         *
         * @see org.hibernate.query.QueryProducer.createQuery
         */
        fun <R> createQuery(typedQueryReference: TypedQueryReference<R>): Query<R>

        /**
         * Create an instance of [MutationQuery] for the given HQL/JPQL update or delete statement.
         *
         * @param queryString The HQL/JPQL query, update or delete statement
         *
         * @return The [MutationQuery] instance for manipulation and execution
         *
         * @see jakarta.persistence.EntityManager.createQuery
         */
        fun createMutationQuery(queryString: String?): MutationQuery

        /**
         * Create an instance of [MutationQuery] for the given update tree.
         *
         * @param updateQuery the update criteria query
         *
         * @return The [MutationQuery] instance for manipulation and execution
         *
         * @see org.hibernate.query.QueryProducer.createMutationQuery
         */
        fun createMutationQuery(updateQuery: CriteriaUpdate<*>): MutationQuery

        /**
         * Create an instance of [MutationQuery] for the given delete tree.
         *
         * @param deleteQuery the delete criteria query
         *
         * @return The [MutationQuery] instance for manipulation and execution
         *
         * @see org.hibernate.query.QueryProducer.createMutationQuery
         */
        fun createMutationQuery(deleteQuery: CriteriaDelete<*>): MutationQuery

        /**
         * Create a [MutationQuery] from the given insert select criteria tree
         *
         * @param insert the insert select criteria query
         *
         * @return The [MutationQuery] instance for manipulation and execution
         *
         * @see org.hibernate.query.QueryProducer.createMutationQuery
         */
        fun createMutationQuery(insert: JpaCriteriaInsert<*>): MutationQuery

        /**
         * Create an instance of [Query] for the given HQL/JPQL query
         * string or HQL/JPQL update or delete statement. In the case of an
         * update or delete, the returned [Query] must be executed using
         * [Query.executeUpdate] which returns an affected row count.
         *
         * @param queryString The HQL/JPQL query, update or delete statement
         *
         * @return The [Query] instance for manipulation and execution
         *
         * @see jakarta.persistence.EntityManager.createQuery
         */
        @Deprecated(
            "See explanation in [org.hibernate.query.QueryProducer.createSelectionQuery(string)]",
            ReplaceWith("createSelectionQuery(queryString, resultType)"),
            DeprecationLevel.WARNING,
        )
        fun <R> createQuery(queryString: String?): Query<R>

        /**
         * Create an instance of [SelectionQuery] for the given HQL/JPQL query string and query result type.
         *
         * @param queryString The HQL/JPQL query
         * @param resultType the Java type returned in each row of query results
         *
         * @return The [SelectionQuery] instance for manipulation and execution
         *
         * @see jakarta.persistence.EntityManager.createQuery
         */
        fun <R> createQuery(
            queryString: String?,
            resultType: Class<R>?,
        ): SelectionQuery<R>

        /**
         * Create an instance of [SelectionQuery] for the given criteria query.
         *
         * @param criteriaQuery The [CriteriaQuery]
         *
         * @return The [SelectionQuery] instance for manipulation and execution
         *
         * @see jakarta.persistence.EntityManager.createQuery
         */
        fun <R> createQuery(criteriaQuery: CriteriaQuery<R>): SelectionQuery<R>

        /**
         * Create an instance of [MutationQuery] for the given criteria update.
         *
         * @param criteriaUpdate The [CriteriaUpdate]
         *
         * @return The [MutationQuery] instance for manipulation and execution
         */
        fun <R> createQuery(criteriaUpdate: CriteriaUpdate<R>): MutationQuery

        /**
         * Create an instance of [MutationQuery] for the given criteria delete.
         *
         * @param criteriaDelete The [CriteriaDelete]
         *
         * @return The [MutationQuery] instance for manipulation and execution
         */
        fun <R> createQuery(criteriaDelete: CriteriaDelete<R>): MutationQuery

        /**
         * Create an instance of [Query] for the named query.
         *
         * @param queryName The name of the query
         *
         * @return The [Query] instance for manipulation and execution
         *
         * @see jakarta.persistence.EntityManager.createQuery
         */
        fun <R> createNamedQuery(queryName: String?): Query<R>

        /**
         * Create an instance of [SelectionQuery] for the named query.
         *
         * @param queryName The name of the query
         * @param resultType the Java type returned in each row of query results
         *
         * @return The [SelectionQuery] instance for manipulation and execution
         *
         * @see jakarta.persistence.EntityManager.createQuery
         */
        fun <R> createNamedQuery(
            queryName: String?,
            resultType: Class<R>,
        ): SelectionQuery<R>

        /**
         * Create an instance of [Query] for the given SQL query string, or SQL update, insert, or delete statement.
         * In the case of an update, insert, or delete, the returned [Query] must be executed using [Query.executeUpdate]
         * which returns an affected row count.
         * In the case of a query:
         *
         * * If the result set has a single column, the results will be returned as scalars.
         * * Otherwise, if the result set has multiple columns, the results will be returned as elements of arrays of type `Object[]`.
         *
         * @param queryString The SQL select, update, insert, or delete statement
         */
        fun <R> createNativeQuery(queryString: String?): Query<R>

        /**
         * Create an instance of [Query] for the given SQL query string,
         * or SQL update, insert, or delete statement. In the case of an update,
         * insert, or delete, the returned [Query] must be executed using
         * [Query.executeUpdate] which returns an affected row count.
         * In the case of a query:
         *
         * * If the result set has a single column, the results will be returned
         * as scalars.
         * * Otherwise, if the result set has multiple columns, the results will
         * be returned as elements of arrays of type `Object[]`.
         *
         * Any [affected entities][AffectedEntities] are synchronized with
         * the database before execution of the statement.
         *
         * @param queryString The SQL select, update, insert, or delete statement
         * @param affectedEntities The entities which are affected by the statement
         */
        fun <R> createNativeQuery(
            queryString: String?,
            affectedEntities: AffectedEntities,
        ): Query<R>

        /**
         * Create an instance of [SelectionQuery] for the given SQL query
         * string, using the given `resultType` to interpret the results.
         *
         * * If the given result type is [Object], or a built-in type
         * such as [String] or [Integer], the result set must
         * have a single column, which will be returned as a scalar.
         * * If the given result type is `Object[]`, then the result set
         * must have multiple columns, which will be returned in arrays.
         * * Otherwise, the given result type must be an entity class, in which
         * case the result set column aliases must map to the fields of the
         * entity, and the query will return instances of the entity.
         *
         * @param queryString The SQL query
         * @param resultType the Java type returned in each row of query results
         *
         * @return The [SelectionQuery] instance for manipulation and execution
         *
         * @see jakarta.persistence.EntityManager.createNativeQuery
         */
        fun <R> createNativeQuery(
            queryString: String?,
            resultType: Class<R>,
        ): SelectionQuery<R>

        /**
         * Create an instance of [SelectionQuery] for the given SQL query
         * string, using the given `resultType` to interpret the results.
         *
         * * If the given result type is [Object], or a built-in type
         * such as [String] or [Integer], the result set must
         * have a single column, which will be returned as a scalar.
         * * If the given result type is `Object[]`, then the result set
         * must have multiple columns, which will be returned in arrays.
         * * Otherwise, the given result type must be an entity class, in which
         * case the result set column aliases must map to the fields of the
         * entity, and the query will return instances of the entity.
         *
         * Any [affected entities][AffectedEntities] are synchronized with
         * the database before execution of the query.
         *
         * @param queryString The SQL query
         * @param resultType the Java type returned in each row of query results
         * @param affectedEntities The entities which are affected by the query
         *
         * @return The [Query] instance for manipulation and execution
         *
         * @see jakarta.persistence.EntityManager.createNativeQuery
         */
        fun <R> createNativeQuery(
            queryString: String?,
            resultType: Class<R>,
            affectedEntities: AffectedEntities,
        ): SelectionQuery<R>

        /**
         * Create an instance of [SelectionQuery] for the given SQL query
         * string, using the given [ResultSetMapping] to interpret the
         * result set.
         *
         * @param queryString The SQL query
         * @param resultSetMapping the result set mapping
         *
         * @return The [Query] instance for manipulation and execution
         *
         * @see [Coroutines.QueryProducer.getResultSetMapping]
         * @see jakarta.persistence.EntityManager.createNativeQuery
         */
        fun <R> createNativeQuery(
            queryString: String?,
            resultSetMapping: ResultSetMapping<R>?,
        ): SelectionQuery<R>

        /**
         * Create an instance of [SelectionQuery] for the given SQL query
         * string, using the given [ResultSetMapping] to interpret the
         * result set.
         *
         * Any [affected entities][AffectedEntities] are synchronized with the
         * database before execution of the query.
         *
         * @param queryString The SQL query
         * @param resultSetMapping the result set mapping
         * @param affectedEntities The entities which are affected by the query
         *
         * @return The [SelectionQuery] instance for manipulation and execution
         *
         * @see [Coroutines.QueryProducer.getResultSetMapping]
         * @see jakarta.persistence.EntityManager.createNativeQuery
         */
        fun <R> createNativeQuery(
            queryString: String?,
            resultSetMapping: ResultSetMapping<R>?,
            affectedEntities: AffectedEntities,
        ): SelectionQuery<R>

        /** Obtain a native SQL result set mapping defined via the annotation [jakarta.persistence.SqlResultSetMapping]. */
        fun <T> getResultSetMapping(
            resultType: Class<T>?,
            mappingName: String?,
        ): ResultSetMapping<T>

        /** Obtain a named [EntityGraph] */
        fun <T> getEntityGraph(
            rootType: Class<T>?,
            graphName: String?,
        ): EntityGraph<T>

        /** Create a new mutable [EntityGraph] */
        fun <T> createEntityGraph(rootType: Class<T>?): EntityGraph<T>

        /** Create a new mutable copy of a named [EntityGraph] */
        fun <T> createEntityGraph(
            rootType: Class<T>?,
            graphName: String?,
        ): EntityGraph<T>

        /**
         * Convenience method to obtain the [CriteriaBuilder].
         *
         * @since 3
         */
        fun getCriteriaBuilder(): CriteriaBuilder
    }

    /**
     * A non-blocking counterpart to the Hibernate [org.hibernate.Session]
     * interface, allowing a reactive style of interaction with the database.
     *
     * * The semantics of operations on this interface are identical to the
     * semantics of the similarly named operations of `Session`, except
     * that the operations are performed asynchronously, without blocking the calling thread.
     *
     * * Entities associated with an `Session` do not support transparent
     * lazy association fetching.
     * Instead, [Coroutines.Session.fetch] should be used to explicitly request asynchronous fetching of an association,
     * or the association should be fetched eagerly when the entity is first retrieved, for example, by:
     *
     * * [enabling a fetch profile] [Coroutines.Session.enableFetchProfile],
     * * using an [EntityGraph], or
     * * writing a `join fetch` clause in an HQL query.
     *
     * @see org.hibernate.Session
     */
    interface Session :
        QueryProducer,
        Closeable {
        /**
         * Asynchronously return the persistent instance of the given entity class with the given identifier,
         * or `null` if there is no such persistent instance.
         * If the instance is already associated with the session, return the associated instance.
         * This method never returns an uninitialized instance.
         *
         * ```java
         * session.find(Book.class, id).map(book -> print(book.getTitle()));
         * ```
         *
         * @param entityClass The entity type
         * @param id an identifier
         *
         * @return a persistent instance or null via suspend.
         *
         * @see jakarta.persistence.EntityManager.find
         */
        @JvmSynthetic
        suspend fun <T> find(
            entityClass: Class<T>,
            id: Any?,
        ): T?

        /**
         * Asynchronously return the persistent instance of the given entity class with the given identifier,
         * requesting the given [LockMode].
         *
         * @param entityClass The entity type
         * @param id an identifier
         * @param lockMode the requested [LockMode]
         *
         * @return a persistent instance or null via suspend.
         *
         * @see [Coroutines.Session.find]
         * @see [Coroutines.Session.lock]
         */
        @JvmSynthetic
        suspend fun <T> find(
            entityClass: Class<T>,
            id: Any?,
            lockMode: LockMode?,
        ): T?

        /**
         * Asynchronously return the persistent instance of the given entity
         * class with the given identifier, requesting the given [LockModeType].
         *
         * @param entityClass The entity type
         * @param id an identifier
         * @param lockModeType the requested [LockModeType]
         *
         * @return a persistent instance or null via suspend.
         *
         * @see [Coroutines.Session.find]
         * @see [Coroutines.Session.lock]
         */
        @JvmSynthetic
        suspend fun <T> find(
            entityClass: Class<T>,
            id: Any?,
            lockModeType: LockModeType,
        ): T? = find(entityClass, id, LockModeConverter.convertToLockMode(lockModeType))

        /**
         * Asynchronously return the persistent instance with the given
         * identifier of an entity class, using the given [EntityGraph]
         * as a fetch plan.
         *
         * @param entityGraph an [EntityGraph] specifying the entity and associations to be fetched
         * @param id an identifier
         *
         * @see [Coroutines.Session.find]
         */
        @JvmSynthetic
        suspend fun <T> find(
            entityGraph: EntityGraph<T>,
            id: Any?,
        ): T?

        /**
         * Asynchronously return the persistent instances of the given entity
         * class with the given identifiers, or null if there is no such
         * persistent instance.
         *
         * @param entityClass The entity type
         * @param ids the identifiers
         *
         * @return a list of persistent instances and nulls via suspend.
         *
         * @see org.hibernate.Session.findMultiple
         */
        @JvmSynthetic
        suspend fun <T> find(
            entityClass: Class<T>,
            vararg ids: Any?,
        ): List<T?>

        /**
         * Asynchronously return the persistent instance of the given entity class with the given natural identifier,
         * or null if there is no such persistent instance.
         *
         * @param entityClass The entity type
         * @param naturalId the natural identifier
         *
         * @return a persistent instance or null via suspend.
         */
        @Incubating
        @JvmSynthetic
        suspend fun <T> find(
            entityClass: Class<T>,
            naturalId: Identifier<T>,
        ): T?

        /**
         * Return the persistent instance of the given entity class with the
         * given identifier, assuming that the instance exists. This method
         * never results in access to the underlying data store, and thus
         * might return a proxied instance that must be initialized explicitly
         * using [Coroutines.Session.fetch].
         *
         * You should not use this method to determine if an instance exists
         * (use [Coroutines.Session.find] instead). Use this only to retrieve an instance
         * which you safely assume exists, where non-existence would be an
         * actual error.
         *
         * @param entityClass a persistent class
         * @param id a valid identifier of an existing persistent instance of the class
         *
         * @return the persistent instance or proxy
         *
         * @see jakarta.persistence.EntityManager.getReference
         */
        fun <T> getReference(
            entityClass: Class<T?>?,
            id: Any?,
        ): T?

        /**
         * Return the persistent instance with the same identity as the given
         * instance, which might be detached, assuming that the instance is
         * still persistent in the database. This method never results in
         * access to the underlying data store, and thus might return a proxy
         * that must be initialized explicitly using [Coroutines.Session.fetch].
         *
         * @param entity a detached persistent instance
         *
         * @return the persistent instance or proxy
         */
        fun <T> getReference(entity: T?): T?

        /**
         * Asynchronously persist the given transient instance, first assigning
         * a generated identifier. (Or using the current value of the identifier
         * property if the entity has assigned identifiers.)
         *
         * This operation cascades to associated instances if the association is
         * mapped with [jakarta.persistence.CascadeType.PERSIST].
         *
         * ```java
         * session.persist(newBook).map(v -> session.flush());
         * ```
         *
         * @param instance a transient instance of a persistent class
         *
         * @see jakarta.persistence.EntityManager.persist
         */
        @JvmSynthetic
        suspend fun persist(instance: Any?)

        /**
         * Make a transient instance persistent and mark it for later insertion in the
         * database. This operation cascades to associated instances if the association
         * is mapped with [jakarta.persistence.CascadeType.PERSIST].
         *
         *
         * For entities with a [generated id][jakarta.persistence.GeneratedValue],
         * `persist()` ultimately results in generation of an identifier for the
         * given instance. But this may happen asynchronously when the session is
         * [flushed][Coroutines.Session.flush], depending on the identifier generation strategy.
         *
         * @param entityName the entity name
         * @param instance a transient instance to be made persistent
         * @see [Coroutines.Session.persist]
         */
        @JvmSynthetic
        suspend fun persist(
            entityName: String?,
            instance: Any?,
        )

        /**
         * Persist multiple transient entity instances at once.
         *
         * @see [Coroutines.Session.persist]
         */
        @JvmSynthetic
        suspend fun persistAll(vararg entities: Any?)

        /**
         * Asynchronously remove a persistent instance from the datastore. The
         * argument may be an instance associated with the receiving session or
         * a transient instance with an identifier associated with the existing
         * persistent state.
         *
         * This operation cascades to associated instances if the association is
         * mapped with [jakarta.persistence.CascadeType.REMOVE].
         *
         * ```java
         * session.delete(book).thenAccept(v -> session.flush());
         * ```
         *
         * @param entity the managed persistent instance to be removed
         *
         * @throws IllegalArgumentException if the given instance is not managed
         * @see jakarta.persistence.EntityManager.remove
         */
        @JvmSynthetic
        suspend fun remove(entity: Any?)

        /**
         * Remove multiple entity instances at once.
         *
         * @see [Coroutines.Session.remove]
         */
        @JvmSynthetic
        suspend fun removeAll(vararg entities: Any?)

        /**
         * Copy the state of the given object onto the persistent instance with
         * the same identifier. If there is no such persistent instance currently
         * associated with the session, it will be loaded. Return the persistent
         * instance. Or, if the given instance is transient, save a copy of it
         * and return the copy as a newly persistent instance. The given instance
         * does not become associated with the session.
         *
         * This operation cascades to associated instances if the association is
         * mapped with [jakarta.persistence.CascadeType.MERGE].
         *
         * @param entity a detached instance with state to be copied
         *
         * @return an updated persistent instance
         *
         * @see jakarta.persistence.EntityManager.merge
         */
        @JvmSynthetic
        suspend fun <T> merge(entity: T?): T?

        /**
         * Merge multiple entity instances at once.
         *
         * @see [Coroutines.Session.merge]
         */
        @JvmSynthetic
        suspend fun mergeAll(vararg entities: Any?)

        /**
         * Re-read the state of the given instance from the underlying database.
         * It is inadvisable to use this to implement long-running sessions that
         * span many business tasks. This method is, however, useful in certain
         * special circumstances, for example:
         *
         * * where a database trigger alters the object state after insert or update, or
         * * after executing direct native SQL in the same session.
         *
         * @param entity a managed persistent instance
         *
         * @throws IllegalArgumentException if the given instance is not managed
         * @see jakarta.persistence.EntityManager.refresh
         */
        @JvmSynthetic
        suspend fun refresh(entity: Any?)

        /**
         * Re-read the state of the given instance from the underlying database, requesting the given [LockMode].
         *
         * @param entity a managed persistent entity instance
         * @param lockMode the requested lock mode
         *
         * @see [Coroutines.Session.refresh]
         */
        @JvmSynthetic
        suspend fun refresh(
            entity: Any?,
            lockMode: LockMode?,
        )

        /**
         * Re-read the state of the given instance from the underlying database, requesting the given [LockModeType].
         *
         * @param entity a managed persistent entity instance
         * @param lockModeType the requested lock mode
         *
         * @see [Coroutines.Session.refresh]
         */
        @JvmSynthetic
        suspend fun refresh(
            entity: Any?,
            lockModeType: LockModeType,
        ) = refresh(entity, LockModeConverter.convertToLockMode(lockModeType))

        /**
         * Refresh multiple entity instances at once.
         *
         * @see [Coroutines.Session.refresh]
         */
        @JvmSynthetic
        suspend fun refreshAll(vararg entities: Any?)

        /**
         * Obtain the specified lock level upon the given object. For example,
         * this operation may be used to:
         *
         * * perform a version check with [LockMode.PESSIMISTIC_READ],
         * * upgrade to a pessimistic lock with [LockMode.PESSIMISTIC_WRITE],
         * * force a version increment with [LockMode.PESSIMISTIC_FORCE_INCREMENT],
         * * schedule a version check just before the end of the transaction with [LockMode.OPTIMISTIC], or
         * * schedule a version increment just before the end of the transaction with [LockMode.OPTIMISTIC_FORCE_INCREMENT].
         *
         * This operation cascades to associated instances if the association is mapped with [org.hibernate.annotations.CascadeType.LOCK].
         *
         * @param entity a managed persistent instance
         * @param lockMode the lock level
         *
         * @throws IllegalArgumentException if the given instance is not managed
         */
        @JvmSynthetic
        suspend fun lock(
            entity: Any?,
            lockMode: LockMode?,
        )

        /**
         * Obtain the specified lock level upon the given object. For example,
         * this operation may be used to:
         *
         * * perform a version check with [LockModeType.PESSIMISTIC_READ],
         * * upgrade to a pessimistic lock with [LockModeType.PESSIMISTIC_WRITE],
         * * force a version increment with [LockModeType.PESSIMISTIC_FORCE_INCREMENT],
         * * schedule a version check just before the end of the transaction with [LockModeType.OPTIMISTIC], or
         * * schedule a version increment just before the end of the transaction with [LockModeType.OPTIMISTIC_FORCE_INCREMENT].
         *
         * This operation cascades to associated instances if the association is mapped with [org.hibernate.annotations.CascadeType.LOCK].
         *
         * @param entity a managed persistent instance
         * @param lockModeType the lock level
         *
         * @throws IllegalArgumentException if the given instance is not managed
         */
        @JvmSynthetic
        suspend fun lock(
            entity: Any?,
            lockModeType: LockModeType,
        ) = lock(entity, LockModeConverter.convertToLockMode(lockModeType))

        /**
         * Force this session to flush asynchronously. Must be called at the
         * end of a unit of work, before committing the transaction and closing
         * the session. *Flushing* is the process of synchronizing the
         * underlying persistent store with state held in memory.
         *
         * ```java
         * session.flush().thenAccept(v -> print("done saving changes"));
         * ```
         *
         * @see jakarta.persistence.EntityManager.flush
         */
        @JvmSynthetic
        suspend fun flush()

        /**
         * Asynchronously fetch an association configured for lazy loading.
         *
         * ```
         * session.fetch(author.getBook()).thenAccept(book -> print(book.getTitle()));
         * ```
         *
         * This operation may even be used to initialize a reference returned by
         * [Coroutines.Session.getReference].
         *
         * ```
         * session.fetch(session.getReference(Author.class, authorId))
         * ```
         *
         * @param association a lazy-loaded association, or a proxy
         *
         * @return the fetched association, via suspend.
         *
         * @see Coroutines.fetch
         * @see [Coroutines.Session.getReference]
         * @see org.hibernate.Hibernate.initialize
         */
        @JvmSynthetic
        suspend fun <T> fetch(association: T?): T?

        /**
         * Fetch a lazy property of the given entity, identified by a JPA
         * [attribute metamodel][Attribute].
         * Note that this feature is only supported in conjunction with the Hibernate bytecode enhancer.
         *
         * ```java
         * session.fetch(book, Book_.isbn).thenAccept(isbn -> print(isbn))
         * ```
         */
        @JvmSynthetic
        suspend fun <E, T> fetch(
            entity: E?,
            field: Attribute<E, T>,
        ): T?

        /**
         * Asynchronously fetch an association that's configured for lazy loading,
         * and unwrap the underlying entity implementation from any proxy.
         *
         * ```java
         * session.unproxy(author.getBook()).thenAccept(book -> print(book.getTitle()));
         * ```
         *
         * @param association a lazy-loaded association
         *
         * @return the fetched association, via suspend.
         *
         * @see org.hibernate.Hibernate.unproxy
         */
        @JvmSynthetic
        suspend fun <T> unproxy(association: T?): T?

        /** Determine the current lock mode of the given entity. */
        fun getLockMode(entity: Any?): LockMode?

        /** Determine if the given instance belongs to this persistence context. */
        fun contains(entity: Any?): Boolean

        /**
         * Set the [flush mode][FlushMode] for this session.
         *
         * The flush mode determines the points at which the session is flushed.
         * *Flushing* is the process of synchronizing the underlying persistent
         * store with a persistable state held in memory.
         *
         * For a logically "read-only" session, it is reasonable to set the session's
         * flush mode to [FlushMode.MANUAL] at the start of the session
         * (in order to achieve some extra performance).
         *
         * @param flushMode the new flush mode
         */
        fun setFlushMode(flushMode: FlushMode): Session

        /**
         * Set the [flush mode][FlushModeType] for this session.
         *
         * The flush mode determines the points at which the session is flushed.
         * *Flushing* is the process of synchronizing the underlying persistent
         * store with a persistable state held in memory.
         *
         * @param flushModeType the new flush mode
         */
        fun setFlushMode(flushModeType: FlushModeType): Session = setFlushMode(FlushModeTypeHelper.getFlushMode(flushModeType))

        /**
         * Get the current flush mode for this session.
         *
         * @return the flush mode
         */
        fun getFlushMode(): FlushMode?

        /**
         * Remove this instance from the session cache. Changes to the instance
         * will not be synchronized with the database.
         *
         * This operation cascades to associated instances if the association is
         * mapped with [jakarta.persistence.CascadeType.DETACH].
         *
         * @param entity The entity to evict
         *
         * @throws NullPointerException if the passed object is `null`
         * @throws IllegalArgumentException if the passed object is not defined as an entity
         * @see jakarta.persistence.EntityManager.detach
         */
        fun detach(entity: Any?): Session

        /**
         * Completely clear the session. Detach all persistent instances and cancel
         * all pending insertions, updates, and deletions.
         *
         * @see jakarta.persistence.EntityManager.clear
         */
        fun clear(): Session

        /**
         * Enable a particular fetch profile on this session or do nothing if
         *  the requested fetch profile is already enabled.
         *
         * @param name The name of the fetch profile to be enabled.
         *
         * @throws org.hibernate.UnknownProfileException Indicates that the given name does not
         * match any known profile names
         * @see org.hibernate.engine.profile.FetchProfile for discussion of this feature
         */
        fun enableFetchProfile(name: String?): Session

        /**
         * Disable a particular fetch profile on this session or do nothing if
         * the requested fetch profile is not enabled.
         *
         * @param name The name of the fetch profile to be disabled.
         *
         * @throws org.hibernate.UnknownProfileException Indicates that the given name does not
         * match any known profile names
         * @see org.hibernate.engine.profile.FetchProfile for discussion of this feature
         */
        fun disableFetchProfile(name: String?): Session

        /**
         * Determine if the fetch profile with the given name is enabled for this
         * session.
         *
         * @param name The name of the profile to be checked.
         *
         * @return True if fetch profile is enabled; false if not.
         *
         * @throws org.hibernate.UnknownProfileException Indicates that the given name does not match any known profile names
         * @see org.hibernate.engine.profile.FetchProfile for discussion of this feature
         */
        fun isFetchProfileEnabled(name: String?): Boolean

        /**
         * Change the default for entities and proxies loaded into this session
         * from modifiable to read-only mode, or from modifiable to read-only mode.
         *
         * Read-only entities are not dirty-checked, and snapshots of persistent
         * state are not maintained. Read-only entities can be modified, but
         * changes are not persisted.
         *
         * @see org.hibernate.Session.setDefaultReadOnly
         */
        fun setDefaultReadOnly(readOnly: Boolean): Session

        /** @return the default read-only mode for entities and proxies loaded in this session */
        fun isDefaultReadOnly(): Boolean

        /**
         * Set an unmodified persistent object to read-only mode, or a read-only
         * object to modifiable mode. In read-only mode, no snapshot is maintained,
         * the instance is never dirtily checked, and changes are not persisted.
         *
         * @see org.hibernate.Session.setReadOnly
         */
        fun setReadOnly(
            entityOrProxy: Any?,
            readOnly: Boolean,
        ): Session

        /**
         * Is the specified entity or proxy read-only?
         *
         * @see org.hibernate.Session.isReadOnly
         */
        fun isReadOnly(entityOrProxy: Any?): Boolean

        /**
         * Set the [cache mode][CacheMode] for this session.
         *
         * The cache mode determines the manner in which this session interacts
         * with the second level cache.
         *
         * @param cacheMode The new cache mode.
         */
        fun setCacheMode(cacheMode: CacheMode?): Session

        /**
         * Set the [CacheStoreMode] for this session.
         *
         * @param cacheStoreMode The new cache store mode.
         */
        fun setCacheStoreMode(cacheStoreMode: CacheStoreMode?): Session =
            setCacheMode(
                CacheModeHelper.interpretCacheMode(
                    cacheStoreMode,
                    CacheModeHelper.interpretCacheRetrieveMode(this.getCacheMode()),
                ),
            )

        /**
         * Set the [CacheRetrieveMode] for this session.
         *
         * @param cacheRetrieveMode The new cache retrieve mode.
         */
        fun setCacheRetrieveMode(cacheRetrieveMode: CacheRetrieveMode?): Session =
            setCacheMode(
                CacheModeHelper.interpretCacheMode(
                    CacheModeHelper.interpretCacheStoreMode(this.getCacheMode()),
                    cacheRetrieveMode,
                ),
            )

        /**
         * Get the current cache mode.
         *
         * @return The current cache mode.
         */
        fun getCacheMode(): CacheMode?

        /** Set the session-level batch size, overriding the batch size set by the configuration property `hibernate.jdbc.batch_size`. */
        fun setBatchSize(batchSize: Int?): Session

        /** The session-level batch size, or `null` if it has not been overridden. */
        fun getBatchSize(): Int?

        /**
         * Enable the named filter for this session.
         *
         * @param filterName The name of the filter to be enabled.
         *
         * @return The Filter instance representing the enabled filter.
         */
        fun enableFilter(filterName: String?): Filter?

        /**
         * Disable the named filter for this session.
         *
         * @param filterName The name of the filter to be disabled.
         */
        fun disableFilter(filterName: String?)

        /**
         * Retrieve a currently enabled filter by name.
         *
         * @param filterName The name of the filter to be retrieved.
         *
         * @return The Filter instance representing the enabled filter.
         */
        fun getEnabledFilter(filterName: String?): Filter?

        /**
         * Get the maximum batch size for batch fetching associations by id in this session.
         *
         * @since 2.1
         */
        fun getFetchBatchSize(): Int

        /**
         * Set the maximum batch size for batch fetching associations by id in this session.
         * Override the default controlled by the configuration property `hibernate.default_batch_fetch_size`.
         *
         * * If `batchSize>1`, then batch fetching is enabled.
         * * If `batchSize<0`, the batch size is inherited from the factory-level setting.
         * * Otherwise, batch fetching is disabled.
         *
         * @param batchSize the maximum batch size for batch fetching
         *
         * @since 2.1
         */
        fun setFetchBatchSize(batchSize: Int): Session

        /**
         * Determine if subselect fetching is enabled in this session.
         *
         * @return `true` if subselect fetching is enabled
         *
         * @since 2.1
         */
        fun isSubselectFetchingEnabled(): Boolean

        /**
         * Enable or disable subselect fetching in this session.
         * Override the default controlled by the configuration property `hibernate.use_subselect_fetch`.
         *
         * @param enabled `true` to enable subselect fetching
         *
         * @since 2.1
         */
        fun setSubselectFetchingEnabled(enabled: Boolean): Session

        /**
         * Performs the given work within the scope of a database transaction,
         * automatically flushing the session. The transaction will be rolled
         * back if the work completes with an uncaught exception, or if
         * [Transaction.markForRollback] is called.
         *
         * The resulting [Transaction] object may also be obtained via
         * [Coroutines.Session.currentTransaction].
         *
         * *  If there is already a transaction associated with this session,
         * the work is executed in the context of the existing transaction, and
         * no new transaction is initiated.
         * *  If there is no transaction associated with this session, a new
         * transaction is started, and the work is executed in the context of
         * the new transaction.
         *
         * @param work a function which accepts [Transaction] and returns the result of the work.
         *
         * @see SessionFactory.withTransaction
         */
        @JvmSynthetic
        suspend fun <T> withTransaction(work: suspend (Transaction) -> T): T

        /**
         * Obtain the transaction currently associated with this session, if any.
         *
         * @return the [Transaction], or null if no transaction
         * was started using [Coroutines.Session.withTransaction].
         *
         * @see Coroutines.Session.withTransaction
         * @see SessionFactory.withTransaction
         */
        fun currentTransaction(): Transaction?

        /** @return false if [Coroutines.Session.close] has been called */
        fun isOpen(): Boolean

        /** The [SessionFactory] which created this session. */
        fun getFactory(): SessionFactory
    }

    /**
     * A non-blocking counterpart to the Hibernate
     * [org.hibernate.StatelessSession] interface, which provides a
     * command-oriented API for performing bulk operations against a database.
     *
     * A stateless session does not implement a first-level cache nor interact
     * with any second-level cache, nor does it implement transactional
     * write-behind or automatic dirty checking, nor do operations cascade to
     * associated instances. Changes to many to many associations and element
     * collections may not be made persistent in a stateless session.
     * Operations performed via a stateless session bypass Hibernate's event
     * model and interceptors.
     *
     * For certain kinds of work, a stateless session may perform slightly
     * better than a stateful session.
     *
     * In particular, for a session which loads many entities, use of a `StatelessSession` alleviates the need to call:
     *
     * * [Session.clear] or [Session.detach] to perform first-level cache management, and
     * * [Session.setCacheMode] to bypass interaction with the second-level cache.
     *
     * Stateless sessions are vulnerable to data aliasing effects, due to the lack of a first-level cache.
     *
     * @see org.hibernate.StatelessSession
     */
    interface StatelessSession :
        QueryProducer,
        Closeable {
        /**
         * Retrieve a row.
         *
         * @param entityClass The class of the entity to retrieve
         * @param id The id of the entity to retrieve
         *
         * @return a detached entity instance, via suspend.
         *
         * @see org.hibernate.StatelessSession.get
         */
        @JvmSynthetic
        suspend fun <T> get(
            entityClass: Class<T>,
            id: Any?,
        ): T?

        /**
         * Retrieve multiple rows.
         *
         * @param entityClass The class of the entity to retrieve
         * @param ids The ids of the entities to retrieve
         *
         * @return a list of detached entity instances, via suspend.
         *
         * @see org.hibernate.StatelessSession.getMultiple
         */
        @JvmSynthetic
        suspend fun <T> get(
            entityClass: Class<T>,
            vararg ids: Any,
        ): List<T?>

        /**
         * Retrieve a row, obtaining the specified lock mode.
         *
         * @param entityClass The class of the entity to retrieve
         * @param id The id of the entity to retrieve
         * @param lockMode The lock mode to apply to the entity
         *
         * @return a detached entity instance, via suspend.
         *
         * @see org.hibernate.StatelessSession.get
         */
        @JvmSynthetic
        suspend fun <T> get(
            entityClass: Class<T>,
            id: Any,
            lockMode: LockMode,
        ): T?

        /**
         * Retrieve a row, obtaining the specified lock mode.
         *
         * @param entityClass The class of the entity to retrieve
         * @param id The id of the entity to retrieve
         * @param lockModeType The lock mode to apply to the entity
         *
         * @return a detached entity instance, via suspend.
         *
         * @see org.hibernate.StatelessSession.get
         */
        @JvmSynthetic
        suspend fun <T> get(
            entityClass: Class<T>,
            id: Any,
            lockModeType: LockModeType,
        ): T? = get(entityClass, id, LockModeConverter.convertToLockMode(lockModeType))

        /**
         * Retrieve a row, using the given [EntityGraph] as a fetch plan.
         *
         * @param entityGraph an [EntityGraph] specifying the entity
         * and associations to be fetched
         * @param id The id of the entity to retrieve
         *
         * @return a detached entity instance, via suspend.
         */
        @JvmSynthetic
        suspend fun <T> get(
            entityGraph: EntityGraph<T>,
            id: Any,
        ): T?

        /**
         * Insert a row.
         *
         * @param entity a new transient instance
         *
         * @see org.hibernate.StatelessSession.insert
         */
        @JvmSynthetic
        suspend fun insert(entity: Any?)

        /**
         * Insert multiple rows, using the number of the
         * given entities as the batch size.
         *
         * @param entities new transient instances
         *
         * @see org.hibernate.StatelessSession.insert
         */
        @JvmSynthetic
        suspend fun insertAll(vararg entities: Any?)

        /**
         * Insert multiple rows using the specified batch size.
         *
         * @param batchSize the batch size
         * @param entities new transient instances
         *
         * @see org.hibernate.StatelessSession.insert
         */
        @JvmSynthetic
        suspend fun insertAll(
            batchSize: Int,
            vararg entities: Any,
        )

        /**
         * Insert multiple rows, using the size of the
         * given list as the batch size.
         *
         * @param entities new transient instances
         *
         * @see org.hibernate.StatelessSession.insert
         */
        @JvmSynthetic
        suspend fun insertMultiple(entities: List<*>)

        /**
         * Delete a row.
         *
         * @param entity a detached entity instance
         *
         * @see org.hibernate.StatelessSession.delete
         */
        @JvmSynthetic
        suspend fun delete(entity: Any)

        /**
         * Delete multiple rows, using the number of the
         * given entities as the batch size.
         *
         * @param entities detached entity instances
         *
         * @see org.hibernate.StatelessSession.delete
         */
        @JvmSynthetic
        suspend fun deleteAll(vararg entities: Any?)

        /**
         * Delete multiple rows.
         *
         * @param batchSize the batch size
         * @param entities detached entity instances
         *
         * @see org.hibernate.StatelessSession.delete
         */
        @JvmSynthetic
        suspend fun deleteAll(
            batchSize: Int,
            vararg entities: Any?,
        )

        /**
         * Delete multiple rows, using the size of the given list as the batch size.
         *
         * @param entities detached entity instances
         *
         * @see org.hibernate.StatelessSession.delete
         */
        @JvmSynthetic
        suspend fun deleteMultiple(entities: List<*>)

        /**
         * Update a row.
         *
         * @param entity a detached entity instance
         *
         * @see org.hibernate.StatelessSession.update
         */
        @JvmSynthetic
        suspend fun update(entity: Any?)

        /**
         * Update multiple rows, using the number of the given entities as the batch size.
         *
         * @param entities detached entity instances
         *
         * @see org.hibernate.StatelessSession.update
         */
        @JvmSynthetic
        suspend fun updateAll(vararg entities: Any?)

        /**
         * Update multiple rows.
         *
         * @param batchSize the batch size
         * @param entities detached entity instances
         *
         * @see org.hibernate.StatelessSession.update
         */
        @JvmSynthetic
        suspend fun updateAll(
            batchSize: Int,
            vararg entities: Any?,
        )

        /**
         * Update multiple rows, using the size of the given list as the batch size.
         *
         * @param entities detached entity instances
         *
         * @see org.hibernate.StatelessSession.update
         */
        @JvmSynthetic
        suspend fun updateMultiple(entities: List<*>)

        /**
         * Use a SQL `merge into` statement to perform an upsert.
         *
         * @param entity a detached entity instance
         *
         * @see org.hibernate.StatelessSession.upsert
         */
        @Incubating
        @JvmSynthetic
        suspend fun upsert(entity: Any?)

        /**
         * Use a SQL `merge into` statement to perform
         * an upsert on multiple rows using the size of the given array
         * as batch size.
         *
         * @param entities the entities to upsert
         *
         * @see org.hibernate.StatelessSession.upsert
         */
        @Incubating
        @JvmSynthetic
        suspend fun upsertAll(vararg entities: Any?)

        /**
         * Use a SQL `merge into` statement to perform
         * an upsert on multiple rows using the specified batch size.
         *
         * @param batchSize the batch size
         * @param entities the list of entities to upsert
         *
         * @see org.hibernate.StatelessSession.upsert
         */
        @Incubating
        @JvmSynthetic
        suspend fun upsertAll(
            batchSize: Int,
            vararg entities: Any?,
        )

        /**
         * Use a SQL `merge into` statement to perform
         * an upsert on multiple rows using the size of the given list
         * as batch size.
         *
         * @param entities the entities to upsert
         *
         * @see org.hibernate.StatelessSession.upsert
         */
        @Incubating
        @JvmSynthetic
        suspend fun upsertMultiple(entities: List<*>)

        /**
         * Refresh the entity instance state from the database.
         *
         * @param entity The entity to be refreshed.
         *
         * @see org.hibernate.StatelessSession.refresh
         */
        @JvmSynthetic
        suspend fun refresh(entity: Any?)

        /**
         * Refresh the entity instance state from the database, using the number of the
         * given entities as the batch size.
         *
         * @param entities The entities to be refreshed.
         *
         * @see org.hibernate.StatelessSession.refresh
         */
        @JvmSynthetic
        suspend fun refreshAll(vararg entities: Any?)

        /**
         * Refresh the entity instance state from the database
         * using the selected batch size.
         *
         * @param batchSize the batch size
         * @param entities The entities to be refreshed.
         *
         * @see org.hibernate.StatelessSession.refresh
         */
        @JvmSynthetic
        suspend fun refreshAll(
            batchSize: Int,
            vararg entities: Any?,
        )

        /**
         * Refresh the entity instance state from the database
         * using the size of the given list as the batch size.
         *
         * @param entities The entities to be refreshed.
         *
         * @see org.hibernate.StatelessSession.refresh
         */
        @JvmSynthetic
        suspend fun refreshMultiple(entities: List<*>)

        /**
         * Refresh the entity instance state from the database.
         *
         * @param entity The entity to be refreshed.
         * @param lockMode The LockMode to be applied.
         *
         * @see org.hibernate.StatelessSession.refresh
         */
        @JvmSynthetic
        suspend fun refresh(
            entity: Any?,
            lockMode: LockMode?,
        )

        /**
         * Refresh the entity instance state from the database.
         *
         * @param entity The entity to be refreshed.
         * @param lockModeType The LockMode to be applied.
         *
         * @see org.hibernate.StatelessSession.refresh
         */
        @JvmSynthetic
        suspend fun refresh(
            entity: Any?,
            lockModeType: LockModeType,
        ) = refresh(entity, LockModeConverter.convertToLockMode(lockModeType))

        /**
         * Asynchronously fetch an association configured for lazy loading.
         *
         * ```java
         * session.fetch(author.getBook()).thenAccept(book -> print(book.getTitle()))
         * ```
         *
         * Warning: this operation in a stateless session is quite sensitive to
         * data aliasing effects and should be used with great care.
         *
         * @param association a lazy-loaded association
         *
         * @return the fetched association, via suspend.
         *
         * @see org.hibernate.Hibernate.initialize
         */
        @JvmSynthetic
        suspend fun <T> fetch(association: T?): T?

        /**
         * Return the identifier value of the given entity, which may be detached.
         *
         * @param entity a persistent instance associated with this session
         *
         * @return the identifier
         *
         * @since 3.0
         */
        fun getIdentifier(entity: Any?): Any?

        /**
         * Performs the given work within the scope of a database transaction,
         * automatically flushing the session. The transaction will be rolled
         * back if the work completes with an uncaught exception, or if
         * [Transaction.markForRollback] is called.
         *
         * *  If there is already a transaction associated with this session,
         * the work is executed in the context of the existing transaction, and
         * no new transaction is initiated.
         * *  If there is no transaction associated with this session, a new
         * transaction is started, and the work is executed in the context of
         * the new transaction.
         *
         * @param work a function which accepts [Transaction] and returns the result of the work.
         *
         * @see SessionFactory.withTransaction
         */
        @JvmSynthetic
        suspend fun <T> withTransaction(work: suspend (Transaction) -> T): T

        /**
         * Obtain the transaction currently associated with this session,
         * if any.
         *
         * @return the [Transaction], or null if no transaction
         * was started using [.withTransaction].
         *
         * @see [Coroutines.StatelessSession.withTransaction]
         * @see SessionFactory.withTransaction
         */
        fun currentTransaction(): Transaction?

        /**
         * @return false if [Coroutines.StatelessSession.close] has been called
         */
        fun isOpen(): Boolean

        /** Close the reactive session and release the underlying database connection. */
        @JvmSynthetic
        override suspend fun close()

        /** The [SessionFactory] which created this session. */
        fun getFactory(): SessionFactory
    }

    /**
     * Allows code within [Session.withTransaction] to mark a
     * transaction for rollback. A transaction marked for rollback will
     * never be committed.
     */
    interface Transaction {
        /** Mark the current transaction for rollback. */
        fun markForRollback()

        /** Is the current transaction marked for rollback. */
        fun isMarkedForRollback(): Boolean
    }

    /**
     * Factory for [reactive sessions][Session].
     * * A [Coroutines.SessionFactory] may be obtained from an instance of
     * [jakarta.persistence.EntityManagerFactory] as follows:
     *
     * ```java
     * Mutiny.SessionFactory sessionFactory = createEntityManagerFactory("example").unwrap(Mutiny.SessionFactory.class);
     * ```
     * * Here, configuration properties must be specified in `persistence.xml`.
     * * Alternatively, a [Coroutines.SessionFactory] may be obtained via
     * programmatic configuration of Hibernate using:
     *
     * ```java
     * Configuration configuration = new Configuration();
     * Mutiny.SessionFactory sessionFactory =
     *   configuration.buildSessionFactory(
     *     new ReactiveServiceRegistryBuilder()
     *       .applySettings( configuration.getProperties() )
     *       .build()
     *   )
     *    .unwrap(Mutiny.SessionFactory.class);
     * ```
     */
    interface SessionFactory : AutoCloseable {
        /**
         * Obtain a new [reactive session][Session], the main interaction point between the user's program and
         * Hibernate Reactive.
         *
         * When completes successfully it returns a newly created session.
         *
         * The client must explicitly close the session by calling [Session.close].
         *
         * @see Coroutines.SessionFactory.withSession
         */
        @JvmSynthetic
        suspend fun openSession(): Session

        /**
         * Obtain a new [reactive session][Session] for a specified tenant.
         *
         * When completes successfully it returns a newly created session.
         *
         * The client must explicitly close the session by calling [Session.close].
         *
         * @param tenantId the id of the tenant
         *
         * @see Coroutines.SessionFactory.withSession
         */
        @JvmSynthetic
        suspend fun openSession(tenantId: String?): Session

        /**
         * Obtain a [reactive stateless session][StatelessSession].
         *
         * When completes successfully it returns a newly created session.
         *
         * The client must explicitly close the session by calling [StatelessSession.close].
         *
         * @see Coroutines.SessionFactory.withStatelessSession
         */
        @JvmSynthetic
        suspend fun openStatelessSession(): StatelessSession

        /**
         * Obtain a [reactive stateless session][StatelessSession].
         *
         * When completed successfully, it returns a newly created session.
         *
         * The client must explicitly close the session by calling [StatelessSession.close].
         *
         * @param tenantId the id of the tenant
         *
         * @see Coroutines.SessionFactory.withStatelessSession
         */
        @JvmSynthetic
        suspend fun openStatelessSession(tenantId: String?): StatelessSession

        /**
         * Perform work using a [reactive session][Session].
         *
         * * If there is already a session associated with the current
         * reactive stream, then the work will be executed using that
         * session.
         * * Otherwise, if there is no session associated with the
         * current stream, a new session will be created.
         *
         * The session will be closed automatically but must be flushed
         * explicitly if necessary.
         *
         * @param work a function which accepts the session and returns the result of the work.
         */
        @JvmSynthetic
        suspend fun <T> withSession(work: suspend (Session) -> T): T

        /**
         * Perform work using a [reactive session][Session] for a specified tenant.
         *
         * * If there is already a session associated with the current
         * reactive stream and the given tenant, then the work will be
         * executed using that session.
         * * Otherwise, a new session will be created.
         *
         * The session will be closed automatically but must be flushed explicitly if necessary.
         *
         * @param tenantId the id of the tenant
         * @param work a function which accepts the session and returns the result of the work.
         */
        @JvmSynthetic
        suspend fun <T> withSession(
            tenantId: String,
            work: suspend (Session) -> T,
        ): T

        /**
         * Perform work using a [reactive session][Session]
         * within an associated [transaction][Transaction].
         *
         * * If there is already a session associated with the current
         * reactive stream, then the work will be executed using that
         * session.
         * * Otherwise, if there is no session associated with the
         * current stream, a new session will be created.
         *
         * The session will be [flushed][Session.flush] and closed
         * automatically, and the transaction committed automatically.
         *
         * @param work a function which accepts the session and transaction
         * and returns the result of the work.
         *
         * @see Coroutines.SessionFactory.withSession
         * @see Coroutines.Session.withTransaction
         */
        @JvmSynthetic
        suspend fun <T> withTransaction(work: suspend (Session, Transaction) -> T): T

        /**
         * Perform work using a [reactive session][Session]
         * within an associated transaction.
         *
         * * If there is already a session associated with the current
         * reactive stream, then the work will be executed using that
         * session.
         * * Otherwise, if there is no session associated with the
         * current stream, a new session will be created.
         *
         * The session will be [flushed][Session.flush] and closed
         * automatically, and the transaction committed automatically.
         *
         * @param work a function which accepts the session and returns the result of the work.
         *
         * @see Coroutines.SessionFactory.withTransaction
         * @see Coroutines.Session.withTransaction
         */
        @JvmSynthetic
        suspend fun <T> withTransaction(work: suspend (Session) -> T) = withTransaction { session, _ -> work(session) }

        /**
         * Perform work using a [reactive session][StatelessSession]
         * within an associated [transaction][Transaction].
         *
         * * If there is already a stateless session associated with the
         * current reactive stream, then the work will be executed using that
         * session.
         * * Otherwise, if there is no stateless session associated with the
         * current stream, a new stateless session will be created.
         *
         * The session will be closed automatically and the transaction committed
         * automatically.
         *
         * @param work a function which accepts the stateless session and returns the result of the work.
         *
         * @see [Coroutines.SessionFactory.withStatelessSession]
         * @see StatelessSession.withTransaction
         */
        @JvmSynthetic
        suspend fun <T> withStatelessTransaction(work: suspend (StatelessSession) -> T): T =
            withStatelessTransaction { statelessSession, _ -> work(statelessSession) }

        /**
         * Perform work using a [reactive session][StatelessSession] within an associated [transaction][Transaction].
         *
         * * If there is already a stateless session associated with the
         * current reactive stream, then the work will be executed using that
         * session.
         * * Otherwise, if there is no stateless session associated with the
         * current stream, a new stateless session will be created.
         *
         * The session will be closed automatically and the transaction committed
         * automatically.
         *
         * @param work a function which accepts the stateless session and returns the result of the work.
         *
         * @see Coroutines.SessionFactory.withStatelessSession
         * @see Coroutines.SessionFactory.withTransaction
         */
        @JvmSynthetic
        suspend fun <T> withStatelessTransaction(work: suspend (StatelessSession, Transaction) -> T): T

        /**
         * Perform work using a [stateless session][StatelessSession].
         *
         * * If there is already a stateless session associated with the
         * current reactive stream, then the work will be executed using that
         * session.
         * * Otherwise, if there is no stateless session associated with the
         * current stream, a new stateless session will be created.
         *
         * The session will be closed automatically.
         *
         * @param work a function which accepts the session and returns the result of the work.
         */
        @JvmSynthetic
        suspend fun <T> withStatelessSession(work: suspend (StatelessSession) -> T): T

        /**
         * Perform work using a [stateless session][StatelessSession].
         *
         * * If there is already a stateless session associated with the
         * current reactive stream and given tenant id, then the work will be
         * executed using that session.
         * * Otherwise, if there is no stateless session associated with the
         * current stream and given tenant id, a new stateless session will be
         * created.
         *
         * The session will be closed automatically.
         *
         * @param tenantId the id of the tenant
         * @param work a function which accepts the session and returns the result of the work.
         */
        @JvmSynthetic
        suspend fun <T> withStatelessSession(
            tenantId: String?,
            work: suspend (StatelessSession) -> T,
        ): T

        /**
         * Perform work using a [reactive session][Session] for
         * the tenant with the specified tenant id within an associated
         * [transaction][Transaction].
         *
         * * If there is already a session associated with the current
         * reactive stream and given tenant id, then the work will be
         * executed using that session.
         * * Otherwise, if there is no session associated with the
         * current stream and given tenant id, a new stateless session
         * will be created.
         *
         * The session will be [flushed][Session.flush] and closed
         * automatically, and the transaction committed automatically.
         *
         * @param tenantId the id of the tenant
         * @param work a function which accepts the session and returns the result of the work.
         *
         * @see [Coroutines.SessionFactory.withSession]
         * @see Session.withTransaction
         */
        @JvmSynthetic
        suspend fun <T> withTransaction(
            tenantId: String,
            work: suspend (Session, Transaction) -> T,
        ): T

        /**
         * Perform work using a [reactive session][StatelessSession]
         * for the tenant with the specified tenant id within an associated
         * [transaction][Transaction].
         *
         * * If there is already a stateless session associated with the
         * current reactive stream and given tenant id, then the work will be
         * executed using that session.
         * * Otherwise, if there is no stateless session associated with the
         * current stream and given tenant id, a new stateless session will be
         * created.
         *
         * The session will be closed automatically and the transaction committed
         * automatically.
         *
         * @param tenantId the id of the tenant
         * @param work a function which accepts the stateless session and returns the result of the work.
         *
         * @see [Coroutines.SessionFactory.withStatelessSession]
         * @see StatelessSession.withTransaction
         */
        @JvmSynthetic
        suspend fun <T> withStatelessTransaction(
            tenantId: String?,
            work: suspend (StatelessSession, Transaction) -> T,
        ): T

        /** @return an instance of [CriteriaBuilder] for creating criteria queries. */
        fun getCriteriaBuilder(): HibernateCriteriaBuilder

        /** Obtain the JPA [Metamodel] for the persistence unit. */
        fun getMetamodel(): Metamodel?

        /** Obtain the [Cache] object for managing the second-level cache. */
        fun getCache(): Cache?

        /** Obtain the [Statistics] object exposing factory-level metrics. */
        fun getStatistics(): Statistics?

        /**
         * Return the current instance of [Session], if any.
         * A current session exists only when this method is called
         * from within an invocation of [Coroutines.SessionFactory.withSession]
         * or [Coroutines.SessionFactory.withTransaction].
         *
         * @return the current instance, if any, or `null`
         *
         * @since 3.0
         */
        fun getCurrentSession(): Session?

        /**
         * Return the current instance of [Session], if any.
         * A current session exists only when this method is called
         * from within an invocation of
         * [Coroutines.SessionFactory.withStatelessSession] or
         * [Coroutines.SessionFactory.withStatelessTransaction].
         *
         * @return the current instance, if any, or `null`
         *
         * @since 3.0
         */
        fun getCurrentStatelessSession(): StatelessSession?

        /** Destroy the session factory and clean up its connection pool. */
        override fun close()

        /** @return false if [Coroutines.SessionFactory.close] has been called. */
        fun isOpen(): Boolean
    }

    /** An object whose [Coroutines.Closeable.close] is suspend method. */
    interface Closeable {
        @JvmSynthetic
        suspend fun close()

        fun closeAsStage(): CompletionStage<Void?> =
            CoroutineScope(EmptyCoroutineContext).future {
                this@Closeable.close()
                null
            }
    }

    companion object {
        /**
         * Asynchronously fetch an association configured for lazy loading.
         *
         * ```java
         * Mutiny.fetch(author.getBook()).map(book -> print(book.getTitle()));
         * ```
         *
         * @param association a lazy-loaded association
         *
         * @return the fetched association, via suspend.
         *
         * @see org.hibernate.Hibernate.initialize
         */
        @JvmSynthetic
        suspend fun <T> fetch(association: T): T? {
            val session: SharedSessionContractImplementor
            when {
                association is HibernateProxy -> {
                    session = association.hibernateLazyInitializer.session
                }

                association is AbstractPersistentCollection<*> -> {
                    // this unfortunately doesn't work for stateless session because the session ref gets set to null
                    session = association.session
                }

                ManagedTypeHelper.isPersistentAttributeInterceptable(association) -> {
                    val interceptable = ManagedTypeHelper.asPersistentAttributeInterceptable(association)
                    val interceptor = interceptable.`$$_hibernate_getInterceptor`()
                    if (interceptor is EnhancementAsProxyLazinessInterceptor) {
                        session = interceptor.linkedSession
                    } else {
                        return association
                    }
                }

                else -> {
                    return association
                }
            }
            // Is safe to call without change the coroutine context?
            return ReactiveQueryExecutorLookup.extract(session).reactiveFetch(association, false).await()
        }
    }
}
