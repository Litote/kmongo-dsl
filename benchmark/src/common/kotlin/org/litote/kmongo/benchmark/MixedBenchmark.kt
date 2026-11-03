/*
 * Copyright (C) 2026 Litote
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

import org.bson.io.BasicOutputBuffer
import org.litote.kmongo.benchmark.BenchmarkSupport.bsonBytes
import org.litote.kmongo.benchmark.BenchmarkSupport.decode
import org.litote.kmongo.benchmark.BenchmarkSupport.encode
import org.litote.kmongo.golden.goldenSamples
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Object mapping of several models in the same JVM, as an application does (one operation = all the documents):
 * [CodecBenchmark] and [GoldenCodecBenchmark] run one model per JVM, so the JIT optimizes the shared code for it.
 * Use this benchmark to compare the mappings, and the other ones to look at a given model.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class MixedBenchmark {

    private val values: List<Any> = goldenSamples(BenchmarkSupport.serialization).let { golden ->
        listOf(
            decode(bsonBytes(BenchmarkSupport.FRIEND_JSON), Friend::class.java),
            decode(bsonBytes(BenchmarkSupport.FRIEND_WITH_BUDDIES_JSON), FriendWithBuddies::class.java),
        ) + GoldenCodecBenchmark.samples.map { golden.getValue(it) }
    }
    private val documents: List<Pair<ByteArray, Class<out Any>>> = values.map { encode(it).toByteArray() to it.javaClass }

    @Benchmark
    fun decodeAll(): List<Any> = documents.map { (bytes, type) -> decode(bytes, type) }

    @Benchmark
    fun encodeAll(): List<BasicOutputBuffer> = values.map { encode(it) }
}
