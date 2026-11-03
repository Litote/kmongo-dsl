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

package org.litote.kmongo.reactivestreams

import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.litote.kmongo.coroutine.getCollection as coroutineGetCollection
import org.litote.kmongo.coroutine.getCollectionOfName as coroutineGetCollectionOfName

// KMongo 4/5 source compatibility: `database.getCollection<T>("name").coroutine` code
// (with `import org.litote.kmongo.reactivestreams.getCollection`) compiles on the coroutine driver database.
// Note: do not star import both `org.litote.kmongo.reactivestreams.*` and `org.litote.kmongo.coroutine.*` (ambiguity).

/**
 * Gets a collection - the name of the collection is determined by [org.litote.kmongo.util.KMongoUtil.defaultCollectionName].
 */
@Deprecated(
    "kmongo-dsl: use org.litote.kmongo.coroutine.getCollection",
    ReplaceWith("getCollection<T>()", "org.litote.kmongo.coroutine.getCollection")
)
public inline fun <reified T : Any> MongoDatabase.getCollection(): MongoCollection<T> = coroutineGetCollection<T>()

/**
 * Gets a collection.
 */
@Deprecated(
    "kmongo-dsl: use org.litote.kmongo.coroutine.getCollection",
    ReplaceWith("getCollection<T>(collectionName)", "org.litote.kmongo.coroutine.getCollection")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public inline fun <reified T : Any> MongoDatabase.getCollection(collectionName: String): MongoCollection<T> =
    coroutineGetCollectionOfName<T>(collectionName)

/**
 * Gets a collection.
 */
@Deprecated(
    "kmongo-dsl: use org.litote.kmongo.coroutine.getCollectionOfName",
    ReplaceWith("getCollectionOfName<T>(collectionName)", "org.litote.kmongo.coroutine.getCollectionOfName")
)
public inline fun <reified T : Any> MongoDatabase.getCollectionOfName(collectionName: String): MongoCollection<T> =
    coroutineGetCollectionOfName<T>(collectionName)
