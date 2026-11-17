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

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import org.junit.jupiter.api.Tag
import org.litote.kmongo.*
import org.litote.kmongo.test.MongoTestServer
import kotlin.test.Test
import kotlin.test.assertEquals

data class ReadmeAddress(val city: String)
data class ReadmeSyncHero(val name: String, val age: Int, val address: ReadmeAddress)

/**
 * The "Quick look" code snippets of the README (sync flavor, Jackson mapping).
 */
@Tag("jackson")
class ReadmeSyncSample {

    private val client = KMongo.createClient(MongoTestServer.connectionString)
    private val database = client.getDatabase("readme")

    @Test
    fun `quick look`() {
        val col = database.getCollection<ReadmeSyncHero>()
        assertEquals("readmeSyncHero", col.namespace.collectionName)
        col.drop()

        col.insertOne(ReadmeSyncHero("Batman", 40, ReadmeAddress("Gotham")))
        val batman: ReadmeSyncHero? = col.findOne(ReadmeSyncHero::address / ReadmeAddress::city eq "Gotham")
        assertEquals("Batman", batman?.name)
        col.updateOne(ReadmeSyncHero::name eq "Batman", setValue(ReadmeSyncHero::age, 41))
        val heroes: List<ReadmeSyncHero> = col.find(ReadmeSyncHero::age gt 30).toList()
        assertEquals(listOf(41), heroes.map { it.age })
    }

    @Test
    fun `quick look - driver alone`() {
        val col = database.getCollection<ReadmeSyncHero>("readmeSyncHero")
        col.drop()
        col.insertOne(ReadmeSyncHero("Batman", 40, ReadmeAddress("Gotham")))

        val batman: ReadmeSyncHero? = col.find(Filters.eq("address.city", "Gotham")).firstOrNull()
        assertEquals("Batman", batman?.name)
        col.updateOne(Filters.eq("name", "Batman"), Updates.set("age", 41))
        assertEquals(41, col.findOne(ReadmeSyncHero::name eq "Batman")?.age)
    }
}
