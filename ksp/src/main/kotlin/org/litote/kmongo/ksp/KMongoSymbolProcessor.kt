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

package org.litote.kmongo.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.writeTo
import org.litote.kmongo.codegen.DataClassModel
import org.litote.kmongo.codegen.DataPropertyModel
import org.litote.kmongo.codegen.PropertyKind
import org.litote.kmongo.codegen.PropertyPathGenerator

/**
 * [KMongoSymbolProcessor] provider.
 */
public class KMongoSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KMongoSymbolProcessor(environment.codeGenerator, environment.logger)
}

/**
 * KSP port of the KMongo KAPT annotation processor: generates the type-safe property paths
 * (`MyClass_`, `MyClass_Col`, `MyClass_Map`) for classes annotated with `@Data`
 * (or listed in a `@DataRegistry`).
 */
public class KMongoSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private companion object {
        val DATA_ANNOTATIONS = listOf("org.litote.kmongo.Data", "org.litote.jackson.data.JacksonData")
        val REGISTRY_ANNOTATIONS =
            listOf("org.litote.kmongo.DataRegistry", "org.litote.jackson.data.JacksonDataRegistry")
        const val PATHS_RUNTIME_CLASS = "org.litote.kmongo.property.KPropertyPath"
    }

    /**
     * The `@JacksonData` classes are processed only if the property paths runtime (`kmongo-dsl-core`) is in the classpath
     * (a module may use `@JacksonData` only for the Jackson serializers).
     */
    private var dataAnnotations: List<String> = DATA_ANNOTATIONS
    private var registryAnnotations: List<String> = REGISTRY_ANNOTATIONS

    /**
     * A class whose property paths are generated.
     *
     * @param origins the files that require the generation (the class file, the `@DataRegistry` file
     * or the files of the annotated subclasses)
     */
    private class DataClass(
        val declaration: KSClassDeclaration,
        val internal: Boolean,
        val origins: MutableSet<KSFile> = mutableSetOf()
    )

    /** Generated class name -> source class qualified name (all rounds). */
    private val generated = mutableMapOf<String, String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val jacksonDataSupported = resolver.getClassDeclarationByName(resolver.getKSNameFromString(PATHS_RUNTIME_CLASS)) != null
        dataAnnotations = if (jacksonDataSupported) DATA_ANNOTATIONS else DATA_ANNOTATIONS.take(1)
        registryAnnotations = if (jacksonDataSupported) REGISTRY_ANNOTATIONS else REGISTRY_ANNOTATIONS.take(1)
        val registered = registeredClasses(resolver)
        dataAnnotatedOrRegistered = { declaration ->
            dataAnnotations.any { declaration.findAnnotation(it) != null }
                    || registered.containsKey(declaration.qualifiedName?.asString())
        }

        val dataClasses = linkedMapOf<String, DataClass>()

        fun register(declaration: KSClassDeclaration, internal: Boolean, origins: Set<KSFile>) {
            val name = declaration.qualifiedName?.asString() ?: return
            val dataClass = dataClasses.getOrPut(name) { DataClass(declaration, internal) }
            dataClass.origins += origins
            declaration.containingFile?.let { dataClass.origins += it }
            // superclasses are registered too
            declaration.dataSuperclass()?.let { register(it, internal, dataClass.origins) }
        }

        dataAnnotations.forEach { annotationName ->
            resolver.getSymbolsWithAnnotation(annotationName)
                .filterIsInstance<KSClassDeclaration>()
                .forEach { register(it, it.findAnnotation(annotationName).booleanArgument("internal"), emptySet()) }
        }
        registered.values.forEach { (declaration, dataClass) ->
            register(declaration, dataClass.internal, dataClass.origins)
        }

        dataClasses.values
            .filter { checkNameCollision(it) }
            .forEach {
                try {
                    generate(it, dataClasses)
                } catch (e: Exception) {
                    logger.error(
                        "KMongo: unable to generate property paths for ${it.declaration.qualifiedName?.asString()}: $e",
                        it.declaration
                    )
                }
            }
        return emptyList()
    }

    /**
     * The classes listed in a `@DataRegistry` (searched in all the files, so that the incremental processing
     * knows all the registered classes, even if the registry file is not modified).
     */
    private fun registeredClasses(resolver: Resolver): Map<String, Pair<KSClassDeclaration, DataClass>> {
        val registered = linkedMapOf<String, Pair<KSClassDeclaration, DataClass>>()
        resolver.getAllFiles().forEach { file ->
            (sequenceOf<KSAnnotated>(file) + file.declarations).forEach { annotated ->
                registryAnnotations.forEach { annotationName ->
                    annotated.findAnnotation(annotationName)?.let { registerAll(it, file, registered) }
                }
            }
        }
        return registered
    }

    private fun registerAll(
        annotation: KSAnnotation,
        file: KSFile,
        registered: MutableMap<String, Pair<KSClassDeclaration, DataClass>>
    ) {
        val internal = annotation.booleanArgument("internal")
        @Suppress("UNCHECKED_CAST")
        (annotation.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? List<KSType>)
            ?.mapNotNull { it.declaration as? KSClassDeclaration }
            ?.forEach { declaration ->
                val name = declaration.qualifiedName?.asString() ?: return@forEach
                registered.getOrPut(name) { declaration to DataClass(declaration, internal) }.second.origins += file
            }
    }

    /**
     * Returns true if the generated classes of [dataClass] have to be generated (ie not already generated),
     * reports an error if another class has the same generated class name.
     */
    private fun checkNameCollision(dataClass: DataClass): Boolean {
        val qualifiedName = dataClass.declaration.qualifiedName!!.asString()
        val generatedName = dataClass.declaration.generatedName().canonicalName
        val previous = generated.putIfAbsent(generatedName, qualifiedName)
        return when (previous) {
            null -> true
            qualifiedName -> false
            else -> {
                logger.error(
                    "KMongo: $qualifiedName and $previous have the same generated class name $generatedName " +
                            "(nested classes with the same simple name in the same package are not supported)",
                    dataClass.declaration
                )
                false
            }
        }
    }

    /** True if the class is annotated with `@Data` (or `@JacksonData`) or listed in a `@DataRegistry`. */
    private var dataAnnotatedOrRegistered: (KSClassDeclaration) -> Boolean = { false }

    private fun KSAnnotated.findAnnotation(qualifiedName: String): KSAnnotation? =
        annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
        }

    private fun KSAnnotation?.booleanArgument(name: String): Boolean =
        this?.arguments?.firstOrNull { it.name?.asString() == name }?.value as? Boolean ?: false

    private fun KSClassDeclaration.dataSuperclass(): KSClassDeclaration? =
        superTypes
            .map { it.resolve().declaration }
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.classKind == ClassKind.CLASS && it.qualifiedName?.asString() != "kotlin.Any" }

    private fun KSClassDeclaration.generatedName(): ClassName = PropertyPathGenerator.generatedName(toClassName())

    private fun generate(dataClass: DataClass, dataClasses: Map<String, DataClass>) {
        val element = dataClass.declaration
        val typeParameterResolver = element.typeParameters.toTypeParameterResolver()
        val model = DataClassModel(
            className = element.toClassName(),
            typeParameterCount = element.typeParameters.size,
            internal = dataClass.internal,
            open = element.modifiers.any { it == Modifier.OPEN || it == Modifier.ABSTRACT || it == Modifier.SEALED },
            dataSuperclass = element.dataSuperclass()?.takeIf { isData(it, dataClasses) }?.toClassName(),
            properties = element.getDeclaredProperties()
                .filter { it.extensionReceiver == null }
                .map { property(it, dataClasses, typeParameterResolver) }
                .toList(),
        )
        // isolating output: the class file, its superclasses files, the files that require the generation
        // and the files of the data classes used as property types
        val sources = (dataClass.origins + element.superclassFiles() + referencedDataFiles(element, dataClasses))
            .distinct()
        PropertyPathGenerator.generate(model).writeTo(codeGenerator, Dependencies(false, *sources.toTypedArray()))
    }

    private fun KSClassDeclaration.superclassFiles(): List<KSFile> =
        generateSequence(dataSuperclass()) { it.dataSuperclass() }.mapNotNull { it.containingFile }.toList()

    private fun referencedDataFiles(element: KSClassDeclaration, dataClasses: Map<String, DataClass>): List<KSFile> =
        element.getDeclaredProperties()
            .flatMap { property ->
                val type = property.type.resolve()
                sequenceOf(type) + type.arguments.asSequence().mapNotNull { it.type?.resolve() }
            }
            .mapNotNull { it.declaration as? KSClassDeclaration }
            .filter { isData(it, dataClasses) }
            .mapNotNull { it.containingFile }
            .toList()

    private fun isData(declaration: KSClassDeclaration, dataClasses: Map<String, DataClass>): Boolean =
        dataClasses.containsKey(declaration.qualifiedName?.asString()) || dataAnnotatedOrRegistered(declaration)

    private fun property(
        property: KSPropertyDeclaration,
        dataClasses: Map<String, DataClass>,
        typeParameterResolver: TypeParameterResolver,
    ): DataPropertyModel {
        val type = property.type.resolve().expandTypeAlias()
        fun KSType.argument(index: Int): KSType? = arguments.getOrNull(index)?.type?.resolve()
        fun KSType?.dataClass(): ClassName? =
            (this?.declaration as? KSClassDeclaration)?.takeIf { isData(it, dataClasses) }?.toClassName()

        val kind = when {
            type.isSubtypeOf("kotlin.collections.Collection")
                    || type.declaration.qualifiedName?.asString() == "kotlin.collections.Iterable" -> PropertyKind.COLLECTION
            type.isSubtypeOf("kotlin.collections.Map") -> PropertyKind.MAP
            else -> PropertyKind.SIMPLE
        }
        return DataPropertyModel(
            name = property.simpleName.asString(),
            type = type.toTypeName(typeParameterResolver),
            // not accessible from the generated code: private, protected or internal in another module
            private = property.isPrivate() || property.isProtected()
                    || (property.isInternal() && property.origin != Origin.KOTLIN),
            kind = kind,
            dataClass = when (kind) {
                PropertyKind.SIMPLE -> type.dataClass()
                PropertyKind.COLLECTION -> type.argument(0).dataClass()
                PropertyKind.MAP -> type.argument(1).dataClass()
            },
        )
    }

    /**
     * Expands the type aliases (as the KAPT processor, that reads the expanded types from the Kotlin metadata).
     */
    private fun KSType.expandTypeAlias(): KSType {
        val alias = declaration as? KSTypeAlias ?: return this
        val aliasArguments = alias.typeParameters.map { it.name.asString() }.zip(arguments).toMap()
        val expanded = alias.type.resolve()
        val substituted = if (aliasArguments.isEmpty()) expanded else expanded.replace(
            expanded.arguments.map { argument ->
                (argument.type?.resolve()?.declaration as? KSTypeParameter)
                    ?.let { aliasArguments[it.name.asString()] }
                    ?: argument
            }
        )
        return (if (isMarkedNullable) substituted.makeNullable() else substituted).expandTypeAlias()
    }

    private fun KSType.isSubtypeOf(qualifiedName: String): Boolean {
        val declaration = declaration as? KSClassDeclaration ?: return false
        if (declaration.qualifiedName?.asString() == qualifiedName) return true
        return declaration.superTypes.any { it.resolve().isSubtypeOf(qualifiedName) }
    }
}
