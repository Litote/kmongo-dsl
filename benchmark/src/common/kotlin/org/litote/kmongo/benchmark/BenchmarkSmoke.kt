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

import org.bson.BsonValue
import org.bson.RawBsonDocument
import org.bson.io.BasicOutputBuffer
import org.openjdk.jmh.annotations.Benchmark
import java.io.File

/**
 * Runs each benchmark once and writes its result (one line per benchmark), to check that KMongo 5.12 and
 * kmongo-dsl do the same work (compared by the benchmarkSmokeCheck task).
 *
 * args: output file
 */
fun main(args: Array<String>) {
    val lines = mutableListOf<String>()

    fun run(name: String, instance: Any) {
        instance.javaClass.methods
            .filter { it.isAnnotationPresent(Benchmark::class.java) }
            .sortedBy { it.name }
            .forEach { method ->
                val result = runCatching { method.invoke(instance) }
                    .fold({ describe(it) }, { "ERROR ${it.cause ?: it}" })
                lines += "$name.${method.name}\t$result"
            }
    }

    run("CodecBenchmark", CodecBenchmark().apply { checkTestObjects() })
    run("DslBenchmark", DslBenchmark())
    GoldenCodecBenchmark.samples.forEach { sample ->
        run("GoldenCodecBenchmark[$sample]", GoldenCodecBenchmark().apply { this.sample = sample; setup() })
    }

    run("MixedBenchmark", MixedBenchmark())

    File(args[0]).apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n", postfix = "\n"))
    println("${lines.size} benchmark results written to ${args[0]}")
}

private fun describe(result: Any?): String =
    when (result) {
        is BasicOutputBuffer -> RawBsonDocument(result.toByteArray()).toJson()
        is List<*> -> result.joinToString(prefix = "[", postfix = "]") { describe(it) }
        is BsonValue -> if (result.isDocument) result.asDocument().toJson() else result.toString()
        // codec instances differ between the versions: only check that a codec is found
        is org.bson.codecs.Codec<*> -> "codec for ${result.encoderClass.name}"
        else -> result.toString()
    }
