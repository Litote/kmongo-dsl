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

package org.litote.kmongo.golden

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.Id
import org.litote.kmongo.id.toId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// Models used to check that kmongo-dsl reads & writes the same BSON documents than KMongo 5.12.
// This file is compiled with KMongo 5.12 (kmongo-dsl-golden generator) and with kmongo-dsl (mapping tests):
// it must only use APIs available in both.

@Serializable
enum class GoldenColor { RED, GREEN }

@Serializable
data class GoldenSimple(
    val _id: String,
    val string: String,
    val int: Int,
    val long: Long,
    val double: Double,
    val float: Float,
    val boolean: Boolean,
    val nullableString: String?,
    val color: GoldenColor,
    val list: List<String>,
    val set: Set<Int>,
    val defaultValue: String = "default",
    @SerialName("renamed") val renamedProperty: String = "x"
)

@Serializable
data class GoldenAddress(val city: String, val zip: String? = null)

@Serializable
data class GoldenNested(
    @Contextual val _id: Id<GoldenNested>,
    val address: GoldenAddress?,
    val nullAddress: GoldenAddress?,
    val addresses: List<GoldenAddress>,
    val nullableInt: Int?
)

@Serializable
data class GoldenMaps(
    @Contextual val _id: ObjectId,
    val byColor: Map<GoldenColor, Int>,
    val byString: Map<String, GoldenAddress>,
    val byId: Map<@Contextual Id<GoldenAddress>, String>,
    val nested: Map<String, Map<String, Long>>
)

@Serializable
data class GoldenTypes(
    @Contextual val _id: ObjectId,
    @Contextual val date: Date,
    @Contextual val instant: Instant,
    @Contextual val localDate: LocalDate,
    @Contextual val localDateTime: LocalDateTime,
    @Contextual val localTime: LocalTime,
    @Contextual val bigDecimal: BigDecimal,
    @Contextual val locale: Locale,
    val bytes: ByteArray,
    val ktxInstant: kotlinx.datetime.Instant,
    val ktxLocalDate: kotlinx.datetime.LocalDate,
    val ktxLocalDateTime: kotlinx.datetime.LocalDateTime
) {
    override fun equals(other: Any?): Boolean =
        other is GoldenTypes && toString() == other.toString() && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = _id.hashCode()

    override fun toString(): String =
        "GoldenTypes($_id, ${date.time}, $instant, $localDate, $localDateTime, $localTime, " +
                "${bigDecimal.stripTrailingZeros()}, $locale, $ktxInstant, $ktxLocalDate, $ktxLocalDateTime)"
}

/** The other documented date types (UTC: the dates are always loaded in UTC). */
@Serializable
data class GoldenJavaDates(
    val _id: String,
    @Contextual val calendar: Calendar,
    @Contextual val zonedDateTime: ZonedDateTime,
    @Contextual val offsetDateTime: OffsetDateTime,
    @Contextual val offsetTime: OffsetTime,
    @Contextual val uuid: UUID
) {
    override fun equals(other: Any?): Boolean = other is GoldenJavaDates && toString() == other.toString()

    override fun hashCode(): Int = _id.hashCode()

    override fun toString(): String =
        "GoldenJavaDates($_id, ${calendar.timeInMillis}, ${zonedDateTime.toInstant()}, ${offsetDateTime.toInstant()}, " +
                "${offsetTime.withOffsetSameInstant(ZoneOffset.UTC)}, $uuid)"
}

/** ZoneId is supported by the Jackson mapping only. */
data class GoldenZone(val _id: String, val zoneId: ZoneId)

@Serializable
sealed class GoldenShape {
    abstract val name: String
}

@Serializable
@SerialName("circle")
data class GoldenCircle(override val name: String, val radius: Int) : GoldenShape()

@Serializable
data class GoldenSquare(override val name: String, val side: Double) : GoldenShape()

@Serializable
data class GoldenDrawing(val _id: String, val shapes: List<GoldenShape>, val main: GoldenShape)

/**
 * The golden samples: name -> value.
 *
 * @param serialization true for the kotlinx.serialization mapping, false for the Jackson mapping
 */
fun goldenSamples(serialization: Boolean): Map<String, Any> = linkedMapOf<String, Any>(
    "simple" to GoldenSimple(
        "simple", "a", 1, 2L, 3.5, 4.25f, true, null, GoldenColor.GREEN, listOf("x", "y"), setOf(1, 2)
    ),
    "nested" to GoldenNested(
        ObjectId("5f1e9d6c8a1b2c3d4e5f6a7b").toId(),
        GoldenAddress("Paris"),
        null,
        listOf(GoldenAddress("Lyon", "69000")),
        null
    ),
    "maps" to GoldenMaps(
        ObjectId("5f1e9d6c8a1b2c3d4e5f6a7c"),
        mapOf(GoldenColor.RED to 1, GoldenColor.GREEN to 2),
        mapOf("home" to GoldenAddress("Nantes")),
        mapOf(ObjectId("5f1e9d6c8a1b2c3d4e5f6a7d").toId<GoldenAddress>() to "office"),
        mapOf("a" to mapOf("b" to 3L))
    ),
    "types" to GoldenTypes(
        ObjectId("5f1e9d6c8a1b2c3d4e5f6a7e"),
        Date(1_600_000_000_000),
        Instant.ofEpochMilli(1_600_000_000_123),
        LocalDate.of(2020, 9, 13),
        LocalDateTime.of(2020, 9, 13, 12, 26, 40),
        LocalTime.of(12, 26, 40),
        BigDecimal("12.50"),
        Locale.FRANCE,
        byteArrayOf(1, 2, 3),
        kotlinx.datetime.Instant.fromEpochMilliseconds(1_600_000_000_456),
        kotlinx.datetime.LocalDate(2020, 9, 14),
        kotlinx.datetime.LocalDateTime(2020, 9, 14, 1, 2, 3)
    ),
    "javaDates" to GoldenJavaDates(
        "javaDates",
        GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { timeInMillis = 1_600_000_000_789 },
        ZonedDateTime.of(2020, 9, 13, 12, 26, 40, 0, ZoneOffset.UTC),
        OffsetDateTime.of(2020, 9, 13, 12, 26, 41, 0, ZoneOffset.UTC),
        OffsetTime.of(12, 26, 42, 0, ZoneOffset.UTC),
        UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    ),
).apply {
    if (!serialization) {
        put("zone", GoldenZone("zone", ZoneId.of("Europe/Paris")))
    }
    if (serialization) {
        put(
            "drawing",
            GoldenDrawing(
                "drawing",
                listOf(GoldenCircle("c", 2), GoldenSquare("s", 1.5)),
                GoldenCircle("main", 3)
            )
        )
    }
}

/** The golden file name. */
fun goldenFileName(name: String, serializeNull: Boolean): String =
    if (serializeNull) "$name.json" else "$name-without-nulls.json"
