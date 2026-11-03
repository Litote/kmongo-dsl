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
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import org.litote.kmongo.AllCategoriesKMongoBaseTest
import org.litote.kmongo.model.Friend
import org.litote.kmongo.runCommand
import kotlin.test.assertEquals

/**
 *
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class CommandWithSessionTest : AllCategoriesKMongoBaseTest<Friend>() {

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
    fun canRunACommand() {
        val r = database.runCommand<Document>(session,"{ ping: 1 }")
        assertEquals(1.0, r.get("ok"))
    }

}
