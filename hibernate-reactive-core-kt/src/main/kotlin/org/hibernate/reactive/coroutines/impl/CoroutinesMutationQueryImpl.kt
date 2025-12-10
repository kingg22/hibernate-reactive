/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.impl

import jakarta.persistence.Parameter
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.DelicateHibernateReactiveCoroutineApi
import org.hibernate.reactive.coroutines.ExperimentalHibernateReactiveCoroutineApi
import org.hibernate.reactive.coroutines.HibernateReactiveOpen
import org.hibernate.reactive.query.ReactiveMutationQuery

@HibernateReactiveOpen
@ExperimentalHibernateReactiveCoroutineApi
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(DelicateHibernateReactiveCoroutineApi::class)
class CoroutinesMutationQueryImpl<R>(private val delegate: ReactiveMutationQuery<R>) : Coroutines.MutationQuery {
    override suspend fun executeUpdate(): Int = TODO("Use delegate::executeReactiveUpdate)")

    override fun setParameter(parameter: Int, argument: Any): Coroutines.MutationQuery = apply {
        delegate.setParameter(parameter, argument)
    }

    override fun setParameter(parameter: String, argument: Any): Coroutines.MutationQuery = apply {
        delegate.setParameter(parameter, argument)
    }

    override fun <T> setParameter(parameter: Parameter<T>, argument: T): Coroutines.MutationQuery = apply {
        delegate.setParameter(parameter, argument)
    }

    override fun setComment(comment: String?): Coroutines.MutationQuery = apply { delegate.comment = comment }

    override fun getComment(): String? = delegate.comment
}
