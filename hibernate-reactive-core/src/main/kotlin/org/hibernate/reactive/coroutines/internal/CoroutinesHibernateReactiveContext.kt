/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.internal

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Special [AbstractCoroutineContextElement] to avoid problems when change the [CoroutineContext]
 * in [kotlinx.coroutines.withContext] inside of [org.hibernate.reactive.context.Context].
 *
 * For Java consumers: **Don't use this class, only for internal use.**
 */
internal class CoroutinesHibernateReactiveContext : AbstractCoroutineContextElement(CoroutinesHibernateReactiveContext) {
    /** [CoroutineContext.Key] for [org.hibernate.reactive.context.Context] */
    internal companion object Key : CoroutineContext.Key<CoroutinesHibernateReactiveContext>
}
