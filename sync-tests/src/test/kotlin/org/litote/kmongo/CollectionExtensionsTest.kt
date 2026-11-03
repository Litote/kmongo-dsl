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
import com.mongodb.client.model.IndexOptions
import org.junit.jupiter.api.Tag
import org.litote.kmongo.model.Friend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The overloads of the [com.mongodb.kotlin.client.MongoCollection] extensions not used by the other tests.
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class CollectionExtensionsTest : AllCategoriesKMongoBaseTest<Friend>() {

    private fun uniqueIndexes(): Int = col.listIndexes().toList().count { it["unique"] == true }

    private fun indexes(): Int = col.listIndexes().toList().size

    @Test
    fun `can find with an empty default filter`() {
        col.insertOne(Friend("Paul"))
        assertEquals(listOf("Paul"), col.find("{}").toList().map { it.name })
    }

    @Test
    fun `can delete with several filters`() {
        col.insertMany(listOf(Friend("Paul", "a"), Friend("Paul", "a"), Friend("Paul", "b")))
        col.deleteOne(Friend::name eq "Paul", Friend::address eq "a")
        assertEquals(2, col.countDocuments())
        col.deleteMany(Friend::name eq "Paul", Friend::address eq "a")
        assertEquals(listOf("b"), col.find().toList().map { it.address })
    }

    @Test
    fun `can update many with properties`() {
        col.insertMany(listOf(Friend("Paul", "a"), Friend("Paul", "b"), Friend("John", "c")))
        val result = col.updateMany(Friend::name eq "Paul", Friend::address setTo "z")
        assertEquals(2, result.modifiedCount)
        assertEquals(2, col.countDocuments("{address:'z'}"))
    }

    @Test
    fun `can create and drop an index from a string`() {
        col.createIndex("{name:1}")
        assertEquals(2, indexes())
        col.dropIndexOfKeys("{name:1}")
        assertEquals(1, indexes())
    }

    @Test
    fun `dropIndex with a string is the driver function and takes the name of the index`() {
        col.createIndex("{name:1}")
        assertFailsWith<MongoCommandException> { col.dropIndex("{name:1}") }
        col.dropIndex("name_1")
        assertEquals(1, indexes())
    }

    @Test
    fun `ensureIndex recreates an index whose options have changed`() {
        col.ensureIndex("{name:1}")
        assertEquals(0, uniqueIndexes())
        col.ensureIndex("{name:1}", IndexOptions().unique(true))
        assertEquals(1, uniqueIndexes())
    }

    @Test
    fun `ensureIndex with keys recreates an index whose options have changed`() {
        val keys = ascending(Friend::address)
        col.ensureIndex(keys)
        col.ensureIndex(keys, IndexOptions().unique(true))
        assertEquals(1, uniqueIndexes())
        assertEquals(2, indexes())
    }
}
