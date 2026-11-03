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

package org.litote.kmongo.benchmark

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

// Models of the KMongo benchmark (kmongo-benchmark). Compiled with KMongo 5.12 and with kmongo-dsl:
// only use APIs available in both.

@Serializable
enum class Gender { FEMALE, MALE }

@Serializable
data class Coordinate(val lat: Int, val lng: Int)

@Serializable
data class Friend(
    @Contextual
    @SerialName("_id")
    @BsonId
    val id: ObjectId? = null,
    val name: String? = null,
    val address: String? = null,
    val coordinate: Coordinate? = null,
    val gender: Gender? = null
)

@Serializable
data class FriendWithBuddies(
    @Contextual
    @SerialName("_id")
    @BsonId
    val id: ObjectId? = null,
    val name: String? = null,
    val address: String? = null,
    val coordinate: Coordinate? = null,
    val gender: Gender? = null,
    val buddies: List<FriendWithBuddies> = emptyList()
)

/** Result of the aggregation pipeline benchmark. */
@Serializable
data class FriendStats(val _id: Gender?, val count: Int, val averageLat: Double)
