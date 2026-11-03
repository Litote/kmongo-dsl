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

// Generated from kmongo-dsl-sync MongoDatabases.kt by the generateCoroutineSources task: do not edit.

package org.litote.kmongo.coroutine

import org.litote.kmongo.*

import com.mongodb.ReadPreference
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.litote.kmongo.util.KMongoUtil
import org.litote.kmongo.util.KMongoUtil.defaultCollectionName

/**
 * Returns a [MongoDatabase] with a KMongo codec.
 */
public suspend fun MongoDatabase.withKMongo(): MongoDatabase =
    withCodecRegistry(org.litote.kmongo.util.KMongoClientSettings.configureRegistry(codecRegistry))

/**
 * Gets a collection.
 *
 * @param collectionName the name of the collection to return
 * @param <T>            the default target type of the collection to return
 * @return the collection
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public inline fun <reified T : Any> MongoDatabase.getCollection(collectionName: String): MongoCollection<T> =
    getCollectionOfName(collectionName)

/**
 * Gets a collection.
 *
 * @param collectionName the name of the collection to return
 * @param <T>            the default target type of the collection to return
 * @return the collection
 */
public inline fun <reified T : Any> MongoDatabase.getCollectionOfName(collectionName: String): MongoCollection<T> =
    getCollection(collectionName, T::class.java)

/**
 * Gets a collection.
 *
 * @param <T>            the default target type of the collection to return
 *                       - the name of the collection is determined by [defaultCollectionName]
 * @return the collection
 * @see defaultCollectionName
 */
public inline fun <reified T : Any> MongoDatabase.getCollection(): MongoCollection<T> =
    getCollection(defaultCollectionName(T::class), T::class.java)

/**
 * Executes the given command in the context of the current database with the given read preference.
 *
 * @param command        the command to be run
 * @param readPreference the {@link com.mongodb.ReadPreference} to be used when executing the command
 * @param <TResult>      the type of the class to use instead of {@code Document}.
 */
public suspend inline fun <reified TResult : Any> MongoDatabase.runCommand(command: String, readPreference: ReadPreference): TResult =
    runCommand(KMongoUtil.toBson(command), readPreference, TResult::class.java)

/**
 * Executes the given command in the context of the current database with the given read preference.
 *
 * @param command        the command to be run
 * @param <TResult>      the type of the class to use instead of {@code Document}.
 */
public suspend inline fun <reified TResult : Any> MongoDatabase.runCommand(command: String): TResult =
    runCommand(command, readPreference)


/**
 * Drops the default collection of [T] (see [getCollection]).
 */
public suspend inline fun <reified T : Any> MongoDatabase.dropCollection(): Unit = getCollection<T>().drop()
