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
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonNull
import org.bson.BsonString
import org.bson.BsonValue
import org.bson.codecs.Codec
import org.bson.codecs.CollectibleCodec
import org.bson.codecs.EncoderContext
import org.litote.kmongo.util.KMongoConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The scalar values and the document ids of [JacksonCodec].
 */
class JacksonCodecTest {

    data class WithId(val _id: String?)

    data class WithoutId(val name: String)

    private fun <T : Any> codec(type: Class<T>): Codec<T> =
        KMongoConfiguration.jacksonCodecProvider.get(type, MongoClientSettings.getDefaultCodecRegistry())!!

    private fun <T : Any> collectibleCodec(type: Class<T>): CollectibleCodec<T> = codec(type) as CollectibleCodec<T>

    private fun <T : Any> encoded(type: Class<T>, value: T?): BsonValue {
        val document = BsonDocument()
        BsonDocumentWriter(document).use { writer ->
            writer.writeStartDocument()
            writer.writeName("v")
            codec(type).encode(writer, value, EncoderContext.builder().build())
            writer.writeEndDocument()
        }
        return document["v"]!!
    }

    @Test
    fun `scalar values keep their BSON type`() {
        assertEquals(BsonDouble(1.5), encoded(Double::class.javaObjectType, 1.5))
        assertEquals(BsonInt32(1), encoded(Int::class.javaObjectType, 1))
        assertEquals(BsonInt64(3_000_000_000), encoded(Long::class.javaObjectType, 3_000_000_000))
        assertEquals(BsonBoolean.TRUE, encoded(Boolean::class.javaObjectType, true))
        assertEquals(BsonString("a"), encoded(String::class.java, "a"))
    }

    @Test
    fun `a null value is written as a BSON null`() {
        assertEquals(BsonNull.VALUE, encoded(String::class.java, null))
    }

    @Test
    fun `the codec exposes its class`() {
        assertEquals(WithId::class.java, codec(WithId::class.java).encoderClass)
    }

    @Test
    fun `the document id is read from the id property`() {
        val codec = collectibleCodec(WithId::class.java)
        assertTrue(codec.documentHasId(WithId("a")))
        assertEquals(BsonString("a"), codec.getDocumentId(WithId("a")))
        assertFailsWith<IllegalStateException> { codec.getDocumentId(WithId(null)) }
    }

    @Test
    fun `a document without id property has no id`() {
        val codec = collectibleCodec(WithoutId::class.java)
        assertFalse(codec.documentHasId(WithoutId("a")))
        assertFailsWith<IllegalStateException> { codec.getDocumentId(WithoutId("a")) }
    }
}
