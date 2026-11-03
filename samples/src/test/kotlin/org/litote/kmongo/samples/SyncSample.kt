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

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Tag
import org.litote.kmongo.Data
import org.litote.kmongo.Id
import org.litote.kmongo.KMongo
import org.litote.kmongo.aggregate
import org.litote.kmongo.descending
import org.litote.kmongo.div
import org.litote.kmongo.dropCollection
import org.litote.kmongo.eq
import org.litote.kmongo.ensureIndex
import org.litote.kmongo.find
import org.litote.kmongo.findOne
import org.litote.kmongo.findOneById
import org.litote.kmongo.getCollection
import org.litote.kmongo.gt
import org.litote.kmongo.newId
import org.litote.kmongo.projection
import org.litote.kmongo.save
import org.litote.kmongo.setValue
import org.litote.kmongo.test.MongoTestServer
import org.litote.kmongo.updateOne
import org.litote.kmongo.updateOneById
import kotlin.test.Test
import kotlin.test.assertEquals

@Data
@Serializable
data class Address(val city: String, val zip: String)

@Data
@Serializable
data class Hero(
    val name: String,
    val age: Int,
    val address: Address? = null,
    val powers: List<String> = emptyList(),
    @Contextual val _id: Id<Hero> = newId()
)

/**
 * KMongo 4/5 sync code - only the dependencies changed (kmongo-dsl-sync + a mapping + kmongo-dsl-ksp).
 */
abstract class AbstractSyncSample {

    private val client = KMongo.createClient(MongoTestServer.connectionString)
    private val database = client.getDatabase("samples")

    @Test
    fun crud() {
        database.dropCollection<Hero>()
        val col = database.getCollection<Hero>()
        col.ensureIndex(Hero::name)

        val batman = Hero("Batman", 40, Address("Gotham", "10001"), listOf("money"))
        col.insertOne(batman)
        col.save(Hero("Superman", 35, Address("Metropolis", "20002"), listOf("flight", "strength")))

        // KProperty DSL
        assertEquals(batman, col.findOne(Hero::name eq "Batman"))
        assertEquals(batman, col.findOneById(batman._id))
        // nested property path
        assertEquals("Superman", col.findOne(Hero::address / Address::city eq "Metropolis")?.name)
        // KSP generated paths
        assertEquals("Superman", col.findOne(Hero_.Address.city eq "Metropolis")?.name)
        // JSON queries
        assertEquals(listOf("Superman"), col.find("{age: {\$lt: 40}}").toList().map { it.name })

        // updates
        col.updateOne(Hero::name eq "Batman", setValue(Hero::age, 41))
        col.updateOneById(batman._id, setValue(Hero_.Address.zip, "10002"))
        assertEquals(Address("Gotham", "10002"), col.findOneById(batman._id)?.address)
        col.updateOne(batman.copy(age = 42))
        assertEquals(42, col.findOneById(batman._id)?.age)

        // typed projection
        assertEquals(
            listOf("Batman" to 42, "Superman" to 35),
            col.projection(Hero::name, Hero::age).toList().sortedBy { it.first }.map { it.first to it.second }
        )

        // aggregate
        assertEquals(
            2,
            col.aggregate<org.bson.Document>("[{\$match: {age: {\$gt: 30}}}, {\$count: 'count'}]")
                .first()["count"]
        )

        // sort
        assertEquals("Batman", col.find(Hero::age gt 0).sort(descending(Hero::age)).first().name)
    }
}

@Tag("jackson")
class SyncJacksonSample : AbstractSyncSample()

@Tag("serialization")
class SyncSerializationSample : AbstractSyncSample()
