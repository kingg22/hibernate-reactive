/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
@file:OptIn(ExperimentalContracts::class)

package org.hibernate.reactive.coroutines

import jakarta.persistence.LockModeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.hibernate.LockMode
import org.hibernate.reactive.coroutines.impl.CoroutinesSessionImpl
import org.hibernate.reactive.coroutines.impl.CoroutinesStatelessSessionImpl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

suspend inline fun <reified T> Coroutines.QueryProducer.createSelectionQuery(queryString: String) =
    createSelectionQuery(queryString, T::class.java)

// Shadowed by deprecated function on interface, if we mark hidden, this works fine
suspend inline fun <reified T> Coroutines.QueryProducer.createQuery(queryName: String) = createQuery(queryName, T::class.java)

suspend inline fun <reified T> Coroutines.QueryProducer.createNamedQueryOfType(queryName: String) =
    createNamedQuery(queryName, T::class.java)

suspend inline fun <reified T> Coroutines.QueryProducer.createNativeQueryOfType(queryName: String) =
    createNativeQuery(queryName, T::class.java)

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

suspend inline operator fun <reified T> Coroutines.StatelessSession.get(id: Any) = get(T::class.java, id)

suspend inline fun <reified T> Coroutines.StatelessSession.get(vararg ids: Any) = get(T::class.java, *ids)

// -- Iterable overloads --
suspend inline fun <reified T> Coroutines.Session.find(ids: Iterable<Any>) = find(T::class.java, *ids.toList().toTypedArray())

/**
 * Run all the [block] in Hibernate Reactive Context and thread of the session.
 *
 * **Important**: The thread can be blocked, you need to ensure change the dispatcher [withContext] and back to this scope.
 */
@JvmSynthetic
suspend fun <T> Coroutines.Session.hibernateScope(block: suspend CoroutineScope.() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return withContext((this as CoroutinesSessionImpl).dispatcher, block)
}

/**
 * Run all the [block] in Hibernate Reactive Context and thread of the session.
 *
 * **Important**: The thread can be blocked, you need to ensure change the dispatcher [withContext] and back to this scope.
 */
@JvmSynthetic
suspend fun <T> Coroutines.StatelessSession.hibernateScope(block: suspend CoroutineScope.() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return withContext((this as CoroutinesStatelessSessionImpl).dispatcher, block)
}

/**
 * Executes the given block function on this resource and then closes it down correctly whether an exception is thrown or not.
 *
 * Equivalente is function [Coroutines.SessionFactory.withSession] or [Coroutines.SessionFactory.withStatelessSession].
 * But, this function can block the thread _event loop_ if the context does not have good manage.
 *
 * This function **NOT** execute inside a transaction, use [Coroutines.SessionFactory.withTransaction] or
 * [Coroutines.SessionFactory.withStatelessTransaction].
 * @param block - a function to process this Closeable resource.
 * @return the result of block function invoked on this resource.
 * @throws Throwable exception caught during the execution
 */
@JvmSynthetic
suspend inline fun <T : Coroutines.Closeable, R> T.use(block: (T) -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    var cause: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        cause = e
        throw e
    } finally {
        when {
            cause == null -> close()
            else ->
                try {
                    close()
                } catch (closeException: Throwable) {
                    cause.addSuppressed(closeException)
                }
        }
    }
}
