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

package org.litote.kmongo.coroutine

import com.mongodb.kotlin.client.coroutine.AggregateFlow
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.ChangeStreamFlow
import com.mongodb.kotlin.client.coroutine.DistinctFlow
import com.mongodb.kotlin.client.coroutine.FindFlow
import com.mongodb.kotlin.client.coroutine.ListCollectionsFlow
import com.mongodb.kotlin.client.coroutine.ListIndexesFlow
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson
import org.litote.kmongo.EMPTY_BSON

// KMongo 4/5 coroutine API source compatibility layer.
// The KMongo 4/5 coroutine wrappers are replaced by the official Kotlin coroutine driver classes.

@Deprecated(
    "kmongo-dsl: use the official driver MongoClient",
    ReplaceWith("MongoClient", "com.mongodb.kotlin.client.coroutine.MongoClient"),
)
public typealias CoroutineClient = MongoClient

@Deprecated("kmongo-dsl: use the official driver MongoDatabase", ReplaceWith("MongoDatabase", "com.mongodb.kotlin.client.coroutine.MongoDatabase"))
public typealias CoroutineDatabase = MongoDatabase

@Deprecated("kmongo-dsl: use the official driver MongoCollection", ReplaceWith("MongoCollection<T>", "com.mongodb.kotlin.client.coroutine.MongoCollection"))
public typealias CoroutineCollection<T> = MongoCollection<T>

@Deprecated("kmongo-dsl: use the official driver FindFlow", ReplaceWith("FindFlow<T>", "com.mongodb.kotlin.client.coroutine.FindFlow"))
public typealias CoroutineFindPublisher<T> = FindFlow<T>

@Deprecated("kmongo-dsl: use the official driver AggregateFlow", ReplaceWith("AggregateFlow<T>", "com.mongodb.kotlin.client.coroutine.AggregateFlow"))
public typealias CoroutineAggregatePublisher<T> = AggregateFlow<T>

@Deprecated("kmongo-dsl: use the official driver DistinctFlow", ReplaceWith("DistinctFlow<T>", "com.mongodb.kotlin.client.coroutine.DistinctFlow"))
public typealias CoroutineDistinctPublisher<T> = DistinctFlow<T>

@Deprecated("kmongo-dsl: use the official driver ChangeStreamFlow", ReplaceWith("ChangeStreamFlow<T>", "com.mongodb.kotlin.client.coroutine.ChangeStreamFlow"))
public typealias CoroutineChangeStreamPublisher<T> = ChangeStreamFlow<T>

@Deprecated("kmongo-dsl: use the official driver ListIndexesFlow", ReplaceWith("ListIndexesFlow<T>", "com.mongodb.kotlin.client.coroutine.ListIndexesFlow"))
public typealias CoroutineListIndexesPublisher<T> = ListIndexesFlow<T>

@Deprecated(
    "kmongo-dsl: use the official driver ListCollectionsFlow",
    ReplaceWith("ListCollectionsFlow<T>", "com.mongodb.kotlin.client.coroutine.ListCollectionsFlow")
)
public typealias CoroutineListCollectionsPublisher<T> = ListCollectionsFlow<T>

/**
 * KMongo 4/5 `CoroutineCollection.findAndCast`: finds all documents in the collection, mapped to [T].
 */
@Deprecated("kmongo-dsl: use the official driver find<T>(filter)", ReplaceWith("find<T>(filter)"))
public inline fun <reified T : Any> MongoCollection<*>.findAndCast(filter: Bson = EMPTY_BSON): FindFlow<T> =
    find(filter, T::class.java)

/**
 * KMongo 4/5 `CoroutineCollection.findAndCast`: finds all documents in the collection, mapped to [T].
 */
@Deprecated("kmongo-dsl: use the official driver find<T>(clientSession, filter)", ReplaceWith("find<T>(clientSession, filter)"))
public inline fun <reified T : Any> MongoCollection<*>.findAndCast(clientSession: ClientSession, filter: Bson): FindFlow<T> =
    find(clientSession, filter, T::class.java)

/**
 * KMongo 4/5 `CoroutinePublisher.consumeEach`: iterates over all elements.
 */
@Deprecated("kmongo-dsl: use collect", ReplaceWith("collect(action)"))
public suspend fun <T> Flow<T>.consumeEach(action: suspend (T) -> Unit): Unit = collect { action(it) }

/**
 * KMongo 4/5: converted a reactive streams client to a coroutine client. The client is already a coroutine client.
 */
@Deprecated("kmongo-dsl: the client is already a coroutine client", ReplaceWith("this"))
public val MongoClient.coroutine: MongoClient get() = this

/**
 * KMongo 4/5: converted a reactive streams database. The database is already a coroutine database.
 */
@Deprecated("kmongo-dsl: the database is already a coroutine database", ReplaceWith("this"))
public val MongoDatabase.coroutine: MongoDatabase get() = this

/**
 * KMongo 4/5: converted a reactive streams collection. The collection is already a coroutine collection.
 */
@Deprecated("kmongo-dsl: the collection is already a coroutine collection", ReplaceWith("this"))
public val <T : Any> MongoCollection<T>.coroutine: MongoCollection<T> get() = this

/**
 * KMongo 4/5: returned the publisher as a [Flow]. Driver flows are already [Flow]s.
 */
@Deprecated("kmongo-dsl: already a Flow", ReplaceWith("this"))
public fun <T : Any> FindFlow<T>.toFlow(): Flow<T> = this

/**
 * KMongo 4/5: returned the publisher as a [Flow]. Driver flows are already [Flow]s.
 */
@Deprecated("kmongo-dsl: already a Flow", ReplaceWith("this"))
public fun <T : Any> AggregateFlow<T>.toFlow(): Flow<T> = this

/**
 * KMongo 4/5 [CoroutineFindPublisher.first] returned null if there is no result
 * (`kotlinx.coroutines.flow.first` throws a [NoSuchElementException]).
 *
 * This extension is more specific than `kotlinx.coroutines.flow.first`, so it would silently be selected
 * for `find(...).first()` calls: it is deprecated with the ERROR level, so that each call is reviewed.
 * An explicit `import kotlinx.coroutines.flow.first` takes precedence over the star import of this package.
 */
@Deprecated(
    "kmongo-dsl: KMongo 4/5 first() returned null when there is no result. " +
            "Use firstOrNull(), or explicitly import kotlinx.coroutines.flow.first to get the throwing variant.",
    ReplaceWith("firstOrNull()", "kotlinx.coroutines.flow.firstOrNull"),
    level = DeprecationLevel.ERROR
)
public suspend fun <T : Any> FindFlow<T>.first(): T? = firstOrNull()

/**
 * KMongo 4/5 [CoroutineAggregatePublisher.first] returned null if there is no result.
 */
@Deprecated(
    "kmongo-dsl: KMongo 4/5 first() returned null when there is no result. " +
            "Use firstOrNull(), or explicitly import kotlinx.coroutines.flow.first to get the throwing variant.",
    ReplaceWith("firstOrNull()", "kotlinx.coroutines.flow.firstOrNull"),
    level = DeprecationLevel.ERROR
)
public suspend fun <T : Any> AggregateFlow<T>.first(): T? = firstOrNull()

/**
 * KMongo 4/5 [CoroutineDistinctPublisher.first] returned null if there is no result.
 */
@Deprecated(
    "kmongo-dsl: KMongo 4/5 first() returned null when there is no result. " +
            "Use firstOrNull(), or explicitly import kotlinx.coroutines.flow.first to get the throwing variant.",
    ReplaceWith("firstOrNull()", "kotlinx.coroutines.flow.firstOrNull"),
    level = DeprecationLevel.ERROR
)
public suspend fun <T : Any> DistinctFlow<T>.first(): T? = firstOrNull()

/**
 * Returns all the results as a list (KMongo 4/5 publishers provided a `toList()` member).
 */
public suspend fun <T : Any> FindFlow<T>.toList(): List<T> = (this as Flow<T>).toList()

/**
 * Returns all the results as a list (KMongo 4/5 publishers provided a `toList()` member).
 */
public suspend fun <T : Any> AggregateFlow<T>.toList(): List<T> = (this as Flow<T>).toList()

/**
 * Returns all the results as a list (KMongo 4/5 publishers provided a `toList()` member).
 */
public suspend fun <T : Any> DistinctFlow<T>.toList(): List<T> = (this as Flow<T>).toList()

/**
 * Returns all the results as a list (KMongo 4/5 publishers provided a `toList()` member).
 */
public suspend fun <T : Any> ListIndexesFlow<T>.toList(): List<T> = (this as Flow<T>).toList()

/**
 * Returns all the results as a list (KMongo 4/5 publishers provided a `toList()` member).
 */
public suspend fun <T : Any> ListCollectionsFlow<T>.toList(): List<T> = (this as Flow<T>).toList()

/**
 * KMongo 4/5 coroutine extension of the reactive streams ClientSession.
 */
@Deprecated("kmongo-dsl: use commitTransaction()", ReplaceWith("commitTransaction()"))
public suspend fun ClientSession.commitTransactionAndAwait(): Unit = commitTransaction()

/**
 * KMongo 4/5 coroutine extension of the reactive streams ClientSession.
 */
@Deprecated("kmongo-dsl: use abortTransaction()", ReplaceWith("abortTransaction()"))
public suspend fun ClientSession.abortTransactionAndAwait(): Unit = abortTransaction()
