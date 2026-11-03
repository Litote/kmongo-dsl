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

@file:Suppress("DEPRECATION")

package org.litote.kmongo.coroutine

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.litote.kmongo.EMPTY_BSON
import org.litote.kmongo.eq
import org.litote.kmongo.model.Friend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Tests the KMongo 4/5 coroutine API compatibility layer ([KMongo4Compatibility.kt]).
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class KMongo4CompatibilityTest : KMongoReactiveStreamsCoroutineBaseTest<Friend>() {

    @Test
    fun `typealiases and coroutine extensions are the official driver classes`() = runBlocking {
        val client: CoroutineClient = mongoClient.coroutine
        val database: CoroutineDatabase = client.getDatabase(database.name).coroutine
        val collection: CoroutineCollection<Friend> = database.getCollection<Friend>(col.namespace.collectionName).coroutine
        assertSame(mongoClient, client)
        collection.insertOne(Friend("Joe"))
        assertEquals(1, col.countDocuments())
    }

    @Test
    fun `toList and toFlow return all the results`() = runBlocking {
        col.insertMany(listOf(Friend("Joe"), Friend("Bob")))
        val publisher: CoroutineFindPublisher<Friend> = col.find()
        assertEquals(2, publisher.toList().size)
        assertEquals(2, col.find().toFlow().let { flow -> flow.collectCount() })
        assertEquals(2, col.aggregate<Friend>(org.litote.kmongo.match(org.litote.kmongo.EMPTY_BSON)).toList().size)
        assertEquals(listOf("Bob", "Joe"), col.distinct(Friend::name).toList().sorted())
        assertEquals(1, col.listIndexes<org.bson.Document>().toList().size)
    }

    @Test
    fun `an explicit import of kotlinx first takes precedence over the deprecated KMongo 4 first`() = runBlocking {
        // kotlinx.coroutines.flow.first is explicitly imported: the throwing variant is used
        assertFailsWith<NoSuchElementException> { col.find(Friend::name eq "Joe").first() }
        col.insertOne(Friend("Joe"))
        assertEquals("Joe", col.find(Friend::name eq "Joe").first().name)
    }

    @Test
    fun `findAndCast and consumeEach`() = runBlocking {
        col.insertOne(Friend("Joe"))
        val documents = mutableListOf<org.bson.Document>()
        col.findAndCast<org.bson.Document>(Friend::name eq "Joe").consumeEach { documents += it }
        assertEquals("Joe", documents.single()["name"])
        mongoClient.startSession().use { session ->
            assertEquals(1, col.findAndCast<org.bson.Document>(session, EMPTY_BSON).toList().size)
        }
        val collections: CoroutineListCollectionsPublisher<org.bson.Document> = database.listCollections()
        assertEquals(true, collections.toList().isNotEmpty())
    }

    @Test
    fun `commitTransactionAndAwait commits the transaction`() = runBlocking {
        col.insertOne(Friend("Joe"))
        mongoClient.startSession().use { session ->
            session.startTransaction()
            col.insertOne(session, Friend("Bob"))
            session.commitTransactionAndAwait()
        }
        assertEquals(2, col.countDocuments())
    }

    @Test
    fun `abortTransactionAndAwait aborts the transaction`() = runBlocking {
        col.insertOne(Friend("Joe"))
        mongoClient.startSession().use { session ->
            session.startTransaction()
            col.insertOne(session, Friend("Bob"))
            session.abortTransactionAndAwait()
        }
        assertEquals(1, col.countDocuments())
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.collectCount(): Int {
        var count = 0
        collect { count++ }
        return count
    }
}
