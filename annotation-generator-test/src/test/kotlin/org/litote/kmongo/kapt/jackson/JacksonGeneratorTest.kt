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

package org.litote.kmongo.kapt.jackson

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.ToStringSerializer
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import org.litote.jackson.JacksonModuleServiceLoader
import org.litote.kmongo.KMongo
import org.litote.kmongo.getCollection
import org.litote.kmongo.kapt.jackson.model.Annotated
import org.litote.kmongo.kapt.jackson.model.Annotated_
import org.litote.kmongo.kapt.jackson.model.Child
import org.litote.kmongo.kapt.jackson.model.Collections
import org.litote.kmongo.kapt.jackson.model.Color
import org.litote.kmongo.kapt.jackson.model.Generic
import org.litote.kmongo.kapt.jackson.model.GeneratedNames
import org.litote.kmongo.kapt.jackson.model.Keywords
import org.litote.kmongo.kapt.jackson.model.Outer
import org.litote.kmongo.kapt.jackson.model.Registered
import org.litote.kmongo.kapt.jackson.model.Scalars
import org.litote.kmongo.newId
import org.litote.kmongo.testcontainers.KMongoTestServer
import org.litote.kmongo.util.KMongoConfiguration
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.Locale
import java.util.ServiceLoader
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JacksonGeneratorTest {

    /** The kmongo-dsl Jackson mapping (JSON format). */
    private val mapper: ObjectMapper = KMongoConfiguration.bsonMapperCopy

    /** Jackson Kotlin module only, with the KMongo locale format: the reference serialization. */
    private val defaultMapper: ObjectMapper = jsonMapper {
        configureForJackson2()
        addModule(kotlinModule())
        addModule(SimpleModule().addSerializer(Locale::class.java, ToStringSerializer(Locale::class.java)))
    }

    private val scalars = Scalars(
        string = "a",
        nullableString = null,
        booleanValue = true,
        intValue = 1,
        longValue = 2,
        shortValue = 3,
        floatValue = 4.5f,
        doubleValue = 6.5,
        bigInteger = BigInteger.TEN,
        bigDecimal = BigDecimal("1.25"),
        nullableInt = null,
        byteValue = 7,
        charValue = 'c',
        color = Color.BLUE,
        instant = Instant.ofEpochMilli(1000),
        locale = Locale.FRANCE,
    )

    private val collections = Collections(
        list = listOf(scalars, scalars.copy(nullableString = "b", nullableInt = 8)),
        set = setOf("a", "b"),
        linkedSet = linkedSetOf(Color.RED, Color.BLUE),
        mutableList = mutableListOf(1, null, 3),
        map = mapOf("a" to listOf(1, 2), "b" to emptyList()),
        nullableList = listOf("a", null),
        nested = listOf(listOf(1L), listOf(2L, 3L)),
        star = mapOf("a" to "b", "c" to 1),
        array = arrayOf("x", "y"),
    )

    private fun generatedClass(className: String): Class<*>? =
        runCatching { Class.forName("org.litote.kmongo.kapt.jackson.model.$className") }.getOrNull()

    private fun serializerOf(type: KClass<*>): String =
        mapper._serializationContext().findValueSerializer(type.java).javaClass.simpleName

    /** BSON round trip (as with MongoDB). */
    private inline fun <reified T : Any> roundTrip(value: T): T =
        KMongoConfiguration.bsonMapper.run { readValue(writeValueAsBytes(value)) }

    @Test
    fun `serializers are generated and registered with the service loader`() {
        val loaders = ServiceLoader.load(JacksonModuleServiceLoader::class.java).map { it.javaClass.simpleName }.toSet()
        listOf("Scalars", "Collections", "Annotated", "Outer", "Inner", "Registered").forEach {
            assertTrue("${it}_Serializer" in loaders, "$it serializer: $loaders")
            assertTrue("${it}_Deserializer" in loaders, "$it deserializer: $loaders")
        }
        assertEquals("Scalars_Serializer", serializerOf(Scalars::class))
        assertEquals("Inner_Serializer", serializerOf(Outer.Inner::class))
    }

    @Test
    fun `unsupported classes are skipped`() {
        assertNull(generatedClass("Child_Serializer"))
        assertNull(generatedClass("Generic_Serializer"))
        assertEquals(Child::class.java, roundTrip(Child("a")).javaClass)
        assertEquals(Generic("a"), mapper.readValue<Generic<String>>(mapper.writeValueAsString(Generic("a"))))
    }

    @Test
    fun `the generated serialization is the Jackson default serialization`() {
        // no java.time module in the default mapper
        val scalars = scalars.copy(instant = null)
        val collections = collections.copy(list = listOf(scalars))
        listOf(scalars, collections, Outer(Outer.Inner(1), listOf(Outer.Inner(2))), Registered("r")).forEach {
            assertEquals(defaultMapper.writeValueAsString(it), mapper.writeValueAsString(it))
        }
    }

    @Test
    fun `round trip`() {
        assertEquals(scalars, roundTrip(scalars))
        assertEquals(collections, roundTrip(collections))
        assertEquals(Outer(Outer.Inner(1), listOf(Outer.Inner(2))), roundTrip(Outer(Outer.Inner(1), listOf(Outer.Inner(2)))))
        assertEquals(Registered("r"), roundTrip(Registered("r")))
    }

    @Test
    fun `properties named as the variables of the generated code`() {
        val value = GeneratedNames("r", 1, null, mapOf("a" to "b"), "p")
        assertEquals("GeneratedNames_Serializer", serializerOf(GeneratedNames::class))
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun `properties named with a Java keyword`() {
        val value = Keywords("a", 1, null, 2)
        assertEquals("Keywords_Serializer", serializerOf(Keywords::class))
        assertEquals(value, roundTrip(value))
        // no field in the KAPT stubs: last properties
        assertEquals(
            """{"name":"a","value":2,"int":1,"default":null}""",
            mapper.writeValueAsString(value),
        )
    }

    @Test
    fun `the deserialized collections have the declared types`() {
        val result = roundTrip(collections)
        assertEquals<Class<*>>(LinkedHashSet::class.java, result.linkedSet.javaClass)
        assertEquals(Color.RED, result.linkedSet.first())
    }

    @Test
    fun `annotations, private, internal and body properties`() {
        val value = Annotated(
            id = newId(),
            ignored = "ignored",
            fieldRenamed = "field",
            getterRenamed = "getter",
            secret = "secret",
            internalValue = 1,
        ).apply {
            bodyVar = "updated"
            transientValue = "updated"
        }
        val json = mapper.readTree(mapper.writeValueAsString(value)) as ObjectNode
        assertEquals(
            listOf(
                "_id", "renamedField", "renamedGetter", "secret", "internalValue", "withDefault",
                "nullableWithDefault", "bodyVar", "bodyVal",
            ),
            json.propertyNames().toList(),
        )
        assertEquals("secret", json["secret"].asString())

        val result = mapper.treeToValue(json, Annotated::class.java)
        assertEquals(value.copy(ignored = "default"), result)
        assertEquals(result, roundTrip(value).copy(ignored = "default"))
        assertEquals("secret", result.secret())
        assertEquals("updated", result.bodyVar)
        assertEquals("transient", result.transientValue)
    }

    @Test
    fun `missing, null and unknown fields`() {
        val id = newId<Annotated>()
        val json = """
            {
              "unknownObject": {"a": {"b": [1, 2]}},
              "_id": ${mapper.writeValueAsString(id)},
              "unknownArray": [{"a": 1}, [2]],
              "renamedField": "field",
              "renamedGetter": "getter",
              "secret": "secret",
              "nullableWithDefault": null,
              "unknown": "value",
              "internalValue": 2
            }
        """.trimIndent()
        val result = mapper.readValue<Annotated>(json)
        assertEquals(id, result.id)
        assertEquals("default", result.withDefault)
        assertNull(result.nullableWithDefault)
        assertEquals("init", result.bodyVar)
        assertEquals(2, result.internalValue)
    }

    @Test
    fun `the property paths and the serializers are generated in the same KAPT execution`() {
        assertEquals("Annotated_", Annotated_::class.simpleName)
    }

    @Test
    fun `MongoDB round trip`() {
        KMongo.createClient(KMongoTestServer.connectionString).use { client ->
            val collection = client.getDatabase(KMongoTestServer.newDatabaseName("jackson_generator"))
                .getCollection<Annotated>()
            val value = Annotated(
                id = newId(),
                fieldRenamed = "field",
                getterRenamed = "getter",
                secret = "secret",
                internalValue = 1,
            )
            collection.insertOne(value)
            assertEquals(listOf(value), collection.find().toList())
        }
    }
}
