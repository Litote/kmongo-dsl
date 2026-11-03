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

import com.mongodb.kotlin.client.ClientSession
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import org.litote.kmongo.AllCategoriesKMongoBaseTest
import org.litote.kmongo.MongoOperator
import org.litote.kmongo.bulkWrite
import org.litote.kmongo.json
import org.litote.kmongo.model.Friend
import kotlin.test.assertEquals

/**
 *
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class BulkWriteWithSessionTest : AllCategoriesKMongoBaseTest<Friend>() {

    lateinit var session: ClientSession

    @BeforeEach
    fun setup() {
        session = mongoClient.startSession()
    }

    @AfterEach
    fun tearDown() {
        session.close()
    }

    @Test
    fun multilinesBulkWrite() {
        val friend = Friend("John", "22 Wall Street Avenue")
        val result = col.bulkWrite(
            session,
            """ [
                                { insertOne : { "document" : ${friend.json} } },
                                { updateOne : {
                                                "filter" : {name:"Fred"},
                                                "update" : {${MongoOperator.set}:{address:"221B Baker Street"}},
                                                "upsert" : true
                                              }
                                },
                                { updateMany : {
                                                    "filter" : {},
                                                    "update" : {${MongoOperator.set}:{address:"nowhere"}}
                                                }
                                },
                                { replaceOne : {
                                                    "filter" : {name:"Max"},
                                                    "replacement" : {name:"Joe"},
                                                    "upsert" : true
                                                }
                                },
                                { deleteOne :  { "filter" : {name:"Joe"} }},
                                { deleteMany :  { "filter" : {} } }
                                ] """
        )
        assertEquals(1, result.insertedCount)
        assertEquals(2, result.matchedCount)
        assertEquals(3, result.deletedCount)
        assertEquals(2, result.modifiedCount)
        assertEquals(2, result.upserts.size)
        assertEquals(0, col.countDocuments(session))
    }

}
