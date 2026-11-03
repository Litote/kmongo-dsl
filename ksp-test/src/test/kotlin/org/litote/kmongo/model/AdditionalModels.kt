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

@file:DataRegistry([FileRegisteredData::class])

package org.litote.kmongo.model

import org.litote.kmongo.Data
import org.litote.kmongo.DataRegistry

/** Not annotated: registered by the file annotation. */
data class FileRegisteredData(val fileValue: String)

/** Generic data class. */
@Data
data class GenericBox<T>(val value: T, val items: List<T>, val tags: Map<String, T>)

/** Multi-level inheritance: Level1 <- Level2 (not annotated) <- Level3. */
@Data
open class Level1(val a: String = "")

open class Level2(val b: Int = 0) : Level1()

@Data
class Level3(val c: Boolean = false) : Level2()

/** References the other generated classes. */
@Data
data class Holder(
    val level: Level3,
    val levels: List<Level3>,
    val levelsByName: Map<String, Level3>,
    val box: GenericBox<String>,
    val registered: FileRegisteredData
)
