/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive

import io.vertx.junit5.VertxTestContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.Coroutines.Session
import org.hibernate.reactive.coroutines.Coroutines.StatelessSession
import org.hibernate.reactive.coroutines.ExperimentalHibernateReactiveCoroutineApi
import java.util.concurrent.CompletionStage
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Define operations need to be called in [BaseReactiveTest] or other java file with a Java compatible manner.
 * Define more helper to kotlin equivalente to some method on java code if not have a way to define it on java.
 */
@ExperimentalHibernateReactiveCoroutineApi
object CoroutinesTestHelper {
    /** Coroutines equivalent to [BaseReactiveTest.test] */
    @JvmSynthetic
    suspend inline fun test(context: VertxTestContext, work: suspend () -> Unit) {
        try {
            work()
            context.completeNow()
        } catch (e: Throwable) {
            context.failNow(e)
        }
    }

    @JvmStatic
    fun Coroutines.SessionFactory.openStatelessSessionAsStage(): CompletionStage<StatelessSession> =
        CoroutineScope(EmptyCoroutineContext).future { openStatelessSession() }

    @JvmStatic
    fun Coroutines.SessionFactory.openSessionAsStage(): CompletionStage<Session> =
        CoroutineScope(EmptyCoroutineContext).future { openSession() }

    @JvmStatic
    fun Coroutines.Closeable.closeAsStage(): CompletionStage<Void> = CoroutineScope(EmptyCoroutineContext).future {
        close()
        null
    }
}
