/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.reactive.coroutines

suspend fun <T> Coroutines.SessionFactory.session(work: suspend Coroutines.Session.() -> T) = withSession { work(it) }

suspend fun <T> Coroutines.SessionFactory.transaction(work: suspend Coroutines.Session.() -> T) = withTransaction { work(it) }

suspend fun <T> Coroutines.SessionFactory.transaction(work: suspend Coroutines.Session.(Coroutines.Transaction) -> T) =
    withTransaction { s, t -> work(s, t) }

suspend fun <T> Coroutines.SessionFactory.statelessSession(work: suspend Coroutines.StatelessSession.() -> T) =
    withStatelessSession { work(it) }

suspend fun <T> Coroutines.SessionFactory.statelessTransaction(work: Coroutines.StatelessSession.() -> T) =
    withStatelessSession { work(it) }

suspend fun <T> Coroutines.SessionFactory.statelessTransaction(work: suspend Coroutines.StatelessSession.(Coroutines.Transaction) -> T) =
    withStatelessTransaction { s, t -> work(s, t) }

// -- tenant --
suspend fun <T> Coroutines.SessionFactory.session(
    tenantId: String,
    work: suspend Coroutines.Session.() -> T,
) = withSession(tenantId) { work(it) }

suspend fun <T> Coroutines.SessionFactory.transaction(
    tenantId: String,
    work: suspend Coroutines.Session.() -> T,
) = withTransaction(tenantId) { s, _ -> work(s) }

suspend fun <T> Coroutines.SessionFactory.transaction(
    tenantId: String,
    work: suspend Coroutines.Session.(Coroutines.Transaction) -> T,
) = withTransaction(tenantId) { s, t -> work(s, t) }

suspend fun <T> Coroutines.SessionFactory.statelessSession(
    tenantId: String,
    work: Coroutines.StatelessSession.() -> T,
) = withStatelessSession(tenantId) { work(it) }

suspend fun <T> Coroutines.SessionFactory.statelessTransaction(
    tenantId: String,
    work: suspend Coroutines.StatelessSession.() -> T,
) = withStatelessSession(tenantId) { work(it) }

suspend fun <T> Coroutines.SessionFactory.statelessTransaction(
    tenantId: String,
    work: suspend Coroutines.StatelessSession.(Coroutines.Transaction) -> T,
) = withStatelessTransaction(tenantId) { s, t -> work(s, t) }

// -- sessions --
suspend fun <T> Coroutines.Session.transaction(work: suspend Coroutines.Session.(Coroutines.Transaction) -> T) =
    withTransaction { work(it) }

suspend fun <T> Coroutines.StatelessSession.transaction(work: suspend Coroutines.StatelessSession.(Coroutines.Transaction) -> T) =
    withTransaction { work(it) }
