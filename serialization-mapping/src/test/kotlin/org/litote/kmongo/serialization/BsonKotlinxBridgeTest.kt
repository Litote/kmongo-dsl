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
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * KMongo relies on a few `bson-kotlinx` internals (accessed by reflection).
 * These tests fail explicitly if a new `bson-kotlinx` version breaks these assumptions.
 */
class BsonKotlinxBridgeTest {

    /**
     * Checks that the bridge finds the underlying reader & writer of the official encoder & decoder.
     */
    object BridgeCheckSerializer : KSerializer<String> {
        var writerFound = false
        var readerFound = false

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BridgeCheck", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: String) {
            writerFound = BsonKotlinxBridge.writer(encoder) != null
            encoder.encodeString(value)
        }

        override fun deserialize(decoder: Decoder): String {
            readerFound = BsonKotlinxBridge.reader(decoder) != null
            return decoder.decodeString()
        }
    }

    @Serializable
    data class BridgeCheck(@Serializable(with = BridgeCheckSerializer::class) val value: String)

    @Test
    fun `bson-kotlinx encoder and decoder expose their writer and reader`() {
        val codec = SerializationCodec(BridgeCheck::class, configuration)
        val document = BsonDocument()
        codec.encode(BsonDocumentWriter(document), BridgeCheck("a"), EncoderContext.builder().build())
        assertTrue(BridgeCheckSerializer.writerFound, "BsonEncoder writer field not found")

        val decoded = codec.decode(BsonDocumentReader(document), DecoderContext.builder().build())
        assertTrue(BridgeCheckSerializer.readerFound, "BsonDecoder reader field not found")
        assertEquals(BridgeCheck("a"), decoded)
    }

    @Test
    fun `KotlinSerializerCodec private constructor is available`() {
        assertNotNull(
            KotlinSerializerCodecCache.unvalidatedConstructor,
            "KotlinSerializerCodec(KClass, KSerializer, SerializersModule, BsonConfiguration) constructor not found"
        )
    }

    @Serializable
    sealed class Shape

    @Serializable
    data class Circle(val radius: Int) : Shape()

    @Test
    fun `runtime built serializers are cached`() {
        assertSame(
            KMongoSerializationRepository.getSerializer(Shape::class, Circle(1)),
            KMongoSerializationRepository.getSerializer(Shape::class, Circle(2))
        )
        assertSame(
            KMongoSerializationRepository.getSerializer(Any::class),
            KMongoSerializationRepository.getSerializer(Any::class)
        )
        val pair: Any = 1 to "a"
        @Suppress("UNCHECKED_CAST")
        assertSame(
            KMongoSerializationRepository.getSerializer(Pair::class as kotlin.reflect.KClass<Any>, pair),
            KMongoSerializationRepository.getSerializer(Pair::class as kotlin.reflect.KClass<Any>, 2 to "b")
        )
    }

    @Test
    fun `codec registry caches codecs`() {
        val registry = SerializationCodecRegistry(configuration)
        assertSame(registry.get(BridgeCheck::class.java), registry.get(BridgeCheck::class.java))
    }
}
