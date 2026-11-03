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

package org.litote.kmongo.samples

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.coroutine.deleteOne
import org.litote.kmongo.coroutine.findOne
import org.litote.kmongo.coroutine.getCollection
import org.litote.kmongo.coroutine.save
import org.litote.kmongo.coroutine.toList
import org.litote.kmongo.coroutine.updateOne
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.setValue
import org.litote.kmongo.test.MongoTestServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * KMongo 4/5 coroutine code (`KMongo.createClient().coroutine`, `CoroutineCollection`...) compiled against kmongo-dsl:
 * it compiles with deprecation warnings only, thanks to the compatibility layer.
 */
abstract class AbstractCoroutineMigrationSample {

    private val client = KMongo.createClient(MongoTestServer.connectionString).coroutine
    private val database = client.getDatabase("samples")
    private val col: CoroutineCollection<Hero> = database.getCollection<Hero>("coroutineHero")

    @Test
    fun crud() = runBlocking {
        col.drop()
        val batman = Hero("Batman", 40)
        col.insertOne(batman)
        col.save(Hero("Robin", 20))

        assertEquals(batman, col.findOne(Hero::name eq "Batman"))
        assertEquals(listOf("Batman", "Robin"), col.find().toList().map { it.name }.sorted())
        // KMongo 4/5 publisher first() returned null when there is no result:
        // kmongo-dsl reports a compilation error on first() calls, replace them with firstOrNull()
        assertNull(col.find(Hero::name eq "Joker").firstOrNull())

        col.updateOne(Hero::name eq "Robin", setValue(Hero::age, 21))
        assertEquals(21, col.findOne(Hero::name eq "Robin")?.age)

        col.deleteOne(Hero::name eq "Robin")
        assertEquals(1, col.countDocuments())
    }
}

@Tag("jackson")
class CoroutineMigrationJacksonSample : AbstractCoroutineMigrationSample()

@Tag("serialization")
class CoroutineMigrationSerializationSample : AbstractCoroutineMigrationSample()
