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
import com.squareup.kotlinpoet.FileSpec
import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import kotlin.metadata.KmClass
import kotlin.metadata.jvm.KotlinClassMetadata

// Utilities shared by the kmongo-dsl KAPT (JSR 269) processors.

/** The KAPT option giving the directory of the generated Kotlin sources. */
public const val KAPT_KOTLIN_GENERATED_OPTION: String = "kapt.kotlin.generated"

/** Kotlin collection types -> Java types. */
private val KOTLIN_TO_JAVA = mapOf(
    "kotlin.collections.Iterable" to "java.lang.Iterable",
    "kotlin.collections.MutableIterable" to "java.lang.Iterable",
    "kotlin.collections.Collection" to "java.util.Collection",
    "kotlin.collections.MutableCollection" to "java.util.Collection",
    "kotlin.collections.List" to "java.util.List",
    "kotlin.collections.MutableList" to "java.util.List",
    "kotlin.collections.Set" to "java.util.Set",
    "kotlin.collections.MutableSet" to "java.util.Set",
    "kotlin.collections.Map" to "java.util.Map",
    "kotlin.collections.MutableMap" to "java.util.Map",
)

/**
 * Writes a generated Kotlin file: in the KAPT generated Kotlin sources directory (compiled by KAPT),
 * or with the [javax.annotation.processing.Filer] if the processor is not run by KAPT.
 */
public fun ProcessingEnvironment.writeKotlin(file: FileSpec) {
    val kaptKotlinGenerated = options[KAPT_KOTLIN_GENERATED_OPTION]
    if (kaptKotlinGenerated != null) file.writeTo(File(kaptKotlinGenerated)) else file.writeTo(filer)
}

/** The Kotlin metadata of the class, null if the element is not a Kotlin class. */
public fun TypeElement.kmClass(): KmClass? =
    getAnnotation(Metadata::class.java)
        ?.let { KotlinClassMetadata.readLenient(it) as? KotlinClassMetadata.Class }
        ?.kmClass

/** The Kotlin class name (from its metadata - or from the element). */
public fun TypeElement.kotlinClassName(): ClassName =
    kmClass()?.name?.toClassName() ?: ClassName.bestGuess(qualifiedName.toString())

/** The superclass of this class, null if the superclass is `Object`. */
public fun TypeElement.superclassElement(): TypeElement? =
    ((superclass as? DeclaredType)?.takeIf { it.kind == TypeKind.DECLARED }?.asElement() as? TypeElement)
        ?.takeIf { it.kind == ElementKind.CLASS && it.qualifiedName.toString() != "java.lang.Object" }

/** The annotation of the element with the specified qualified name, null if there is no such annotation. */
public fun Element.findAnnotation(qualifiedName: String): AnnotationMirror? =
    annotationMirrors.firstOrNull {
        (it.annotationType.asElement() as TypeElement).qualifiedName.contentEquals(qualifiedName)
    }

/** The explicit value of an annotation argument. */
public fun AnnotationMirror?.argument(name: String): AnnotationValue? =
    this?.elementValues?.entries?.firstOrNull { it.key.simpleName.contentEquals(name) }?.value

/** The value of a boolean annotation argument (false if not set). */
public fun AnnotationMirror?.booleanArgument(name: String): Boolean = argument(name)?.value as? Boolean ?: false

/** The value of a String annotation argument (null if not set). */
public fun AnnotationMirror?.stringArgument(name: String): String? = argument(name)?.value as? String

/** The classes of a `KClass` (or array of `KClass`) annotation argument. */
public fun AnnotationMirror?.classesArgument(name: String): List<TypeElement> =
    when (val value = argument(name)?.value) {
        is List<*> -> value.mapNotNull { ((it as? AnnotationValue)?.value as? TypeMirror)?.asTypeElement() }
        is TypeMirror -> listOfNotNull(value.asTypeElement())
        else -> emptyList()
    }

/** The element of a declared type. */
public fun TypeMirror.asTypeElement(): TypeElement? = (this as? DeclaredType)?.asElement() as? TypeElement

/** The Java element of a Kotlin class (the Kotlin collection types are mapped to the Java types). */
public fun ProcessingEnvironment.typeElement(kotlinQualifiedName: String): TypeElement? =
    elementUtils.getTypeElement(KOTLIN_TO_JAVA[kotlinQualifiedName] ?: kotlinQualifiedName)

/** True if the Kotlin class is a subtype of the Java class. */
public fun ProcessingEnvironment.isSubtypeOf(kotlinQualifiedName: String?, javaQualifiedName: String): Boolean {
    val element = kotlinQualifiedName?.let { typeElement(it) }
    val superElement = elementUtils.getTypeElement(javaQualifiedName)
    return element != null && superElement != null &&
            typeUtils.isAssignable(typeUtils.erasure(element.asType()), typeUtils.erasure(superElement.asType()))
}
