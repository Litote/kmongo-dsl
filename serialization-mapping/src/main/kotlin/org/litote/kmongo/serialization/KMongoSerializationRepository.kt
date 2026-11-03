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

package org.litote.kmongo.serialization

import org.bson.codecs.kotlinx.BsonConfiguration
import org.bson.codecs.kotlinx.ByteArrayAsBsonBinary
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.TripleSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import org.bson.BsonTimestamp
import org.bson.types.Binary
import org.bson.types.ObjectId
import org.litote.kmongo.Id
import org.litote.kmongo.id.StringId
import org.litote.kmongo.id.WrappedObjectId
import org.litote.kmongo.util.KotlinxDatetimeLoader
import org.litote.kmongo.util.ObjectMappingConfiguration
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.superclasses
import kotlinx.datetime.Instant as KTXInstant
import kotlinx.datetime.LocalDate as KTXLocalDate
import kotlinx.datetime.LocalDateTime as KTXLocalDateTime
import kotlinx.datetime.LocalTime as KTXLocalTime

@PublishedApi
internal val customSerializersMap: MutableMap<KClass<*>, KSerializer<*>> = ConcurrentHashMap()
private val customModules = CopyOnWriteArraySet<SerializersModule>()

@Volatile
@PublishedApi
internal var checkBaseModule: Boolean = true

/**
 * Add a custom [SerializersModule] to KMongo kotlinx.serialization mapping.
 */
public fun registerModule(module: SerializersModule) {
    customModules.add(module)
    checkBaseModule = true
}

/**
 * Add a custom serializer to KMongo kotlinx.serialization mapping
 */
public inline fun <reified T> registerSerializer(serializer: KSerializer<T>) {
    customSerializersMap[T::class] = serializer
    checkBaseModule = true
}

/**
 * The kotlinx serialization default configuration.
 */
@Volatile
public var configuration: BsonConfiguration = BsonConfiguration(
    encodeDefaults = true,
    explicitNulls = ObjectMappingConfiguration.serializeNull,
    classDiscriminator = "___type"
)

/**
 * The KMongo [SerializersModule].
 */
public val kmongoSerializationModule: SerializersModule get() = KMongoSerializationRepository.module

/**
 *
 */
internal object KMongoSerializationRepository {

    private val serializersMap: Map<KClass<*>, KSerializer<*>> = mapOf(
        ObjectId::class to ObjectIdSerializer,
        BigDecimal::class to BigDecimalSerializer,
        ByteArray::class to ByteArrayAsBsonBinary,
        Date::class to DateSerializer,
        Calendar::class to CalendarSerializer,
        GregorianCalendar::class to CalendarSerializer,
        Instant::class to InstantSerializer,
        ZonedDateTime::class to ZonedDateTimeSerializer,
        OffsetDateTime::class to OffsetDateTimeSerializer,
        LocalDate::class to LocalDateSerializer,
        LocalDateTime::class to LocalDateTimeSerializer,
        LocalTime::class to LocalTimeSerializer,
        OffsetTime::class to OffsetTimeSerializer,
        BsonTimestamp::class to BsonTimestampSerializer,
        Locale::class to LocaleSerializer,
        Binary::class to BinarySerializer,
        Id::class to IdSerializer(false),
        StringId::class to IdSerializer(true),
        WrappedObjectId::class to IdSerializer(false),
        Pattern::class to PatternSerializer,
        Regex::class to RegexSerializer,
        UUID::class to UUIDSerializer
    ) + KotlinxDatetimeLoader.loadKotlinxDateTime({
        mapOf(
            KTXInstant::class to KTXInstantSerializer,
            KTXLocalDate::class to KTXLocalDateSerializer,
            KTXLocalDateTime::class to KTXLocalDateTimeSerializer,
            KTXLocalTime::class to KTXLocalTimeSerializer
        )
    }, { mapOf() })



    /**
     * Serializers built at runtime are cached, so that the codec cache ([KotlinSerializerCodecCache])
     * that uses the serializer instance as key does not grow at each call.
     */
    private val polymorphicSerializers = ConcurrentHashMap<KClass<*>, KSerializer<*>>()
    private val compositeSerializers = ConcurrentHashMap<List<Any>, KSerializer<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun polymorphicSerializer(kClass: KClass<*>): KSerializer<*> =
        polymorphicSerializers.getOrPut(kClass) { PolymorphicSerializer(kClass as KClass<Any>) }

    private fun compositeSerializer(key: List<Any>, create: () -> KSerializer<*>): KSerializer<*> =
        compositeSerializers[key] ?: create().let { compositeSerializers.putIfAbsent(key, it) ?: it }

    private fun getCustomSerializer(kClass: KClass<*>): KSerializer<*>? =
        customSerializersMap[kClass] ?: serializersMap[kClass]

    @ExperimentalSerializationApi
    @InternalSerializationApi
    private fun <T : Any> getBaseSerializer(
        obj: T,
        kClass: KClass<T> = obj.javaClass.kotlin
    ): SerializationStrategy<*>? {
        @Suppress("UNCHECKED_CAST")
        return when (obj) {
            is KProperty<*> -> KPropertySerializer
            is Pair<*, *> -> {
                val first = getSerializer(obj.first)
                val second = getSerializer(obj.second)
                compositeSerializer(listOf(Pair::class, first, second)) { PairSerializer(first, second) }
            }

            is Triple<*, *, *> -> {
                val first = getSerializer(obj.first)
                val second = getSerializer(obj.second)
                val third = getSerializer(obj.third)
                compositeSerializer(listOf(Triple::class, first, second, third)) {
                    TripleSerializer(first, second, third)
                }
            }

            is Array<*> -> {
                val elementSerializer = obj.filterNotNull().let {
                    if (it.isEmpty()) String.serializer() else getSerializer(it.first())
                } as KSerializer<Any>
                compositeSerializer(listOf(Array::class, kClass, elementSerializer)) {
                    ArraySerializer(kClass as KClass<Any>, elementSerializer)
                }
            }

            else -> module.getContextual(kClass)
                ?: findPolymorphic(kClass, obj)?.let { polymorphicSerializer(it) }
                ?: findSealed(kClass)?.serializerOrNull()
        }
    }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> findPolymorphic(kClass: KClass<*>, obj: T): KClass<*>? =
        module.getPolymorphic(kClass as KClass<T>, obj)
            ?.let { kClass }
            ?: kClass.superclasses.asSequence().map { findPolymorphic(it, obj) }.filterNotNull().firstOrNull()

    @ExperimentalSerializationApi
    @InternalSerializationApi
    @Suppress("UNCHECKED_CAST")
    private fun findSealed(kClass: KClass<*>): KClass<*>? =
        kClass.takeIf { it.isSealed }
            ?: kClass.superclasses.asSequence().map { findSealed(it) }.filterNotNull().firstOrNull()


    private const val MAX_RESOLVED_SERIALIZERS = 4096

    /**
     * The serializers resolved by [getSerializer] (class, object) when they only depend on the class of the object:
     * the resolution (custom, contextual, polymorphic and sealed lookups, walking the superclasses with
     * kotlin-reflect) would otherwise be done at each encode.
     *
     * Key: the class of the object when it is the requested class (the usual case, no allocation),
     * else the (requested class, object class) pair.
     * Replaced when the module is rebuilt ([registerSerializer], [registerModule]).
     */
    @Volatile
    private var resolvedSerializers = ConcurrentHashMap<Any, KSerializer<*>>()

    /** false if the serializer depends on the content of the object (or if the class is synthetic). */
    private fun Any.hasClassSerializer(): Boolean =
        this !is Pair<*, *> && this !is Triple<*, *, *> && this !is Array<*> && this !is KProperty<*>

    @ExperimentalSerializationApi
    @InternalSerializationApi
    fun <T : Any> getSerializer(kClass: KClass<T>, obj: T?): KSerializer<T> =
        when {
            obj == null -> error("no serializer for null")
            obj.hasClassSerializer() -> cachedSerializer(kClass, obj)
            else -> resolveSerializer(kClass, obj)
        }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cachedSerializer(kClass: KClass<T>, obj: T): KSerializer<T> {
        // applies the pending registerSerializer / registerModule calls (and replaces the resolved serializers)
        module
        val resolved = resolvedSerializers
        val objClass = obj.javaClass
        val key: Any = if (objClass === kClass.java) objClass else kClass.java to objClass
        return resolved[key] as KSerializer<T>? ?: resolveSerializer(kClass, obj).also {
            if (resolved.size >= MAX_RESOLVED_SERIALIZERS) {
                resolved.clear()
            }
            resolved.putIfAbsent(key, it)
        }
    }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> resolveSerializer(kClass: KClass<T>, obj: T): KSerializer<T> =
        (getCustomSerializer(kClass)
            ?: getBaseSerializer(obj, kClass)
            ?: kClass.serializer()) as? KSerializer<T>
            ?: error("no serializer for $obj of class $kClass")

    @ExperimentalSerializationApi
    @InternalSerializationApi
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getSerializer(obj: T?): KSerializer<T> =
        if (obj == null) {
            error("no serializer for null")
        } else {
            (getCustomSerializer(obj.javaClass.kotlin)
                ?: getBaseSerializer(obj)
                ?: obj.javaClass.kotlin.serializer()) as? KSerializer<T>
                ?: error("no serializer for $obj of class ${obj.javaClass.kotlin}")
        }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getSerializer(kClass: KClass<T>): KSerializer<T> =
        (getCustomSerializer(kClass)
            ?: module.getContextual(kClass)
            ?: try {
                kClass.serializer()
            } catch (e: SerializationException) {
                if (kClass.isAbstract || kClass.isOpen || kClass.isSealed) {
                    polymorphicSerializer(kClass)
                } else {
                    throw e
                }
            }
                ) as? KSerializer<T>
            ?: error("no serializer for $kClass of class $kClass")

    @Volatile
    private var baseModule: SerializersModule = initBaseModule()

    @Suppress("UNCHECKED_CAST")
    private fun initBaseModule(): SerializersModule =
        SerializersModule {
            serializersMap.forEach { contextual(it.key as KClass<Any>, it.value as KSerializer<Any>) }
        }
            .overwriteWith(
                SerializersModule {
                    customSerializersMap.forEach { contextual(it.key as KClass<Any>, it.value as KSerializer<Any>) }
                    customModules.forEach { include(it) }
                }
            )

    val module: SerializersModule
        get() {
            if (checkBaseModule) {
                checkBaseModule = false
                baseModule = initBaseModule()
                // after the module: a serializer resolved with the previous module goes to the previous map
                resolvedSerializers = ConcurrentHashMap()
            }
            return baseModule
        }
}
