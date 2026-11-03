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

package org.litote.kmongo.buildlogic

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File
import java.util.Locale

/**
 * The KMongo 5.12 vs kmongo-dsl benchmark (kmongo-dsl-benchmark) reports.
 */
object Benchmarks {

    private data class Score(val time: Double, val error: Double, val alloc: Double?)

    /** A JMH JSON number ("NaN" is written as a string, e.g. the error of a single iteration). */
    private fun Any?.jmhNumber(): Double? = when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }?.takeUnless { it.isNaN() }

    /** Reads a JMH JSON result file: "Class.method[params]" -> score. */
    private fun readJmh(file: File): Map<String, Score> {
        if (!file.exists()) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        val entries = JsonSlurper().parse(file) as List<Map<String, Any?>>
        return entries.associate { entry ->
            val benchmark = entry["benchmark"] as String
            val params = (entry["params"] as Map<*, *>?)?.entries?.joinToString(",") { "${it.key}=${it.value}" }
            val name = benchmark.substringBeforeLast('.').substringAfterLast('.') + "." +
                benchmark.substringAfterLast('.') + (params?.let { "[$it]" } ?: "")
            val primary = entry["primaryMetric"] as Map<*, *>
            val alloc = ((entry["secondaryMetrics"] as Map<*, *>?)?.get("gc.alloc.rate.norm") as Map<*, *>?)
                ?.get("score")
            name to Score(
                primary["score"].jmhNumber() ?: Double.NaN,
                primary["scoreError"].jmhNumber() ?: 0.0,
                alloc.jmhNumber()
            )
        }
    }

    /** The JVM of a JMH JSON result file: "vmName jdkVersion jvmArgs". */
    private fun readJvm(file: File): String? {
        if (!file.exists()) return null
        @Suppress("UNCHECKED_CAST")
        val entry = (JsonSlurper().parse(file) as List<Map<String, Any?>>).firstOrNull() ?: return null
        val jvmArgs = (entry["jvmArgs"] as List<*>?).orEmpty().joinToString(" ")
        return "${entry["vmName"]} ${entry["jdkVersion"]}" + (if (jvmArgs.isNotBlank()) " ($jvmArgs)" else "")
    }

    private fun Double.format(digits: Int = 1): String = String.format(Locale.ROOT, "%,.${digits}f", this)

    /** A variant of a mapping: label (e.g. "kmongo-dsl") and result file. */
    data class Variant(val label: String, val file: File)

    /** Resolves the result files: mapping -> (variant label, result file). */
    fun resolve(results: Map<String, List<Pair<String, Provider<RegularFile>>>>): Map<String, List<Variant>> =
        results.mapValues { (_, files) -> files.map { (label, file) -> Variant(label, file.get().asFile) } }

    private fun Score?.time(): String = this?.let { "${it.time.format()} ± ${it.error.format()}" }.orEmpty()

    /**
     * The markdown comparison report.
     *
     * @param results mapping name -> variants, the first one (KMongo 5.12) is the reference of the ratios
     */
    fun report(results: Map<String, List<Variant>>): String = buildString {
        appendLine("# KMongo 5.12 vs kmongo-dsl")
        appendLine()
        appendLine("Average time per operation (ns/op, lower is better) and allocations per operation (B/op, JMH gc profiler).")
        appendLine("ratio = variant / KMongo 5.12 (< 1: faster than KMongo 5.12).")
        appendLine("The `driver*` benchmarks do not use KMongo: they are the baseline.")
        results.forEach { (mapping, variants) ->
            val scores = variants.map { readJmh(it.file) }
            appendLine()
            appendLine("## ${mapping.replaceFirstChar { it.uppercase() }} mapping")
            appendLine()
            variants.forEach { variant -> readJvm(variant.file)?.let { appendLine("- ${variant.label} JVM: $it") } }
            appendLine()
            if (scores.all { it.isEmpty() }) {
                appendLine("No result.")
                return@forEach
            }
            val others = variants.drop(1)
            appendLine(
                "| benchmark | " +
                    variants.joinToString(" | ") { "${it.label} (ns/op)" } + " | " +
                    others.joinToString(" | ") { "ratio ${it.label}" } + " | " +
                    variants.joinToString(" | ") { "${it.label} (B/op)" } + " |"
            )
            appendLine("|---|" + "---:|".repeat(variants.size * 2 + others.size))
            scores.flatMap { it.keys }.toSortedSet().forEach { name ->
                val values = scores.map { it[name] }
                val ratios = values.drop(1).map { value ->
                    val old = values.first()
                    if (old != null && value != null) (value.time / old.time).format(2) else ""
                }
                appendLine(
                    "| $name | " +
                        values.joinToString(" | ") { it.time() } + " | " +
                        ratios.joinToString(" | ") + " | " +
                        values.joinToString(" | ") { it?.alloc?.format(0).orEmpty() } + " |"
                )
            }
        }
    }

    private fun readSmoke(file: File): Map<String, String> =
        file.readLines().filter { it.isNotBlank() }.associate { it.substringBefore('\t') to it.substringAfter('\t') }

    /**
     * Checks that KMongo 5.12 and the kmongo-dsl variants produce the same benchmark results (benchmarkSmoke tasks).
     *
     * @param results mapping name -> variants, the first one (KMongo 5.12) is the reference
     */
    fun checkSmoke(results: Map<String, List<Variant>>) {
        val errors = results.flatMap { (mapping, variants) ->
            val reference = variants.first()
            val expected = readSmoke(reference.file)
            variants.drop(1).flatMap { variant ->
                val actual = readSmoke(variant.file)
                (expected.keys + actual.keys).sorted().mapNotNull { name ->
                    val old = expected[name]
                    val new = actual[name]
                    when {
                        old == null || new == null -> "$mapping $name: missing result (${variant.label})"
                        old.startsWith("ERROR") || new.startsWith("ERROR") || old != new ->
                            "$mapping $name:\n  ${reference.label} $old\n  ${variant.label} $new"
                        else -> null
                    }
                }
            }
        }
        if (errors.isNotEmpty()) {
            throw GradleException("KMongo 5.12 and kmongo-dsl benchmark results differ:\n" + errors.joinToString("\n"))
        }
    }
}
