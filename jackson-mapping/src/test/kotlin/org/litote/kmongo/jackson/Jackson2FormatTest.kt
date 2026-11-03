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

package org.litote.kmongo.jackson

import org.litote.kmongo.util.KMongoConfiguration
import tools.jackson.module.kotlin.readValue
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The formats that the Jackson 3 defaults change and that the stored documents depend on.
 */
class Jackson2FormatTest {

    data class Localized(val locale: Locale, val byLocale: Map<Locale, Int>)

    private val value = Localized(Locale.FRANCE, mapOf(Locale.CANADA_FRENCH to 1))

    @Test
    fun `locale values and keys keep the KMongo format in the queries`() {
        assertEquals(
            """{"locale":"fr_FR","byLocale":{"fr_CA":1}}""",
            KMongoConfiguration.extendedJsonMapper.writeValueAsString(value),
        )
    }

    @Test
    fun `locale values and keys are read from the stored documents`() {
        val mapper = KMongoConfiguration.bsonMapper
        assertEquals(value, mapper.readValue<Localized>(mapper.writeValueAsBytes(value)))
    }
}
