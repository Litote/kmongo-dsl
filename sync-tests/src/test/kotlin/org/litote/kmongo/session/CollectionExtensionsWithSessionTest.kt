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

package org.litote.kmongo.session

import com.mongodb.client.model.IndexOptions
import com.mongodb.kotlin.client.ClientSession
import org.bson.types.ObjectId
import org.junit.jupiter.api.Tag
import org.litote.kmongo.AllCategoriesKMongoBaseTest
import org.litote.kmongo.ascendingIndex
import org.litote.kmongo.countDocuments
import org.litote.kmongo.deleteMany
import org.litote.kmongo.deleteOne
import org.litote.kmongo.dropIndexOfKeys
import org.litote.kmongo.ensureIndex
import org.litote.kmongo.ensureUniqueIndex
import org.litote.kmongo.eq
import org.litote.kmongo.find
import org.litote.kmongo.findOne
import org.litote.kmongo.findOneAndDelete
import org.litote.kmongo.findOneAndReplace
import org.litote.kmongo.findOneById
import org.litote.kmongo.createIndex
import org.litote.kmongo.model.Friend
import org.litote.kmongo.replaceOne
import org.litote.kmongo.replaceOneWithFilter
import org.litote.kmongo.save
import org.litote.kmongo.setTo
import org.litote.kmongo.updateMany
import org.litote.kmongo.updateOne
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The overloads of the [com.mongodb.kotlin.client.MongoCollection] extensions with a [ClientSession].
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class CollectionExtensionsWithSessionTest : AllCategoriesKMongoBaseTest<Friend>() {

    private fun withSession(block: (ClientSession) -> Unit) = mongoClient.startSession().use(block)

    private fun uniqueIndexes(): Int = col.listIndexes().toList().count { it["unique"] == true }

    private fun indexes(): Int = col.listIndexes().toList().size

    @Test
    fun `can find with a session`() {
        withSession {
            col.insertMany(listOf(Friend("Paul", "a"), Friend("John", "b")))
            assertEquals(2, col.find(it).toList().size)
            assertEquals(listOf("a"), col.find(it, "{name:'Paul'}").toList().map { f -> f.address })
            assertEquals(1, col.find(it, Friend::name eq "Paul", Friend::address eq "a").toList().size)
            assertEquals("Paul", col.findOne(it, "{address:'a'}")?.name)
            assertEquals("Paul", col.findOne(it, Friend::address eq "a")?.name)
            assertEquals("Paul", col.findOne(it, Friend::name eq "Paul", Friend::address eq "a")?.name)
        }
    }

    @Test
    fun `can find by id with a session`() {
        withSession {
            val id = ObjectId()
            col.insertOne(it, Friend(id, "Paul"))
            assertEquals("Paul", col.findOneById(it, id)?.name)
            assertNull(col.findOneById(it, ObjectId()))
        }
    }

    @Test
    fun `can delete with a session`() {
        withSession {
            col.insertMany(
                listOf(Friend("A", "a"), Friend("B", "a"), Friend("C", "b"), Friend("C", "b"), Friend("D", "d")),
            )
            col.deleteOne(it, "{name:'A'}")
            assertEquals(4, col.countDocuments(it, "{}"))
            col.deleteOne(it, Friend::name eq "B", Friend::address eq "a")
            assertEquals(3, col.countDocuments(it, "{}"))
            col.deleteMany(it, "{name:'D'}")
            assertEquals(2, col.countDocuments(it, "{}"))
            col.deleteMany(it, Friend::name eq "C", Friend::address eq "b")
            assertEquals(0, col.countDocuments(it, "{}"))
        }
    }

    @Test
    fun `can replace with a session`() {
        withSession {
            val id = ObjectId()
            col.insertOne(it, Friend(id, "Paul"))
            col.replaceOne(it, "{name:'Paul'}", Friend(id, "John"))
            assertEquals("John", col.findOneById(it, id)?.name)
            col.replaceOneWithFilter(it, Friend::name eq "John", Friend(id, "Peter"))
            assertEquals("Peter", col.findOneById(it, id)?.name)
            col.save(it, Friend(id, "Jack"))
            assertEquals("Jack", col.findOneById(it, id)?.name)
            col.save(it, Friend("New"))
            assertEquals(2, col.countDocuments(it, "{}"))
        }
    }

    @Test
    fun `can update one with a session`() {
        withSession {
            col.insertOne(it, Friend("Paul", "a"))
            col.updateOne(it, "{name:'Paul'}", "{\$set:{name:'John'}}")
            assertEquals("a", col.findOne(it, "{name:'John'}")?.address)
            col.updateOne(it, "{name:'John'}", Friend("Peter", "b"))
            assertEquals("b", col.findOne(it, "{name:'Peter'}")?.address)
            col.updateOne(it, Friend::name eq "Peter", Friend("Jack", "c"))
            assertEquals("c", col.findOne(it, "{name:'Jack'}")?.address)
            col.updateOne(it, Friend::name eq "Jack", Friend::address setTo "d")
            assertEquals("d", col.findOne(it, "{name:'Jack'}")?.address)
        }
    }

    @Test
    fun `can update many with a session`() {
        withSession {
            col.insertMany(listOf(Friend("Paul", "a"), Friend("Paul", "b")))
            col.updateMany(it, "{name:'Paul'}", "{\$set:{address:'z'}}")
            assertEquals(2, col.countDocuments(it, "{address:'z'}"))
            col.updateMany(it, Friend::name eq "Paul", Friend::address setTo "y")
            assertEquals(2, col.countDocuments(it, "{address:'y'}"))
        }
    }

    @Test
    fun `can find and modify with a session`() {
        withSession {
            val id = ObjectId()
            col.insertOne(it, Friend(id, "Paul"))
            assertEquals("Paul", col.findOneAndReplace(it, "{name:'Paul'}", Friend(id, "John"))?.name)
            assertEquals("John", col.findOneAndDelete(it, "{name:'John'}")?.name)
            assertEquals(0, col.countDocuments(it, "{}"))
        }
    }

    @Test
    fun `can manage indexes with a session`() {
        withSession {
            col.createIndex(it, "{name:1}")
            assertEquals(2, indexes())
            col.dropIndexOfKeys(it, "{name:1}")
            assertEquals(1, indexes())
            col.ensureIndex(it, ascendingIndex(Friend::name))
            col.dropIndexOfKeys(it, "{name:1}")
            col.ensureIndex(it, Friend::name)
            col.ensureIndex(it, "{address:1}")
            assertEquals(3, indexes())
        }
    }

    @Test
    fun `ensureIndex with a session recreates an index whose options have changed`() {
        withSession {
            col.ensureIndex(it, "{name:1}")
            col.ensureIndex(it, "{name:1}", IndexOptions().unique(true))
            assertEquals(1, uniqueIndexes())
            col.ensureIndex(it, ascendingIndex(Friend::address))
            col.ensureIndex(it, ascendingIndex(Friend::address), IndexOptions().unique(true))
            assertEquals(2, uniqueIndexes())
            col.ensureUniqueIndex(it, Friend::tags)
            assertEquals(3, uniqueIndexes())
        }
    }
}
