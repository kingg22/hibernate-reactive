/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines

@RequiresOptIn(
    "This API is a experimental support of Kotlin Coroutines, is not stable at all",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalHibernateReactiveCoroutineApi
