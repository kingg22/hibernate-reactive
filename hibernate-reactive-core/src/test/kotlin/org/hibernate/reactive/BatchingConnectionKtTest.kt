/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive

import io.vertx.junit5.VertxTestContext
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.hibernate.reactive.CoroutinesTestHelper.test
import org.hibernate.reactive.coroutines.impl.CoroutinesSessionImpl
import org.hibernate.reactive.coroutines.impl.CoroutinesStatelessSessionImpl
import org.hibernate.reactive.pool.BatchingConnection
import org.hibernate.reactive.pool.impl.SqlClientConnection
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class BatchingConnectionKtTest : BatchingConnectionTest() {
    // See io.vertx.junit5.Timeout annotation on super test class
    private val timeout = 10.minutes

    @Test
    fun testBatchingWithStageAsCoroutinesStateless(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                val pigs =
                    arrayOf(
                        GuineaPig(11, "One"),
                        GuineaPig(22, "Two"),
                        GuineaPig(33, "Three"),
                        GuineaPig(44, "Four"),
                        GuineaPig(55, "Five"),
                        GuineaPig(66, "Six"),
                    )

                // The same purpose, but all types are platform types and syntax 100% java style
                getSessionFactory()
                    .withStatelessSession { session ->
                        session.insert(10, *pigs)
                    }.await()
                Assertions.assertThat(sqlTracker.loggedQueries).hasSize(1)
                Assertions
                    .assertThat(sqlTracker.loggedQueries[0])
                    .matches("insert into pig \\(name,version,id\\) values (.*)")
                sqlTracker.clear()
            }
        }

    @Test
    fun testBatchingWithCoroutinesStateless(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val pigs =
                arrayOf(
                    GuineaPig(11, "One"),
                    GuineaPig(22, "Two"),
                    GuineaPig(33, "Three"),
                    GuineaPig(44, "Four"),
                    GuineaPig(55, "Five"),
                    GuineaPig(66, "Six"),
                )

            test(context) {
                getCoroutinesSessionFactory()
                    .withStatelessTransaction { s ->
                        // Important, use “spread” (*) when use functions with vararg
                        // Consumers probably don't want to know that, maybe create an overload to this case (Iterable)
                        s.insertAll(10, *pigs)
                    }

                // We expect only one insert query
                Assertions.assertThat(sqlTracker.loggedQueries).hasSize(1)
                // Parameters are different for different dbs, so we cannot do an exact match
                Assertions
                    .assertThat(sqlTracker.loggedQueries[0])
                    .matches("insert into pig \\(name,version,id\\) values (.*)")
                sqlTracker.clear()
            }
        }

    @Test
    fun testCoroutinesInsertAllWithStateless(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val pigs =
                arrayOf(
                    GuineaPig(11, "One"),
                    GuineaPig(22, "Two"),
                    GuineaPig(33, "Three"),
                    GuineaPig(44, "Four"),
                    GuineaPig(55, "Five"),
                    GuineaPig(66, "Six"),
                )
            test(context) {
                getCoroutinesSessionFactory()
                    .withStatelessTransaction { s -> s.insertAll(*pigs) }

                // We expect only 1 insert query, despite hibernate.jdbc.batch_size is set to 5
                // insertAll by default use the pigs.length as batch size
                Assertions.assertThat(sqlTracker.loggedQueries).hasSize(1)
                // Parameters are different for different dbs, so we cannot do an exact match
                Assertions
                    .assertThat(sqlTracker.loggedQueries[0])
                    .matches("insert into pig \\(name,version,id\\) values (.*)")
                sqlTracker.clear()
            }
        }

    @Test
    fun testBatchingConnectionCoroutines(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                openCoroutinesSession().let { session ->
                    Assertions
                        .assertThat((session.await() as CoroutinesSessionImpl).getReactiveConnection())
                        .isInstanceOf(BatchingConnection::class.java)
                }
            }
        }

    @Test
    fun testBatchingConnectionWithCoroutinesStateless(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                openCoroutinesStatelessSession()
                    .let { session ->
                        Assertions
                            .assertThat((session.await() as CoroutinesStatelessSessionImpl).getReactiveConnection())
                            // Stateless session is not affected by the STATEMENT_BATCH_SIZE property
                            .isInstanceOf(SqlClientConnection::class.java)
                    }
            }
        }
}
