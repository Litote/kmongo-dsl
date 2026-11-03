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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import org.bson.BsonReader
import org.bson.BsonValue
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.CollectibleCodec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlinx.BsonConfiguration
import org.bson.codecs.kotlinx.KotlinSerializerCodec
import org.litote.kmongo.service.ClassMappingType
import org.litote.kmongo.util.KMongoUtil.getIdBsonValue
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Caches the official [KotlinSerializerCodec] instances (one per serializer / configuration / module).
 */
internal object KotlinSerializerCodecCache {

    private val logger = System.getLogger(KotlinSerializerCodecCache::class.java.name)

    private const val MAX_SIZE = 4096

    private data class Key(
        val serializer: KSerializer<*>,
        val configuration: BsonConfiguration,
        val module: SerializersModule
    )

    private val cache = ConcurrentHashMap<Key, Codec<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> codec(
        kClass: KClass<T>,
        serializer: KSerializer<T>,
        module: SerializersModule,
        configuration: BsonConfiguration
    ): Codec<T> {
        if (cache.size > MAX_SIZE) {
            cache.clear()
        }
        return cache.getOrPut(Key(serializer, configuration, module)) {
            createWithoutAnnotationValidation(kClass, serializer, module, configuration)
        } as Codec<T>
    }

    /**
     * [KotlinSerializerCodec.create] rejects classes annotated with `@BsonId`, `@BsonProperty`... annotations,
     * but KMongo models are often shared by several object mappings (Jackson & kotlinx.serialization)
     * and KMongo 4/5 ignored these annotations with kotlinx.serialization.
     * So the private constructor is used when available.
     */
    internal val unvalidatedConstructor by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            KotlinSerializerCodec::class.java.getDeclaredConstructor(
                KClass::class.java,
                KSerializer::class.java,
                SerializersModule::class.java,
                BsonConfiguration::class.java
            ).apply { isAccessible = true }
        } catch (e: Exception) {
            logger.log(
                System.Logger.Level.WARNING,
                "KotlinSerializerCodec private constructor not found (bson-kotlinx version not supported?): " +
                        "classes annotated with @BsonId, @BsonProperty... can not be serialized with kotlinx.serialization",
                e
            )
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> createWithoutAnnotationValidation(
        kClass: KClass<T>,
        serializer: KSerializer<T>,
        module: SerializersModule,
        configuration: BsonConfiguration
    ): Codec<T> {
        val constructor = unvalidatedConstructor
        return if (constructor != null) {
            constructor.newInstance(kClass, serializer, module, configuration) as Codec<T>
        } else {
            KotlinSerializerCodec.create(kClass, serializer, module, configuration)
        }
    }
}

/**
 * KMongo [CollectibleCodec] backed by the official `bson-kotlinx` encoder & decoder.
 */
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
internal class SerializationCodec<T : Any>(
    private val clazz: KClass<T>,
    private val configuration: BsonConfiguration
) : CollectibleCodec<T> {

    @Suppress("UNCHECKED_CAST")
    private val idProperty: KProperty1<T, *>? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ClassMappingType.findIdProperty(clazz) as KProperty1<T, *>?
    }

    private val decoderSerializer: KSerializer<T> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KMongoSerializationRepository.getSerializer(clazz)
    }

    /** A [KotlinSerializerCodecCache] entry. */
    private class CachedCodec<T : Any>(
        val serializer: KSerializer<T>,
        val module: SerializersModule,
        val codec: Codec<T>
    )

    // the last codecs used: avoids the KotlinSerializerCodecCache lookup (key allocation and hashing) at each call
    @Volatile
    private var lastEncoder: CachedCodec<T>? = null

    @Volatile
    private var lastDecoder: CachedCodec<T>? = null

    private fun codec(serializer: KSerializer<T>, last: CachedCodec<T>?): CachedCodec<T> {
        val module = KMongoSerializationRepository.module
        return if (last != null && last.serializer === serializer && last.module === module) {
            last
        } else {
            CachedCodec(serializer, module, KotlinSerializerCodecCache.codec(clazz, serializer, module, configuration))
        }
    }

    override fun getEncoderClass(): Class<T> = clazz.java

    override fun encode(writer: BsonWriter, value: T, encoderContext: EncoderContext) {
        val last = lastEncoder
        val codec = codec(KMongoSerializationRepository.getSerializer(clazz, value), last)
        if (codec !== last) {
            lastEncoder = codec
        }
        codec.codec.encode(writer, value, encoderContext)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): T {
        val last = lastDecoder
        val codec = codec(decoderSerializer, last)
        if (codec !== last) {
            lastDecoder = codec
        }
        return codec.codec.decode(reader, decoderContext)
    }

    override fun getDocumentId(document: T): BsonValue =
        idProperty?.let {
            val idValue = getIdBsonValue(it.get(document))
            idValue ?: error("$clazz has null id")
        } ?: error("$clazz has no id field")

    override fun documentHasId(document: T): Boolean = idProperty != null

    override fun generateIdIfAbsentFromDocument(document: T): T {
        idProperty?.also {
            @Suppress("UNCHECKED_CAST")
            val idValue = ClassMappingType.getIdValue(it as KProperty1<Any, Any>, document)
            if (idValue == null) {
                @Suppress("UNCHECKED_CAST")
                idController.setIdValue(it as KProperty1<T, Any>, document)
            }
        }
        return document
    }
}

/**
 * Registry that returns a [SerializationCodec] for every class.
 */
internal class SerializationCodecRegistry(private val configuration: BsonConfiguration) : CodecRegistry {

    private val codecs = ConcurrentHashMap<Class<*>, Codec<*>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(clazz: Class<T>): Codec<T> =
        codecs.getOrPut(clazz) { SerializationCodec(clazz.kotlin, configuration) } as Codec<T>

    override fun <T : Any> get(clazz: Class<T>, codecRegistry: CodecRegistry): Codec<T> = get(clazz)
}
