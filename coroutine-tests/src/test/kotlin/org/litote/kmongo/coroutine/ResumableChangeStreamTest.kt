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

package org.litote.kmongo.coroutine

import com.mongodb.MongoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.bson.Document
import org.junit.jupiter.api.Tag
import org.litote.kmongo.model.Friend
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests [watchIndefinitely].
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class ResumableChangeStreamTest : KMongoReactiveStreamsCoroutineBaseTest<Friend>() {

    @Test
    fun `watchIndefinitely survives errors and invalidations`() = runBlocking {
        val names = Channel<String>(Channel.UNLIMITED)
        val errors = CopyOnWriteArrayList<Throwable>()
        var opens = 0
        val job = launch(Dispatchers.IO) {
            col.watchIndefinitely(
                retryDelay = 10.milliseconds,
                onError = { errors += it },
                configure = { if (++opens == 1) throw MongoException("first open fails") else this }
            ).collect { change -> change.fullDocument?.name?.let { names.send(it) } }
        }
        try {
            // the change stream is opened asynchronously
            withTimeout(30.seconds) {
                while (true) {
                    col.insertOne(Friend("ping"))
                    if (withTimeoutOrNull(200.milliseconds) { names.receive() } == "ping") break
                }
            }
            kotlinx.coroutines.delay(200)
            while (names.tryReceive().isSuccess) Unit

            col.insertOne(Friend("Fred"))
            assertEquals("Fred", withTimeout(30.seconds) { names.receive() })
            assertTrue(errors.any { it.message == "first open fails" })

            // invalidate event: the change stream is closed by the server, then reopened
            col.drop()
            col.insertOne(Friend("Ivan"))
            assertEquals("Ivan", withTimeout(30.seconds) { names.receive() })
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `watchIndefinitely skips the documents that can not be decoded`() = runBlocking {
        val names = Channel<String>(Channel.UNLIMITED)
        val errors = CopyOnWriteArrayList<Throwable>()
        val job = launch(Dispatchers.IO) {
            col.watchIndefinitely(retryDelay = 10.milliseconds, onError = { errors += it })
                .collect { change -> change.fullDocument?.name?.let { names.send(it) } }
        }
        try {
            // the change stream is opened asynchronously
            withTimeout(30.seconds) {
                while (true) {
                    col.insertOne(Friend("ping"))
                    if (withTimeoutOrNull(200.milliseconds) { names.receive() } == "ping") break
                }
            }
            kotlinx.coroutines.delay(200)
            while (names.tryReceive().isSuccess) Unit

            col.withDocumentClass<Document>().insertOne(Document("name", Document("not", "a string")))
            col.insertOne(Friend("Fred"))
            assertEquals("Fred", withTimeout(30.seconds) { names.receive() })
            assertEquals(1, errors.size)

            // the skipped document is not received again when the change stream is reopened
            col.drop()
            col.insertOne(Friend("Ivan"))
            assertEquals("Ivan", withTimeout(30.seconds) { names.receive() })
            assertEquals(1, errors.size)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `watchIndefinitely does not catch the collector exceptions`() = runBlocking {
        val failure = runCatching {
            withTimeout(30.seconds) {
                launch(Dispatchers.IO) {
                    while (true) {
                        col.insertOne(Friend("Joe"))
                        kotlinx.coroutines.delay(100)
                    }
                }.let { inserter ->
                    try {
                        col.watchIndefinitely(retryDelay = 10.milliseconds).collect { error("collector failure") }
                    } finally {
                        inserter.cancel()
                    }
                }
            }
        }.exceptionOrNull()
        assertEquals("collector failure", failure?.message)
    }
}
