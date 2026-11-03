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

import com.mongodb.MongoClientSettings
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.kotlinx.KotlinSerializerCodec
import org.litote.kmongo.model.Friend
import org.litote.kmongo.util.KMongoClientSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * The official driver registry provides `bson-kotlinx` codecs for `@Serializable` classes:
 * KMongo must always use its own object mapping.
 */
class KMongoClientSettingsTest {

    private fun assertKMongoCodec(codec: Any) {
        assertFalse(codec is KotlinSerializerCodec<*>, "bson-kotlinx codec used: $codec")
        assertIs<SerializationCodec<*>>(codec)
    }

    @Test
    fun `official driver registry provides bson-kotlinx codecs`() {
        assertIs<KotlinSerializerCodec<*>>(MongoClientSettings.getDefaultCodecRegistry().get(Friend::class.java))
    }

    @Test
    fun `KMongo mapping is used with the driver default registry`() {
        assertKMongoCodec(KMongoClientSettings.configureRegistry().get(Friend::class.java))
    }

    @Test
    fun `KMongo mapping is used with a custom registry containing the driver default registry`() {
        val registry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry())
        assertKMongoCodec(KMongoClientSettings.configureRegistry(registry).get(Friend::class.java))
    }

    @Test
    fun `KMongo mapping is used with settings built from the driver defaults`() {
        val settings = KMongoClientSettings.settings(MongoClientSettings.builder().build())
        assertKMongoCodec(settings.codecRegistry.get(Friend::class.java))
    }

    @Test
    fun `base codecs are still provided`() {
        val registry = KMongoClientSettings.configureRegistry(
            CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry())
        )
        assertEquals("org.bson.codecs.DocumentCodec", registry.get(org.bson.Document::class.java).javaClass.name)
    }
}
