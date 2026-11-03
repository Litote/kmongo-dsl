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

import com.mongodb.MongoCommandException
import com.mongodb.MongoException
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.kotlin.client.coroutine.ChangeStreamFlow
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import org.bson.BsonDocument
import org.bson.RawBsonDocument
import org.bson.conversions.Bson
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** `ChangeStreamHistoryLost` & `ChangeStreamFatalError` server error codes: the resume token can not be used. */
private val NON_RESUMABLE_TOKEN_ERRORS = setOf(286, 280)

/**
 * Watches the collection indefinitely: a change stream that survives the errors and the invalidations.
 *
 * The official driver already resumes the change stream once after a resumable error. This flow goes further:
 * - after any [MongoException] (server down, election, network...) the change stream is reopened after [retryDelay]
 * (doubled after each consecutive error, up to [maxRetryDelay]), starting after the last received change
 * (the resume token is kept) - no change is lost if the oplog still contains it
 * - if the resume token is not usable any more (history lost), the change stream restarts from the current time
 * - if the change stream is invalidated (the collection is dropped or renamed), it is reopened
 * - a change that can not be decoded is skipped: the exception is sent to [onError], the flow continues
 *
 * The exceptions thrown by the collector are not caught (the flow is cancelled).
 * Cancel the collecting coroutine to stop watching.
 *
 * Sample:
 * ```
 * scope.launch { col.watchIndefinitely().collect { change -> reload(change.fullDocument) } }
 * ```
 *
 * @param pipeline the aggregation pipeline used to filter the changes
 * @param fullDocument the full document option ([FullDocument.UPDATE_LOOKUP] to get the document of the updates)
 * @param retryDelay the delay before reopening the change stream after an error
 * @param maxRetryDelay the maximum delay before reopening the change stream after consecutive errors
 * @param onError called for each error, before the retry (for logging purposes)
 * @param configure configures the change stream (batch size, collation, `fullDocumentBeforeChange`...)
 */
public fun <T : Any> MongoCollection<T>.watchIndefinitely(
    pipeline: List<Bson> = emptyList(),
    fullDocument: FullDocument = FullDocument.DEFAULT,
    retryDelay: Duration = 1.seconds,
    maxRetryDelay: Duration = 1.minutes,
    onError: suspend (Throwable) -> Unit = {},
    configure: ChangeStreamFlow<T>.() -> ChangeStreamFlow<T> = { this },
): Flow<ChangeStreamDocument<T>> = flow {
    // the state belongs to this collection of the flow
    val watch = ResumableWatch(this@watchIndefinitely, retryDelay, maxRetryDelay, onError) {
        watch(pipeline).fullDocument(fullDocument).configure()
    }
    emitAll(watch.changes())
}

/**
 * The state of a [watchIndefinitely] flow.
 */
private class ResumableWatch<T : Any>(
    private val collection: MongoCollection<T>,
    private val retryDelay: Duration,
    private val maxRetryDelay: Duration,
    private val onError: suspend (Throwable) -> Unit,
    private val openChangeStream: MongoCollection<T>.() -> ChangeStreamFlow<T>,
) {
    private val codec = ChangeStreamDocument.createCodec(collection.documentClass, collection.codecRegistry)
    private var resumeToken: BsonDocument? = null
    private var currentDelay = retryDelay

    fun changes(): Flow<ChangeStreamDocument<T>> = flow {
        while (true) {
            val stream = collection.openChangeStream()
                .let { stream -> resumeToken?.let { stream.startAfter(it) } ?: stream }
            // decoded here: the driver decodes the batches, a failure would hide the resume token of the change
            stream.withDocumentClass(RawBsonDocument::class.java).collect { rawChange ->
                resumeToken = rawChange.getDocument("_id")
                currentDelay = retryDelay
                decodeOrNull(rawChange)?.let { emit(it) }
            }
            // the change stream is closed (invalidated): reopened after the invalidate event
        }
    }.retryWhen { cause, _ -> cause is MongoException && retryAfter(cause) }

    private suspend fun decodeOrNull(rawChange: RawBsonDocument): ChangeStreamDocument<T>? =
        try {
            rawChange.decode(codec)
        } catch (e: RuntimeException) {
            onError(e)
            null
        }

    private suspend fun retryAfter(cause: MongoException): Boolean {
        if (cause is MongoCommandException && cause.errorCode in NON_RESUMABLE_TOKEN_ERRORS) resumeToken = null
        onError(cause)
        delay(currentDelay)
        currentDelay = (currentDelay * 2).coerceAtMost(maxRetryDelay).coerceAtLeast(1.milliseconds)
        return true
    }
}
