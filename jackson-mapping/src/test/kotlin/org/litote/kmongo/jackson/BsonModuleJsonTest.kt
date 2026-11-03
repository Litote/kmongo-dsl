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

import org.bson.BsonBinaryWriter
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.BsonTimestamp
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer
import org.bson.types.Binary
import org.bson.types.MaxKey
import org.bson.types.MinKey
import org.bson.types.ObjectId
import org.litote.kmongo.util.KMongoConfiguration
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.deser.DeserializationProblemHandler
import tools.jackson.databind.node.BinaryNode
import tools.jackson.databind.node.POJONode
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import de.undercouch.bson4jackson.types.ObjectId as Bson4JacksonObjectId

/**
 * Tests the deserializers of [BsonModule] outside of a BSON parser: extended JSON and trees.
 */
class BsonModuleJsonTest {

    data class IdValue(val id: Bson4JacksonObjectId)

    data class DateValue(val date: Date)

    private val mapper = KMongoConfiguration.bsonMapperCopy

    private inline fun <reified T : Any> read(json: String): T = mapper.readValue(json, T::class.java)

    private inline fun <reified T : Any> read(tree: JsonNode): T = mapper.treeToValue(tree, T::class.java)

    private fun bson(document: BsonDocument): ByteArray =
        BasicOutputBuffer().use { buffer ->
            BsonDocumentCodec().encode(BsonBinaryWriter(buffer), document, EncoderContext.builder().build())
            buffer.toByteArray()
        }

    @Test
    fun `object id is read from its fields`() {
        val id = read<Bson4JacksonObjectId>("""{"${'$'}time":1,"${'$'}machine":2,"${'$'}inc":3}""")

        assertEquals(1, id.timestamp)
    }

    @Test
    fun `timestamp is read from extended json and from a pojo`() {
        assertEquals(BsonTimestamp(1, 2), read<BsonTimestamp>("""{"${'$'}timestamp":{"t":1,"i":2}}"""))
        assertEquals(BsonTimestamp(1, 2), read<BsonTimestamp>(POJONode(BsonTimestamp(1, 2))))
    }

    @Test
    fun `timestamp fails on an unexpected value`() {
        assertFailsWith<JacksonException> { read<BsonTimestamp>("1") }
    }

    @Test
    fun `binary is read from extended json, from a pojo and from a binary node`() {
        val bytes = byteArrayOf(1, 2)

        val binary = read<Binary>("""{"${'$'}binary":"AQI=","${'$'}type":"80"}""")

        assertContentEquals(bytes, binary.data)
        assertEquals(0x80.toByte(), binary.type)
        assertEquals(Binary(bytes), read<Binary>(POJONode(Binary(bytes))))
        assertEquals(Binary(bytes), read<Binary>(BinaryNode.valueOf(bytes)))
    }

    @Test
    fun `binary fails on an unexpected value`() {
        assertFailsWith<JacksonException> { read<Binary>("1") }
    }

    @Test
    fun `max key is read from extended json, from a pojo and from a string`() {
        assertEquals(MaxKey(), read<MaxKey>("""{"${'$'}maxKey":1}"""))
        assertEquals(MaxKey(), read<MaxKey>(POJONode(MaxKey())))
        assertEquals(MaxKey(), read<MaxKey>("\"MaxKey\""))
    }

    @Test
    fun `max key fails on an unexpected value`() {
        assertFailsWith<JacksonException> { read<MaxKey>("""{"${'$'}maxKey":2}""") }
        assertFailsWith<JacksonException> { read<MaxKey>("1") }
    }

    @Test
    fun `min key is read from extended json, from a pojo and from a string`() {
        assertEquals(MinKey(), read<MinKey>("""{"${'$'}minKey":1}"""))
        assertEquals(MinKey(), read<MinKey>(POJONode(MinKey())))
        assertEquals(MinKey(), read<MinKey>("\"MinKey\""))
    }

    @Test
    fun `min key fails on an unexpected value`() {
        assertFailsWith<JacksonException> { read<MinKey>("""{"${'$'}minKey":2}""") }
        assertFailsWith<JacksonException> { read<MinKey>("1") }
    }

    @Test
    fun `a problem handler can replace the unexpected values`() {
        val lenientMapper = mapper.rebuild().addHandler(object : DeserializationProblemHandler() {
            override fun handleUnexpectedToken(
                ctxt: DeserializationContext,
                targetType: JavaType,
                t: JsonToken,
                p: JsonParser,
                failureMsg: String?
            ): Any = when (targetType.rawClass) {
                BsonTimestamp::class.java -> BsonTimestamp(1, 2)
                Binary::class.java -> Binary(byteArrayOf(1))
                MaxKey::class.java -> MaxKey()
                MinKey::class.java -> MinKey()
                else -> NOT_HANDLED
            }
        }).build()

        assertEquals(BsonTimestamp(1, 2), lenientMapper.readValue("1", BsonTimestamp::class.java))
        assertEquals(Binary(byteArrayOf(1)), lenientMapper.readValue("1", Binary::class.java))
        assertEquals(MaxKey(), lenientMapper.readValue("1", MaxKey::class.java))
        assertEquals(MinKey(), lenientMapper.readValue("1", MinKey::class.java))
    }

    @Test
    fun `date is read from bson`() {
        val document = BsonDocument("date", BsonDateTime(123))

        assertEquals(Date(123), KMongoConfiguration.bsonMapper.readValue(bson(document), DateValue::class.java).date)
    }

    @Test
    fun `object id is read from bson and written back`() {
        val id = ObjectId()
        val bytes = bson(BsonDocument("id", BsonObjectId(id)))

        val value = KMongoConfiguration.bsonMapper.readValue(bytes, IdValue::class.java)

        assertEquals(id.toHexString(), value.id.toString())
        assertEquals(id.timestamp, value.id.timestamp)
        assertContentEquals(bytes, KMongoConfiguration.bsonMapper.writeValueAsBytes(value))
    }

    @Test
    fun `object id fails on an unexpected bson type`() {
        val document = BsonDocument("id", BsonString("id"))

        assertFailsWith<JacksonException> {
            KMongoConfiguration.bsonMapper.readValue(bson(document), IdValue::class.java)
        }
    }

    @Test
    fun `date fails on an unexpected bson type`() {
        val document = BsonDocument("date", BsonString("date"))

        assertFailsWith<JacksonException> {
            KMongoConfiguration.bsonMapper.readValue(bson(document), DateValue::class.java)
        }
    }

    @Test
    fun `date is read from its epoch milliseconds`() {
        assertEquals(Date(123), read<Date>("123"))
    }
}
