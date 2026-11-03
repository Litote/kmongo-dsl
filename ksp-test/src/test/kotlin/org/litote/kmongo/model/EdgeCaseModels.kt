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

package org.litote.kmongo.model

import org.litote.kmongo.Data

/** A type alias used as property type. */
typealias EdgeTags = List<String>

/** A nested data class. */
class EdgeOuter {
    @Data
    data class EdgeInner(val x: Int = 0)
}

/** Edge cases of the property types (the KSP and the KAPT processors must generate the same code). */
@Data
data class EdgeCases(
    val alias: EdgeTags,
    val nullableAlias: EdgeTags?,
    val callback: ((String) -> Int)? = null,
    val projected: List<out Number>,
    val starMap: Map<String, *>,
    val inner: EdgeOuter.EdgeInner,
    val inners: MutableList<EdgeOuter.EdgeInner>,
    val innersByKey: MutableMap<String, EdgeOuter.EdgeInner?>,
    val array: Array<String>,
    val iterable: Iterable<Level1>,
    val set: Set<Level1>?,
) {
    /** A property declared in the body. */
    var mutable: String? = null

    /** Not generated (extension property). */
    val String.extension: Int get() = length
}
