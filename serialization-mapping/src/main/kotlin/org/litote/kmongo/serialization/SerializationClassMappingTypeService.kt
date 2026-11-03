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

import kotlinx.serialization.SerialName
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.codecs.Codec
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.json.JsonMode
import org.bson.json.JsonWriter
import org.bson.json.JsonWriterSettings
import org.litote.kmongo.id.MongoId
import org.litote.kmongo.id.MongoProperty
import org.litote.kmongo.service.ClassMappingTypeService
import org.litote.kmongo.util.KMongoUtil
import org.litote.kmongo.util.ObjectMappingConfiguration
import java.io.StringWriter
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

private class BaseRegistryWithoutCustomSerializers(private val codecRegistry: CodecRegistry) : CodecRegistry {
    override fun <T : Any> get(clazz: Class<T>): Codec<T>? =
        if (clazz.isEnum || customSerializersMap.containsKey(clazz.kotlin)) null else codecRegistry.get(clazz)

    override fun <T : Any> get(clazz: Class<T>, registry: CodecRegistry): Codec<T>? =
        if (clazz.isEnum || customSerializersMap.containsKey(clazz.kotlin)) null else codecRegistry.get(clazz, registry)
}

/**
 * kotlinx serialization ClassMapping.
 */
public class SerializationClassMappingTypeService : ClassMappingTypeService {

    override fun priority(): Int = 200

    @Volatile
    private lateinit var codecRegistryWithNonEncodeNull: CodecRegistry

    @Volatile
    private lateinit var codecRegistryWithEncodeNull: CodecRegistry

    /**
     * The registries are set by [coreCodecRegistry] at the creation of a client: without client, the default ones are used.
     */
    private fun initCodecRegistries() {
        if (!::codecRegistryWithNonEncodeNull.isInitialized || !::codecRegistryWithEncodeNull.isInitialized) {
            coreCodecRegistry(KMongoUtil.defaultCodecRegistry)
        }
    }

    override fun filterIdToBson(obj: Any, filterNullProperties: Boolean): BsonDocument {
        val doc = BsonDocument()
        val writer = BsonDocumentWriter(doc)

        initCodecRegistries()
        (if (filterNullProperties) codecRegistryWithNonEncodeNull else codecRegistryWithEncodeNull)
            .get(obj.javaClass).encode(writer, obj, EncoderContext.builder().build())

        writer.flush()

        doc.remove("_id")
        return doc
    }

    override fun toExtendedJson(obj: Any?): String {
        return if (obj == null) {
            "null"
        } else {
            val writer = StringWriter()
            val jsonWriter = JsonWriter(
                writer,
                JsonWriterSettings
                    .builder()
                    .indent(false)
                    .outputMode(JsonMode.RELAXED)
                    .build()
            )
            //create a fake document to bypass bson writer built-in checks
            jsonWriter.writeStartDocument()
            jsonWriter.writeName("tmp")
            initCodecRegistries()
            codecRegistryWithNonEncodeNull.get(obj.javaClass).encode(jsonWriter, obj, EncoderContext.builder().build())
            jsonWriter.writeEndDocument()
            writer.toString().run {
                substring("{ \"tmp\":".length, length - "}".length).trim()
            }
        }
    }

    override fun findIdProperty(type: KClass<*>): KProperty1<*, *>? = idController.findIdProperty(type)

    override fun <T, R> getIdValue(idProperty: KProperty1<T, R>, instance: T): R? =
        idController.getIdValue(idProperty, instance)

    override fun coreCodecRegistry(baseCodecRegistry: CodecRegistry): CodecRegistry {
        val withNonEncodeNull = SerializationCodecRegistry(configuration.copy(explicitNulls = false))
        codecRegistryWithNonEncodeNull =
            codecRegistryWithCustomCodecs(
                filterBaseCodecRegistry(baseCodecRegistry),
                withNonEncodeNull
            )
        val withEncodeNull = SerializationCodecRegistry(configuration.copy(explicitNulls = true))
        codecRegistryWithEncodeNull = codecRegistryWithCustomCodecs(
            filterBaseCodecRegistry(baseCodecRegistry),
            withEncodeNull
        )
        return object : CodecRegistry {
            override fun <T : Any> get(clazz: Class<T>): Codec<T> =
                if (ObjectMappingConfiguration.serializeNull) withEncodeNull[clazz]
                else withNonEncodeNull[clazz]


            override fun <T : Any> get(clazz: Class<T>, registry: CodecRegistry): Codec<T> =
                if (ObjectMappingConfiguration.serializeNull) withEncodeNull[clazz, registry]
                else withNonEncodeNull[clazz, registry]
        }
    }

    override fun filterBaseCodecRegistry(baseCodecRegistry: CodecRegistry): CodecRegistry =
        BaseRegistryWithoutCustomSerializers(baseCodecRegistry)

    override fun <T> calculatePath(property: KProperty<T>): String =
        property.findAnnotation<SerialName>()?.value
            ?: (if (property.hasAnnotation<MongoId>()) "_id" else property.findAnnotation<MongoProperty>()?.value)
            ?: property.name
}
