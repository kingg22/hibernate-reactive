/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive

import io.vertx.junit5.VertxTestContext
import kotlinx.coroutines.test.runTest
import org.hibernate.reactive.CoroutinesTestHelper.test
import org.hibernate.reactive.MutinyStatelessSessionTest.GuineaPig
import org.hibernate.reactive.coroutines.createSelectionQuery
import org.hibernate.reactive.coroutines.use
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Duration.Companion.minutes

// Use GuineaPig of MutinyStatelessSessionTest because if kotlin class want to have a JPA entity, need more dependencies like plugin jpa
class CoroutinesStatelessSessionTest : BaseReactiveTest() {
    private val timeout = 10.minutes

    override fun annotatedEntities(): Collection<Class<*>> = listOf(GuineaPig::class.java)

    @OptIn(ExperimentalContracts::class)
    private fun assertThatPigsAreEqual(
        expected: GuineaPig,
        actual: GuineaPig?,
    ) {
        contract { returns() implies (actual != null) }
        assertNotNull(actual)
        checkNotNull(actual)
        assertEquals(expected.id, actual.id)
        assertEquals(expected.name, actual.name)
    }

    @Test
    fun testStatelessSession(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                val pig = GuineaPig("Aloi")
                getCoroutinesSessionFactory().withStatelessSession { ss ->
                    ss.insert(pig)
                    val list =
                        ss
                            .createSelectionQuery<GuineaPig>("from GuineaPig where name=:n")
                            .setParameter("n", pig.name)
                            .getResultList()

                    assertFalse(list.isEmpty())
                    assertEquals(1, list.size)
                    assertThatPigsAreEqual(pig, list[0])

                    val p = checkNotNull(ss.get(GuineaPig::class.java, pig.id))
                    assertThatPigsAreEqual(pig, p)

                    p.name = "X"
                    ss.update(p)
                    ss.refresh(pig)
                    assertEquals("X", pig.name)

                    ss.createMutationQuery("update GuineaPig set name='Y'").executeUpdate()
                    ss.refresh(pig)
                    assertEquals("Y", pig.name)

                    ss.delete(pig)
                    val list2 = ss.createSelectionQuery("from GuineaPig", GuineaPig::class.java).getResultList()
                    assertTrue(list2.isEmpty())
                }
            }
        }

    @Test
    fun testStatelessSessionWithNamed(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                val pig = GuineaPig("Aloi")
                getCoroutinesSessionFactory().withStatelessSession { ss ->
                    ss.insert(pig)
                    val list =
                        ss
                            .createNamedQuery("findbyname", GuineaPig::class.java)
                            .setParameter("n", pig.name)
                            .getResultList()

                    assertFalse(list.isEmpty())
                    assertEquals(1, list.size)
                    assertThatPigsAreEqual(pig, list[0])

                    val p = ss.get(GuineaPig::class.java, pig.id)
                    assertThatPigsAreEqual(pig, p)

                    p.name = "X"
                    ss.update(p)
                    ss.refresh(pig)
                    assertEquals("X", pig.name)

                    ss.createNamedQuery<Any>("updatebyname").executeUpdate()
                    ss.refresh(pig)
                    assertEquals("Y", pig.name)

                    ss.delete(pig)
                    val list2 = ss.createNamedQuery<Any>("findall").getResultList()
                    assertTrue(list2.isEmpty())
                }
            }
        }

    @Test
    fun testStatelessSessionWithNative(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                val pig = GuineaPig("Aloi")
                val ss = getCoroutinesSessionFactory().openStatelessSession()

                ss.use {
                    it.insert(pig)

                    val list =
                        it
                            .createNativeQuery("select * from Piggy where name=:n", GuineaPig::class.java)
                            .setParameter("n", pig.name)
                            .getResultList()

                    assertFalse(list.isEmpty())
                    assertEquals(1, list.size)
                    assertThatPigsAreEqual(pig, list[0])

                    val p = it.get(GuineaPig::class.java, pig.id)
                    assertThatPigsAreEqual(pig, p)

                    p.name = "X"
                    it.update(p)
                    it.refresh(pig)
                    assertEquals("X", pig.name)

                    val rows = it.createNativeQuery<Any>("update Piggy set name='Y'").executeUpdate()
                    assertEquals(1, rows)

                    it.refresh(pig)
                    assertEquals("Y", pig.name)

                    it.delete(pig)

                    val list2 = it.createNativeQuery<Int>("select id from Piggy").getResultList()
                    assertTrue(list2.isEmpty())
                }
            }
        }

    @Test
    fun testStatelessSessionGetMultiple(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                val a = GuineaPig("A")
                val b = GuineaPig("B")
                val c = GuineaPig("C")

                val ss = getCoroutinesSessionFactory().openStatelessSession()

                ss.use {
                    it.insertMultiple(listOf(a, b, c))
                    val list = it.get(GuineaPig::class.java, a.id, c.id)

                    assertEquals(2, list.size)
                    assertThatPigsAreEqual(a, list[0])
                    assertThatPigsAreEqual(c, list[1])
                }
            }
        }

    @Test
    fun testStatelessSessionCriteria(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                val pig = GuineaPig("Aloi")
                val mate = GuineaPig("Aloina")
                pig.mate = mate

                val cb = getCoroutinesSessionFactory().getCriteriaBuilder()

                val query =
                    cb.createQuery(GuineaPig::class.java).apply {
                        val gp = from(GuineaPig::class.java)
                        where(cb.equal(gp.get<String>("name"), cb.parameter(String::class.java, "n")))
                        orderBy(cb.asc(gp.get<String>("name")))
                    }

                val update =
                    cb.createCriteriaUpdate(GuineaPig::class.java).apply {
                        val updatedPig = from(GuineaPig::class.java)
                        set("name", "Bob")
                        where(updatedPig.get<Any>("mate").isNotNull)
                    }

                val delete =
                    cb.createCriteriaDelete(GuineaPig::class.java).apply {
                        val deletedPig = from(GuineaPig::class.java)
                        where(deletedPig.get<Any>("mate").isNotNull)
                    }

                getCoroutinesSessionFactory().withStatelessSession {
                    it.insertMultiple(listOf(mate, pig))

                    val list =
                        it
                            .createQuery(query)
                            .setParameter("n", pig.name)
                            .getResultList()

                    assertFalse(list.isEmpty())
                    assertEquals(1, list.size)
                    assertThatPigsAreEqual(pig, list[0])

                    val updatedRows = it.createQuery(update).executeUpdate()
                    assertEquals(1, updatedRows)

                    val deletedRows = it.createQuery(delete).executeUpdate()
                    assertEquals(1, deletedRows)
                }
            }
        }

    @Test
    @Disabled("Need correct propagation across coroutine context")
    fun testTransactionPropagation(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                getCoroutinesSessionFactory().withStatelessSession { session ->
                    session.withTransaction { tx ->
                        session.createSelectionQuery("from GuineaPig", GuineaPig::class.java).getResultList()

                        val currentTx = checkNotNull(session.currentTransaction())
                        assertFalse(currentTx.isMarkedForRollback())
                        currentTx.markForRollback()
                        assertTrue(currentTx.isMarkedForRollback())
                        assertTrue(tx.isMarkedForRollback())

                        session.withTransaction { nestedTx ->
                            assertTrue(nestedTx.isMarkedForRollback())
                            session.createSelectionQuery("from GuineaPig", GuineaPig::class.java).getResultList()
                        }
                    }
                }
            }
        }

    @Test
    @Disabled("need correct propagation across coroutine context")
    fun testSessionPropagation(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                getCoroutinesSessionFactory().withStatelessSession { session ->
                    session.createSelectionQuery("from GuineaPig", GuineaPig::class.java).getResultList()

                    getCoroutinesSessionFactory().withStatelessSession { s ->
                        assertEquals(session, s)
                        s.createSelectionQuery("from GuineaPig", GuineaPig::class.java).getResultList()
                    }
                }
            }
        }
}
