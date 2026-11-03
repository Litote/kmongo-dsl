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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

/**
 * A `@JacksonData` class.
 *
 * @param className the class
 * @param properties the serialized properties, in declaration order
 */
internal class JacksonClassModel(
    val className: ClassName,
    val properties: List<JacksonPropertyModel>,
)

/**
 * A serialized property.
 *
 * @param name the Kotlin name
 * @param jsonName the JSON field name (`@JsonProperty`)
 * @param type the Kotlin type
 * @param kind how the value is written / read
 * @param javaType the Jackson type of a [ValueKind.COLLECTION] or a [ValueKind.MAP]
 * @param accessible true if the property can be read from the generated code (false: reflection)
 * @param constructorParameter true if the property is a parameter of the primary constructor
 * @param writable true if the property has a setter accessible from the generated code
 */
internal data class JacksonPropertyModel(
    val name: String,
    val jsonName: String,
    val type: TypeName,
    val kind: ValueKind,
    val javaType: JavaTypeModel?,
    val accessible: Boolean,
    val constructorParameter: Boolean,
    val writable: Boolean,
)

/**
 * How a value is written ([writeMethod]) and read ([readProperty]) with the Jackson streaming API.
 */
internal enum class ValueKind(val writeMethod: String?, val readProperty: String?) {
    STRING("writeString", "string"),
    BOOLEAN("writeBoolean", "booleanValue"),
    INT("writeNumber", "intValue"),
    LONG("writeNumber", "longValue"),
    SHORT("writeNumber", "shortValue"),
    FLOAT("writeNumber", "floatValue"),
    DOUBLE("writeNumber", "doubleValue"),
    BIG_INTEGER("writeNumber", "bigIntegerValue"),
    BIG_DECIMAL("writeNumber", "decimalValue"),
    COLLECTION(null, null),
    MAP(null, null),
    OTHER(null, null),
}

/**
 * The Jackson type of a value (built with the `TypeFactory` of the serializer provider).
 */
internal sealed interface JavaTypeModel {
    /** The raw class. */
    val rawClass: ClassName

    /** A simple type. */
    class Simple(override val rawClass: ClassName) : JavaTypeModel

    /** A collection type. */
    class Collection(override val rawClass: ClassName, val element: JavaTypeModel) : JavaTypeModel

    /** A map type. */
    class Map(override val rawClass: ClassName, val key: JavaTypeModel, val value: JavaTypeModel) : JavaTypeModel
}
