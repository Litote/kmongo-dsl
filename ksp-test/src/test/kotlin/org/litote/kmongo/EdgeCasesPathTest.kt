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

import org.litote.kmongo.model.EdgeCases_
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Edge cases of the property types (nullable map values, `Iterable`, type aliases, star projections...).
 */
class EdgeCasesPathTest {

    @Test
    fun `edge cases paths`() {
        assertEquals("alias", EdgeCases_.Alias.name)
        assertEquals("alias.\$", EdgeCases_.Alias.posOp.name)
        assertEquals("nullableAlias", EdgeCases_.NullableAlias.name)
        assertEquals("callback", EdgeCases_.Callback.name)
        assertEquals("projected.0", EdgeCases_.Projected.pos(0).name)
        assertEquals("starMap.a", EdgeCases_.StarMap.keyProjection("a").name)
        assertEquals("inner.x", EdgeCases_.Inner.x.name)
        assertEquals("inners.\$.x", EdgeCases_.Inners.posOp.x.name)
        assertEquals("innersByKey.k.x", EdgeCases_.InnersByKey.keyProjection("k").x.name)
        assertEquals("iterable.\$[].a", EdgeCases_.Iterable.allPosOp.a.name)
        assertEquals("set.a", EdgeCases_.Set.a.name)
        assertEquals("mutable", EdgeCases_.Mutable.name)
    }
}
