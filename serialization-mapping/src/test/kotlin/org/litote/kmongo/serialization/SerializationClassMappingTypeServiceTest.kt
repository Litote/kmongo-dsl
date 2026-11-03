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

import com.mongodb.MongoClientSettings.getDefaultCodecRegistry
import kotlinx.serialization.Serializable
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.Document
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.litote.kmongo.util.KMongoUtil
import org.litote.kmongo.util.ObjectMappingConfiguration
import kotlin.test.assertEquals

/**
 *
 */
class SerializationClassMappingTypeServiceTest {

    @Serializable
    data class Value(val _id: String, val name: String, val nullable: String? = null)

    @kotlin.test.Test
    fun `an object is converted to extended json before the creation of a client`() {
        val value = Value("id", "a")
        val initialized = SerializationClassMappingTypeService()
            .apply { coreCodecRegistry(KMongoUtil.defaultCodecRegistry) }

        val json = SerializationClassMappingTypeService().toExtendedJson(value)

        assertEquals(initialized.toExtendedJson(value), json)
        assertEquals(BsonDocument.parse("""{"_id": "id", "name": "a"}"""), BsonDocument.parse(json))
    }

    @kotlin.test.Test
    fun `an object is converted to bson before the creation of a client`() {
        val value = Value("id", "a")

        assertEquals(
            BsonDocument.parse("""{"name": "a"}"""),
            SerializationClassMappingTypeService().filterIdToBson(value, filterNullProperties = true),
        )
        assertEquals(
            BsonDocument.parse("""{"name": "a", "nullable": null}"""),
            SerializationClassMappingTypeService().filterIdToBson(value, filterNullProperties = false),
        )
    }

    @kotlin.test.Test
    fun `the core codec registry follows the null serialization configuration`() {
        val registry = SerializationClassMappingTypeService().coreCodecRegistry(getDefaultCodecRegistry())
        val serializeNull = ObjectMappingConfiguration.serializeNull
        try {
            listOf(true, false).forEach {
                ObjectMappingConfiguration.serializeNull = it
                assertEquals(Document::class.java, registry.get(Document::class.java).encoderClass)
            }
        } finally {
            ObjectMappingConfiguration.serializeNull = serializeNull
        }
    }

    @kotlin.test.Test
    fun `encode and decode document`() {
        val doc = Document()
        doc["a"] = "b"
        val codec =
            SerializationClassMappingTypeService().codecRegistry(getDefaultCodecRegistry()).get(Document::class.java)
        val document = BsonDocument()
        val writer = BsonDocumentWriter(document)
        codec.encode(writer, doc, EncoderContext.builder().build())

        println(document)
        val newFriend = codec.decode(BsonDocumentReader(document), DecoderContext.builder().build())

        assertEquals(doc, newFriend)
    }
}
