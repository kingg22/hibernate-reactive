/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.reactive

import io.vertx.junit5.VertxTestContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.hibernate.reactive.BaseReactiveTest.closeSession
import org.hibernate.reactive.BaseReactiveTest.getCoroutinesSessionFactory
import org.hibernate.reactive.coroutines.Coroutines

class BaseCoroutineTest {
    private var session: Coroutines.Session? = null
    private var statelessSession: Coroutines.StatelessSession? = null

    @JvmSynthetic
    suspend fun openCoroutinesSession(): Coroutines.Session {
        closeSession(session).await()
        return getCoroutinesSessionFactory().openSession().also { session = it }
    }

    @JvmSynthetic
    suspend fun openCoroutinesStatelessSesion(): Coroutines.StatelessSession {
        closeSession(statelessSession).await()
        return getCoroutinesSessionFactory().openStatelessSession().also { statelessSession = it }
    }

    companion object {
        @JvmSynthetic
        suspend fun test(
            context: VertxTestContext,
            work: suspend () -> Unit,
        ) {
            try {
                withContext(Dispatchers.IO) {
                    work()
                }
                context.completeNow()
            } catch (e: Throwable) {
                context.failNow(e)
            }
        }
    }
}
