/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

package org.hibernate.reactive.coroutines

inline fun <reified T> Coroutines.QueryProducer.createSelectionQuery(queryString: String) = createSelectionQuery(queryString, T::class.java)

inline fun <reified T> Coroutines.QueryProducer.createQuery(queryName: String) = createQuery(queryName, T::class.java)

inline fun <reified T> Coroutines.QueryProducer.createNamedQuery(queryName: String) = createNamedQuery(queryName, T::class.java)

inline fun <reified T> Coroutines.QueryProducer.createNativeQuery(queryName: String) = createNativeQuery(queryName, T::class.java)

suspend inline fun <reified T> Coroutines.Session.find(id: Any?) = find(T::class.java, id)

suspend inline fun <reified T> Coroutines.Session.find(vararg ids: Any?) = find(T::class.java, *ids)

suspend inline operator fun <reified T> Coroutines.StatelessSession.get(id: Any?) = get(T::class.java, id)

suspend inline fun <reified T> Coroutines.StatelessSession.get(vararg ids: Any?) = get(T::class.java, ids)

// -- Iterable overloads --
suspend inline fun <reified T> Coroutines.Session.find(ids: Iterable<Any>) = find(T::class.java, *ids.toList().toTypedArray())
