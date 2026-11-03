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

// Generated from kmongo-dsl-sync FindIterables.kt by the generateCoroutineSources task: do not edit.

package org.litote.kmongo.coroutine

import org.litote.kmongo.*

import com.mongodb.kotlin.client.coroutine.FindFlow
import org.litote.kmongo.util.KMongoUtil
import kotlin.reflect.KProperty

/**
 * Sets the query filter to apply to the query.
 *
 * @param filter the filter, which may be null
 * @return this
 */
public fun <T : Any> FindFlow<T>.filter(filter: String): FindFlow<T> = filter(KMongoUtil.toBson(filter))

/**
 * Sets a document describing the fields to return for all matching documents.
 *
 * @param projection the project document
 * @return this
 */
public fun <T : Any> FindFlow<T>.projection(projection: String): FindFlow<T> = projection(KMongoUtil.toBson(projection))

/**
 * Sets a document describing the fields to return for all matching documents.
 *
 * @param projections the properties of the returned fields
 * @return this
 */
public fun <T : Any> FindFlow<T>.projection(vararg projections: KProperty<*>): FindFlow<T> =
    projection(include(*projections))


/**
 * Sets the sort criteria to apply to the query.
 *
 * @param sort the sort criteria
 * @return this
 */
public fun <T : Any> FindFlow<T>.sort(sort: String): FindFlow<T> = sort(KMongoUtil.toBson(sort))

/**
 * Sets the sort criteria with specified ascending properties to apply to the query.
 *
 * @param properties the properties
 * @return this
 */
public fun <T : Any> FindFlow<T>.ascendingSort(vararg properties: KProperty<*>): FindFlow<T> = sort(ascending(*properties))

/**
 * Sets the sort criteria with specified descending properties to apply to the query.
 *
 * @param properties the properties
 * @return this
 */
public fun <T : Any> FindFlow<T>.descendingSort(vararg properties: KProperty<*>): FindFlow<T> = sort(descending(*properties))
