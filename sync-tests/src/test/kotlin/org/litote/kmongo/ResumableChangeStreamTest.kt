/*
 * Copyright (C) 2016/2026 Litote
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.litote.kmongo

import com.mongodb.MongoException
import org.bson.Document
import org.junit.jupiter.api.Tag
import org.litote.kmongo.model.Friend
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests [listenIndefinitely].
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class ResumableChangeStreamTest : AllCategoriesKMongoBaseTest<Friend>() {

    private val friends = ArrayBlockingQueue<String>(10)

    private fun next(): String? = friends.poll(30, TimeUnit.SECONDS)

    /** Inserts friends until the listener receives one (the change stream is opened asynchronously). */
    private fun awaitListening() {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            col.insertOne(Friend("ping"))
            if (friends.poll(200, TimeUnit.MILLISECONDS) == "ping") break
        }
        Thread.sleep(200)
        friends.clear()
    }

    @Test
    fun `listenIndefinitely survives the invalidation of the change stream`() {
        val listening = col.listenIndefinitely(retryDelay = 10.milliseconds) { change ->
            change.fullDocument?.name?.let { friends.add(it) }
        }
        try {
            awaitListening()
            col.insertOne(Friend("Fred"))
            assertEquals("Fred", next())
            // invalidate event: the change stream is closed by the server
            col.drop()
            col.insertOne(Friend("Ivan"))
            assertEquals("Ivan", next())
        } finally {
            listening.close()
        }
        assertListenerThreadStops()
    }

    @Test
    fun `listenIndefinitely retries after an error and the listener errors do not stop the listening`() {
        val opens = AtomicInteger()
        val errors = CopyOnWriteArrayList<Throwable>()
        val listening = col.listenIndefinitely(
            retryDelay = 10.milliseconds,
            onError = { errors += it },
            configure = { if (opens.incrementAndGet() == 1) throw MongoException("first open fails") else this }
        ) { change ->
            val name = change.fullDocument?.name
            if (name == "Boom") error("listener failure")
            name?.let { friends.add(it) }
        }
        try {
            awaitListening()
            col.insertOne(Friend("Boom"))
            col.insertOne(Friend("Lea"))
            assertEquals("Lea", next())
            assertTrue(errors.any { it is MongoException && it.message == "first open fails" })
            assertTrue(errors.any { it is IllegalStateException && it.message == "listener failure" })
        } finally {
            listening.close()
        }
        assertListenerThreadStops()
        // no change is received after close
        col.insertOne(Friend("Bob"))
        assertNull(friends.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `listenIndefinitely skips the documents that can not be decoded`() {
        val errors = CopyOnWriteArrayList<Throwable>()
        val listening = col.listenIndefinitely(retryDelay = 10.milliseconds, onError = { errors += it }) { change ->
            change.fullDocument?.name?.let { friends.add(it) }
        }
        try {
            awaitListening()
            col.withDocumentClass<Document>().insertOne(Document("name", Document("not", "a string")))
            col.insertOne(Friend("Fred"))
            assertEquals("Fred", next())
            assertEquals(1, errors.size)
            // the skipped document is not received again when the change stream is reopened
            col.drop()
            col.insertOne(Friend("Ivan"))
            assertEquals("Ivan", next())
            assertEquals(1, errors.size)
        } finally {
            listening.close()
        }
        assertListenerThreadStops()
    }

    private fun assertListenerThreadStops() {
        val deadline = System.currentTimeMillis() + 10_000
        while (listenerThreadAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertFalse(listenerThreadAlive())
    }

    private fun listenerThreadAlive(): Boolean =
        Thread.getAllStackTraces().keys.any { it.name == "kmongo-change-stream-listener-${col.namespace.fullName}" && it.isAlive }
}
