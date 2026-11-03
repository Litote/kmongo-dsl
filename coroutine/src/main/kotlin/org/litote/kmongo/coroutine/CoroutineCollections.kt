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

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.conversions.Bson
import org.litote.kmongo.util.KMongoUtil
import org.litote.kmongo.util.KMongoUtil.toBsonModifier

// Coroutine specific KMongo 4/5 operations (not available in the sync API)

/**
 * Finds the document with the specified id.
 *
 * @param id the object id
 * @param clientSession the client session with which to associate this operation
 */
public suspend fun <T : Any> MongoCollection<T>.findOneById(id: Any, clientSession: ClientSession): T? =
    findOne(clientSession, KMongoUtil.idFilterQuery(id))

/**
 * Atomically find a document and update it with the not null properties of [update].
 *
 * @param filter  a document describing the query filter
 * @param update  the object used to build the update
 * @param options the options to apply to the operation
 * @return the document that was updated (before or after the update, depending of the options)
 */
public suspend fun <T : Any> MongoCollection<T>.findOneAndUpdate(
    filter: Bson,
    update: T,
    options: FindOneAndUpdateOptions = FindOneAndUpdateOptions()
): T? = findOneAndUpdate(filter, toBsonModifier(update), options)

/**
 * Atomically find a document and update it with the not null properties of [update].
 *
 * @param clientSession the client session with which to associate this operation
 * @param filter  a document describing the query filter
 * @param update  the object used to build the update
 * @param options the options to apply to the operation
 * @return the document that was updated (before or after the update, depending of the options)
 */
public suspend fun <T : Any> MongoCollection<T>.findOneAndUpdate(
    clientSession: ClientSession,
    filter: Bson,
    update: T,
    options: FindOneAndUpdateOptions = FindOneAndUpdateOptions()
): T? = findOneAndUpdate(clientSession, filter, toBsonModifier(update), options)

/**
 * Replaces a document in the collection according to the specified arguments (the _id of the replacement is ignored).
 */
public suspend fun <T : Any> MongoCollection<T>.replaceOneWithoutId(
    filter: Bson,
    replacement: T,
    options: ReplaceOptions = ReplaceOptions()
): UpdateResult = replaceOneWithFilter(filter, replacement, options)

/**
 * Replaces a document in the collection according to the specified arguments (the _id of the replacement is ignored).
 */
public suspend fun <T : Any> MongoCollection<T>.replaceOneWithoutId(
    clientSession: ClientSession,
    filter: Bson,
    replacement: T,
    options: ReplaceOptions = ReplaceOptions()
): UpdateResult = replaceOneWithFilter(clientSession, filter, replacement, options)
