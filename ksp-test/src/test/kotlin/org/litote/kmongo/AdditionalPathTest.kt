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

import org.litote.kmongo.model.FileRegisteredData_
import org.litote.kmongo.model.GenericBox_
import org.litote.kmongo.model.Holder_
import org.litote.kmongo.model.Level1_
import org.litote.kmongo.model.Level2_
import org.litote.kmongo.model.Level3_
import org.litote.kmongo.model.SimpleReferenced2Data_
import org.litote.kmongo.model.TestData_
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Additional cases of the KSP processor.
 */
class AdditionalPathTest {

    @Test
    fun `file DataRegistry annotation is detected`() {
        assertEquals("fileValue", FileRegisteredData_.FileValue.name)
        assertEquals("registered.fileValue", Holder_.Registered.fileValue.name)
    }

    @Test
    fun `generic classes`() {
        assertEquals("value", GenericBox_.Value.name)
        assertEquals("items", GenericBox_.Items.name)
        assertEquals("box.tags", Holder_.Box.tags.name)
        assertEquals("box.items", Holder_.Box.items.name)
    }

    @Test
    fun `multi-level inheritance`() {
        assertTrue(Level1_::class.java.isAssignableFrom(Level2_::class.java))
        assertTrue(Level2_::class.java.isAssignableFrom(Level3_::class.java))
        assertEquals("c", Level3_.C.name)
        assertEquals("level.a", Holder_.Level.a.name)
        assertEquals("level.b", Holder_.Level.b.name)
        assertEquals("level.c", Holder_.Level.c.name)
    }

    @Test
    fun `collections and maps of data classes`() {
        assertEquals("levels.c", Holder_.Levels.c.name)
        assertEquals("levels.\$.a", Holder_.Levels.posOp.a.name)
        assertEquals("levelsByName.x.a", Holder_.LevelsByName.keyProjection("x").a.name)
        assertEquals("map2.en", TestData_.Map2.keyProjection(Locale.ENGLISH).name)
        assertEquals("map2.en.price", TestData_.Map2.keyProjection(Locale.ENGLISH).price.name)
        assertEquals("price", SimpleReferenced2Data_.Price.name)
    }
}
