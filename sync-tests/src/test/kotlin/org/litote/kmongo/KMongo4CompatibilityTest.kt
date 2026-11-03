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

package org.litote.kmongo

import com.mongodb.client.gridfs.GridFSBuckets
import org.bson.Document
import org.junit.jupiter.api.Tag
import org.litote.kmongo.model.Friend
import org.litote.kmongo.test.MongoTestServer
import org.litote.kmongo.withDocumentClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the KMongo 4/5 sync API compatibility layer (KMongo4Compatibility.kt) and the Java client helpers.
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class KMongo4CompatibilityTest : AllCategoriesKMongoBaseTest<Friend>() {

    @Test
    fun `for loops, into and withDocumentClass import compile`() {
        col.insertMany(listOf(Friend("Joe"), Friend("Bob")))
        val names = mutableListOf<String?>()
        for (friend in col.find().sort(ascending(Friend::name))) {
            names.add(friend.name)
        }
        assertEquals<List<String?>>(listOf("Bob", "Joe"), names)
        assertEquals(2, col.find().into(mutableListOf<Friend>()).size)
        assertEquals("Joe", col.withDocumentClass<Document>().findOne(Friend::name eq "Joe")?.getString("name"))
    }

    @Test
    fun `Java client with KMongo mapping, Kotlin client sharing its pool, GridFS`() {
        KMongo.createJavaClient(MongoTestServer.connectionString).use { javaClient ->
            val client = KMongo.createClient(javaClient)
            val friends = client.getDatabase(database.name).getCollection<Friend>(col.namespace.collectionName)
            friends.insertOne(Friend("Joe"))
            // the Java client uses the KMongo mapping
            assertEquals(
                "Joe",
                javaClient.getDatabase(database.name).getCollection(col.namespace.collectionName, Friend::class.java)
                    .find().first()?.name
            )

            val bucket = GridFSBuckets.create(javaClient.getDatabase(database.name), "kmongo_gridfs_test")
            try {
                val id = bucket.uploadFromStream("hello.txt", "Hello GridFS".byteInputStream())
                assertEquals("Hello GridFS", bucket.openDownloadStream(id).use { String(it.readAllBytes()) })
                assertEquals("hello.txt", bucket.find().first()?.filename)
            } finally {
                bucket.drop()
            }
        }
    }
}
