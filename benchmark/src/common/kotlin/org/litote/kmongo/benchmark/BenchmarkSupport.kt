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

package org.litote.kmongo.benchmark

import com.mongodb.MongoClientSettings
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.BsonDocument
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlinx.BsonConfiguration
import org.bson.codecs.kotlinx.KotlinSerializerCodec
import org.bson.codecs.kotlinx.defaultSerializersModule
import org.bson.conversions.Bson
import org.bson.io.BasicOutputBuffer
import org.litote.kmongo.service.ClassMappingType
import org.litote.kmongo.service.ClassMappingTypeService
import org.litote.kmongo.util.KMongoUtil
import java.nio.ByteBuffer
import java.util.ServiceLoader
import kotlin.reflect.KClass

/**
 * Shared benchmark helpers. The KMongo version and the object mapping are selected by the classpath.
 */
object BenchmarkSupport {

    /** The KMongo codec registry (what the KMongo collections use). */
    val registry: CodecRegistry = ClassMappingType.codecRegistry(KMongoUtil.defaultCodecRegistry)

    /** The plain MongoDB driver registry (baseline, does not depend on KMongo). */
    val driverRegistry: CodecRegistry = MongoClientSettings.getDefaultCodecRegistry()

    /** true for the kotlinx.serialization mapping, false for the Jackson mapping. */
    val serialization: Boolean = ServiceLoader.load(ClassMappingTypeService::class.java)
        .any { it.javaClass.name.contains("Serialization") }

    private val decoderContext: DecoderContext = DecoderContext.builder().build()
    private val encoderContext: EncoderContext = EncoderContext.builder().build()

    const val FRIEND_JSON: String =
        """{"_id": {"${'$'}oid": "507480ff3004983e1fe5ed55"}, "name": "John0", "address": "Address0",
           "coordinate": {"lat": 1, "lng": 0}, "gender": "MALE"}"""

    val FRIEND_WITH_BUDDIES_JSON: String =
        """{"_id": {"${'$'}oid": "507480ff3004983e1fe5ed55"}, "name": "John0", "address": "Address0",
           "coordinate": {"lat": 1, "lng": 0}, "gender": "MALE", "buddies": [""" +
            (1..4).joinToString(", ") {
                """{"name": "John0$it", "address": "Address0", "coordinate": {"lat": 1, "lng": 0}, "buddies": []}"""
            } + "]}"

    /** The BSON bytes of a JSON document. */
    fun bsonBytes(json: String): ByteArray {
        val output = BasicOutputBuffer()
        BsonDocumentCodec().encode(BsonBinaryWriter(output), BsonDocument.parse(json), encoderContext)
        return output.toByteArray()
    }

    /** Decodes BSON bytes (the codec is looked up in the registry, as the driver does for each operation). */
    fun <T : Any> decode(bytes: ByteArray, type: Class<T>, codecRegistry: CodecRegistry = registry): T =
        codecRegistry.get(type).decode(BsonBinaryReader(ByteBuffer.wrap(bytes)), decoderContext)

    /** Encodes a value to BSON (the codec is looked up in the registry, as the driver does for each operation). */
    fun encode(value: Any): BasicOutputBuffer {
        val output = BasicOutputBuffer()
        @Suppress("UNCHECKED_CAST")
        (registry.get(value.javaClass) as Codec<Any>).encode(BsonBinaryWriter(output), value, encoderContext)
        return output
    }

    /**
     * Configuration of the official `bson-kotlinx` codec used by the driverKotlinx* baseline benchmarks:
     * the kmongo-dsl serialization mapping default configuration.
     */
    private val kotlinxConfiguration = BsonConfiguration(
        encodeDefaults = true,
        explicitNulls = true,
        classDiscriminator = "___type"
    )

    /**
     * The official `bson-kotlinx` codec of a class, without KMongo (baseline).
     *
     * Created with the private constructor as kmongo-dsl does: [KotlinSerializerCodec.create] rejects the classes
     * annotated with `@BsonId` (the models are shared with the Jackson mapping).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> kotlinxCodec(type: KClass<T>, serializer: KSerializer<T>): Codec<T> =
        KotlinSerializerCodec::class.java.getDeclaredConstructor(
            KClass::class.java,
            KSerializer::class.java,
            SerializersModule::class.java,
            BsonConfiguration::class.java
        )
            .apply { isAccessible = true }
            .newInstance(type, serializer, defaultSerializersModule, kotlinxConfiguration) as Codec<T>

    /** Decodes BSON bytes with a given codec (no registry lookup). */
    fun <T : Any> decode(bytes: ByteArray, codec: Codec<T>): T =
        codec.decode(BsonBinaryReader(ByteBuffer.wrap(bytes)), decoderContext)

    /** Encodes a value with a given codec (no registry lookup). */
    fun <T : Any> encode(value: T, codec: Codec<T>): BasicOutputBuffer {
        val output = BasicOutputBuffer()
        codec.encode(BsonBinaryWriter(output), value, encoderContext)
        return output
    }

    /** Renders a filter / update / projection... as the driver does before sending it. */
    fun Bson.render(): BsonDocument = toBsonDocument(BsonDocument::class.java, registry)
}
