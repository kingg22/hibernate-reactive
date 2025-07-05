/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines.internal

/**
 * A documented warning for [org.hibernate.reactive.coroutines.Coroutines] implementations need
 * sync the coroutines context with the hibernate reactive context.
 */
@RequiresOptIn(
    "The method or property of the API require the specific HibernateReactiveContext, else unexpected result or throw a exception",
    RequiresOptIn.Level.WARNING,
)
annotation class RequireHibernateReactiveContext
