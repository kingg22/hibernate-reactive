/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines

@RequiresOptIn(
    "This API is very delicate and can only work with Completion Stage and Coroutines extension, knowledge of dispatcher, context, etc. Prefer use default implementation.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
annotation class DelicateHibernateReactiveCoroutineApi
