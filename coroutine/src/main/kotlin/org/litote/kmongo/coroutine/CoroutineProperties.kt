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

// Generated from kmongo-dsl-sync SyncProperties.kt by the generateCoroutineSources task: do not edit.

package org.litote.kmongo.coroutine

import org.litote.kmongo.*

import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.conversions.Bson
import kotlin.reflect.KProperty1

/**
 * Creates a $lookup pipeline stage for the specified filter (Typesafe version)
 *
 * @param from         the collection in the same database to perform the join with.
 * @param localField   specifies the field from the local collection to match values against.
 * @param foreignField specifies the field in the from collection to match values against.
 * @param newAs           the name of the new array field to add to the input documents.
 * @return the $lookup pipeline stage
 * @mongodb.driver.manual reference/operator/aggregation/lookup/ $lookup
 */
public suspend fun <FROM : Any> lookup(
    from: MongoCollection<FROM>,
    localField: KProperty1<out Any, Any?>,
    foreignField: KProperty1<FROM, Any?>,
    newAs: KProperty1<out Any, Any?>
): Bson =
    lookup(from.namespace.collectionName, localField.path(), foreignField.path(), newAs.path())
