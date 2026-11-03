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

import com.mongodb.client.model.changestream.FullDocument
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import org.litote.kmongo.model.Friend
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 *
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class ChangeStreamTest : AllCategoriesKMongoBaseTest<Friend>() {

    @Test
    fun `ChangeStreamIterable#listen listens all changes`() {
        col.insertOne(Friend("Joe"))
        val friends = ArrayBlockingQueue<Friend>(3)
        Thread.sleep(100)
        val listening = col.watch().fullDocument(FullDocument.UPDATE_LOOKUP).listen {
            if (it.fullDocument != null) {
                friends.add(it.fullDocument!!)
            }
        }

        Thread.sleep(100)
        val fred = Friend("Fred")
        col.insertOne(fred)
        Thread.sleep(100)
        val ivan = Friend("Ivan")
        col.insertOne(ivan)
        Thread.sleep(100)
        val lea = Friend("Lea")
        col.insertOne(lea)
        Thread.sleep(100)

        //retrieve all friends in the right order
        assertEquals(fred, friends.poll(50, TimeUnit.SECONDS))
        assertEquals(ivan, friends.poll(50, TimeUnit.SECONDS))
        assertEquals(lea, friends.poll(50, TimeUnit.SECONDS))

        // the listener thread is stopped when the listening is closed
        listening.close()
        val deadline = System.currentTimeMillis() + 10_000
        while (listenerThreadAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertFalse(listenerThreadAlive())
    }

    private fun listenerThreadAlive(): Boolean =
        Thread.getAllStackTraces().keys.any { it.name == "kmongo-change-stream-listener" && it.isAlive }
}
