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

import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.BucketAutoOptions
import com.mongodb.client.model.BucketOptions
import com.mongodb.client.model.Facet
import com.mongodb.client.model.Field
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Projections
import com.mongodb.client.model.PushOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.model.geojson.Point
import com.mongodb.client.model.geojson.Position
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.BsonType
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The DSL functions render the same BSON as the driver builders, with the name of the Kotlin property.
 */
class DslMappingTest {

    class Item(val a: String)

    class Doc(
        val s: String?,
        val t: String?,
        val i: Int,
        val l: List<String>,
        val items: List<Item>,
        val date: Instant?,
        val oid: ObjectId?,
    )

    private val filter = Filters.eq("a", 1)
    private val other = Filters.eq("b", 2)
    private val point = Point(Position(1.0, 2.0))
    private val pointDocument = BsonDocument("type", BsonString("Point"))

    private fun Bson.render(): BsonDocument =
        toBsonDocument(BsonDocument::class.java, MongoClientSettings.getDefaultCodecRegistry())

    private fun same(expected: Bson, actual: Bson) = assertEquals(expected.render(), actual.render())

    @Test
    fun `comparison and logical filters`() {
        same(Filters.lte("i", 1), Doc::i lte 1)
        same(Filters.`in`("i", listOf(1)), Doc::i `in` listOf(1))
        same(Filters.nin("i", listOf(1)), Doc::i nin listOf(1))
        same(Filters.not(filter), not(filter))
        same(Filters.nor(filter, other), nor(filter, other))
        same(Filters.nor(filter, other), nor(listOf(filter, other)))
        same(Filters.type("i", BsonType.INT32), Doc::i.type(BsonType.INT32))
        same(Filters.mod("i", 2, 1), Doc::i.mod(2, 1))
        same(Filters.text("x"), text("x"))
        same(Filters.where("1 == 1"), where("1 == 1"))
        same(Filters.jsonSchema(filter), jsonSchema(filter))
    }

    @Test
    fun `array and bitwise filters`() {
        same(Filters.all("l", "a", "b"), Doc::l.all("a", "b"))
        same(Filters.elemMatch("items", filter), Doc::items elemMatch filter)
        same(Filters.size("l", 1), Doc::l size 1)
        same(Filters.bitsAllClear("i", 1), Doc::i bitsAllClear 1)
        same(Filters.bitsAllSet("i", 1), Doc::i bitsAllSet 1)
        same(Filters.bitsAnyClear("i", 1), Doc::i bitsAnyClear 1)
        same(Filters.bitsAnySet("i", 1), Doc::i bitsAnySet 1)
    }

    @Test
    fun `geospatial filters`() {
        val polygon = listOf(listOf(0.0, 0.0), listOf(1.0, 1.0), listOf(1.0, 0.0))

        same(Filters.geoWithin("s", point), Doc::s geoWithin point)
        same(Filters.geoWithin("s", pointDocument), Doc::s geoWithin pointDocument)
        same(Filters.geoWithinBox("s", 0.0, 1.0, 2.0, 3.0), Doc::s.geoWithinBox(0.0, 1.0, 2.0, 3.0))
        same(Filters.geoWithinPolygon("s", polygon), Doc::s geoWithinPolygon polygon)
        same(Filters.geoWithinCenter("s", 0.0, 1.0, 2.0), Doc::s.geoWithinCenter(0.0, 1.0, 2.0))
        same(Filters.geoWithinCenterSphere("s", 0.0, 1.0, 2.0), Doc::s.geoWithinCenterSphere(0.0, 1.0, 2.0))
        same(Filters.geoIntersects("s", point), Doc::s geoIntersects point)
        same(Filters.geoIntersects("s", pointDocument), Doc::s geoIntersects pointDocument)
        same(Filters.near("s", point, 2.0, 1.0), Doc::s.near(point, 2.0, 1.0))
        same(Filters.near("s", pointDocument, 2.0, 1.0), Doc::s.near(pointDocument, 2.0, 1.0))
        same(Filters.near("s", 0.0, 1.0, 2.0, 1.0), Doc::s.near(0.0, 1.0, 2.0, 1.0))
        same(Filters.nearSphere("s", point, 2.0, 1.0), Doc::s.nearSphere(point, 2.0, 1.0))
        same(Filters.nearSphere("s", pointDocument, 2.0, 1.0), Doc::s.nearSphere(pointDocument, 2.0, 1.0))
        same(Filters.nearSphere("s", 0.0, 1.0, 2.0, 1.0), Doc::s.nearSphere(0.0, 1.0, 2.0, 1.0))
    }

    @Test
    fun `aggregation stages`() {
        val facet = Facet("f", Aggregates.limit(1))
        val sum = Accumulators.sum("n", 1)

        same(Aggregates.addFields(Field("a", 1)), addFields(Field("a", 1)))
        same(Aggregates.addFields(Field("a", 1)), addFields(listOf(Field("a", 1))))
        same(Aggregates.bucket("\$i", listOf(1, 2), BucketOptions()), bucket("\$i", listOf(1, 2)))
        same(Aggregates.bucketAuto("\$i", 2, BucketAutoOptions()), bucketAuto("\$i", 2))
        same(Aggregates.count("i"), Doc::i.count())
        same(Aggregates.project(BsonDocument("i", BsonInt32(1))), project(mapOf(Doc::i to 1)))
        same(Aggregates.sortByCount("\$i"), sortByCount("\$i"))
        same(Aggregates.skip(1), skip(1))
        same(Aggregates.facet(facet), facet(facet))
        same(Aggregates.facet(facet), facet(listOf(facet)))
        same(Aggregates.graphLookup("c", "\$i", "a", "b", "r"), graphLookup("c", "\$i", "a", "b", "r"))
        same(Aggregates.group("\$i", sum), group("\$i", listOf(sum)))
        same(Aggregates.out("c"), out("c"))
    }

    @Test
    fun `date operators on a date property`() {
        listOf(
            "\$dayOfYear" to dayOfYear(Doc::date),
            "\$dayOfMonth" to dayOfMonth(Doc::date),
            "\$dayOfWeek" to dayOfWeek(Doc::date),
            "\$hour" to hour(Doc::date),
            "\$millisecond" to millisecond(Doc::date),
            "\$minute" to minute(Doc::date),
            "\$month" to month(Doc::date),
            "\$second" to second(Doc::date),
        ).forEach { (operator, bson) ->
            assertEquals(BsonDocument(operator, BsonString("\$date")), bson.render())
        }
    }

    @Test
    fun `date operators on an ObjectId property`() {
        listOf(
            "\$dayOfYear" to dayOfYear(Doc::oid),
            "\$dayOfMonth" to dayOfMonth(Doc::oid),
            "\$dayOfWeek" to dayOfWeek(Doc::oid),
            "\$hour" to hour(Doc::oid),
            "\$millisecond" to millisecond(Doc::oid),
            "\$minute" to minute(Doc::oid),
            "\$month" to month(Doc::oid),
            "\$second" to second(Doc::oid),
            "\$week" to week(Doc::oid),
        ).forEach { (operator, bson) ->
            assertEquals(BsonDocument(operator, BsonString("\$oid")), bson.render())
        }
    }

    @Test
    fun `array and date string operators`() {
        val expected = BsonDocument(
            "\$arrayElemAt",
            org.bson.BsonArray(listOf(BsonString("\$l"), BsonInt32(1))),
        )
        assertEquals(expected, Doc::l.arrayElemAt(1).render())
        assertEquals(expected, arrayElemAt("\$l", 1).render())

        val date = Doc::date.dateToString("%Y", ZoneId.of("UTC"), "none").render()["\$dateToString"]!!.asDocument()
        assertEquals("%Y", date["format"]!!.asString().value)
        assertEquals("UTC", date["timezone"]!!.asString().value)
        assertEquals("none", date["onNull"]!!.asString().value)
        assertEquals("\$date", date["date"]!!.asString().value)
    }

    @Test
    fun `field updates`() {
        same(Updates.rename("s", "t"), rename(Doc::s, Doc::t))
        same(Updates.mul("i", 2), mul(Doc::i, 2))
        same(Updates.min("i", 1), min(Doc::i, 1))
        same(Updates.max("i", 1), max(Doc::i, 1))
        same(Updates.currentDate("date"), currentDate(Doc::date))
        same(Updates.currentTimestamp("date"), currentTimestamp(Doc::date))
    }

    @Test
    fun `array updates`() {
        same(Updates.addEachToSet("l", listOf("a")), addEachToSet(Doc::l, listOf("a")))
        same(Updates.push("l", "a"), push(Doc::l, "a"))
        same(Updates.pushEach("l", listOf("a"), PushOptions()), pushEach(Doc::l, listOf("a")))
        same(Updates.pullAll("l", listOf("a")), pullAll(Doc::l, listOf("a")))
        same(Updates.popFirst("l"), popFirst(Doc::l))
        same(Updates.popLast("l"), popLast(Doc::l))
    }

    @Test
    fun `bitwise updates`() {
        same(Updates.bitwiseAnd("i", 1), bitwiseAnd(Doc::i, 1))
        same(Updates.bitwiseAnd("i", 1L), bitwiseAnd(Doc::i, 1L))
        same(Updates.bitwiseOr("i", 1), bitwiseOr(Doc::i, 1))
        same(Updates.bitwiseOr("i", 1L), bitwiseOr(Doc::i, 1L))
        same(Updates.bitwiseXor("i", 1), bitwiseXor(Doc::i, 1))
        same(Updates.bitwiseXor("i", 1L), bitwiseXor(Doc::i, 1L))
    }

    @Test
    fun `bulk write models`() {
        val update = updateOne<Doc>(filter, other)
        assertEquals(filter, update.filter)
        assertEquals(other, update.update)

        val replacement = Item("a")
        val replace = replaceOne(filter, replacement)
        assertEquals(filter, replace.filter)
        assertEquals(replacement, replace.replacement)
    }

    @Test
    fun indexes() {
        same(Indexes.ascending("i", "s"), ascendingIndex(listOf(Doc::i, Doc::s)))
        same(Indexes.descending("i", "s"), descendingIndex(Doc::i, Doc::s))
        same(Indexes.descending("i", "s"), descendingIndex(listOf(Doc::i, Doc::s)))
        same(Indexes.geo2dsphere("i", "s"), geo2dsphere(Doc::i, Doc::s))
        same(Indexes.geo2dsphere("i", "s"), geo2dsphere(listOf(Doc::i, Doc::s)))
        same(Indexes.text("s"), Doc::s.textIndex())
        same(Indexes.hashed("s"), Doc::s.hashedIndex())

        val expected = BsonDocument("i", BsonInt32(1)).append("s", BsonInt32(-1))
        assertEquals(expected, index(Doc::i to true, Doc::s to false).render())
        assertEquals(expected, index(mapOf(Doc::i to true, Doc::s to false)).render())
    }

    @Test
    fun projections() {
        same(Projections.exclude("i", "s"), exclude(Doc::i, Doc::s))
        same(Projections.exclude("i", "s"), exclude(listOf(Doc::i, Doc::s)))
        same(Projections.elemMatch("items"), Doc::items.elemMatchProj())
        same(Projections.elemMatch("items", filter), Doc::items.elemMatchProj(filter))
        same(Projections.metaTextScore("s"), Doc::s.metaTextScore())
        same(Projections.slice("l", 1), Doc::l.slice(1))
        same(Projections.slice("l", 1, 2), Doc::l.slice(1, 2))
        same(Projections.fields(filter, other), fields(listOf(filter, other)))
    }
}
