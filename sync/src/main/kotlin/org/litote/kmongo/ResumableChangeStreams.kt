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

import com.mongodb.MongoCommandException
import com.mongodb.MongoException
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.kotlin.client.ChangeStreamIterable
import com.mongodb.kotlin.client.MongoChangeStreamCursor
import com.mongodb.kotlin.client.MongoCollection
import org.bson.BsonDocument
import org.bson.conversions.Bson
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** The period of the checks of the closing of the listening while waiting before a retry. */
private val CLOSED_CHECK_PERIOD = 100.milliseconds

/** `ChangeStreamHistoryLost` & `ChangeStreamFatalError` server error codes: the resume token can not be used. */
private val NON_RESUMABLE_TOKEN_ERRORS = setOf(286, 280)

/**
 * Listens the changes of the collection indefinitely, in a (daemon) thread:
 * a change stream that survives the errors and the invalidations.
 *
 * The official driver already resumes the change stream once after a resumable error. This listener goes further:
 * - after any [MongoException] (server down, election, network...) the change stream is reopened after [retryDelay]
 * (doubled after each consecutive error, up to [maxRetryDelay]), starting after the last received change
 * (the resume token is kept) - no change is lost if the oplog still contains it
 * - if the resume token is not usable any more (history lost), the change stream restarts from the current time
 * - if the change stream is invalidated (the collection is dropped or renamed), it is reopened
 * - the exceptions thrown by the [listener] are sent to [onError], the listening continues
 * - a change that can not be decoded is skipped: the exception is sent to [onError], the listening continues
 *
 * Sample:
 * ```
 * val listening = col.listenIndefinitely { change -> reload(change.fullDocument) }
 * // ...
 * listening.close()
 * ```
 *
 * @param pipeline the aggregation pipeline used to filter the changes
 * @param fullDocument the full document option ([FullDocument.UPDATE_LOOKUP] to get the document of the updates)
 * @param retryDelay the delay before reopening the change stream after an error
 * @param maxRetryDelay the maximum delay before reopening the change stream after consecutive errors
 * @param onError called for each error (for logging purposes)
 * @param configure configures the change stream (batch size, collation, `fullDocumentBeforeChange`...)
 * @param listener to listen changes
 * @return an [AutoCloseable] that stops the listening
 */
@Suppress("LongParameterList")
public fun <T : Any> MongoCollection<T>.listenIndefinitely(
    pipeline: List<Bson> = emptyList(),
    fullDocument: FullDocument = FullDocument.DEFAULT,
    retryDelay: Duration = 1.seconds,
    maxRetryDelay: Duration = 1.minutes,
    onError: (Throwable) -> Unit = {},
    configure: ChangeStreamIterable<T>.() -> ChangeStreamIterable<T> = { this },
    listener: (ChangeStreamDocument<T>) -> Unit
): AutoCloseable {
    val listening = ResumableListening(this, retryDelay, maxRetryDelay, onError, listener) {
        watch(pipeline).fullDocument(fullDocument).configure()
    }
    thread(isDaemon = true, name = "kmongo-change-stream-listener-${namespace.fullName}") { listening.run() }
    return listening
}

/**
 * The state of a [listenIndefinitely] listening.
 */
private class ResumableListening<T : Any>(
    private val collection: MongoCollection<T>,
    private val retryDelay: Duration,
    private val maxRetryDelay: Duration,
    private val onError: (Throwable) -> Unit,
    private val listener: (ChangeStreamDocument<T>) -> Unit,
    private val openChangeStream: MongoCollection<T>.() -> ChangeStreamIterable<T>,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val currentCursor = AtomicReference<MongoChangeStreamCursor<ChangeStreamDocument<T>>?>()
    private var resumeToken: BsonDocument? = null
    private var currentDelay = retryDelay

    fun run() {
        while (!closed.get()) {
            try {
                listenUntilInvalidated()
            } catch (e: MongoException) {
                if (closed.get()) return
                if (e is MongoCommandException && e.errorCode in NON_RESUMABLE_TOKEN_ERRORS) resumeToken = null
                onError(e)
                sleep(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(maxRetryDelay)
            } catch (e: IllegalStateException) {
                // the cursor has been closed by close()
                if (!closed.get()) throw e
            }
        }
    }

    /** Listens the change stream until it is closed (invalidated): it is then reopened after the invalidate event. */
    private fun listenUntilInvalidated() {
        val iterable = collection.openChangeStream().let { iterable -> resumeToken?.let { iterable.startAfter(it) } ?: iterable }
        iterable.cursor().use { cursor ->
            currentCursor.set(cursor)
            while (!closed.get() && cursor.hasNext()) {
                val change = nextChange(cursor) ?: continue
                resumeToken = change.resumeToken
                currentDelay = retryDelay
                @Suppress("TooGenericExceptionCaught")
                try {
                    listener(change)
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }
    }

    /** Returns the next change, or null if it can not be decoded: the driver has already moved the cursor after it. */
    private fun nextChange(cursor: MongoChangeStreamCursor<ChangeStreamDocument<T>>): ChangeStreamDocument<T>? =
        try {
            cursor.next()
        } catch (e: MongoException) {
            throw e
        } catch (e: RuntimeException) {
            // the cursor closed by close() is also reported by an exception
            if (!closed.get()) {
                resumeToken = cursor.resumeToken
                currentDelay = retryDelay
                onError(e)
            }
            null
        }

    /** Sleeps [duration], or less if the listening is closed. */
    private fun sleep(duration: Duration) {
        val end = System.nanoTime() + duration.inWholeNanoseconds
        while (!closed.get() && System.nanoTime() < end) {
            Thread.sleep((end - System.nanoTime()).nanoseconds.coerceIn(1.milliseconds, CLOSED_CHECK_PERIOD).inWholeMilliseconds)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            currentCursor.get()?.close()
        }
    }
}
