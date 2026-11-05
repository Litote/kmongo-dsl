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

package org.litote.kmongo.jackson

import com.mongodb.MongoClientSettings
import de.undercouch.bson4jackson.types.Decimal128
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.BsonDecimal128
import org.bson.BsonDocument
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonMaxKey
import org.bson.BsonMinKey
import org.bson.BsonString
import org.bson.BsonTimestamp
import org.bson.Document
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.conversions.Bson
import org.bson.io.BasicOutputBuffer
import org.bson.types.MaxKey
import org.bson.types.MinKey
import org.bson.types.ObjectId
import org.litote.kmongo.util.KMongoConfiguration
import org.litote.kmongo.util.KMongoUtil
import java.math.BigDecimal
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The BSON types written by the Jackson mapping, in BSON and in extended JSON, and the [KMongoUtil] helpers relying on it.
 */
class BsonTypesWriteTest {

    data class Keys(val min: MinKey, val max: MaxKey)

    data class Decimal(val v: Decimal128)

    data class DriverDecimal(val v: org.bson.types.Decimal128)

    private fun write(value: Any): BsonDocument =
        BsonDocumentCodec().decode(
            BsonBinaryReader(ByteBuffer.wrap(KMongoConfiguration.bsonMapper.writeValueAsBytes(value))),
            DecoderContext.builder().build(),
        )

    private fun bson(document: BsonDocument): ByteArray =
        BasicOutputBuffer().use { buffer ->
            BsonDocumentCodec().encode(BsonBinaryWriter(buffer), document, EncoderContext.builder().build())
            buffer.toByteArray()
        }

    @Test
    fun `min and max keys are written in BSON`() {
        assertEquals(
            BsonDocument("min", BsonMinKey()).append("max", BsonMaxKey()),
            write(Keys(MinKey(), MaxKey())),
        )
    }

    @Test
    fun `a bson4jackson Decimal128 is written and read in BSON`() {
        val decimal = BigDecimal("1.5")
        val value = Decimal(Decimal128(decimal))
        val bytes = KMongoConfiguration.bsonMapper.writeValueAsBytes(value)

        assertEquals(BsonDocument("v", BsonDecimal128(org.bson.types.Decimal128(decimal))), write(value))
        assertEquals(
            decimal,
            KMongoConfiguration.bsonMapper.readValue(bytes, Decimal::class.java).v.bigDecimalValue(),
        )
    }

    @Test
    fun `a driver Decimal128 is written and read in BSON`() {
        val value = DriverDecimal(org.bson.types.Decimal128(BigDecimal("1.5")))
        val bytes = KMongoConfiguration.bsonMapper.writeValueAsBytes(value)

        assertEquals(BsonDocument("v", BsonDecimal128(value.v)), write(value))
        assertEquals(value, KMongoConfiguration.bsonMapper.readValue(bytes, DriverDecimal::class.java))
    }

    @Test
    fun `a driver Decimal128 is read from the values written by the previous versions`() {
        val expected = DriverDecimal(org.bson.types.Decimal128(BigDecimal("1.5")))
        listOf(BsonString("1.5"), BsonDouble(1.5)).forEach {
            assertEquals(expected, KMongoConfiguration.bsonMapper.readValue(bson(BsonDocument("v", it)), DriverDecimal::class.java))
        }
    }

    @Test
    fun `the special values of a driver Decimal128 are kept`() {
        listOf(
            org.bson.types.Decimal128.NaN,
            org.bson.types.Decimal128.POSITIVE_INFINITY,
            org.bson.types.Decimal128.NEGATIVE_ZERO,
        ).forEach {
            val bytes = KMongoConfiguration.bsonMapper.writeValueAsBytes(DriverDecimal(it))
            assertEquals(it, KMongoConfiguration.bsonMapper.readValue(bytes, DriverDecimal::class.java).v)
        }
    }

    @Test
    fun `a driver Decimal128 is written in extended json`() {
        assertEquals(
            """{"${'$'}numberDecimal":"1.5"}""",
            KMongoConfiguration.extendedJsonMapper.writeValueAsString(org.bson.types.Decimal128(BigDecimal("1.5"))),
        )
    }

    @Test
    fun `min key, max key and timestamp are written in extended json`() {
        val mapper = KMongoConfiguration.extendedJsonMapper
        assertEquals("""{"${'$'}minKey":1}""", mapper.writeValueAsString(MinKey()))
        assertEquals("""{"${'$'}maxKey":1}""", mapper.writeValueAsString(MaxKey()))
        assertEquals(
            """{"${'$'}timestamp":{"t":1,"i":2}}""",
            mapper.writeValueAsString(BsonTimestamp(1, 2)),
        )
    }

    @Test
    fun `the factory can be copied`() {
        assertEquals(KMongoBsonFactory::class, KMongoBsonFactory().copy()::class)
    }

    @Test
    fun `an update operator map is not wrapped in a set`() {
        val modifier = KMongoUtil.setModifier(mapOf("\$inc" to mapOf("a" to 1)))
        assertEquals(
            BsonDocument("\$inc", BsonDocument("a", BsonInt32(1))),
            modifier.toBsonDocument(BsonDocument::class.java, MongoClientSettings.getDefaultCodecRegistry()),
        )
    }

    @Test
    fun `toBsonModifier keeps a Bson and parses a String`() {
        val bson: Bson = Document("a", 1)
        assertSame(bson, KMongoUtil.toBsonModifier(bson))
        assertEquals(BsonDocument("a", BsonInt32(1)), KMongoUtil.toBsonModifier("{a:1}"))
    }

    @Test
    fun `the id of a map is its _id entry`() {
        assertEquals(1, KMongoUtil.extractId(mapOf("_id" to 1), Map::class))
        assertFailsWith<IllegalStateException> { KMongoUtil.extractId(emptyMap<String, Any>(), Map::class) }
    }

    @Test
    fun `an id value is converted to its BSON value`() {
        val objectId = ObjectId()
        assertNull(KMongoUtil.getIdBsonValue(null))
        assertEquals(objectId, KMongoUtil.getIdBsonValue(objectId)!!.asObjectId().value)
        assertEquals(BsonString("a"), KMongoUtil.getIdBsonValue("a"))
        assertEquals(1.5, KMongoUtil.getIdBsonValue(1.5)!!.asDouble().value)
        assertEquals(BsonInt32(1), KMongoUtil.getIdBsonValue(1))
        assertEquals(2L, KMongoUtil.getIdBsonValue(2L)!!.asInt64().value)
        assertEquals(BsonDocument("a", BsonInt32(1)), KMongoUtil.getIdBsonValue(mapOf("a" to 1)))
    }
}
