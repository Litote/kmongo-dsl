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

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import org.litote.kmongo.codegen.kapt.KAPT_KOTLIN_GENERATED_OPTION
import org.litote.kmongo.codegen.kapt.argument
import org.litote.kmongo.codegen.kapt.classesArgument
import org.litote.kmongo.codegen.kapt.findAnnotation
import org.litote.kmongo.codegen.kapt.isSubtypeOf
import org.litote.kmongo.codegen.kapt.kmClass
import org.litote.kmongo.codegen.kapt.qualifiedClassName
import org.litote.kmongo.codegen.kapt.stringArgument
import org.litote.kmongo.codegen.kapt.superclassElement
import org.litote.kmongo.codegen.kapt.toClassName
import org.litote.kmongo.codegen.kapt.toTypeName
import org.litote.kmongo.codegen.kapt.writeKotlin
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation
import kotlin.metadata.ClassKind
import kotlin.metadata.KmClass
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmProperty
import kotlin.metadata.KmType
import kotlin.metadata.Modality
import kotlin.metadata.Visibility
import kotlin.metadata.declaresDefaultValue
import kotlin.metadata.isSecondary
import kotlin.metadata.isVar
import kotlin.metadata.jvm.fieldSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.kind
import kotlin.metadata.modality
import kotlin.metadata.visibility

/**
 * KAPT (JSR 269) processor generating the Jackson serializers (`MyClass_Serializer`) and deserializers
 * (`MyClass_Deserializer`) of the Kotlin classes annotated with `@JacksonData` (or listed in a `@JacksonDataRegistry`).
 *
 * It replaces `org.litote.jackson:jackson-generator` (same annotations, same generated classes):
 * it can be used in the same KAPT execution as `kmongo-dsl-annotation-processor` (its dependencies are embedded
 * and relocated). The generated classes are listed in `META-INF/services/org.litote.jackson.JacksonModuleServiceLoader`
 * and registered by the kmongo-dsl Jackson mapping.
 *
 * Supported classes: Kotlin final or open classes without type parameters and without superclass,
 * built with their primary constructor. The serialized properties are the properties with a backing field
 * (minus the `@JsonIgnore` and `@Transient` ones), the JSON names can be set with `@JsonProperty`.
 * The other classes are skipped (Jackson default serialization).
 */
@SupportedOptions(KAPT_KOTLIN_GENERATED_OPTION)
public class JacksonGeneratorProcessor : AbstractProcessor() {

    private companion object {
        const val DATA_ANNOTATION = "org.litote.jackson.data.JacksonData"
        const val REGISTRY_ANNOTATION = "org.litote.jackson.data.JacksonDataRegistry"
        const val JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty"
        const val JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore"
        const val SERVICE_FILE = "META-INF/services/org.litote.jackson.JacksonModuleServiceLoader"

        val VALUE_KINDS = mapOf(
            "kotlin.String" to ValueKind.STRING,
            "kotlin.Boolean" to ValueKind.BOOLEAN,
            "kotlin.Int" to ValueKind.INT,
            "kotlin.Long" to ValueKind.LONG,
            "kotlin.Short" to ValueKind.SHORT,
            "kotlin.Float" to ValueKind.FLOAT,
            "kotlin.Double" to ValueKind.DOUBLE,
            "java.math.BigInteger" to ValueKind.BIG_INTEGER,
            "java.math.BigDecimal" to ValueKind.BIG_DECIMAL,
        )
    }

    /** The class is not supported - skipped with a note. */
    private class UnsupportedClassException(message: String) : Exception(message)

    /** Generated class name -> source class qualified name (all rounds). */
    private val generated = mutableMapOf<String, String>()

    /** The generated module loaders (all rounds). */
    private val moduleLoaders = sortedSetOf<String>()

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(DATA_ANNOTATION, REGISTRY_ANNOTATION)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (roundEnv.processingOver()) {
            writeServiceFile()
            return false
        }
        val classes = linkedMapOf<String, TypeElement>()
        processingEnv.elementUtils.getTypeElement(DATA_ANNOTATION)?.let { annotation ->
            roundEnv.getElementsAnnotatedWith(annotation)
                .filterIsInstance<TypeElement>()
                .forEach { classes.putIfAbsent(it.qualifiedName.toString(), it) }
        }
        processingEnv.elementUtils.getTypeElement(REGISTRY_ANNOTATION)?.let { annotation ->
            roundEnv.getElementsAnnotatedWith(annotation).forEach { registry ->
                registry.findAnnotation(REGISTRY_ANNOTATION).classesArgument("value")
                    .forEach { classes.putIfAbsent(it.qualifiedName.toString(), it) }
            }
        }
        val sources = roundEnv.rootElements.filterIsInstance<TypeElement>().map { it.qualifiedName.toString() }.toSet()
        classes.values.forEach { generate(it, it.outermostClass().qualifiedName.toString() in sources) }
        // the annotations are not claimed: they can be processed by the other processors (kmongo-dsl-annotation-processor)
        return false
    }

    private fun generate(element: TypeElement, fromSources: Boolean) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val model = Context(element, fromSources).model()
            if (checkNameCollision(element, model.className)) {
                processingEnv.writeKotlin(JacksonCodeGenerator.serializer(model))
                processingEnv.writeKotlin(JacksonCodeGenerator.deserializer(model))
                moduleLoaders += JacksonCodeGenerator.serializerName(model.className).canonicalName
                moduleLoaders += JacksonCodeGenerator.deserializerName(model.className).canonicalName
            }
        } catch (e: UnsupportedClassException) {
            message(Diagnostic.Kind.NOTE, "KMongo: ${element.qualifiedName}: ${e.message} - Jackson serializers not generated", element)
        } catch (e: Exception) {
            message(Diagnostic.Kind.ERROR, "KMongo: unable to generate Jackson serializers for ${element.qualifiedName}: $e", element)
        }
    }

    private fun writeServiceFile() {
        if (moduleLoaders.isEmpty()) return
        processingEnv.filer.createResource(StandardLocation.CLASS_OUTPUT, "", SERVICE_FILE)
            .openWriter()
            .use { writer -> moduleLoaders.forEach { writer.write("$it\n") } }
    }

    private fun checkNameCollision(element: TypeElement, className: ClassName): Boolean {
        val qualifiedName = element.qualifiedName.toString()
        val generatedName = JacksonCodeGenerator.serializerName(className).canonicalName
        return when (val previous = generated.putIfAbsent(generatedName, qualifiedName)) {
            null -> true
            qualifiedName -> false
            else -> {
                message(
                    Diagnostic.Kind.ERROR,
                    "KMongo: $qualifiedName and $previous have the same generated Jackson serializer name $generatedName " +
                            "(nested classes with the same simple name in the same package are not supported)",
                    element
                )
                false
            }
        }
    }

    private fun message(kind: Diagnostic.Kind, message: String, element: Element) {
        processingEnv.messager.printMessage(kind, message, element)
    }

    private fun TypeElement.outermostClass(): TypeElement =
        (enclosingElement as? TypeElement)?.outermostClass() ?: this

    /**
     * The model builder of a class.
     *
     * @param fromSources true if the class is compiled with the generated code (its internal members are accessible)
     */
    private inner class Context(val element: TypeElement, val fromSources: Boolean) {

        private val kmClass: KmClass = element.kmClass() ?: unsupported("not a Kotlin class")

        fun model(): JacksonClassModel {
            when {
                kmClass.kind != ClassKind.CLASS -> unsupported("${kmClass.kind.name.lowercase()} not supported")
                kmClass.modality == Modality.ABSTRACT || kmClass.modality == Modality.SEALED ->
                    unsupported("abstract class not supported")

                kmClass.typeParameters.isNotEmpty() -> unsupported("class with type parameters not supported")
                element.superclassElement() != null -> unsupported("inheritance not supported")
                !kmClass.visibility.isAccessible() -> unsupported("class not accessible")
            }
            val constructor = kmClass.constructors.firstOrNull { !it.isSecondary }
                ?: unsupported("no primary constructor")
            if (!constructor.visibility.isAccessible()) unsupported("primary constructor not accessible")

            val properties = properties()
            val propertyNames = properties.map { it.name }.toSet()
            constructor.valueParameters
                .firstOrNull { it.name !in propertyNames && !it.declaresDefaultValue }
                ?.let { unsupported("the constructor parameter ${it.name} is not a serialized property and has no default value") }
            val parameterNames = constructor.valueParameters.map { it.name }.toSet()
            return JacksonClassModel(
                className = kmClass.name.toClassName(),
                properties = properties.map { it.copy(constructorParameter = it.name in parameterNames) },
            )
        }

        /**
         * The serialized properties (with a backing field), in declaration order (the order of the fields).
         *
         * The properties whose name is a Java keyword have no field in the KAPT stubs: they are the last ones.
         */
        private fun properties(): List<JacksonPropertyModel> {
            val fields = element.enclosedElements
                .filterIsInstance<VariableElement>()
                .filter { it.kind == ElementKind.FIELD }
                .associateBy { it.simpleName.toString() }
            val fieldIndexes = fields.keys.withIndex().associate { (index, name) -> name to index }
            return kmClass.properties
                .filter { it.receiverParameterType == null && it.fieldSignature?.name == it.name }
                .sortedBy { fieldIndexes[it.name] ?: Int.MAX_VALUE }
                .filterNot { property ->
                    val modifiers = fields[property.name]?.modifiers.orEmpty()
                    Modifier.STATIC in modifiers || Modifier.TRANSIENT in modifiers
                }
                .filterNot { property -> annotated(fields[property.name], property).any { it.isJsonIgnore() } }
                .map { property(fields[it.name], it) }
        }

        private fun property(field: VariableElement?, property: KmProperty): JacksonPropertyModel {
            val type = property.returnType
            val className = type.qualifiedClassName()
            val kind = VALUE_KINDS[className] ?: when {
                processingEnv.isSubtypeOf(className, "java.util.Collection") -> ValueKind.COLLECTION
                processingEnv.isSubtypeOf(className, "java.util.Map") -> ValueKind.MAP
                else -> ValueKind.OTHER
            }
            val jsonName = annotated(field, property)
                .firstNotNullOfOrNull { it.findAnnotation(JSON_PROPERTY).stringArgument("value")?.takeIf(String::isNotEmpty) }
                ?: property.name
            return JacksonPropertyModel(
                name = property.name,
                jsonName = jsonName,
                type = type.toTypeName(),
                kind = kind,
                javaType = if (kind == ValueKind.COLLECTION || kind == ValueKind.MAP) javaType(type) else null,
                accessible = property.visibility.isAccessible(),
                constructorParameter = false,
                writable = property.isVar && (property.setter?.visibility ?: property.visibility).isAccessible(),
            )
        }

        private fun javaType(type: KmType?): JavaTypeModel {
            val classifier = type?.classifier as? KmClassifier.Class ?: return JavaTypeModel.Simple(ANY)
            val rawClass = classifier.name.toClassName()
            val className = rawClass.canonicalName
            val arguments = type.arguments.map { it.type }
            return when {
                arguments.size == 1 && processingEnv.isSubtypeOf(className, "java.util.Collection") ->
                    JavaTypeModel.Collection(rawClass, javaType(arguments[0]))

                arguments.size == 2 && processingEnv.isSubtypeOf(className, "java.util.Map") ->
                    JavaTypeModel.Map(rawClass, javaType(arguments[0]), javaType(arguments[1]))

                else -> JavaTypeModel.Simple(rawClass)
            }
        }

        /** The Java elements of a property: the field, the getter and the constructor parameters. */
        private fun annotated(field: VariableElement?, property: KmProperty): List<Element> {
            val getterName = property.getterSignature?.name
            val getters = element.enclosedElements
                .filterIsInstance<ExecutableElement>()
                .filter { it.kind == ElementKind.METHOD && it.parameters.isEmpty() && it.simpleName.toString() == getterName }
            val parameters = element.enclosedElements
                .filterIsInstance<ExecutableElement>()
                .filter { it.kind == ElementKind.CONSTRUCTOR }
                .flatMap { constructor -> constructor.parameters.filter { it.simpleName.contentEquals(property.name) } }
            return listOfNotNull(field) + getters + parameters
        }

        private fun Element.isJsonIgnore(): Boolean =
            findAnnotation(JSON_IGNORE)?.let { it.argument("value")?.value as? Boolean ?: true } ?: false

        private fun Visibility.isAccessible(): Boolean =
            this == Visibility.PUBLIC || (this == Visibility.INTERNAL && fromSources)

        private fun unsupported(message: String): Nothing = throw UnsupportedClassException(message)
    }
}
