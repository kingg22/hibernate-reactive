/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
@file:OptIn(ExperimentalContracts::class, ExperimentalTypeInference::class)

package org.hibernate.reactive.coroutines.internal

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.hibernate.reactive.context.Context
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.coroutineContext
import kotlin.experimental.ExperimentalTypeInference

/**
 * Run the block in the [Hibernate Context '_event loop_'][Context]
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
 *             // await in the same thread of reactiveFind and it can't never complete.
 *             delegate.reactiveFind(entityClass, id, null, null).await()
 *         }
 * ```
 * TLDR: await in the same thread of [CompletionStage.complete][CompletableFuture.complete] block the [event loop][Context].
 * @param context the Hibernate context
 * @param block operation to execute and need the context
 * @see <a href="https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html#dispatchers-and-threads">Read more about coroutines and dispatchers</a>
 */
@JvmSynthetic
@OverloadResolutionByLambdaReturnType
internal suspend inline fun <T> withHibernateContext(
    context: Context,
    crossinline block: suspend () -> T,
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    // Force coroutines context uses Vertx Context as Dispatcher
    // Don't create a new coroutine context because can change the thread and
    // hibernate forbidden execute in other thread included if the thread is in event loop
    return if (coroutineContext[CoroutinesHibernateReactiveContext] != null) {
        try {
            block()
        } catch (c: java.util.concurrent.CompletionException) {
            // Rethrow the original cause unwrap of CompletionException
            throw c.cause ?: c
        }
    } else {
        withContext(
            context.asCoroutineDispatcher() + CoroutinesHibernateReactiveContext() + CoroutineName("ReactiveHibernateContextAsDispatcher"),
        ) {
            try {
                block()
            } catch (c: java.util.concurrent.CompletionException) {
                // Rethrow the original cause unwrap of CompletionException
                throw c.cause ?: c
            }
        }
    }
}

/**
 * When use [CompletionStage.get][java.util.concurrent.CompletableFuture.get] cause block the event loop.
 * Or await in the same thread of [CompletionStage.complete][CompletableFuture.complete], complete never success and await indefinitely.
 *
 * Need other context (thread) to await the result of Stage (e.g. [Dispatchers.IO])
 * @param context The Hibernate Context to run the work
 * @param block The work in [CompletionStage] API.
 * @see safeAwait
 * @see withHibernateContext
 */
@JvmSynthetic
internal suspend inline fun <T> withHibernateContext(
    context: Context,
    crossinline block: () -> CompletionStage<T>,
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    // When use CompletionStage.get cause block the event loop if await in the same thread of completion stage complete
    // First, change to use vertx context as dispatcher
    return if (coroutineContext[CoroutinesHibernateReactiveContext] != null) {
        safeAwait(block())
    } else {
        withContext(
            context.asCoroutineDispatcher() + CoroutinesHibernateReactiveContext() + CoroutineName("withReactiveHibernateContext"),
        ) {
            safeAwait(block())
        }
    }
}

/**
 * Unwrap the [java.util.concurrent.CompletionException] if occurs.
 * @param stage Work to await
 */
@JvmSynthetic
internal suspend inline fun <T> safeAwait(stage: CompletionStage<T>): T =
    withContext(CoroutineName("safeAwait")) {
        try {
            stage.await()
        } catch (c: java.util.concurrent.CompletionException) {
            // Rethrow the original cause unwrap of CompletionException
            throw c.cause ?: c
        }
    }
