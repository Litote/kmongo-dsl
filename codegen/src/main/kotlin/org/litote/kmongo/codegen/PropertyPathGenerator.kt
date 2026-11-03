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

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName

/**
 * Generates the type-safe property paths (`MyClass_`, `MyClass_Col`, `MyClass_Map`) of a [DataClassModel].
 *
 * Used by the `kmongo-dsl-ksp` (KSP) and `kmongo-dsl-annotation-processor` (KAPT) processors:
 * both generate the same code.
 */
public object PropertyPathGenerator {

    private val KPROPERTY_PATH_FIELDS = setOf(
        "annotations", "getter", "isAbstract", "isConst", "isFinal", "isLateinit", "isOpen",
        "name", "parameters", "returnType", "visibility", "path"
    )

    private const val PROPERTY_PACKAGE = "org.litote.kmongo.property"
    private val KPROPERTY1 = ClassName("kotlin.reflect", "KProperty1")
    private val KPROPERTY_PATH = ClassName(PROPERTY_PACKAGE, "KPropertyPath")
    private val KCOLLECTION_PROPERTY_PATH = ClassName(PROPERTY_PACKAGE, "KCollectionPropertyPath")
    private val KCOLLECTION_SIMPLE_PROPERTY_PATH = ClassName(PROPERTY_PACKAGE, "KCollectionSimplePropertyPath")
    private val KMAP_PROPERTY_PATH = ClassName(PROPERTY_PACKAGE, "KMapPropertyPath")
    private val KMAP_SIMPLE_PROPERTY_PATH = ClassName(PROPERTY_PACKAGE, "KMapSimplePropertyPath")
    private val ITERABLE = ClassName("kotlin.collections", "Iterable")
    private val MAP = ClassName("kotlin.collections", "Map")
    private val DECLARED_MEMBER_PROPERTIES = MemberName("kotlin.reflect.full", "declaredMemberProperties")
    private val NULLABLE_ANY = ANY.copy(nullable = true)
    private val T = TypeVariableName("T")
    private val K = TypeVariableName("K")

    /**
     * The name of the generated class for the [source] class: `Source_` + [suffix]
     * (the simple name is used, even for nested classes).
     */
    public fun generatedName(source: ClassName, suffix: String = ""): ClassName =
        ClassName(source.packageName, "${source.simpleName}_$suffix")

    /**
     * Generates the file of the property paths of [model].
     */
    public fun generate(model: DataClassModel): FileSpec {
        val source = model.generified()
        val nullableSource = source.copy(nullable = true)
        val className = generatedName(model.className)
        val superclass = model.dataSuperclass

        val memberWithAdditionalPath = FunSpec.builder("memberWithAdditionalPath")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("additionalPath", String::class)
            .returns(className.parameterizedBy(T))
            .addCode("return %T(this, customProperty(this, additionalPath))", className)
            .build()

        val classBuilder = pathClassBuilder(
            model, className,
            propertyType = nullableSource,
            superclass = superclass?.let { generatedName(it) }?.parameterizedBy(T)
                ?: KPROPERTY_PATH.parameterizedBy(T, nullableSource)
        )
        val colClassBuilder = pathClassBuilder(
            model, generatedName(model.className, "Col"),
            // nullable elements are accepted (Iterable<Source?>)
            propertyType = ITERABLE.parameterizedBy(nullableSource).copy(nullable = true),
            superclass = superclass?.let { generatedName(it, "Col") }?.parameterizedBy(T)
                ?: KCOLLECTION_PROPERTY_PATH.parameterizedBy(T, nullableSource, className.parameterizedBy(T))
        ).addFunction(memberWithAdditionalPath)
        val mapClassBuilder = pathClassBuilder(
            model, generatedName(model.className, "Map"),
            // nullable values are accepted (Map<out K, Source?>)
            propertyType = MAP.parameterizedBy(WildcardTypeName.producerOf(K), nullableSource).copy(nullable = true),
            superclass = superclass?.let { generatedName(it, "Map") }?.parameterizedBy(T, K)
                ?: KMAP_PROPERTY_PATH.parameterizedBy(T, K, nullableSource, className.parameterizedBy(T)),
            map = true
        ).addFunction(memberWithAdditionalPath)

        val fileBuilder = FileSpec.builder(className)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S, %S, %S", "UNCHECKED_CAST", "unused", "RedundantVisibilityModifier")
                    .build()
            )
        val companion = TypeSpec.companionObjectBuilder()

        // sorted by name: the processing APIs do not return the properties in the same order
        model.properties.sortedBy { it.name }.forEach { property ->
            val generated = GeneratedProperty(property, model, source)
            fileBuilder.addProperty(generated.propertyReference())
            companion.addProperty(generated.companionProperty())
            val pathProperty = generated.pathProperty()
            listOf(classBuilder, colClassBuilder, mapClassBuilder).forEach { it.addProperty(pathProperty) }
        }

        fileBuilder.addType(classBuilder.addType(companion.build()).build())
        fileBuilder.addType(colClassBuilder.build())
        fileBuilder.addType(mapClassBuilder.build())
        return fileBuilder.build()
    }

    private fun pathClassBuilder(
        model: DataClassModel,
        className: ClassName,
        propertyType: TypeName,
        superclass: TypeName,
        map: Boolean = false,
    ): TypeSpec.Builder =
        TypeSpec.classBuilder(className)
            .addTypeVariable(T)
            .apply {
                if (model.internal) addModifiers(KModifier.INTERNAL)
                if (model.open) addModifiers(KModifier.OPEN)
                if (map) addTypeVariable(K)
            }
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("previous", KPROPERTY_PATH.parameterizedBy(T, STAR).copy(nullable = true))
                    .addParameter("property", KPROPERTY1.parameterizedBy(STAR, propertyType))
                    .build()
            )
            .superclass(superclass)
            .addSuperclassConstructorParameter("%L, %L", "previous", "property")

    /**
     * The source class with star projected type parameters.
     */
    private fun DataClassModel.generified(): TypeName =
        if (typeParameterCount == 0) className
        else className.parameterizedBy(List(typeParameterCount) { STAR })

    /**
     * Type variables of the data classes are replaced by `Any?` (the generated classes use their own `T`).
     */
    private fun TypeName.eraseTypeVariables(): TypeName =
        when (this) {
            is TypeVariableName -> NULLABLE_ANY
            is ParameterizedTypeName -> rawType.parameterizedBy(typeArguments.map { it.eraseTypeVariables() })
                .copy(nullable = isNullable)
            is LambdaTypeName -> LambdaTypeName.get(
                receiver?.eraseTypeVariables(),
                parameters.map { it.toBuilder(type = it.type.eraseTypeVariables()).build() },
                returnType.eraseTypeVariables()
            ).copy(nullable = isNullable, suspending = isSuspending)
            else -> this
        }

    /**
     * The generated declarations of a property.
     */
    private class GeneratedProperty(val property: DataPropertyModel, val model: DataClassModel, val source: TypeName) {

        val propertyType = property.type.eraseTypeVariables()
        val nullablePropertyType = propertyType.copy(nullable = true)
        val capitalized = property.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val propertyDef = "__$capitalized"

        /** The generated class of the data class used by the property (if any). */
        val generatedPropertyClass: ClassName? = property.dataClass?.let {
            when (property.kind) {
                PropertyKind.COLLECTION -> generatedName(it, "Col")
                PropertyKind.MAP -> generatedName(it, "Map")
                PropertyKind.SIMPLE -> generatedName(it)
            }
        }

        /** The path class instantiated for the property (null for a simple property: KProperty1 / KPropertyPath). */
        val pathClass: ClassName? = generatedPropertyClass ?: when (property.kind) {
            PropertyKind.COLLECTION -> KCOLLECTION_SIMPLE_PROPERTY_PATH
            PropertyKind.MAP -> KMAP_SIMPLE_PROPERTY_PATH
            PropertyKind.SIMPLE -> null
        }

        fun typeArgument(index: Int): TypeName =
            when (val argument = (propertyType as? ParameterizedTypeName)?.typeArguments?.getOrNull(index)) {
                null, STAR -> NULLABLE_ANY
                // `out X` -> X, `in X` -> Any?
                is WildcardTypeName -> argument.outTypes.singleOrNull()?.takeIf { argument.inTypes.isEmpty() }
                    ?.copy(nullable = true) ?: NULLABLE_ANY
                else -> argument.copy(nullable = true)
            }

        /** The type of the property path, from the source class ([start]) or from a path (`T`). */
        fun propertyClass(start: Boolean): TypeName {
            val receiver = if (start) source else T
            return when {
                generatedPropertyClass != null && property.kind == PropertyKind.MAP ->
                    generatedPropertyClass.parameterizedBy(receiver, typeArgument(0).copy(nullable = false))
                generatedPropertyClass != null -> generatedPropertyClass.parameterizedBy(receiver)
                property.kind == PropertyKind.COLLECTION ->
                    KCOLLECTION_SIMPLE_PROPERTY_PATH.parameterizedBy(receiver, typeArgument(0))
                property.kind == PropertyKind.MAP ->
                    KMAP_SIMPLE_PROPERTY_PATH.parameterizedBy(receiver, typeArgument(0), typeArgument(1))
                start -> KPROPERTY1.parameterizedBy(receiver, nullablePropertyType)
                else -> KPROPERTY_PATH.parameterizedBy(receiver, nullablePropertyType)
            }
        }

        /** The private (file) property reference. */
        fun propertyReference(): PropertySpec {
            val reference = if (property.private) {
                CodeBlock.of(
                    "%T::class.%M.first { it.name == %S } as %T",
                    model.className,
                    DECLARED_MEMBER_PROPERTIES,
                    property.name,
                    KPROPERTY1.parameterizedBy(source, nullablePropertyType)
                )
            } else {
                CodeBlock.of("%T::%N", source, property.name)
            }
            return PropertySpec.builder(propertyDef, KPROPERTY1.parameterizedBy(source, nullablePropertyType))
                .addModifiers(KModifier.PRIVATE)
                .getter(FunSpec.getterBuilder().addCode("return %L", reference).build())
                .build()
        }

        /** The companion property (path from the source class). */
        fun companionProperty(): PropertySpec =
            PropertySpec.builder(capitalized, propertyClass(true))
                .getter(
                    FunSpec.getterBuilder().apply {
                        if (pathClass != null) addCode("return %T(null, %N)", pathClass, propertyDef)
                        else addCode("return %N", propertyDef)
                    }.build()
                )
                .build()

        /** The path property of the generated classes. */
        fun pathProperty(): PropertySpec {
            val fieldName = if (KPROPERTY_PATH_FIELDS.contains(property.name)) "${property.name}_" else property.name
            return PropertySpec.builder(fieldName, propertyClass(false))
                .getter(
                    FunSpec.getterBuilder()
                        .addCode("return %T(this, %N)", pathClass ?: KPROPERTY_PATH, propertyDef)
                        .build()
                )
                .build()
        }
    }
}
