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

package org.litote.kmongo

import org.litote.kmongo.KTXDateTest.KTXDateValue
import org.junit.jupiter.api.Tag
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class KTXDateTest : AllCategoriesKMongoBaseTest<KTXDateValue>() {

    @Serializable
    data class KTXDateValue(
        @Contextual
        val localDateTime: LocalDateTime?,
        @Contextual
        val localDate: LocalDate?,
        @Contextual
        val localTime: LocalTime?,
        @Contextual
        val instant: Instant?
    ) {

        constructor() : this(TimeZone.UTC)

        constructor(offset: FixedOffsetTimeZone, instant: Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())) : this(
            instant.toLocalDateTime(offset),
            instant.toLocalDateTime(offset).date,
            instant.toLocalDateTime(offset).time,
            instant
        )
    }

    @Test
    fun testInsertAndLoad() {
        val value = KTXDateValue()
        col.insertOne(value)
        val loadedValue = col.findOne()

        assertEquals(value, loadedValue)
    }

    @Test
    fun testGMT1InsertAndLoad() {
        val offset = FixedOffsetTimeZone(UtcOffset(1))
        val value = KTXDateValue(offset)
        col.insertOne(value)
        val loadedValue = col.findOne()!!

        assertEquals(value, loadedValue)
    }

    @Test
    fun testInsertJsonAndLoad() {
        val value = KTXDateValue()
        col.insertOne(value.json)
        val loadedValue = col.findOne()
        assertEquals(value, loadedValue)
    }

    @Test
    fun testInsertAndLoadByValue() {
        val value = KTXDateValue()
        col.insertOne(value)
        assertEquals(value, col.findOne(KTXDateValue::instant eq value.instant))
        assertEquals(value, col.findOne(KTXDateValue::localDate eq value.localDate))
        assertEquals(value, col.findOne(KTXDateValue::localDateTime eq value.localDateTime))
        assertEquals(value, col.findOne(KTXDateValue::localTime eq value.localTime))
    }

    @Test
    fun testNullAndLoad() {
        val value = KTXDateValue(null, null, null, null)
        col.insertOne(value.json)
        val loadedValue = col.findOne()

        assertEquals(value, loadedValue)
    }

    @Test
    fun testDateQuery() {
        val value = KTXDateValue()
        col.insertOne(value)
        val date = value.localDateTime!!
        val one = col.findOne(
            """{"localDateTime":{${MongoOperator.gte}: ${date.date.json}, ${MongoOperator.lt}: ${date.date.plus(
                1,
                DateTimeUnit.DAY
            ).json} } }"""
        )

        assertNotNull(one)
    }

    @Test
    fun testTypedDateQueryQuery() {
        val value = KTXDateValue()
        col.insertOne(value)
        val date = value.instant!!
        val one = col.findOne(KTXDateValue::instant gte date)

        assertNotNull(one)
    }
}
