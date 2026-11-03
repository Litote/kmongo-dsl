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

package org.litote.kmongo.util

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoDriverInformation
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecConfigurationException
import org.bson.codecs.configuration.CodecRegistry
import org.litote.kmongo.service.ClassMappingType

/**
 * Builds the [MongoClientSettings] used by KMongo DSL clients (sync & coroutine).
 */
public object KMongoClientSettings {

    /**
     * The driver information sent to the server.
     */
    public val driverInformation: MongoDriverInformation =
        MongoDriverInformation.builder()
            .driverName("kmongo-dsl")
            .driverPlatform("Kotlin/${KotlinVersion.CURRENT}")
            .build()

    /**
     * Returns default KMongo settings for the given connection string.
     */
    public fun settings(connectionString: ConnectionString): MongoClientSettings =
        settings(
            MongoClientSettings
                .builder()
                .codecRegistry(KMongoUtil.defaultCodecRegistry)
                .applyConnectionString(connectionString)
                .build()
        )

    /**
     * Returns the [settings] with the KMongo codec registry.
     */
    public fun settings(settings: MongoClientSettings): MongoClientSettings =
        MongoClientSettings.builder(settings)
            .codecRegistry(configureRegistry(settings.codecRegistry))
            .build()

    /**
     * Adds the KMongo object mapping to the [codecRegistry].
     *
     * The official driver default registry is replaced by [KMongoUtil.defaultCodecRegistry].
     * For other registries, the `bson-kotlin` / `bson-kotlinx` data class codecs are filtered out,
     * so that the KMongo object mapping is always used.
     */
    public fun configureRegistry(codecRegistry: CodecRegistry = MongoClientSettings.getDefaultCodecRegistry()): CodecRegistry =
        ClassMappingType.codecRegistry(
            if (codecRegistry === MongoClientSettings.getDefaultCodecRegistry()) KMongoUtil.defaultCodecRegistry
            else WithoutKotlinDriverCodecs(codecRegistry)
        )

    /**
     * The codecs of the official driver Kotlin object mappings (`bson-kotlin` & `bson-kotlinx`).
     */
    internal val kotlinDriverObjectCodecs: Set<String> = setOf(
        "org.bson.codecs.kotlin.DataClassCodec",
        "org.bson.codecs.kotlinx.KotlinSerializerCodec"
    )

    internal class WithoutKotlinDriverCodecs(private val registry: CodecRegistry) : CodecRegistry {

        private fun <T> Codec<T>?.filter(): Codec<T>? = this?.takeUnless { it.javaClass.name in kotlinDriverObjectCodecs }

        override fun <T : Any> get(clazz: Class<T>): Codec<T> =
            get(clazz, this) ?: throw CodecConfigurationException("Can't find a codec for $clazz.")

        override fun <T : Any> get(clazz: Class<T>, registry: CodecRegistry): Codec<T>? =
            try {
                this.registry.get(clazz, registry).filter()
            } catch (e: CodecConfigurationException) {
                // the bson-kotlin DataClassCodecProvider throws a CodecConfigurationException
                // when a property type of a KMongo model has no codec in the base registry (Id, for example):
                // these classes are managed by the KMongo object mapping
                null
            }
    }
}
