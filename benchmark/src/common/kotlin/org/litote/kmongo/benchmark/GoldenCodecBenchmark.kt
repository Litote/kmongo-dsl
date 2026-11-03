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

import org.bson.io.BasicOutputBuffer
import org.litote.kmongo.benchmark.BenchmarkSupport.decode
import org.litote.kmongo.benchmark.BenchmarkSupport.encode
import org.litote.kmongo.golden.goldenSamples
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Object mapping of the golden samples (kmongo-dsl-golden): ids, enums, collections, maps, java.time and
 * kotlinx.datetime types...
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class GoldenCodecBenchmark {

    /**
     * The golden samples available with both mappings
     * ("maps" is not: kmongo-dsl serialization needs IdBsonSerializer for the Id map keys, see GoldenFormatTest).
     */
    @Param("simple", "nested", "types", "javaDates")
    lateinit var sample: String

    private lateinit var value: Any
    private lateinit var bytes: ByteArray

    @Setup
    fun setup() {
        value = goldenSamples(BenchmarkSupport.serialization).getValue(sample)
        bytes = encode(value).toByteArray()
    }

    @Benchmark
    fun decodeGolden(): Any = decode(bytes, value.javaClass)

    @Benchmark
    fun encodeGolden(): BasicOutputBuffer = encode(value)

    companion object {
        /** The values of [sample] (for the smoke run). */
        val samples: List<String> = listOf("simple", "nested", "types", "javaDates")
    }
}
