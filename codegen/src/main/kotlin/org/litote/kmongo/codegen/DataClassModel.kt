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

package org.litote.kmongo.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

/**
 * A class whose property paths are generated (`MyClass_`, `MyClass_Col`, `MyClass_Map`).
 *
 * This model is independent of the processing API (KSP or KAPT): each processor builds it
 * from its own symbols, and [PropertyPathGenerator] generates the code.
 *
 * @param className the (raw) class name of the source class
 * @param typeParameterCount the number of type parameters of the source class
 * @param internal true if the generated classes are `internal`
 * @param open true if the source class is `open`, `abstract` or `sealed` (the generated classes are then `open`)
 * @param dataSuperclass the (raw) class name of the superclass, if its property paths are generated too
 * @param properties the properties declared by the source class (not the inherited ones)
 */
public class DataClassModel(
    public val className: ClassName,
    public val typeParameterCount: Int,
    public val internal: Boolean,
    public val open: Boolean,
    public val dataSuperclass: ClassName?,
    public val properties: List<DataPropertyModel>,
)

/**
 * A property of a [DataClassModel].
 *
 * @param name the property name
 * @param type the property type (with its nullability) - the type variables are replaced by `Any?` by the generator
 * @param private true if the property is not accessible from the generated code (private, protected,
 * or internal in another module): it is then accessed through `kotlin-reflect`
 * @param kind the kind of the property
 * @param dataClass the (raw) class name of the "data" class (whose property paths are generated) used by the property:
 * the property type ([PropertyKind.SIMPLE]), the collection element type ([PropertyKind.COLLECTION])
 * or the map value type ([PropertyKind.MAP]) - null if this type is not a data class
 */
public class DataPropertyModel(
    public val name: String,
    public val type: TypeName,
    public val private: Boolean,
    public val kind: PropertyKind,
    public val dataClass: ClassName?,
)

/**
 * The kind of [DataPropertyModel].
 */
public enum class PropertyKind {
    /** Neither a collection nor a map. */
    SIMPLE,

    /** A `Collection` (or an `Iterable`). */
    COLLECTION,

    /** A `Map`. */
    MAP,
}
