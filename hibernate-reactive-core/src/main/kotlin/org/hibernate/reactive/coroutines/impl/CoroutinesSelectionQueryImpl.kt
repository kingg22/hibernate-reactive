/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.impl

import jakarta.persistence.CacheRetrieveMode
import jakarta.persistence.CacheStoreMode
import jakarta.persistence.EntityGraph
import jakarta.persistence.Parameter
import org.hibernate.CacheMode
import org.hibernate.FlushMode
import org.hibernate.LockMode
import org.hibernate.graph.GraphSemantic
import org.hibernate.graph.spi.RootGraphImplementor
import org.hibernate.query.Page
import org.hibernate.reactive.context.Context
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.internal.withHibernateContext
import org.hibernate.reactive.query.ReactiveSelectionQuery

class CoroutinesSelectionQueryImpl<R>(
    private val delegate: ReactiveSelectionQuery<R>,
    private val context: Context,
) : Coroutines.SelectionQuery<R> {
    override fun setFlushMode(flushMode: FlushMode?): Coroutines.SelectionQuery<R> =
        apply {
            delegate.hibernateFlushMode = flushMode
        }

    override fun setLockMode(lockMode: LockMode?): Coroutines.SelectionQuery<R> =
        apply {
            delegate.hibernateLockMode = lockMode
        }

    override fun setLockMode(
        alias: String?,
        lockMode: LockMode?,
    ): Coroutines.SelectionQuery<R> =
        apply {
            delegate.setLockMode(alias, lockMode)
        }

    override fun setMaxResults(maxResults: Int): Coroutines.SelectionQuery<R> =
        apply {
            delegate.maxResults = maxResults
        }

    override fun setFirstResult(firstResult: Int): Coroutines.SelectionQuery<R> =
        apply {
            delegate.firstResult = firstResult
        }

    override fun setPage(page: Page): Coroutines.SelectionQuery<R> =
        apply {
            setFirstResult(page.firstResult)
            setMaxResults(page.maxResults)
        }

    override fun getMaxResults(): Int = delegate.maxResults

    override fun getFirstResult(): Int = delegate.firstResult

    override suspend fun getSingleResult(): R = withHibernateContext(context, delegate::getReactiveSingleResult)

    override suspend fun getSingleResultOrNull(): R? = withHibernateContext(context, delegate::getReactiveSingleResultOrNull)

    override suspend fun getResultCount(): Long? = withHibernateContext(context, delegate::getReactiveResultCount)

    override suspend fun getResultList(): List<R> = withHibernateContext(context, delegate::getReactiveResultList)

    override fun setReadOnly(readOnly: Boolean): Coroutines.SelectionQuery<R> =
        apply {
            delegate.isReadOnly = readOnly
        }

    override fun isReadOnly(): Boolean = delegate.isReadOnly

    override fun setCacheable(cacheable: Boolean): Coroutines.SelectionQuery<R> =
        apply {
            delegate.isCacheable = cacheable
        }

    override fun isCacheable(): Boolean = delegate.isCacheable

    override fun setCacheRegion(cacheRegion: String?): Coroutines.SelectionQuery<R> =
        apply {
            delegate.cacheRegion = cacheRegion
        }

    override fun getCacheRegion(): String? = delegate.cacheRegion

    override fun setCacheMode(cacheMode: CacheMode?): Coroutines.SelectionQuery<R> =
        apply {
            delegate.cacheMode = cacheMode
        }

    override fun getCacheStoreMode(): CacheStoreMode? = delegate.cacheStoreMode

    override fun getCacheRetrieveMode(): CacheRetrieveMode? = delegate.cacheRetrieveMode

    override fun getCacheMode(): CacheMode? = delegate.cacheMode

    @Suppress("DEPRECATION")
    override fun getFlushMode(): FlushMode? = delegate.hibernateFlushMode

    override fun setPlan(entityGraph: EntityGraph<R>?): Coroutines.SelectionQuery<R> =
        apply {
            delegate.applyGraph(entityGraph as? RootGraphImplementor<*>?, GraphSemantic.FETCH)
        }

    override fun enableFetchProfile(profileName: String?): Coroutines.SelectionQuery<R> =
        apply {
            delegate.enableFetchProfile(profileName)
        }

    override fun setParameter(
        parameter: Int,
        argument: Any,
    ): Coroutines.SelectionQuery<R> =
        apply {
            delegate.setParameter(parameter, argument)
        }

    override fun setParameter(
        parameter: String,
        argument: Any,
    ): Coroutines.SelectionQuery<R> =
        apply {
            delegate.setParameter(parameter, argument)
        }

    override fun <T> setParameter(
        parameter: Parameter<T>,
        argument: T,
    ): Coroutines.SelectionQuery<R> =
        apply {
            delegate.setParameter(parameter, argument)
        }

    override fun setComment(comment: String?): Coroutines.SelectionQuery<R> =
        apply {
            delegate.comment = comment
        }

    override fun getComment(): String? = delegate.comment
}
