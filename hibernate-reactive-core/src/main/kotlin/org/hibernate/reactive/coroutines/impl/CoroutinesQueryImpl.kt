/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

@file:Suppress("useless_cast")

package org.hibernate.reactive.coroutines.impl

import jakarta.persistence.CacheRetrieveMode
import jakarta.persistence.CacheStoreMode
import jakarta.persistence.EntityGraph
import jakarta.persistence.FlushModeType
import jakarta.persistence.LockModeType
import jakarta.persistence.Parameter
import kotlinx.coroutines.future.await
import org.hibernate.CacheMode
import org.hibernate.FlushMode
import org.hibernate.LockMode
import org.hibernate.graph.GraphSemantic
import org.hibernate.graph.spi.RootGraphImplementor
import org.hibernate.query.Page
import org.hibernate.reactive.context.Context
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.internal.withHibernateContext
import org.hibernate.reactive.query.ReactiveQuery

class CoroutinesQueryImpl<R>(
    private val delegate: ReactiveQuery<R>,
    private val context: Context,
) : Coroutines.Query<R> {
    override fun setFlushMode(flushMode: FlushMode?): Coroutines.Query<R> =
        apply {
            delegate.flushMode = flushMode as FlushModeType?
        }

    override fun setLockMode(lockMode: LockMode?): Coroutines.Query<R> =
        apply {
            delegate.lockMode = lockMode as LockModeType?
        }

    override fun setLockMode(
        alias: String?,
        lockMode: LockMode?,
    ): Coroutines.Query<R> =
        apply {
            delegate.setLockMode(alias, lockMode)
        }

    override fun setMaxResults(maxResults: Int): Coroutines.Query<R> =
        apply {
            delegate.maxResults = maxResults
        }

    override fun setFirstResult(firstResult: Int): Coroutines.Query<R> =
        apply {
            delegate.firstResult = firstResult
        }

    override fun setPage(page: Page): Coroutines.Query<R> =
        apply {
            setFirstResult(page.firstResult)
            setMaxResults(page.maxResults)
        }

    override fun getMaxResults(): Int = delegate.maxResults

    override fun getFirstResult(): Int = delegate.firstResult

    override suspend fun getSingleResult(): R =
        withHibernateContext(context) {
            delegate.reactiveSingleResult.await()
        }

    override suspend fun getSingleResultOrNull(): R? =
        withHibernateContext(context) {
            delegate.reactiveSingleResultOrNull.await()
        }

    override suspend fun getResultCount(): Long? =
        withHibernateContext(context) {
            delegate.reactiveResultCount.await()
        }

    override suspend fun getResultList(): List<R> =
        withHibernateContext(context) {
            delegate.reactiveResultList.await()
        }

    override fun setReadOnly(readOnly: Boolean): Coroutines.Query<R> =
        apply {
            delegate.isReadOnly = readOnly
        }

    override fun isReadOnly(): Boolean = delegate.isReadOnly

    override fun setCacheable(cacheable: Boolean): Coroutines.Query<R> =
        apply {
            delegate.isCacheable = cacheable
        }

    override fun isCacheable(): Boolean = delegate.isCacheable

    override fun setCacheRegion(cacheRegion: String?): Coroutines.Query<R> =
        apply {
            delegate.cacheRegion = cacheRegion
        }

    override fun getCacheRegion(): String? = delegate.cacheRegion

    override fun setCacheMode(cacheMode: CacheMode?): Coroutines.Query<R> =
        apply {
            delegate.cacheMode = cacheMode
        }

    override fun getCacheStoreMode(): CacheStoreMode? = delegate.cacheStoreMode

    override fun getCacheRetrieveMode(): CacheRetrieveMode? = delegate.cacheRetrieveMode

    override fun getCacheMode(): CacheMode? = delegate.cacheMode

    @Suppress("DEPRECATION")
    override fun getFlushMode(): FlushMode? = delegate.flushMode as FlushMode?

    override fun setPlan(entityGraph: EntityGraph<R>?): Coroutines.Query<R> =
        apply {
            delegate.applyGraph(entityGraph as? RootGraphImplementor<*>?, GraphSemantic.FETCH)
        }

    override fun enableFetchProfile(profileName: String?): Coroutines.Query<R> =
        apply {
            delegate.enableFetchProfile(profileName)
        }

    override fun setParameter(
        parameter: Int,
        argument: Any,
    ): Coroutines.Query<R> =
        apply {
            delegate.setParameter(parameter, argument)
        }

    override fun setParameter(
        parameter: String,
        argument: Any,
    ): Coroutines.Query<R> =
        apply {
            delegate.setParameter(parameter, argument)
        }

    override fun <T> setParameter(
        parameter: Parameter<T>,
        argument: T,
    ): Coroutines.Query<R> =
        apply {
            delegate.setParameter(parameter, argument)
        }

    override fun setComment(comment: String?): Coroutines.Query<R> =
        apply {
            delegate.comment = comment
        }

    override fun getComment(): String? = delegate.comment

    override suspend fun executeUpdate(): Int =
        withHibernateContext(context) {
            delegate.executeReactiveUpdate().await()
        }
}
