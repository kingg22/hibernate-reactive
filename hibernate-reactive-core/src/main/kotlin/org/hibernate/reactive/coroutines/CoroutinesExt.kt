/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.coroutines

import jakarta.persistence.LockModeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.hibernate.LockMode
import org.hibernate.reactive.coroutines.impl.CoroutinesSessionImpl
import org.hibernate.reactive.coroutines.impl.CoroutinesStatelessSessionImpl

inline fun <reified T> Coroutines.QueryProducer.createSelectionQuery(queryString: String) = createSelectionQuery(queryString, T::class.java)

// Shadowed by deprecated function on interface, if we mark hidden, this works fine
inline fun <reified T> Coroutines.QueryProducer.createQuery(queryName: String) = createQuery(queryName, T::class.java)

inline fun <reified T> Coroutines.QueryProducer.createNamedQueryOfType(queryName: String) = createNamedQuery(queryName, T::class.java)

inline fun <reified T> Coroutines.QueryProducer.createNativeQueryOfType(queryName: String) = createNativeQuery(queryName, T::class.java)

suspend inline fun <reified T> Coroutines.Session.find(id: Any?) = find(T::class.java, id)

suspend inline fun <reified T> Coroutines.Session.find(
    id: Any?,
    lockMode: LockMode?,
) = find(T::class.java, id, lockMode)

suspend inline fun <reified T> Coroutines.Session.find(
    id: Any?,
    lockMode: LockModeType,
) = find(T::class.java, id, lockMode)

suspend inline fun <reified T> Coroutines.Session.find(vararg ids: Any?) = find(T::class.java, *ids)

suspend inline operator fun <reified T> Coroutines.StatelessSession.get(id: Any?) = get(T::class.java, id)

suspend inline fun <reified T> Coroutines.StatelessSession.get(vararg ids: Any?) = get(T::class.java, ids)

// -- Iterable overloads --
suspend inline fun <reified T> Coroutines.Session.find(ids: Iterable<Any>) = find(T::class.java, *ids.toList().toTypedArray())

/**
 * Run all the [block] in Hibernate Reactive Context and thread of the session.
 *
 * **Important**: The thread can be blocked, you need to ensure change the dispatcher [withContext] and back to this scope.
 */
@JvmSynthetic
suspend fun <T> Coroutines.Session.hibernateScope(block: suspend CoroutineScope.() -> T): T =
    withContext((this as CoroutinesSessionImpl).dispatcher, block)

/**
 * Run all the [block] in Hibernate Reactive Context and thread of the session.
 *
 * **Important**: The thread can be blocked, you need to ensure change the dispatcher [withContext] and back to this scope.
 */
@JvmSynthetic
suspend fun <T> Coroutines.StatelessSession.hibernateScope(block: suspend CoroutineScope.() -> T): T =
    withContext((this as CoroutinesStatelessSessionImpl).dispatcher, block)
