/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive

import io.vertx.junit5.VertxTestContext
import jakarta.persistence.LockModeType
import jakarta.persistence.PersistenceException
import kotlinx.coroutines.test.runTest
import org.hibernate.LockMode
import org.hibernate.reactive.CoroutinesTestHelper.test
import org.hibernate.reactive.MutinySessionTest.GuineaPig
import org.hibernate.reactive.coroutines.Coroutines
import org.hibernate.reactive.coroutines.createQuery
import org.hibernate.reactive.coroutines.createSelectionQuery
import org.hibernate.reactive.coroutines.find
import org.hibernate.reactive.coroutines.session
import org.hibernate.reactive.coroutines.transaction
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
import kotlin.time.Duration.Companion.minutes
import org.assertj.core.api.Assertions as AssertjAssertions

// Use GuineaPig of MutinySessionTest because if kotlin class want to have a JPA entity, need more dependencies like plugin jpa
class CoroutinesSessionTest : BaseReactiveTest() {
    private val timeout = 10.minutes

    override fun annotatedEntities(): Collection<Class<*>> = listOf(GuineaPig::class.java)

    private suspend fun populateDB() =
        getCoroutinesSessionFactory().withSession { session ->
            session.persist(GuineaPig(5, "Aloi"))
            session.flush()
        }

    private suspend fun selectNameFromId(id: Int) =
        getCoroutinesSessionFactory().withSession { session ->
            val rowSet =
                session
                    .createQuery<String>( // Don't use the overload deprecated
                        "SELECT name FROM GuineaPig WHERE ID = $id",
                    ).getResultList()
            when (val size = rowSet.size) {
                0 -> null
                1 -> rowSet.first()
                else -> throw AssertionError("More than one result returned $size")
            }
        }

    private fun assertThatPigsAreEqual(
        expected: GuineaPig,
        actual: GuineaPig?,
    ) {
        assertNotNull(actual)
        checkNotNull(actual) // Use kotlin safe assertion to get smartcast
        assertEquals(expected.id, actual.id)
        assertEquals(expected.name, actual.name)
    }

    @Test
    fun reactiveFindMultipleIds(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val rump = GuineaPig(55, "Rumpelstiltskin")
            val emma = GuineaPig(77, "Emma")
            test(context) {
                getCoroutinesSessionFactory().withTransaction { session ->
                    session.persistAll(emma, rump)
                }
                val pigs =
                    getCoroutinesSessionFactory().withTransaction { session ->
                        session.find<GuineaPig>(emma.id, rump.id)
                    }
                AssertjAssertions.assertThat(pigs).containsExactlyInAnyOrder(emma, rump)
            }
        }

    @Test
    fun sessionClear(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val guineaPig = GuineaPig(81, "Perry")
            test(context) {
                getCoroutinesSessionFactory().withSession { session ->
                    session.persist(guineaPig)
                    session.clear()
                    // If the previous clear doesn't work, this will cause a duplicated entity exception
                    session.persist(guineaPig)
                    session.flush()
                    val result = session.createSelectionQuery<GuineaPig>("FROM GuineaPig").getSingleResult()
                    // By not using .find() we check that there is only one entity in the db with getSingleResult()
                    assertThatPigsAreEqual(guineaPig, result)
                }
            }
        }

    @Test
    fun reactiveWithTransactionStatelessSession(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val guineaPig = GuineaPig(61, "Mr. Peanutbutter")
            test(context) {
                getCoroutinesSessionFactory().withStatelessTransaction { session ->
                    session.insert(guineaPig)
                }
                val result =
                    getCoroutinesSessionFactory().withSession { session ->
                        session.find<GuineaPig>(guineaPig.id)
                    }
                assertThatPigsAreEqual(guineaPig, result)
            }
        }

    @Test
    fun reactiveWithTransactionSession(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val guineaPig = GuineaPig(61, "Mr. Peanutbutter")
            test(context) {
                // Use DSL syntax
                getCoroutinesSessionFactory().transaction { persist(guineaPig) }
                val result = getCoroutinesSessionFactory().session { find<GuineaPig>(guineaPig.id) }
                assertThatPigsAreEqual(guineaPig, result)
            }
        }

    @Test
    fun reactiveFind(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val expectedPig = GuineaPig(5, "Aloi")
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().session {
                    val actualPig = find<GuineaPig>(expectedPig.id)
                    assertThatPigsAreEqual(expectedPig, actualPig)
                    // this is Session
                    assertTrue(actualPig in this) // Use contains operator style
                    assertFalse(expectedPig in this)
                    assertEquals(LockMode.READ, getLockMode(actualPig))
                    detach(actualPig)
                    assertFalse(actualPig in this)
                }
            }
        }

    @Test
    fun reactiveFindWithLock(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val expectedPig = GuineaPig(5, "Aloi")
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().session {
                    val actualPig = find<GuineaPig>(expectedPig.id, LockMode.PESSIMISTIC_WRITE)
                    assertThatPigsAreEqual(expectedPig, actualPig)
                    assertEquals(LockMode.PESSIMISTIC_WRITE, getLockMode(actualPig))
                }
            }
        }

    @Test
    fun reactiveFindRefreshWithLock(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val expectedPig = GuineaPig(5, "Aloi")
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().session {
                    val pig: GuineaPig? = find(expectedPig.id)
                    refresh(pig, LockMode.PESSIMISTIC_WRITE)
                    assertThatPigsAreEqual(expectedPig, pig)
                    assertEquals(LockMode.PESSIMISTIC_WRITE, getLockMode(pig))
                }
            }
        }

    @Test
    fun reactiveFindReadOnlyRefreshWithLock(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val expectedPig = GuineaPig(5, "Aloi")
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().session {
                    val pig = find<GuineaPig>(expectedPig.id)
                    checkNotNull(pig) // Check because java code assume it's not null
                    setReadOnly(pig, true)
                    pig.name = "XXXX"
                    flush()
                    refresh(pig)
                    assertEquals(expectedPig.name, pig.name)
                    assertTrue(isReadOnly(pig))
                }
                getCoroutinesSessionFactory().session {
                    val pig = find<GuineaPig>(expectedPig.id)
                    checkNotNull(pig)
                    setReadOnly(pig, false)
                    pig.name = "XXXX"
                    flush()
                    refresh(pig)
                    assertEquals("XXXX", pig.name)
                    assertFalse(isReadOnly(pig))
                }
            }
        }

    @Test
    fun reactiveFindThenUpgradeLock(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val expectedPig = GuineaPig(5, "Aloi")
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().session {
                    val pig = find<GuineaPig>(expectedPig.id)
                    lock(pig, LockMode.PESSIMISTIC_READ)
                    assertThatPigsAreEqual(expectedPig, pig)
                    assertEquals(LockMode.PESSIMISTIC_READ, getLockMode(pig))
                }
            }
        }

    @Test
    fun reactiveFindThenWriteLock(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val expectedPig = GuineaPig(5, "Aloi")
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().session {
                    val actualPig = find<GuineaPig>(expectedPig.id)
                    lock(actualPig, LockMode.PESSIMISTIC_WRITE)
                    assertThatPigsAreEqual(expectedPig, actualPig)
                    assertEquals(LockMode.PESSIMISTIC_WRITE, getLockMode(actualPig))
                }
            }
        }

    @Test
    fun reactivePersist(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                getCoroutinesSessionFactory().session {
                    persist(GuineaPig(10, "Tulip"))
                    flush()
                }
                val selectRes = selectNameFromId(10)
                assertEquals("Tulip", selectRes)
            }
        }

    @Test
    fun reactivePersistInTx(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                getCoroutinesSessionFactory().transaction { persist(GuineaPig(10, "Tulip")) }
                val selectRes = selectNameFromId(10)
                assertEquals("Tulip", selectRes)
            }
        }

    @Test
    fun reactiveRollbackTx(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                assertThrows<Exception> {
                    getCoroutinesSessionFactory().transaction {
                        persist(ReactiveSessionTest.GuineaPig(10, "Tulip"))
                        flush()
                        throw RuntimeException()
                    }
                }

                assertNull(selectNameFromId(10))
            }
        }

    @Test
    fun reactiveMarkedRollbackTx(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                getCoroutinesSessionFactory().transaction { t ->
                    persist(GuineaPig(10, "Tulip"))
                    flush()
                    t.markForRollback()
                }
                assertNull(selectNameFromId(10))
            }
        }

    @Test
    fun reactiveRemoveTransientEntity(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                populateDB()
                assertNotNull(selectNameFromId(5))
                assertThrows<Exception> {
                    getCoroutinesSessionFactory().withSession { session ->
                        session.remove(GuineaPig(5, "Aloi"))
                        session.flush()
                        session.close()
                        fail("Expected exception when removing transient entity")
                    }
                }
                assertNotNull(selectNameFromId(5))
            }
        }

    @Test
    fun reactiveRemoveManagedEntity(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().session {
                    val pig = find<GuineaPig>(5)
                    remove(pig)
                    flush()
                }
                assertNull(selectNameFromId(5))
            }
        }

    @Test
    fun reactiveRemoveManagedEntityWithTx(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().transaction {
                    val pig = find<GuineaPig>(5)
                    remove(pig)
                }
                assertNull(selectNameFromId(5))
            }
        }

    @Test
    fun reactiveUpdate(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val newName = "Tina"
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().session {
                    val pig = checkNotNull(find<GuineaPig>(5))
                    assertNotEquals(pig.name, newName)
                    pig.name = newName // Use kotlin property syntax
                    flush()
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
    fun reactiveUpdateVersion(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val newName = "Tina"
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().session {
                    val pig = checkNotNull(find<ReactiveSessionTest.GuineaPig>(5))
                    assertNotEquals(newName, pig.name)
                    // assertEquals(0, pig.version)
                    pig.name = newName
                    // pig.version = 10
                    flush()
                }
                val pig = checkNotNull(getCoroutinesSessionFactory().session { find<ReactiveSessionTest.GuineaPig>(5) })
                // assertEquals(1, pig.version)
            }
        }

    @Test
    fun reactiveQueryWithLock(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val expectedPig = GuineaPig(5, "Aloi")
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().transaction {
                    val actualPig =
                        createSelectionQuery("from GuineaPig pig", GuineaPig::class.java)
                            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                            .getSingleResult()
                    assertThatPigsAreEqual(expectedPig, actualPig)
                    assertEquals(LockMode.PESSIMISTIC_WRITE, getLockMode(actualPig))
                }
            }
        }

    @Test
    fun reactiveQueryWithAliasedLock(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val expectedPig = GuineaPig(5, "Aloi")
            test(context) {
                populateDB()
                getCoroutinesSessionFactory().transaction {
                    val actualPig =
                        createSelectionQuery<GuineaPig>("from GuineaPig pig")
                            .setLockMode("pig", LockMode.PESSIMISTIC_WRITE)
                            .getSingleResult()
                    assertThatPigsAreEqual(expectedPig, actualPig)
                    assertEquals(LockMode.PESSIMISTIC_WRITE, getLockMode(actualPig))
                }
            }
        }

    @Test
    fun reactiveMultiQuery(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val foo = GuineaPig(5, "Foo")
            val bar = GuineaPig(6, "Bar")
            val baz = GuineaPig(7, "Baz")
            val i = AtomicInteger()
            test(context) {
                getCoroutinesSessionFactory().transaction { persistAll(foo, bar, baz) }
                getCoroutinesSessionFactory().session {
                    val list =
                        createSelectionQuery<GuineaPig>("from GuineaPig")
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
    fun reactiveClose(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                val session = getCoroutinesSessionFactory().openSession()
                assertTrue(session.isOpen())
                session.close()
                assertFalse(session.isOpen())
            }
        }

    @Test
    fun testFactory(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                getCoroutinesSessionFactory().session {
                    // Mutiny side doesn't handle null of properties, is not null?
                    getFactory().getCache()?.evictAll()
                    getFactory().getMetamodel()?.entity(GuineaPig::class.java)
                    getFactory().getCriteriaBuilder().createQuery(GuineaPig::class.java)
                    getFactory().getStatistics()?.isStatisticsEnabled
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
    fun testTransactionPropagation(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                getCoroutinesSessionFactory().transaction {
                    createSelectionQuery<GuineaPig>("from GuineaPig").getResultList()
                    assertNotNull(currentTransaction())
                    // Special condition because a nullable variable don't have smart cast in kotlin for null free
                    // Can change this with forceNonNull !!
                    assertFalse(currentTransaction()?.isMarkedForRollback() == true)
                    currentTransaction()?.markForRollback()
                    assertTrue(currentTransaction()?.isMarkedForRollback() == true)
                    assertTrue(it.isMarkedForRollback())
                    // Nested transaction
                    // Use withTransaction because not want a nested session, only nested transaction
                    withTransaction { t ->
                        assertTrue(t.isMarkedForRollback())
                        createSelectionQuery<GuineaPig>("from GuineaPig").getResultList()
                    }
                }
            }
        }

    @Test
    @Disabled("need debug for nested propagation")
    fun testSessionPropagation(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                getCoroutinesSessionFactory().session {
                    assertFalse(isDefaultReadOnly())
                    setDefaultReadOnly(true)
                    createSelectionQuery<GuineaPig>("from GuineaPig").getResultList()
                    getCoroutinesSessionFactory().withSession { s ->
                        assertTrue(s.isDefaultReadOnly())
                        s.createSelectionQuery<GuineaPig>("from GuineaPig").getResultList()
                    }
                }
            }
        }

    @Test
    fun testDupeException(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                getCoroutinesSessionFactory().transaction { persist(GuineaPig(10, "Tulip")) }
                assertThrows<PersistenceException> {
                    getCoroutinesSessionFactory().transaction { persist(GuineaPig(10, "Tulip")) }
                }
            }
        }

    @Test
    fun testExceptionInWithSession(context: VertxTestContext) =
        runTest(timeout = timeout) {
            var savedSession: Coroutines.Session? = null
            test(context) {
                assertThrows<Exception> {
                    getCoroutinesSessionFactory().session {
                        assertTrue(isOpen())
                        savedSession = this
                        throw RuntimeException()
                    }
                }
                assertFalse(savedSession?.isOpen() ?: true, "Session should be closed")
            }
        }

    @Test
    fun testExceptionInWithTransaction(context: VertxTestContext) =
        runTest(timeout = timeout) {
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
    fun testExceptionInWithStatelessSession(context: VertxTestContext) =
        runTest(timeout = timeout) {
            test(context) {
                var savedSession: Coroutines.StatelessSession? = null
                assertThrows<Exception> {
                    getCoroutinesSessionFactory().withStatelessSession { session ->
                        assertTrue(session.isOpen())
                        savedSession = session
                        throw RuntimeException("No Panic: This is just a test")
                    }
                }

                assertFalse(savedSession?.isOpen() ?: true, "Session should be closed")
            }
        }

    @Test
    fun testForceFlushWithDelete(context: VertxTestContext) =
        runTest(timeout = timeout) {
            val pig1 = GuineaPig(111, "Pig 1")
            val pig2 = GuineaPig(111, "Pig 2")

            test(context) {
                getCoroutinesSessionFactory().transaction {
                    persist(pig1)
                    remove(pig1)
                    persist(pig2)
                }

                val found =
                    getCoroutinesSessionFactory().withSession {
                        it.find(GuineaPig::class.java, pig2.id)
                    }

                assertThatPigsAreEqual(pig2, found)
            }
        }

    @Test
    @Disabled("need debug for nested session")
    fun testCurrentSession(context: VertxTestContext) =
        runTest(timeout = timeout) {
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
    @Disabled("The problem is how context is getting outside context thread")
    fun testCurrentStatelessSession(context: VertxTestContext) =
        runTest(timeout = timeout) {
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
