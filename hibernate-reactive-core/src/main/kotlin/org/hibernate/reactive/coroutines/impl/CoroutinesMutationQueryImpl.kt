/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.impl

import jakarta.persistence.Parameter
import kotlinx.coroutines.future.await
import org.hibernate.reactive.context.Context
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.internal.withHibernateContext
import org.hibernate.reactive.query.ReactiveMutationQuery

class CoroutinesMutationQueryImpl<R>(
    private val delegate: ReactiveMutationQuery<R>,
    private val context: Context,
) : Coroutines.MutationQuery {
    override suspend fun executeUpdate(): Int =
        withHibernateContext(context) {
            delegate.executeReactiveUpdate().await()
        }

    override fun setParameter(
        parameter: Int,
        argument: Any,
    ): Coroutines.MutationQuery =
        apply {
            delegate.setParameter(parameter, argument)
        }

    override fun setParameter(
        parameter: String,
        argument: Any,
    ): Coroutines.MutationQuery =
        apply {
            delegate.setParameter(parameter, argument)
        }

    override fun <T> setParameter(
        parameter: Parameter<T>,
        argument: T,
    ): Coroutines.MutationQuery =
        apply {
            delegate.setParameter(parameter, argument)
        }

    override fun setComment(comment: String?): Coroutines.MutationQuery =
        apply {
            delegate.comment = comment
        }

    override fun getComment(): String? = delegate.comment
}
