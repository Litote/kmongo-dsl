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

package org.litote.kmongo.codegen.kapt

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmType
import kotlin.metadata.KmTypeProjection
import kotlin.metadata.KmVariance
import kotlin.metadata.isNullable

/**
 * Converts a Kotlin metadata class name (`org/litote/Outer.Inner`) to a KotlinPoet [ClassName].
 */
public fun String.toClassName(): ClassName {
    val packageName = substringBeforeLast('/', "").replace('/', '.')
    val simpleNames = substringAfterLast('/').split('.')
    return ClassName(packageName, simpleNames)
}

/**
 * Converts a Kotlin metadata type to a KotlinPoet [TypeName].
 *
 * As with the KSP processor, the type aliases are expanded and the function types are `FunctionN` types.
 * The type parameters are named `T<id>`.
 */
public fun KmType.toTypeName(): TypeName {
    val typeName: TypeName = when (val classifier = classifier) {
        is KmClassifier.Class -> {
            val className = classifier.name.toClassName()
            if (arguments.isEmpty()) className else className.parameterizedBy(arguments.map { it.toTypeName() })
        }
        is KmClassifier.TypeAlias -> classifier.name.toClassName()
        is KmClassifier.TypeParameter -> TypeVariableName("T${classifier.id}")
    }
    return typeName.copy(nullable = isNullable)
}

private fun KmTypeProjection.toTypeName(): TypeName {
    val type = type ?: return STAR
    val typeName = type.toTypeName()
    return when (variance) {
        KmVariance.IN -> WildcardTypeName.consumerOf(typeName)
        KmVariance.OUT -> WildcardTypeName.producerOf(typeName)
        KmVariance.INVARIANT, null -> typeName
    }
}

/** The qualified name of the (expanded) class of the type, null if the type is not a class. */
public fun KmType.qualifiedClassName(): String? =
    (classifier as? KmClassifier.Class)?.name?.toClassName()?.canonicalName
