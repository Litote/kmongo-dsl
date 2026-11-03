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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.MapperBuilder
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.ToStringSerializer
import tools.jackson.module.kotlin.kotlinModule
import com.mongodb.DBRef
import org.bson.UuidRepresentation
import org.bson.types.ObjectId
import org.litote.jackson.registerModulesFromServiceLoader
import org.litote.kmongo.util.KotlinModuleConfiguration
import org.litote.kmongo.util.ObjectMappingConfiguration
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

internal object ObjectMapperFactory {

    private class SetMappingModule : SimpleModule() {
        init {
            addAbstractTypeMapping(Set::class.java, LinkedHashSet::class.java)
        }
    }

    /** The format of the KMongo documents that [MapperBuilder.configureForJackson2] does not restore. */
    private class Jackson2FormatModule : SimpleModule() {
        init {
            // Jackson 3 writes the language tag: fr-FR instead of fr_FR
            addSerializer(Locale::class.java, ToStringSerializer(Locale::class.java))
        }
    }

    private val defaultInclusion: JsonInclude.Include
        get() = if (ObjectMappingConfiguration.serializeNull) ALWAYS else NON_NULL

    private fun JsonMapper.Builder.inclusion(include: JsonInclude.Include): JsonMapper.Builder =
        changeDefaultPropertyInclusion { JsonInclude.Value.construct(include, include) }

    fun createExtendedJsonObjectMapper(): JsonMapper =
        JsonMapper.builder()
            // the Jackson 3 defaults change the BSON format (properties order, dates, enums...)
            .configureForJackson2()
            .addModule(kotlinModule(KotlinModuleConfiguration.kotlinModuleInitializer))
            .addModule(SetMappingModule())
            .addModule(Jackson2FormatModule())
            .addModule(ExtendedJsonModule())
            .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
            .inclusion(defaultInclusion)
            .registerModulesFromServiceLoader()
            .build()

    fun createBsonObjectMapper(uuidRepresentation: UuidRepresentation? = null): JsonMapper =
        configureBson(JsonMapper.builder(KMongoBsonFactory()), uuidRepresentation)

    fun createBsonObjectMapperCopy(uuidRepresentation: UuidRepresentation? = null): JsonMapper =
        configureBson(JsonMapper.builder(), uuidRepresentation)

    private fun configureBson(builder: JsonMapper.Builder, uuidRepresentation: UuidRepresentation?): JsonMapper =
        builder
            // the Jackson 3 defaults change the BSON format (properties order, dates, enums...)
            .configureForJackson2()
            .addModule(de.undercouch.bson4jackson.BsonModule())
            .addModule(kotlinModule(KotlinModuleConfiguration.kotlinModuleInitializer))
            .addModule(CustomJacksonModule)
            .addModule(SetMappingModule())
            .addModule(Jackson2FormatModule())
            .addModule(BsonModule(uuidRepresentation))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
            .inclusion(defaultInclusion)
            .addHandler(StringDeserializationProblemHandler)
            .registerModulesFromServiceLoader()
            .build()

    fun createFilterIdObjectMapper(objectMapper: JsonMapper): JsonMapper =
        objectMapper.rebuild().addModule(FilterIdModule()).build()

    /** A copy of the mapper with this default property inclusion. */
    fun withInclusion(objectMapper: JsonMapper, include: JsonInclude.Include): JsonMapper =
        objectMapper.rebuild().inclusion(include).build()
}

private object CustomJacksonModule : SimpleModule() {

    init {
        addSerializer(DBRef::class.java, object : ValueSerializer<DBRef>() {
            override fun serialize(value: DBRef?, gen: JsonGenerator, serializers: SerializationContext) {
                if (value == null) {
                    gen.writeNull()
                } else {
                    gen.writeStartObject()
                    gen.writeStringProperty("\$ref", value.collectionName)
                    gen.writeName("\$id")
                    val id = value.id
                    when (id) {
                        is String -> gen.writeString(id)
                        is Long -> gen.writeNumber(id)
                        is Int -> gen.writeNumber(id)
                        is Float -> gen.writeNumber(id)
                        is Double -> gen.writeNumber(id)
                        is BigInteger -> gen.writeNumber(id)
                        is BigDecimal -> gen.writeNumber(id)
                        is ObjectId -> gen.writeObjectId(id)
                        else -> error("dbRef with id $id of type ${id.javaClass} is not supported")
                    }
                    if (value.databaseName != null) {
                        gen.writeStringProperty("\$db", value.databaseName)
                    }
                    gen.writeEndObject()
                }
            }
        })
        addDeserializer(DBRef::class.java, object : ValueDeserializer<DBRef>() {
            override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): DBRef? {
                return if (jp.isExpectedStartObjectToken) {
                    jp.nextValue()
                    val ref = jp.valueAsString
                    jp.nextValue()
                    val id = when (jp.currentToken()) {
                        JsonToken.VALUE_EMBEDDED_OBJECT -> jp.embeddedObject
                        JsonToken.VALUE_STRING -> jp.valueAsString
                        else -> jp.decimalValue
                    }
                    var db: String? = null
                    while (jp.currentToken() != JsonToken.END_OBJECT) {
                        if (jp.currentName() == "\$db") {
                            db = jp.valueAsString
                        }
                        jp.nextToken()
                    }
                    DBRef(db, ref, id)
                } else {
                    null
                }
            }
        })
    }

}
