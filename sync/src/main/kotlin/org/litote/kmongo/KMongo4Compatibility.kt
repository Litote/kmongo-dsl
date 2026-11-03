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

import com.mongodb.kotlin.client.MongoCollection
import com.mongodb.kotlin.client.MongoCursor
import com.mongodb.kotlin.client.MongoIterable

// KMongo 4/5 (Java sync driver) source compatibility: the code compiles with deprecation warnings,
// use the IDE quick fix ("Replace with...") to migrate to the official Kotlin sync driver API.

/**
 * KMongo 4/5 compatibility: the Java driver `MongoIterable` was an `Iterable`
 * (`for (x in col.find())` compiles) - the Kotlin driver `MongoIterable` is not.
 *
 * The returned cursor must be closed if the iteration is not complete: prefer [MongoIterable.forEach],
 * [MongoIterable.toList] or `use { }` on [MongoIterable.cursor].
 */
@Deprecated("kmongo-dsl: use cursor() (and close it), forEach or toList", ReplaceWith("cursor()"))
public operator fun <T : Any> MongoIterable<T>.iterator(): MongoCursor<T> = cursor()

/**
 * KMongo 4/5 compatibility: the Java driver `MongoIterable.into(target)`.
 */
@Deprecated("kmongo-dsl: use toCollection(target)", ReplaceWith("toCollection(target)"))
public fun <T : Any, A : MutableCollection<in T>> MongoIterable<T>.into(target: A): A = toCollection(target)

/**
 * KMongo 4/5 compatibility: `withDocumentClass` was a KMongo extension, it is now a member of the official driver
 * collection - this declaration only keeps `import org.litote.kmongo.withDocumentClass` valid: remove the import.
 */
@Deprecated("kmongo-dsl: withDocumentClass is a member of the driver collection: remove the import")
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public inline fun <reified NewTDocument : Any> MongoCollection<*>.withDocumentClass(): MongoCollection<NewTDocument> =
    withDocumentClass(NewTDocument::class.java)
