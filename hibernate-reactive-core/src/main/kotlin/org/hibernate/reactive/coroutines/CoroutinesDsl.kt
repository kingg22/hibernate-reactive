/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines

// -- Session factory ext --
@JvmSynthetic
suspend fun <T> Coroutines.SessionFactory.session(work: suspend Coroutines.Session.() -> T) = withSession { work(it) }

@JvmSynthetic
suspend fun <T> Coroutines.SessionFactory.transaction(work: suspend Coroutines.Session.(Coroutines.Transaction) -> T) =
    withTransaction { s, t -> work(s, t) }

@JvmSynthetic
suspend fun <T> Coroutines.SessionFactory.statelessSession(work: suspend Coroutines.StatelessSession.() -> T) =
    withStatelessSession { work(it) }

@JvmSynthetic
suspend fun <T> Coroutines.SessionFactory.statelessTransaction(work: suspend Coroutines.StatelessSession.(Coroutines.Transaction) -> T) =
    withStatelessTransaction { s, t -> work(s, t) }

// -- same with tenant --
@JvmSynthetic
suspend fun <T> Coroutines.SessionFactory.session(
    tenantId: String,
    work: suspend Coroutines.Session.() -> T,
) = withSession(tenantId) { work(it) }

@JvmSynthetic
suspend fun <T> Coroutines.SessionFactory.transaction(
    tenantId: String,
    work: suspend Coroutines.Session.(Coroutines.Transaction) -> T,
) = withTransaction(tenantId) { s, t -> work(s, t) }

@JvmSynthetic
suspend fun <T> Coroutines.SessionFactory.statelessSession(
    tenantId: String,
    work: Coroutines.StatelessSession.() -> T,
) = withStatelessSession(tenantId) { work(it) }

@JvmSynthetic
suspend fun <T> Coroutines.SessionFactory.statelessTransaction(
    tenantId: String,
    work: suspend Coroutines.StatelessSession.(Coroutines.Transaction) -> T,
) = withStatelessTransaction(tenantId) { s, t -> work(s, t) }

// -- Session ext --
@JvmSynthetic
suspend fun <T> Coroutines.Session.transaction(work: suspend Coroutines.Session.(Coroutines.Transaction) -> T) =
    withTransaction { work(it) }

@JvmSynthetic
suspend fun <T> Coroutines.StatelessSession.transaction(work: suspend Coroutines.StatelessSession.(Coroutines.Transaction) -> T) =
    withTransaction { work(it) }
