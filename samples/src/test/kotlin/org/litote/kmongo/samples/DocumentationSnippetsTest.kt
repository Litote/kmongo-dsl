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
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.Tag
import org.litote.kmongo.*
import org.litote.kmongo.MongoOperator.lt
import org.litote.kmongo.MongoOperator.match
import org.litote.kmongo.MongoOperator.regex
import org.litote.kmongo.MongoOperator.sample
import org.litote.kmongo.test.MongoTestServer
import org.litote.kmongo.util.UpdateConfiguration
import java.time.Instant
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

data class DocCoordinate(val lat: Int, val lng: Int)

data class DocFriend(
    val name: String?,
    val address: String? = null,
    val coordinate: DocCoordinate? = null,
    val tags: List<String> = emptyList(),
    val localeMap: Map<Locale, DocCoordinate> = emptyMap(),
    val _id: ObjectId = ObjectId()
)

data class DocArticle(
    val title: String,
    val tags: List<String>,
    val date: Instant = Instant.now(),
    val ok: Boolean = true
)

data class DocResult(val _id: String, val count: Int = 0, val averageYear: Double = 0.0)

data class DocFriendWithNameOnly(val name: String)

/**
 * The (sync) code samples of the KMongo documentation (https://litote.org/kmongo/), compiled and run with kmongo-dsl:
 * they are the samples of the KMongo DSL documentation (docs directory, https://kmongo-dsl.litote.org/),
 * where the differences commented with "kmongo-dsl:" are fixed.
 */
@Tag("jackson")
class DocumentationSnippetsTest {

    private val client = KMongo.createClient(MongoTestServer.connectionString)
    private val database = client.getDatabase("documentation")
    private val col = database.getCollection<DocFriend>()

    @BeforeTest
    @AfterTest
    fun clean() {
        col.drop()
        database.getCollection<DocArticle>().drop()
    }

    @Test
    fun `extensions overview - insert update replace delete`() {
        col.insertOne("{'name':'Paul'}")
        col.updateOne("{name:'Paul'}", "{\$set:{name:'John'}}")
        col.updateOne(DocFriend::name eq "John", setValue(DocFriend::name, "Paul"))
        val friend = col.findOne(DocFriend::name eq "Paul")!!
        col.updateOneById(friend._id, friend.copy(address = "22 Wall Street Avenue"))
        col.updateOne(friend.copy(name = "John"))
        col.updateOne(friend.copy(name = null), updateOnlyNotNullProperties = true)
        assertEquals("John", col.findOneById(friend._id)?.name)
        assertEquals(false, UpdateConfiguration.updateOnlyNotNullProperties)
        col.updateMany(DocFriend::name eq "John", setValue(DocFriend::address, "x"))

        col.replaceOne("{name:'John'}", DocFriend("Peter", _id = ObjectId()))
        col.replaceOneById(friend._id, DocFriend("John", _id = ObjectId()))
        col.replaceOne(DocFriend("John", "22 Wall Street Avenue", _id = friend._id))
        col.replaceOneWithFilter(DocFriend::name eq "John", DocFriend("Peter", _id = ObjectId()))
        assertEquals("Peter", col.findOneById(friend._id)?.name)

        assertNotNull(col.findOneAndUpdate(DocFriend::name eq "Peter", setValue(DocFriend::name, "Max")))
        col.save(DocFriend("Joe"))
        col.deleteOne("{name:'Joe'}")
        col.deleteOne(DocFriend::name eq "Max")
        col.deleteOneById(friend._id)
        col.deleteMany("{}")
        assertEquals(0, col.countDocuments())
    }

    @Test
    fun `extensions overview - bulk write`() {
        val friend = DocFriend("John", "22 Wall Street Avenue")
        val result = col.bulkWrite(
            """ [
      { insertOne : { "document" : ${friend.json} } },
      { updateOne : { "filter" : {name:"Fred"}, "update" : {${'$'}set:{address:"221B Baker Street"}}, "upsert" : true } },
      { updateMany : { "filter" : {}, "update" : {${'$'}set:{address:"nowhere"}} } },
      { replaceOne : { "filter" : {name:"Max"}, "replacement" : {name:"Joe"}, "upsert" : true } },
      { deleteOne :  { "filter" : {name:"Joe"} }},
      { deleteMany :  { "filter" : {} } }
      ] """
        )
        assertEquals(1, result.insertedCount)
        assertEquals(2, result.upserts.size)
        assertEquals(0, col.countDocuments())

        with(friend) {
            col.bulkWrite(
                insertOne(friend),
                updateOne(::name eq "Fred", set(::address setTo "221B Baker Street"), upsert()),
                updateMany(EMPTY_BSON, set(::address setTo "nowhere")),
                // kmongo-dsl (and KMongo 4/5): replaceUpsert() instead of the documented upsert()
                replaceOne(::name eq "Max", DocFriend("Joe"), replaceUpsert()),
                deleteOne(::name eq "Max"),
                deleteMany(EMPTY_BSON)
            )
        }
        assertEquals(0, col.countDocuments())
    }

    @Test
    fun `extensions overview - retrieve data`() {
        col.insertOne(DocFriend("John", "22 Wall Street Avenue", DocCoordinate(1, 2)))
        col.insertOne(DocFriend("Joe"))
        assertNotNull(col.findOne("{name:'John'}"))
        assertNotNull(col.findOne(DocFriend::name eq "John", DocFriend::address eq "22 Wall Street Avenue"))
        val list: List<DocFriend> = col.find(DocFriend::name eq "John").toList()
        assertEquals(1, list.size)
        // kmongo-dsl: count(String) -> countDocuments(String)
        assertEquals(1, col.countDocuments("{address:{\$ne:null}}"))
        // the Jackson mapping writes the null values: distinct returns null
        assertEquals(setOf(null, "22 Wall Street Avenue"), col.distinct<String>("address").toSet())
        val addresses: List<String?> = col.distinct(DocFriend::address).toList()
        assertEquals(setOf(null, "22 Wall Street Avenue"), addresses.toSet())
        val names: List<String?> = col.projection(DocFriend::name).toList()
        assertEquals(setOf("John", "Joe"), names.toSet())

        assertEquals(
            setOf("John", "Joe"),
            col.withDocumentClass<DocFriendWithNameOnly>().find().projection(DocFriendWithNameOnly::name)
                .toList().map { it.name }.toSet()
        )
        val (name, lat, lng) =
            col.withDocumentClass<Document>()
                .find()
                .descendingSort(DocFriend::name)
                .projection(
                    fields(
                        include(DocFriend::name, DocFriend::coordinate / DocCoordinate::lat, DocFriend::coordinate / DocCoordinate::lng),
                        excludeId()
                    )
                )
                .map {
                    Triple(
                        it.getString("name"),
                        it.findValue<Int>("coordinate.lat"),
                        it.findValue(DocFriend::coordinate / DocCoordinate::lng)
                    )
                }
                .first()
        assertEquals(Triple("John", 1, 2), Triple(name, lat, lng))

        val notJoe = col.find().evaluate { filter { it.name != "Joe" }.first() }
        assertEquals("John", notJoe.name)
        assertEquals("John", col.find().asSequence().filter { it.name != "Joe" }.first().name)
    }

    @Test
    fun `extensions overview - aggregate and indexes`() {
        val articles = database.getCollection<DocArticle>()
        articles.insertOne(DocArticle("a", listOf("virus")))
        assertEquals(1, articles.aggregate<DocArticle>("[{\$match:{tags:'virus'}},{\$limit:1}]").toList().size)
        val r = articles.aggregate<DocResult>(
            match(DocArticle::tags contains "virus"),
            project(
                DocArticle::title from DocArticle::title,
                DocArticle::ok from cond(DocArticle::ok, 1, 0),
                DocResult::averageYear from year(DocArticle::date)
            ),
            group(
                DocArticle::title,
                DocResult::count sum DocArticle::ok,
                DocResult::averageYear avg DocResult::averageYear
            ),
            sort(ascending(DocResult::_id))
        ).toList()
        assertEquals(1, r.first().count)

        col.ensureIndex("{'name':1,'address':1}")
        col.ensureUniqueIndex(DocFriend::name, DocFriend::address)
        col.dropIndexOfKeys("{'name':1,'address':1}")
        // kmongo-dsl: ensureUniqueIndex(String) is documented, but not provided by KMongo 4/5 nor kmongo-dsl
        col.ensureIndex("{'address':1}", com.mongodb.client.model.IndexOptions().unique(true))
    }

    @Test
    fun `extensions overview - withKMongo and transactions`() {
        assertNotNull(database.withKMongo().getCollection<DocFriend>().withKMongo())
        client.startSession().use { clientSession ->
            clientSession.startTransaction()
            col.insertOne(clientSession, DocFriend("Bob"))
            clientSession.commitTransaction()
        }
        assertEquals(1, col.countDocuments())
    }

    @Test
    fun `mongo shell queries`() {
        col.insertOne(DocFriend("Yoda", tags = listOf("jedi")))
        val yoda = col.findOne("{name: {$regex: 'Yo.*'}}")!!
        assertNull(col.aggregate<DocFriend>("""[ {$match:{name:{$lt : ${yoda.name!!.json}}}}, {$sample:{size:1}} ]""").firstOrNull())
        assertEquals("{\"\$oid\":\"575b17284ce9b17526c26e46\"}", ObjectId("575b17284ce9b17526c26e46").json)
        assertEquals("[{\$group: {_id: \"\$cust_id\"}}]", "[{\$group: {_id: \"\$ cust_id\"}}]".formatJson())
    }

    @Test
    fun `typed queries`() {
        assertEquals("{\"coordinate.lat\": {\"\$lt\": 0}}", (DocFriend::coordinate / DocCoordinate::lat lt 0).json)
        assertEquals("tags.0", DocFriend::tags.pos(0).path())
        assertEquals("tags.\$", DocFriend::tags.posOp.path())
        assertEquals("tags.\$[]", DocFriend::tags.allPosOp.path())
        assertEquals("tags.\$[t]", DocFriend::tags.filteredPosOp("t").path())
        assertEquals("localeMap.en.lat", (DocFriend::localeMap.keyProjection(Locale.ENGLISH) / DocCoordinate::lat).path())
        assertEquals("coordinate.lat", (DocFriend::coordinate % DocCoordinate::lat).path())
        col.insertOne(DocFriend("Paul", tags = listOf("t1", "t2")))
        col.updateOne(DocFriend::name eq "Paul", DocFriend::name setTo "John")
        col.updateOne(DocFriend::name eq "John", set(DocFriend::name setTo "John", DocFriend::address setTo "a"))
        col.updateOne(DocFriend::name eq "John", pull(DocFriend::tags, "t2"))
        assertEquals(listOf("t1"), col.findOne(or(DocFriend::name eq "John", Filters.empty()))?.tags)
        assertNotNull(
            lookup(
                "evaluationsAnswers",
                listOf(DocFriend::tags.variableDefinition()),
                DocResult::_id,
                match(expr(MongoOperator.and from listOf(true)))
            ).json
        )
        assertNotNull(replaceRoot("_id".projection).json)
        assertNotNull(DocFriend::tags.unwind().json)
    }
}
