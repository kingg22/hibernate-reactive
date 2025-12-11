/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive

import io.vertx.junit5.VertxTestContext
import jakarta.persistence.LockModeType
import jakarta.persistence.PersistenceException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import org.hibernate.LockMode
import org.hibernate.reactive.CoroutinesTestHelper.test
import org.hibernate.reactive.MutinySessionTest.GuineaPig
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.ExperimentalHibernateReactiveCoroutineApi
import org.hibernate.reactive.coroutines.HibernateReactiveOpen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.Exception
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions as AssertjAssertions

// Use GuineaPig of MutinySessionTest because if kotlin class want to have a JPA entity, need more dependencies like plugin jpa
@ExperimentalHibernateReactiveCoroutineApi
@HibernateReactiveOpen
class CoroutinesSessionTest : BaseReactiveTest() {
    init {
        System.setProperty("kotlinx.coroutines.test.default_timeout", "3m")
    }

    override fun annotatedEntities(): Collection<Class<*>> = listOf(GuineaPig::class.java)

    private suspend fun populateDB() {
        getCoroutinesSessionFactory().withSession { session ->
            session.persist(GuineaPig(5, "Aloi"))
            session.flush()
        }
    }

    private suspend fun selectNameFromId(id: Int) = getCoroutinesSessionFactory().withSession { session ->
        val rowSet = session.createQuery( // Don't use the overload deprecated
            "SELECT name FROM GuineaPig WHERE ID = $id",
            String::class.java,
        ).getResultList()
        when (val size = rowSet.size) {
            0 -> null
            1 -> rowSet.first()
            else -> throw AssertionError("More than one result returned $size")
        }
    }

    private fun assertThatPigsAreEqual(expected: GuineaPig, actual: GuineaPig?) {
        assertNotNull(actual)
        checkNotNull(actual) // Use kotlin safe assertion to get smartcast
        assertEquals(expected.id, actual.id)
        assertEquals(expected.name, actual.name)
    }

    @Test
    fun reactiveFindMultipleIds(context: VertxTestContext): Unit = runTest {
        val rump = GuineaPig(55, "Rumpelstiltskin")
        val emma = GuineaPig(77, "Emma")
        test(context) {
            getCoroutinesSessionFactory().withTransaction { session ->
                session.persistAll(emma, rump)
            }
            val pigs = getCoroutinesSessionFactory().withTransaction { session ->
                session.find(GuineaPig::class.java, emma.id, rump.id)
            }
            AssertjAssertions.assertThat(pigs).containsExactlyInAnyOrder(emma, rump)
        }
    }

    @Test
    fun sessionClear(context: VertxTestContext): Unit = runTest {
        val guineaPig = GuineaPig(81, "Perry")
        test(context) {
            getCoroutinesSessionFactory().withSession { session ->
                session.persist(guineaPig)
                session.clear()
                // If the previous clear doesn't work, this will cause a duplicated entity exception
                session.persist(guineaPig)
                session.flush()
                val result = session.createSelectionQuery("FROM GuineaPig", GuineaPig::class.java).getSingleResult()
                // By not using .find() we check that there is only one entity in the db with getSingleResult()
                assertThatPigsAreEqual(guineaPig, result)
            }
        }
    }

    @Test
    fun reactiveWithTransactionStatelessSession(context: VertxTestContext): Unit = runTest {
        val guineaPig = GuineaPig(61, "Mr. Peanutbutter")
        test(context) {
            getCoroutinesSessionFactory().withStatelessTransaction { session ->
                session.insert(guineaPig)
            }
            val result = getCoroutinesSessionFactory().withSession { session ->
                session.find(GuineaPig::class.java, guineaPig.id)
            }
            assertThatPigsAreEqual(guineaPig, result)
        }
    }

    @Test
    fun reactiveWithTransactionSession(context: VertxTestContext): Unit = runTest {
        val guineaPig = GuineaPig(61, "Mr. Peanutbutter")
        test(context) {
            // Use DSL syntax
            getCoroutinesSessionFactory().withTransaction { s -> s.persist(guineaPig) }
            val result = getCoroutinesSessionFactory().withSession { s -> s.find(GuineaPig::class.java, guineaPig.id) }
            assertThatPigsAreEqual(guineaPig, result)
        }
    }

    @Test
    fun reactiveFind(context: VertxTestContext): Unit = runTest {
        val expectedPig = GuineaPig(5, "Aloi")
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withSession { session ->
                val actualPig = session.find(GuineaPig::class.java, expectedPig.id)
                assertThatPigsAreEqual(expectedPig, actualPig)
                // this is Session
                assertTrue(session.contains(actualPig))
                assertFalse(session.contains(expectedPig))
                assertEquals(LockMode.READ, session.getLockMode(actualPig))
                session.detach(actualPig)
                assertFalse(session.contains(actualPig))
            }
        }
    }

    @Test
    fun reactiveFindWithLock(context: VertxTestContext): Unit = runTest {
        val expectedPig = GuineaPig(5, "Aloi")
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withSession { session ->
                val actualPig = session.find(GuineaPig::class.java, expectedPig.id, LockMode.PESSIMISTIC_WRITE)
                assertThatPigsAreEqual(expectedPig, actualPig)
                assertEquals(LockMode.PESSIMISTIC_WRITE, session.getLockMode(actualPig))
            }
        }
    }

    @Test
    fun reactiveFindRefreshWithLock(context: VertxTestContext): Unit = runTest {
        val expectedPig = GuineaPig(5, "Aloi")
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withSession { session ->
                val pig: GuineaPig? = session.find(GuineaPig::class.java, expectedPig.id)
                session.refresh(pig, LockMode.PESSIMISTIC_WRITE)
                assertThatPigsAreEqual(expectedPig, pig)
                assertEquals(LockMode.PESSIMISTIC_WRITE, session.getLockMode(pig))
            }
        }
    }

    @Test
    fun reactiveFindReadOnlyRefreshWithLock(context: VertxTestContext): Unit = runTest {
        val expectedPig = GuineaPig(5, "Aloi")
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withSession { session ->
                val pig = session.find(GuineaPig::class.java, expectedPig.id)
                checkNotNull(pig) // Check because java code assume it's not null
                session.setReadOnly(pig, true)
                pig.name = "XXXX"
                session.flush()
                session.refresh(pig)
                assertEquals(expectedPig.name, pig.name)
                assertTrue(session.isReadOnly(pig))
            }
            getCoroutinesSessionFactory().withSession { session ->
                val pig = session.find(GuineaPig::class.java, expectedPig.id)
                checkNotNull(pig)
                session.setReadOnly(pig, false)
                pig.name = "XXXX"
                session.flush()
                session.refresh(pig)
                assertEquals("XXXX", pig.name)
                assertFalse(session.isReadOnly(pig))
            }
        }
    }

    @Test
    fun reactiveFindThenUpgradeLock(context: VertxTestContext): Unit = runTest {
        val expectedPig = GuineaPig(5, "Aloi")
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withSession { session ->
                val pig = session.find(GuineaPig::class.java, expectedPig.id)
                session.lock(pig, LockMode.PESSIMISTIC_READ)
                assertThatPigsAreEqual(expectedPig, pig)
                assertEquals(LockMode.PESSIMISTIC_READ, session.getLockMode(pig))
            }
        }
    }

    @Test
    fun reactiveFindThenWriteLock(context: VertxTestContext): Unit = runTest {
        val expectedPig = GuineaPig(5, "Aloi")
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withSession { session ->
                val actualPig = session.find(GuineaPig::class.java, expectedPig.id)
                session.lock(actualPig, LockMode.PESSIMISTIC_WRITE)
                assertThatPigsAreEqual(expectedPig, actualPig)
                assertEquals(LockMode.PESSIMISTIC_WRITE, session.getLockMode(actualPig))
            }
        }
    }

    @Test
    fun reactivePersist(context: VertxTestContext): Unit = runTest {
        test(context) {
            getCoroutinesSessionFactory().withSession { session ->
                session.persist(GuineaPig(10, "Tulip"))
                session.flush()
            }
            val selectRes = selectNameFromId(10)
            assertEquals("Tulip", selectRes)
        }
    }

    @Test
    fun reactivePersistInTx(context: VertxTestContext): Unit = runTest {
        test(context) {
            getCoroutinesSessionFactory().withTransaction { s -> s.persist(GuineaPig(10, "Tulip")) }
            val selectRes = selectNameFromId(10)
            assertEquals("Tulip", selectRes)
        }
    }

    @Test
    fun reactiveRollbackTx(context: VertxTestContext): Unit = runTest {
        test(context) {
            assertThrows<Exception> {
                getCoroutinesSessionFactory().withTransaction { session ->
                    session.persist(ReactiveSessionTest.GuineaPig(10, "Tulip"))
                    session.flush()
                    throw RuntimeException()
                }
                @Suppress("UNREACHABLE_CODE")
                fail()
            }

            assertNull(selectNameFromId(10))
        }
    }

    @Test
    fun reactiveMarkedRollbackTx(context: VertxTestContext): Unit = runTest {
        test(context) {
            getCoroutinesSessionFactory().withTransaction { s, t ->
                s.persist(GuineaPig(10, "Tulip"))
                s.flush()
                t.markForRollback()
            }
            assertNull(selectNameFromId(10))
        }
    }

    @Test
    fun reactiveRemoveTransientEntity(context: VertxTestContext): Unit = runTest {
        test(context) {
            populateDB()
            assertNotNull(selectNameFromId(5))
            assertThrows<Exception> {
                val session = openCoroutinesSession().await()
                session.remove(GuineaPig(5, "Aloi"))
                session.flush()
                session.close()
                fail("Expected exception when removing transient entity")
            }
            assertNotNull(selectNameFromId(5))
        }
    }

    @Test
    fun reactiveRemoveManagedEntity(context: VertxTestContext): Unit = runTest {
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withSession { s ->
                val pig = s.find(GuineaPig::class.java, 5)
                s.remove(pig)
                s.flush()
            }
            assertNull(selectNameFromId(5))
        }
    }

    @Test
    fun reactiveRemoveManagedEntityWithTx(context: VertxTestContext): Unit = runTest {
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withTransaction { s ->
                val pig = s.find(GuineaPig::class.java, 5)
                s.remove(pig)
            }
            assertNull(selectNameFromId(5))
        }
    }

    @Test
    fun reactiveUpdate(context: VertxTestContext): Unit = runTest {
        val newName = "Tina"
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withSession { s ->
                val pig = checkNotNull(s.find(GuineaPig::class.java, 5))
                assertNotEquals(pig.name, newName)
                pig.name = newName // Use kotlin property syntax
                s.flush()
            }
            val name = selectNameFromId(5)
            assertEquals(newName, name)
        }
    }

    @Test
    @Disabled(
        "Can get version property because it's private and doesn't have getter and setter, " +
            "and can't create new entity cause in class description." +
            "Mutiny have the same problem, but idk mutiny GuineaPig don't have version property",
    )
    fun reactiveUpdateVersion(context: VertxTestContext): Unit = runTest {
        val newName = "Tina"
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withSession { s ->
                val pig = checkNotNull(s.find(ReactiveSessionTest.GuineaPig::class.java, 5))
                assertNotEquals(newName, pig.name)
                // assertEquals(0, pig.version)
                pig.name = newName
                // pig.version = 10
                s.flush()
            }
            /*val pig = */
            checkNotNull(
                getCoroutinesSessionFactory().withSession { s ->
                    s.find(ReactiveSessionTest.GuineaPig::class.java, 5)
                },
            )
            // assertEquals(1, pig.version)
        }
    }

    @Test
    fun reactiveQueryWithLock(context: VertxTestContext): Unit = runTest {
        val expectedPig = GuineaPig(5, "Aloi")
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withTransaction { s ->
                val actualPig = s.createSelectionQuery("from GuineaPig pig", GuineaPig::class.java)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult()
                assertThatPigsAreEqual(expectedPig, actualPig)
                assertEquals(LockMode.PESSIMISTIC_WRITE, s.getLockMode(actualPig))
            }
        }
    }

    @Test
    fun reactiveQueryWithAliasedLock(context: VertxTestContext): Unit = runTest {
        val expectedPig = GuineaPig(5, "Aloi")
        test(context) {
            populateDB()
            getCoroutinesSessionFactory().withTransaction { s ->
                val actualPig = s.createSelectionQuery("from GuineaPig pig", GuineaPig::class.java)
                    .setLockMode("pig", LockMode.PESSIMISTIC_WRITE)
                    .getSingleResult()
                assertThatPigsAreEqual(expectedPig, actualPig)
                assertEquals(LockMode.PESSIMISTIC_WRITE, s.getLockMode(actualPig))
            }
        }
    }

    @Test
    fun reactiveMultiQuery(context: VertxTestContext): Unit = runTest {
        val foo = GuineaPig(5, "Foo")
        val bar = GuineaPig(6, "Bar")
        val baz = GuineaPig(7, "Baz")
        val i = AtomicInteger()
        test(context) {
            getCoroutinesSessionFactory().withTransaction { s -> s.persistAll(foo, bar, baz) }
            getCoroutinesSessionFactory().withSession { session ->
                val list = session.createSelectionQuery("from GuineaPig", GuineaPig::class.java)
                    .getResultList()
                    .onEach { pig ->
                        assertNotNull(pig) // Kotlin type is not null, the correct type is nullable?
                        i.andIncrement
                    }
                assertEquals(3, i.get())
                assertEquals(3, list.size)
            }
        }
    }

    @Test
    fun reactiveClose(context: VertxTestContext): Unit = runTest {
        test(context) {
            val session = getCoroutinesSessionFactory().openSession()
            assertTrue(session.isOpen())
            session.close()
            assertFalse(session.isOpen())
        }
    }

    @Test
    fun testFactory(context: VertxTestContext): Unit = runTest {
        test(context) {
            getCoroutinesSessionFactory().withSession { s ->
                // Mutiny side doesn't handle null of properties, is not null?
                s.getFactory().getCache()?.evictAll()
                s.getFactory().getMetamodel()?.entity(GuineaPig::class.java)
                s.getFactory().getCriteriaBuilder().createQuery(GuineaPig::class.java)
                s.getFactory().getStatistics()?.isStatisticsEnabled
            }
        }
    }

    @Test
    fun testMetamodel() {
        val pig = checkNotNull(getCoroutinesSessionFactory().getMetamodel()).entity(GuineaPig::class.java)
        assertNotNull(pig)
        checkNotNull(pig)
        assertEquals(2, pig.attributes.size)
        assertEquals("GuineaPig", pig.name)
    }

    @Test
    fun testTransactionPropagation(context: VertxTestContext): Unit = runTest {
        test(context) {
            getCoroutinesSessionFactory().withTransaction { s, transaction ->
                s.createSelectionQuery("from GuineaPig", GuineaPig::class.java).getResultList()
                assertNotNull(s.currentTransaction())
                // Special condition because a nullable variable don't have smart cast in kotlin for null free
                // Can change this with forceNonNull !!
                assertFalse(s.currentTransaction()?.isMarkedForRollback() == true)
                s.currentTransaction()?.markForRollback()
                assertTrue(s.currentTransaction()?.isMarkedForRollback() == true)
                assertTrue(transaction.isMarkedForRollback())
                // Nested transaction
                // Use withTransaction because not want a nested session, only nested transaction
                s.withTransaction { t ->
                    assertTrue(t.isMarkedForRollback())
                    s.createSelectionQuery("from GuineaPig", GuineaPig::class.java).getResultList()
                }
            }
        }
    }

    @Test
    fun testSessionPropagation(context: VertxTestContext): Unit = runTest {
        test(context) {
            getCoroutinesSessionFactory().withSession { s ->
                assertFalse(s.isDefaultReadOnly())
                s.setDefaultReadOnly(true)
                s.createSelectionQuery("from GuineaPig", GuineaPig::class.java).getResultList()
                getCoroutinesSessionFactory().withSession { s ->
                    assertTrue(s.isDefaultReadOnly())
                    s.createSelectionQuery("from GuineaPig", GuineaPig::class.java).getResultList()
                }
            }
        }
    }

    @Test
    fun testDupeException(context: VertxTestContext): Unit = runTest {
        test(context) {
            getCoroutinesSessionFactory().withTransaction { s -> s.persist(GuineaPig(10, "Tulip")) }
            assertThrows<PersistenceException> {
                getCoroutinesSessionFactory().withTransaction { s -> s.persist(GuineaPig(10, "Tulip")) }
            }
        }
    }

    @Test
    fun testExceptionInWithSession(context: VertxTestContext): Unit = runTest {
        var savedSession: Coroutines.Session? = null
        test(context) {
            assertThrows<Exception> {
                getCoroutinesSessionFactory().withSession { session ->
                    assertTrue(session.isOpen())
                    savedSession = session
                    throw RuntimeException()
                }
            }
            assertFalse(savedSession?.isOpen() ?: true, "Session should be closed")
        }
    }

    @Test
    fun testExceptionInWithTransaction(context: VertxTestContext): Unit = runTest {
        test(context) {
            var savedSession: Coroutines.Session? = null
            assertThrows<Exception> {
                getCoroutinesSessionFactory().withTransaction { session ->
                    assertTrue(session.isOpen())
                    savedSession = session
                    throw RuntimeException("No Panic: This is just a test")
                }
            }

            assertFalse(savedSession?.isOpen() ?: true, "Session should be closed")
        }
    }

    @Test
    fun testExceptionInWithStatelessSession(context: VertxTestContext): Unit = runTest {
        test(context) {
            var savedSession: Coroutines.StatelessSession? = null
            assertThrows<Exception> {
                getCoroutinesSessionFactory().withStatelessSession { session ->
                    assertTrue(session.isOpen())
                    savedSession = session
                    throw RuntimeException("No Panic: This is just a test")
                }
            }

            assertNotNull(savedSession)
            checkNotNull(savedSession)
            assertFalse(savedSession.isOpen(), "Session should be closed")
        }
    }

    @Test
    fun testForceFlushWithDelete(context: VertxTestContext): Unit = runTest {
        val pig1 = GuineaPig(111, "Pig 1")
        val pig2 = GuineaPig(111, "Pig 2")

        test(context) {
            getCoroutinesSessionFactory().withTransaction { session ->
                session.persist(pig1)
                session.remove(pig1)
                session.persist(pig2)
            }

            val found =
                getCoroutinesSessionFactory().withSession {
                    it.find(GuineaPig::class.java, pig2.id)
                }

            assertThatPigsAreEqual(pig2, found)
        }
    }

    @Test
    fun testCurrentSession(context: VertxTestContext): Unit = runTest {
        test(context) {
            getCoroutinesSessionFactory().withSession { session ->
                getCoroutinesSessionFactory().withSession { s ->
                    assertEquals(session, s)
                    val current = getCoroutinesSessionFactory().getCurrentSession()
                    assertNotNull(current)
                    checkNotNull(current)
                    assertTrue(current.isOpen())
                    assertEquals(session, current)
                }
                assertNotNull(getCoroutinesSessionFactory().getCurrentSession())
            }
            assertNull(getCoroutinesSessionFactory().getCurrentSession())
        }
    }

    @Test
    fun testCurrentStatelessSession(context: VertxTestContext): Unit = runTest {
        test(context) {
            getCoroutinesSessionFactory().withStatelessSession { session ->
                getCoroutinesSessionFactory().withStatelessSession { s ->
                    assertEquals(session, s)
                    val current = getCoroutinesSessionFactory().getCurrentStatelessSession()
                    checkNotNull(current)
                    assertNotNull(current)
                    assertTrue(current.isOpen())
                    assertEquals(session, current)
                }
                assertNotNull(getCoroutinesSessionFactory().getCurrentStatelessSession())
            }
            assertNull(getCoroutinesSessionFactory().getCurrentStatelessSession())
        }
    }
}
