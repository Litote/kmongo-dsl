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

package org.litote.kmongo.coroutine.issues

import org.litote.kmongo.coroutine.*
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.Document
import kotlin.test.Test
import org.junit.jupiter.api.Tag
import org.litote.kmongo.EMPTY_BSON
import org.litote.kmongo.SerializationMappingCategory
import org.litote.kmongo.coroutine.KMongoReactiveStreamsCoroutineBaseTest
import org.litote.kmongo.json
import org.litote.kmongo.set
import org.litote.kmongo.setTo

@Serializable
data class EntityWithStatus(
    val status: Status,
) {
    @Serializable
    enum class Status {
        @SerialName("completed")
        COMPLETED,
        OTHER
    }
}

@Tag("SerializationMappingCategory")
class Issue390SetToWithEnumSerialName : KMongoReactiveStreamsCoroutineBaseTest<EntityWithStatus>() {

    @Test
    fun testSerialization() = runBlocking {
        val json = set(EntityWithStatus::status setTo EntityWithStatus.Status.COMPLETED).json
        assertEquals("""{"${"$"}set": {"status": "completed"}}""", json)

        col.insertOne(EntityWithStatus(EntityWithStatus.Status.OTHER))
        col.findOneAndUpdate(EMPTY_BSON, set(EntityWithStatus::status setTo EntityWithStatus.Status.COMPLETED))

        val doc = col.withDocumentClass<Document>().findOne()

        assertEquals("completed", doc?.get("status"))
    }
}
