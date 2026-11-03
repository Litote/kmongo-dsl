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

import com.mongodb.kotlin.client.MongoIterable
import kotlinx.serialization.Serializable
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.litote.kmongo.MongoIterableExtensionsTest.Item
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The collection functions of [MongoIterable], with the values 1, 2, 2, 3.
 */
@Tag("JacksonMappingCategory") @Tag("SerializationMappingCategory")
class MongoIterableExtensionsTest : AllCategoriesKMongoBaseTest<Item>() {

    @Serializable
    data class Item(val n: Int)

    @BeforeEach
    fun insertItems() {
        col.insertMany(listOf(Item(3), Item(2), Item(1), Item(2)))
    }

    private val values = listOf(1, 2, 2, 3)

    private fun numbers(): MongoIterable<Int> = col.find().sort(Document("n", 1)).map { it.n }

    private fun none(): MongoIterable<Int> = col.find("{n:42}").map { it.n }

    @Suppress("UNCHECKED_CAST")
    private fun nullableNumbers(): MongoIterable<Int?> = numbers() as MongoIterable<Int?>

    @Test
    fun `can check nullability`() {
        assertEquals(values, nullableNumbers().requireNoNulls().toList())
        assertEquals(values, nullableNumbers().filterNotNull())
        assertEquals(values, nullableNumbers().filterNotNullTo(mutableListOf()))
    }

    @Test
    fun `can access elements`() {
        assertTrue(2 in numbers())
        assertFalse(9 in numbers())
        assertEquals(2, numbers().elementAt(1))
        assertEquals(-1, numbers().elementAtOrElse(9) { -1 })
        assertNull(numbers().elementAtOrNull(9))
        assertEquals(1, numbers().indexOf(2))
        assertEquals(2, numbers().lastIndexOf(2))
        assertEquals(3, numbers().last())
        assertEquals(3, numbers().lastOrNull())
        assertNull(none().lastOrNull())
    }

    @Test
    fun `can get the single element`() {
        val single = col.find("{n:3}").map { it.n }
        assertEquals(3, single.single())
        assertEquals(3, single.singleOrNull())
        assertNull(none().singleOrNull())
        assertNull(numbers().singleOrNull())
        assertFailsWith<IllegalArgumentException> { numbers().single() }
    }

    @Test
    fun `can slice and sort`() {
        assertEquals(listOf(2, 2, 3), numbers().drop(1))
        assertEquals(listOf(1, 2), numbers().take(2))
        assertEquals(listOf(3, 2, 2, 1), numbers().reversed())
        assertEquals(values, numbers().sorted())
        assertEquals(listOf(3, 2, 2, 1), numbers().sortedDescending())
        assertEquals(listOf(3, 2, 2, 1), numbers().sortedWith(reverseOrder()))
    }

    @Test
    fun `can convert to collections`() {
        assertEquals(values, numbers().toMutableList())
        assertEquals(setOf(1, 2, 3), numbers().toMutableSet())
        assertEquals(listOf(1, 2, 3), numbers().toSortedSet().toList())
        assertEquals(listOf(3, 2, 1), numbers().toSortedSet(reverseOrder()).toList())
    }

    @Test
    fun `can apply set operations`() {
        assertEquals(listOf(1, 2, 3), numbers().distinct())
        assertEquals(setOf(2), numbers().intersect(listOf(2, 9)))
        assertEquals(setOf(1, 3), numbers().subtract(listOf(2, 9)))
        assertEquals(setOf(1, 2, 3, 9), numbers().union(listOf(2, 9)))
    }

    @Test
    fun `can check the content`() {
        assertTrue(numbers().any())
        assertFalse(none().any())
        assertTrue(none().none())
        assertFalse(numbers().none())
        assertEquals(4, numbers().count())
    }

    @Test
    fun `can get the largest and smallest elements`() {
        val doubles = numbers().map { it.toDouble() }
        val floats = numbers().map { it.toFloat() }

        assertEquals(3, numbers().max())
        assertEquals(3.0, doubles.max())
        assertEquals(3f, floats.max())
        assertEquals(1, numbers().maxWith(reverseOrder()))
        assertEquals(1, numbers().min())
        assertEquals(1.0, doubles.min())
        assertEquals(1f, floats.min())
        assertEquals(3, numbers().minWith(reverseOrder()))

        assertEquals(3, numbers().maxOrNull())
        assertEquals(3.0, doubles.maxOrNull())
        assertEquals(3f, floats.maxOrNull())
        assertEquals(1, numbers().maxWithOrNull(reverseOrder()))
        assertEquals(1, numbers().minOrNull())
        assertEquals(1.0, doubles.minOrNull())
        assertEquals(1f, floats.minOrNull())
        assertEquals(3, numbers().minWithOrNull(reverseOrder()))
        assertNull(none().maxOrNull())
        assertNull(none().minOrNull())
    }

    @Test
    fun `can split in chunks and windows`() {
        assertEquals(listOf(listOf(1, 2), listOf(2, 3)), numbers().chunked(2))
        assertEquals(listOf(3, 5), numbers().chunked(2) { it.sum() })
        assertEquals(listOf(listOf(1, 2), listOf(2, 2), listOf(2, 3)), numbers().windowed(2))
        assertEquals(listOf(3, 4, 5), numbers().windowed(2, 1, false) { it.sum() })
        assertEquals(listOf(1 to 2, 2 to 2, 2 to 3), numbers().zipWithNext())
    }

    @Test
    fun `can add and remove elements`() {
        assertEquals(listOf(2, 2, 3), numbers().minus(1))
        assertEquals(listOf(1, 3), numbers().minus(arrayOf(2)))
        assertEquals(listOf(1, 3), numbers().minus(listOf(2)))
        assertEquals(listOf(1, 3), numbers().minus(sequenceOf(2)))
        assertEquals(values + 4, numbers().plus(4))
        assertEquals(values + 4, numbers().plus(arrayOf(4)))
        assertEquals(values + 4, numbers().plus(listOf(4)))
        assertEquals(values + 4, numbers().plus(sequenceOf(4)))
    }

    @Test
    fun `can zip and join`() {
        assertEquals(listOf(1 to "a", 2 to "b"), numbers().zip(arrayOf("a", "b")))
        assertEquals(listOf(1 to "a", 2 to "b"), numbers().zip(listOf("a", "b")))
        assertEquals("1,2,2,3", numbers().joinToString(","))
        assertEquals("[1-2-2-3]", numbers().joinTo(StringBuilder(), "-", "[", "]").toString())
    }

    @Test
    fun `can compute sum and average`() {
        assertEquals(8, numbers().sum())
        assertEquals(8, numbers().map { it.toByte() }.sum())
        assertEquals(8, numbers().map { it.toShort() }.sum())
        assertEquals(8L, numbers().map { it.toLong() }.sum())
        assertEquals(8f, numbers().map { it.toFloat() }.sum())
        assertEquals(8.0, numbers().map { it.toDouble() }.sum())

        assertEquals(2.0, numbers().average())
        assertEquals(2.0, numbers().map { it.toByte() }.average())
        assertEquals(2.0, numbers().map { it.toShort() }.average())
        assertEquals(2.0, numbers().map { it.toLong() }.average())
        assertEquals(2.0, numbers().map { it.toFloat() }.average())
        assertEquals(2.0, numbers().map { it.toDouble() }.average())
    }

    @Test
    fun `can flatten and unzip`() {
        assertEquals(listOf(1, 1, 2, 2, 2, 2, 3, 3), numbers().map<Iterable<Int>> { listOf(it, it) }.flatten())
        assertEquals(values to values.map { it * 2 }, numbers().map { it to it * 2 }.unzip())
    }

    @Test
    fun `can convert to maps`() {
        val expected = mapOf(1 to "1", 2 to "2", 3 to "3")
        assertEquals(expected, numbers().map { it to it.toString() }.toMap())
        assertEquals(expected, numbers().map { it to it.toString() }.toMap(mutableMapOf()))
    }

    @Test
    fun `can filter by class`() {
        assertEquals(values, numbers().filterIsInstance(Integer::class.java).map { it.toInt() })
        assertEquals(emptyList(), numbers().filterIsInstance(String::class.java))
        assertEquals(
            emptyList<String>(),
            numbers().filterIsInstanceTo(mutableListOf<String>(), String::class.java),
        )
    }

    @Test
    fun `can shuffle`() {
        assertEquals(values, numbers().shuffled().sorted())
        assertEquals(values, numbers().shuffled(Random(1)).sorted())
    }
}
