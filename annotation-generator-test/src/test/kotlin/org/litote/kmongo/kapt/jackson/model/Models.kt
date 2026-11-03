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

package org.litote.kmongo.kapt.jackson.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.litote.jackson.data.JacksonData
import org.litote.kmongo.Data
import org.litote.kmongo.Id
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.Locale

enum class Color { RED, BLUE }

@JacksonData
data class Scalars(
    val string: String,
    val nullableString: String?,
    val booleanValue: Boolean,
    val intValue: Int,
    val longValue: Long,
    val shortValue: Short,
    val floatValue: Float,
    val doubleValue: Double,
    val bigInteger: BigInteger,
    val bigDecimal: BigDecimal,
    val nullableInt: Int?,
    val byteValue: Byte,
    val charValue: Char,
    val color: Color,
    val instant: Instant?,
    val locale: Locale,
)

/** The properties named with a Java keyword have no field in the KAPT stubs. */
@JacksonData
data class Keywords(val name: String, val int: Int, val default: String?, val value: Long)

/** The property names of the generated code variables. */
@JacksonData
data class GeneratedNames(val result: String, val token: Int, val fieldName: String?, val map: Map<String, String>, val p: String)

@JacksonData
data class Collections(
    val list: List<Scalars>,
    val set: Set<String>,
    val linkedSet: LinkedHashSet<Color>,
    val mutableList: MutableList<Int?>,
    val map: Map<String, List<Int>>,
    val nullableList: List<String?>?,
    val nested: List<List<Long>>,
    val star: Map<String, *>,
    val array: Array<String>,
) {
    override fun equals(other: Any?): Boolean =
        other is Collections && list == other.list && set == other.set && linkedSet == other.linkedSet &&
                mutableList == other.mutableList && map == other.map && nullableList == other.nullableList &&
                nested == other.nested && star == other.star && array.contentEquals(other.array)

    override fun hashCode(): Int = list.hashCode()
}

@Data
@JacksonData
data class Annotated(
    @JsonProperty("_id") val id: Id<Annotated>,
    @JsonIgnore val ignored: String = "default",
    @field:JsonProperty("renamedField") val fieldRenamed: String,
    @get:JsonProperty("renamedGetter") val getterRenamed: String,
    private val secret: String,
    internal val internalValue: Int,
    val withDefault: String = "default",
    val nullableWithDefault: String? = "default",
) {
    var bodyVar: String = "init"
    val bodyVal: String = "body-$withDefault"

    @Transient
    var transientValue: String = "transient"

    val computed: String get() = "computed"

    val lazyValue: String by lazy { "lazy" }

    fun secret(): String = secret
}

@JacksonData
data class Outer(val inner: Inner, val inners: List<Inner>) {
    @JacksonData
    data class Inner(val value: Int)
}

/** A class listed in a registry. */
data class Registered(val name: String)

/** Not supported (inheritance): skipped. */
open class Parent(val parentValue: String = "parent")

@JacksonData
class Child(val childValue: String) : Parent()

/** Not supported (type parameter): skipped. */
@JacksonData
data class Generic<T>(val value: T)
