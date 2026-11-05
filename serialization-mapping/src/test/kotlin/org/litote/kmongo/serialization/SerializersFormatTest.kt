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

package org.litote.kmongo.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.bson.BsonDecimal128
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.litote.kmongo.Id
import org.litote.kmongo.id.StringId
import java.math.BigDecimal
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.regex.Pattern
import kotlin.reflect.KProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The serializers with a format that is not BSON, and with the BSON types they accept when reading.
 */
class SerializersFormatTest {

    @Serializable
    data class DecimalValue(@Serializable(with = BigDecimalSerializer::class) val v: BigDecimal)

    @Serializable
    data class Decimal128Value(@Serializable(with = Decimal128Serializer::class) val v: Decimal128)

    @Serializable
    data class DateValue(@Serializable(with = DateSerializer::class) val v: Date)

    @Serializable
    data class IdValue(@Serializable(with = IdBsonSerializer::class) val v: Id<String>)

    @Serializable
    data class StringIdValue(@Serializable(with = StringIdBsonSerializer::class) val v: Id<String>)

    private fun <T : Any> roundTrip(serializer: KSerializer<T>, value: T, json: String) {
        assertEquals(json, Json.encodeToString(serializer, value))
        assertEquals(value, Json.decodeFromString(serializer, json))
    }

    private inline fun <reified T : Any> decode(json: String): T =
        SerializationCodec(T::class, configuration)
            .decode(BsonDocumentReader(BsonDocument.parse(json)), DecoderContext.builder().build())

    private inline fun <reified T : Any> encode(value: T): BsonDocument =
        BsonDocument().also {
            SerializationCodec(T::class, configuration)
                .encode(BsonDocumentWriter(it), value, EncoderContext.builder().build())
        }

    @Test
    fun `values are plain JSON outside of BSON`() {
        val objectId = ObjectId()
        val uuid = UUID.randomUUID()

        roundTrip(ObjectIdSerializer, objectId, "\"${objectId.toHexString()}\"")
        roundTrip(UUIDSerializer, uuid, "\"$uuid\"")
        roundTrip(BigDecimalSerializer, BigDecimal("1.50"), "\"1.50\"")
        roundTrip(DateSerializer, Date(1000), "1000")
        roundTrip(InstantSerializer, Instant.ofEpochMilli(1000), "1000")
        roundTrip(KTXInstantSerializer2, kotlin.time.Instant.fromEpochMilliseconds(1000), "1000")
    }

    @Test
    fun `ids are strings outside of BSON`() {
        val serializer = IdBsonSerializer<Id<String>>()
        assertEquals("\"a\"", Json.encodeToString(serializer, StringId("a")))
        assertEquals(StringId<String>("a"), Json.decodeFromString(serializer, "\"a\""))
    }

    @Test
    fun `a pattern is written with its options`() {
        val json = Json.encodeToString(PatternSerializer, Pattern.compile("a", Pattern.CASE_INSENSITIVE))
        assertEquals(true, json.contains("\"a\""))
        assertEquals(true, json.contains("\"i\""))
    }

    @Test
    fun `the serializers used for queries cannot read`() {
        assertFailsWith<IllegalStateException> { Json.decodeFromString(PatternSerializer, "{}") }
        assertFailsWith<IllegalStateException> { Json.decodeFromString(RegexSerializer, "{}") }
        assertFailsWith<IllegalStateException> {
            @Suppress("UNCHECKED_CAST")
            Json.decodeFromString(KPropertySerializer as KSerializer<KProperty<*>>, "\"a\"")
        }
    }

    @Test
    fun `a BigDecimal is read from the numeric and string BSON types`() {
        assertEquals(BigDecimal("1.5"), decode<DecimalValue>("{v: 1.5}").v)
        assertEquals(BigDecimal(2), decode<DecimalValue>("{v: 2}").v)
        assertEquals(BigDecimal(3), decode<DecimalValue>("{v: {\$numberLong: '3'}}").v)
        assertEquals(BigDecimal("4.5"), decode<DecimalValue>("{v: '4.5'}").v)
        assertFailsWith<SerializationException> { decode<DecimalValue>("{v: true}") }
    }

    @Test
    fun `a date is read from a string and rejects the other BSON types`() {
        assertEquals(Date(1000), decode<DateValue>("{v: '1000'}").v)
        assertFailsWith<SerializationException> { decode<DateValue>("{v: true}") }
    }

    @Test
    fun `a Decimal128 keeps its BSON type and its special values`() {
        val decimal = Decimal128(BigDecimal("1.5"))
        assertEquals(BsonDocument("v", BsonDecimal128(decimal)), encode(Decimal128Value(decimal)))
        assertEquals(decimal, decode<Decimal128Value>("{v: {\$numberDecimal: '1.5'}}").v)
        assertEquals(decimal, decode<Decimal128Value>("{v: 1.5}").v)
        assertEquals(Decimal128.NaN, decode<Decimal128Value>("{v: {\$numberDecimal: 'NaN'}}").v)
        roundTrip(Decimal128Serializer, decimal, "\"1.5\"")
    }

    @Test
    fun `an id is read from its BSON types`() {
        val objectId = ObjectId()
        assertEquals(StringId<String>("a"), decode<IdValue>("{v: 'a'}").v)
        assertEquals(objectId.toHexString(), decode<IdValue>("{v: {\$oid: '$objectId'}}").v.toString())
        assertEquals(StringId<String>("a"), decode<StringIdValue>("{v: 'a'}").v)
        assertFailsWith<SerializationException> { decode<IdValue>("{v: true}") }
    }

    @Test
    fun `a string id is written as a string`() {
        assertEquals(BsonDocument.parse("{v: 'a'}"), encode(StringIdValue(StringId("a"))))
    }
}
