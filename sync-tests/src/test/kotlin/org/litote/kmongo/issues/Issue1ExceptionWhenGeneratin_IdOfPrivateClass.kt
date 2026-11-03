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

package org.litote.kmongo.issues

import com.mongodb.kotlin.client.MongoCollection
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import org.litote.kmongo.SyncTestClient
import org.litote.kmongo.getCollection
import org.litote.kmongo.dropCollection
import org.litote.kmongo.KMongoRootTest
import kotlin.test.assertNotNull

/**
 * [Exception when generating _id of private class](https://github.com/Litote/kmongo/issues/1)
 */
class Issue1ExceptionWhenGeneratin_IdOfPrivateClass : KMongoRootTest() {

    @Serializable
    private data class PrivateClass(val _id: String? = null)

    private lateinit var col: MongoCollection<PrivateClass>

    @BeforeEach
    fun before() {
        col = SyncTestClient.database.getCollection<PrivateClass>()
    }

    @AfterEach
    fun after() {
        SyncTestClient.database.dropCollection<PrivateClass>()
    }

    @Test
    fun testCanGeneratedId() {
        val p = PrivateClass()
        col.insertOne(p)
        assertNotNull(p._id)
    }

}
