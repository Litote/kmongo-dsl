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

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.types.ObjectId
import org.litote.kmongo.Id
import org.litote.kmongo.golden.AbstractGoldenFormatTest
import org.litote.kmongo.golden.GoldenAddress
import org.litote.kmongo.golden.GoldenColor
import org.litote.kmongo.golden.GoldenMaps
import org.litote.kmongo.golden.goldenSamples
import org.litote.kmongo.id.toId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checks the kotlinx.serialization mapping BSON format against KMongo 5.12 (kbson).
 */
class GoldenFormatTest : AbstractGoldenFormatTest("serialization", serialization = true) {

    override val knownDifferences: Map<String, String> = mapOf(
        "maps.json" to MAP_ID_KEYS,
        "maps-without-nulls.json" to MAP_ID_KEYS,
    )

    /** [GoldenMaps] with the kmongo-dsl map key serializer. */
    @Suppress("ConstructorParameterNaming")
    @Serializable
    data class GoldenMapsKMongoDsl(
        @Contextual val _id: ObjectId,
        val byColor: Map<GoldenColor, Int>,
        val byString: Map<String, GoldenAddress>,
        val byId: Map<@Serializable(with = IdBsonSerializer::class) Id<GoldenAddress>, String>,
        val nested: Map<String, Map<String, Long>>
    )

    @Test
    fun `maps with Id keys have the KMongo 5 format with IdBsonSerializer`() {
        val sample = goldenSamples(true).getValue("maps") as GoldenMaps
        val value = GoldenMapsKMongoDsl(sample._id, sample.byColor, sample.byString, sample.byId, sample.nested)
        val golden = BsonDocument.parse(javaClass.getResource("/golden/serialization/maps.json")!!.readText())
        val codec = SerializationCodec(GoldenMapsKMongoDsl::class, configuration)

        val document = BsonDocument()
        codec.encode(BsonDocumentWriter(document), value, EncoderContext.builder().build())
        assertEquals(golden, document)
        assertEquals(value, codec.decode(BsonDocumentReader(golden), DecoderContext.builder().build()))
        assertEquals(ObjectId("5f1e9d6c8a1b2c3d4e5f6a7d").toId<GoldenAddress>(), value.byId.keys.first())
    }

    private companion object {
        const val MAP_ID_KEYS =
            "bson-kotlinx does not support contextual map keys: use @Serializable(with = IdBsonSerializer::class)"
    }
}
