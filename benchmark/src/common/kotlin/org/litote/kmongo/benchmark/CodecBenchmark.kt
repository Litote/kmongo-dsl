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

import org.bson.Document
import org.bson.codecs.Codec
import org.bson.io.BasicOutputBuffer
import org.bson.types.ObjectId
import org.litote.kmongo.benchmark.BenchmarkSupport.bsonBytes
import org.litote.kmongo.benchmark.BenchmarkSupport.decode
import org.litote.kmongo.benchmark.BenchmarkSupport.driverRegistry
import org.litote.kmongo.benchmark.BenchmarkSupport.encode
import org.litote.kmongo.benchmark.BenchmarkSupport.kotlinxCodec
import org.litote.kmongo.benchmark.BenchmarkSupport.registry
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Object mapping: decodes / encodes the KMongo benchmark models.
 *
 * The `driver*` benchmarks (plain driver `Document` + manual mapping) do not depend on KMongo: they are the
 * baseline to compare runs on different machines.
 * The `driverKotlinx*` benchmarks use the official `bson-kotlinx` codec directly (no KMongo, no registry lookup):
 * the floor of the kmongo-dsl kotlinx.serialization mapping.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class CodecBenchmark {

    private val friendBytes = bsonBytes(BenchmarkSupport.FRIEND_JSON)
    private val friendWithBuddiesBytes = bsonBytes(BenchmarkSupport.FRIEND_WITH_BUDDIES_JSON)

    // built directly (not decoded with the KMongo codec): the setup must not run the serializers with another
    // decoder than the measured one (JIT type profile pollution of the generated serializers)
    private val friend = Friend(ObjectId(OBJECT_ID), "John0", "Address0", Coordinate(1, 0), Gender.MALE)
    private val friendWithBuddies = FriendWithBuddies(
        ObjectId(OBJECT_ID),
        "John0",
        "Address0",
        Coordinate(1, 0),
        Gender.MALE,
        (1..4).map { FriendWithBuddies(null, "John0$it", "Address0", Coordinate(1, 0)) }
    )
    private val kotlinxFriendCodec = kotlinxCodec(Friend::class, Friend.serializer())
    private val kotlinxFriendWithBuddiesCodec = kotlinxCodec(FriendWithBuddies::class, FriendWithBuddies.serializer())

    /** Checks that the test objects are the ones of the BSON documents (smoke run only: not in a JMH setup). */
    fun checkTestObjects() {
        check(decode(friendBytes, Friend::class.java) == friend)
        check(decode(friendWithBuddiesBytes, FriendWithBuddies::class.java) == friendWithBuddies)
    }

    @Benchmark
    fun driverFriend(): Friend = parseFriend(decode(friendBytes, Document::class.java, driverRegistry))

    @Benchmark
    fun driverFriendWithBuddies(): FriendWithBuddies =
        parseFriendWithBuddies(decode(friendWithBuddiesBytes, Document::class.java, driverRegistry))

    @Benchmark
    fun driverKotlinxDecodeFriend(): Friend = decode(friendBytes, kotlinxFriendCodec)

    @Benchmark
    fun driverKotlinxDecodeFriendWithBuddies(): FriendWithBuddies =
        decode(friendWithBuddiesBytes, kotlinxFriendWithBuddiesCodec)

    @Benchmark
    fun driverKotlinxEncodeFriend(): BasicOutputBuffer = encode(friend, kotlinxFriendCodec)

    @Benchmark
    fun driverKotlinxEncodeFriendWithBuddies(): BasicOutputBuffer = encode(friendWithBuddies, kotlinxFriendWithBuddiesCodec)

    @Benchmark
    fun decodeFriend(): Friend = decode(friendBytes, Friend::class.java)

    @Benchmark
    fun decodeFriendWithBuddies(): FriendWithBuddies = decode(friendWithBuddiesBytes, FriendWithBuddies::class.java)

    @Benchmark
    fun encodeFriend(): BasicOutputBuffer = encode(friend)

    @Benchmark
    fun encodeFriendWithBuddies(): BasicOutputBuffer = encode(friendWithBuddies)

    /** Cost of the codec cache of the registry. */
    @Benchmark
    fun codecLookup(): Codec<FriendWithBuddies> = registry.get(FriendWithBuddies::class.java)

    private companion object {
        const val OBJECT_ID = "507480ff3004983e1fe5ed55"
    }

    private fun parseCoordinate(document: Document): Coordinate =
        Coordinate(document["lat"] as Int, document["lng"] as Int)

    private fun parseFriend(document: Document): Friend =
        Friend(
            document["_id"] as ObjectId?,
            document["name"] as String?,
            document["address"] as String?,
            (document["coordinate"] as Document?)?.let(::parseCoordinate),
            (document["gender"] as String?)?.let(Gender::valueOf)
        )

    @Suppress("UNCHECKED_CAST")
    private fun parseFriendWithBuddies(document: Document): FriendWithBuddies =
        FriendWithBuddies(
            document["_id"] as ObjectId?,
            document["name"] as String?,
            document["address"] as String?,
            (document["coordinate"] as Document?)?.let(::parseCoordinate),
            (document["gender"] as String?)?.let(Gender::valueOf),
            (document["buddies"] as List<Document>?)?.map(::parseFriendWithBuddies).orEmpty()
        )
}
