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

import org.bson.BsonArray
import org.bson.BsonDocument
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.and
import org.litote.kmongo.avg
import org.litote.kmongo.benchmark.BenchmarkSupport.render
import org.litote.kmongo.descending
import org.litote.kmongo.div
import org.litote.kmongo.elemMatch
import org.litote.kmongo.eq
import org.litote.kmongo.excludeId
import org.litote.kmongo.fields
import org.litote.kmongo.group
import org.litote.kmongo.gt
import org.litote.kmongo.`in`
import org.litote.kmongo.include
import org.litote.kmongo.match
import org.litote.kmongo.or
import org.litote.kmongo.path
import org.litote.kmongo.project
import org.litote.kmongo.projection
import org.litote.kmongo.regex
import org.litote.kmongo.set
import org.litote.kmongo.setTo
import org.litote.kmongo.sort
import org.litote.kmongo.sum
import org.litote.kmongo.util.KMongoUtil
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Type-safe DSL and JSON queries: each query is built, then rendered to a [BsonDocument]
 * (as the driver does before sending it).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class DslBenchmark {

    private val friend = Friend(
        ObjectId("507480ff3004983e1fe5ed55"),
        "John0",
        "Address0",
        Coordinate(1, 0),
        Gender.MALE
    )

    @Benchmark
    fun filterSimple(): BsonDocument = (Friend::name eq "John").render()

    @Benchmark
    fun filterNested(): BsonDocument = (Friend::coordinate / Coordinate::lat gt 1).render()

    @Benchmark
    fun filterComplex(): BsonDocument =
        and(
            FriendWithBuddies::name `in` listOf("John0", "Jane"),
            or(FriendWithBuddies::gender eq Gender.FEMALE, FriendWithBuddies::address regex "^Addr"),
            FriendWithBuddies::buddies elemMatch (FriendWithBuddies::coordinate / Coordinate::lng eq 0)
        ).render()

    @Benchmark
    fun updateSet(): BsonDocument =
        set(
            Friend::name setTo "Jane",
            Friend::gender setTo Gender.FEMALE,
            Friend::coordinate / Coordinate::lat setTo 3
        ).render()

    /** `updateOne(entity)`: the update is generated from the object mapping. */
    @Benchmark
    fun updateEntity(): BsonDocument = KMongoUtil.toBsonModifier(friend).render()

    @Benchmark
    fun projectionAndSort(): BsonDocument =
        BsonDocument("projection", fields(include(Friend::name, Friend::coordinate / Coordinate::lat), excludeId()).render())
            .append("sort", descending(Friend::name, Friend::address).render())

    @Benchmark
    fun aggregatePipeline(): BsonArray =
        BsonArray(
            listOf<Bson>(
                match(Friend::name eq "John0", Friend::coordinate / Coordinate::lat gt 0),
                group(
                    Friend::gender.projection,
                    FriendStats::count sum 1,
                    FriendStats::averageLat avg (Friend::coordinate / Coordinate::lat).projection
                ),
                sort(descending(FriendStats::count)),
                project(FriendStats::count, FriendStats::averageLat)
            ).map { it.render() }
        )

    @Benchmark
    fun propertyPath(): String = (FriendWithBuddies::coordinate / Coordinate::lat).path()

    @Benchmark
    fun jsonQuery(): BsonDocument =
        KMongoUtil.toBson("""{name: 'John0', 'coordinate.lat': {${'$'}lt: 40}, gender: {${'$'}in: ['MALE', 'FEMALE']}}""")
            .render()
}
