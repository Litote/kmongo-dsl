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

package org.litote.kmongo.other

import org.litote.kmongo.asSequence
import org.junit.jupiter.api.Tag
import org.litote.kmongo.find
import org.litote.kmongo.mapIndexed
import org.litote.kmongo.sortedBy
import org.litote.kmongo.toHashSet
import org.litote.kmongo.withIndex
import kotlin.test.Test
import org.litote.kmongo.AllCategoriesKMongoBaseTest
import org.litote.kmongo.evaluate
import org.litote.kmongo.model.Friend
import kotlin.test.assertEquals

/**
 * kmongo-dsl: the official Kotlin driver MongoCursor is a sealed interface, so the cursor closing
 * can not be checked with wrappers any more - these tests check the collection-like operations.
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class MongoIterableTest : AllCategoriesKMongoBaseTest<Friend>() {

    private val john = Friend("John", "22 Wall Street Avenue")

    @Test
    fun `forEach iterates over all elements`() {
        col.insertOne(john)
        val friends = mutableListOf<Friend>()
        col.find().forEach { friends.add(it) }
        assertEquals(listOf(john), friends)
    }

    @Test
    fun `find returns the first matching element`() {
        col.insertOne(john)
        assertEquals(john, col.find().find { true })
    }

    @Test
    fun `firstOrNull returns the first element`() {
        col.insertOne(john)
        assertEquals(john, col.find().firstOrNull())
    }

    @Test
    fun `mapIndexed maps all elements`() {
        col.insertOne(john)
        assertEquals(john, col.find().mapIndexed { _, v -> v }.first())
    }

    @Test
    fun `withIndex then sortedBy`() {
        col.insertOne(john)
        val sorted = col.find().withIndex().sortedBy { it.index }
        assertEquals(listOf(john), sorted.map { it.value })
        assertEquals(0, sorted.map { it.index }.first())
    }

    @Test
    fun `asSequence returns all elements`() {
        col.insertOne(john)
        assertEquals(john, col.find().asSequence().filter { it.name != "Joe" }.last())
    }

    @Test
    fun `evaluate evaluates the sequence expression`() {
        col.insertOne(john)
        assertEquals(john, col.find().evaluate { filter { it.name != "Joe" }.last() })
    }

    @Test
    fun `map then toList`() {
        col.insertOne(john)
        val map = col.find().map { it._id to it }.toList().toMap()
        assertEquals(john, map.values.first())
        assertEquals(john._id, map.keys.first())
    }

    @Test
    fun `toHashSet returns all elements`() {
        col.insertOne(john)
        assertEquals(john, col.find().toHashSet().first())
    }
}
