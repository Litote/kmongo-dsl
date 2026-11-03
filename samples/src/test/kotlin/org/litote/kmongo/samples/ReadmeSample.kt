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

import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Tag
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.KMongo
import org.litote.kmongo.coroutine.findOne
import org.litote.kmongo.coroutine.getCollection
import org.litote.kmongo.coroutine.toList
import org.litote.kmongo.coroutine.updateOne
import org.litote.kmongo.eq
import org.litote.kmongo.gt
import org.litote.kmongo.newId
import org.litote.kmongo.setValue
import org.litote.kmongo.test.MongoTestServer
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class ReadmeHero(val name: String, val age: Int, @Contextual val _id: Id<ReadmeHero> = newId())

/**
 * The README and migration guide code snippets.
 */
@Tag("jackson") @Tag("serialization")
class ReadmeSample {

    private val client = KMongo.createClient(MongoTestServer.connectionString)
    private val col: MongoCollection<ReadmeHero> = client.getDatabase("samples").getCollection<ReadmeHero>()

    @Test
    fun readme() = runBlocking {
        col.drop()
        col.insertOne(ReadmeHero("Batman", 40))
        assertEquals("Batman", col.findOne(ReadmeHero::name eq "Batman")?.name)
        col.updateOne(ReadmeHero::name eq "Batman", setValue(ReadmeHero::age, 41))
        assertEquals(listOf(41), col.find(ReadmeHero::age gt 30).toList().map { it.age })
        // migration guide example
        assertEquals(41, col.find(ReadmeHero::name eq "Batman").firstOrNull()?.age)
    }
}
