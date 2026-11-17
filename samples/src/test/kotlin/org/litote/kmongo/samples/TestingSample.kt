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

package org.litote.kmongo.samples

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.litote.kmongo.getCollection
import org.litote.kmongo.lt
import org.litote.kmongo.testcontainers.KMongoTestServer
import org.litote.kmongo.toList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.litote.kmongo.KMongo as SyncKMongo
import org.litote.kmongo.coroutine.KMongo as CoroutineKMongo
import org.litote.kmongo.coroutine.getCollection as coroutineGetCollection

data class TestingJedi(val name: String, val age: Int)

/**
 * The code snippets of the Testing documentation page (docs/testing.md).
 */
@Tag("jackson")
class TestingSample {

    private val client = SyncKMongo.createClient(KMongoTestServer.connectionString)
    private val database = client.getDatabase(KMongoTestServer.newDatabaseName())
    private val col = database.getCollection<TestingJedi>()

    @AfterTest
    fun tearDown() {
        database.drop()
        client.close()
    }

    @Test
    fun `sync - find the young jedis`() {
        col.insertMany(listOf(TestingJedi("Luke Skywalker", 19), TestingJedi("Yoda", 896)))

        val young = col.find(TestingJedi::age lt 100).toList()

        assertEquals(listOf("Luke Skywalker"), young.map { it.name })
    }

    @Test
    fun `coroutine - find the young jedis`() = runBlocking {
        val coroutineClient = CoroutineKMongo.createClient(KMongoTestServer.connectionString)
        val coroutineCol = coroutineClient.getDatabase(KMongoTestServer.newDatabaseName())
            .coroutineGetCollection<TestingJedi>()

        coroutineCol.insertMany(listOf(TestingJedi("Luke Skywalker", 19), TestingJedi("Yoda", 896)))

        assertEquals(listOf("Luke Skywalker"), coroutineCol.find(TestingJedi::age lt 100).toList().map { it.name })
        coroutineClient.close()
    }
}
