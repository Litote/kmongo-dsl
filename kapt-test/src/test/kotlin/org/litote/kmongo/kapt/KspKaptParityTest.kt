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

package org.litote.kmongo.kapt

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The KAPT processor (`kmongo-dsl-annotation-processor`) and the KSP processor (`kmongo-dsl-ksp`)
 * must generate the same code for the same models.
 */
class KspKaptParityTest {

    private fun generatedFiles(property: String): Map<String, String> {
        val root = File(System.getProperty(property) ?: error("$property is not set"))
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).path to it.readText() }
    }

    @Test
    fun `KAPT and KSP generate the same code`() {
        val ksp = generatedFiles("kmongo.ksp.generated")
        val kapt = generatedFiles("kmongo.kapt.generated")
        assertTrue(ksp.isNotEmpty())
        assertEquals(ksp.keys.sorted(), kapt.keys.sorted())
        ksp.forEach { (path, code) -> assertEquals(code, kapt[path], path) }
    }
}
