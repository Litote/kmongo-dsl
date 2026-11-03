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

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.litote.kmongo.model.Friend
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.reactivestreams.getCollection
import org.litote.kmongo.reactivestreams.getCollectionOfName
import org.litote.kmongo.test.MongoTestServer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KMongo 4/5 reactive streams code (`org.litote.kmongo.reactivestreams` imports + `.coroutine`) compiles and runs.
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class KMongo4ReactiveStreamsCompatibilityTest : KMongoReactiveStreamsCoroutineBaseTest<Friend>() {

    @Test
    fun `KMongo 4 reactive streams code`() = runBlocking {
        val client = KMongo.createClient(MongoTestServer.connectionString).coroutine
        try {
            val database = client.getDatabase(database.name).coroutine
            database.getCollection<Friend>().coroutine.insertOne(Friend("Joe"))
            assertEquals(1, database.getCollection<Friend>("friend").coroutine.countDocuments())
            assertEquals(1, database.getCollectionOfName<Friend>("friend").countDocuments())
        } finally {
            client.close()
        }
    }
}
