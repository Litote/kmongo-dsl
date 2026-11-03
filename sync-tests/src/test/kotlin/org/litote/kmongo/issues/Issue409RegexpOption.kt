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

package org.litote.kmongo.issues

import kotlin.test.Test
import org.junit.jupiter.api.Tag
import org.litote.kmongo.AllCategoriesKMongoBaseTest
import org.litote.kmongo.countDocuments
import org.litote.kmongo.json
import org.litote.kmongo.model.Friend
import org.litote.kmongo.regex
import kotlin.test.assertEquals

data class NewUser(val username: String)

@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class Issue409RegexpOption : AllCategoriesKMongoBaseTest<Friend>() {

    @Test
    fun `querying with regexoption is ok`() {
        col.insertOne(Friend("Joe"))

        assertEquals(1, col.countDocuments("{name:/J/}"))
        assertEquals(0, col.countDocuments("{name:/System#.*R/}"))

        val regex1 = "J".toRegex(setOf(RegexOption.IGNORE_CASE))
        val regex2 = "System#.*R".toRegex(setOf(RegexOption.IGNORE_CASE))

        assertEquals(1, col.countDocuments("{name:${regex1.json}}"))
        assertEquals(0, col.countDocuments("{name:${regex2.json}}"))

        assertEquals(1, col.countDocuments("{name:${regex1.toPattern().json}}"))
        assertEquals(0, col.countDocuments("{name:${regex2.toPattern().json}}"))

        assertEquals(1, col.countDocuments(Friend::name regex regex1))
        assertEquals(0, col.countDocuments(Friend::name regex regex2))

        val bson = (NewUser::username regex "^test$".toRegex(setOf(RegexOption.IGNORE_CASE))).toBsonDocument()

        assertEquals("{\"username\": {\"\$regularExpression\": {\"pattern\": \"^test\$\", \"options\": \"iu\"}}}", bson.toString())
    }


}
