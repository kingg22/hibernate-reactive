/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.reactive.coroutines.internal

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.hibernate.reactive.context.Context
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.coroutineContext

/**
 * Run the block in the [Context] this avoids problems with nested [withContext].
 *
 * Example problematic code:
 * ```kotlin
 * val factory: Coroutines.SessionFactory = getCoroutinesSessionFactory()
 * val foundEntity: Entity = factory.withSession { session ->
 *     session.find(id)
 * }
 * foundEntity.doSomething() // this never run because find operation cause a quiet dead lock
 * ```
 * Implementation do:
 * ```kotlin
 *     override suspend fun <T> withSession(work: suspend (Coroutines.Session) -> T): T {
 *         val current = context?.get(contextKeyForSession)
 *         return if (current != null && current.isOpen()) {
 *             log.debug(REUSING_SESSION)
 *             work(current)
 *         } else {
 *             log.debug(OPENING_NEW_SESSION)
 *             // Force a nonnull vertx context
 *             // Change the coroutine context to use vertx as dispatcher
 *             withContext(checkNotNull(context).asCoroutineDispatcher()) {
 *                 // this nested code doesn't change the coroutine context, only open and close the session
 *                 withSession(openSession(), work, contextKeyForSession)
 *             }
 *         }
 *     }
 *
 * // -- nested code or operation --
 *     override suspend fun <T> find(
 *         entityClass: Class<T>,
 *         id: Any?,
 *     ): T? =
 *         // Change the coroutine context again with context of vertx
 *         withContext(context.asCoroutineDispatcher()) {
 *             delegate.reactiveFind(entityClass, id, null, null).await()
 *         }
 * ```
 * TLDR: change the [kotlin.coroutines.CoroutineContext] 2 times or more in nested code block the event loop of
 * [Vertx][Context].
 */
@JvmSynthetic
@OptIn(ExperimentalContracts::class)
internal suspend fun <T> withHibernateContext(
    context: Context,
    block: suspend () -> T,
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    // When use CompletionStage.get cause block the event loop if the thread's full
    // Need other context (thread) to await the result of Stage

    // Check the mark of reactive context (vertx)
    return if (coroutineContext[CoroutinesHibernateReactiveContext] != null) {
        block()
    } else {
        // Force nested context uses Vertx Context and adds the mark
        withContext(context.asCoroutineDispatcher() + CoroutinesHibernateReactiveContext() + CoroutineName("withHibernateContext")) {
            block()
        }
    }
}
