// JMH benchmark: KMongo 5.12 vs kmongo-dsl (object mapping + type-safe DSL / JSON queries, no MongoDB server).
//
// KMongo 5 and kmongo-dsl share their packages: the benchmark sources (src/common/kotlin) are compiled by
// variant projects (kmongo-dsl.benchmark plugin): kmongo5-jackson, kmongo-dsl-jackson, kmongo5-serialization,
// kmongo-dsl-serialization, kmongo-dsl-serialization-json (kotlinx-serialization-json in the classpath).
//
//   ./gradlew :kmongo-dsl-benchmark:benchmarkSmokeCheck   KMongo 5.12 and kmongo-dsl produce the same results
//   ./gradlew :kmongo-dsl-benchmark:benchmarkQuick        quick run (checks the setup), then the report
//   ./gradlew :kmongo-dsl-benchmark:benchmarkNominal      main benchmarks only (< 5 min), then its own report
//   ./gradlew :kmongo-dsl-benchmark:benchmark             full run (~75 min), then the report
//   options: -Pjmh.include=<regex> -Pjmh.args="<JMH options>" -Pjmh.jvmArgs="<JVM options>"
//            -Pjmh.jdk=<version> (runs on this JDK, default 17) -Pjmh.tag=<result series name, default jdk<version>>
//   reports: benchmark/build/reports/benchmark[-nominal][-<tag>].md
//
// Not published, not in check.

import org.litote.kmongo.buildlogic.Benchmarks

description = "JMH benchmark KMongo 5.12 vs kmongo-dsl (not published)"

/** mapping -> variant projects; the first one (KMongo 5.12) is the reference of the comparisons */
val mappingVariants = mapOf(
    "jackson" to listOf("kmongo5-jackson", "kmongo-dsl-jackson"),
    // kmongo-dsl-serialization-json: kotlinx-serialization-json in the classpath (bson-kotlinx JSON aware codec)
    "serialization" to listOf("kmongo5-serialization", "kmongo-dsl-serialization", "kmongo-dsl-serialization-json"),
)
val variants = mappingVariants.values.flatten()

/** benchmarkNominal: the bson-kotlinx JSON aware codec does not depend on KMongo, it is left to the full run */
val nominalMappingVariants = mappingVariants.mapValues { (_, variants) -> variants.filterNot { it.endsWith("-json") } }
val nominalVariants = nominalMappingVariants.values.flatten()

evaluationDependsOnChildren()

// the benchmarks must not run concurrently (org.gradle.parallel=true)
mapOf("jmh" to variants, "jmhQuick" to variants, "jmhNominal" to nominalVariants).forEach { (taskName, taskVariants) ->
    taskVariants.map { project(":kmongo-dsl-benchmark:$it").tasks.named(taskName) }
        .zipWithNext { previous, next -> next.configure { mustRunAfter(previous) } }
}

fun label(variant: String): String = when {
    variant.startsWith("kmongo5-") -> "KMongo 5.12"
    variant.endsWith("-json") -> "kmongo-dsl + json"
    else -> "kmongo-dsl"
}

/** mapping -> (variant label, result file) */
fun resultFiles(
    mappings: Map<String, List<String>> = mappingVariants,
    path: (variant: String) -> String,
): Map<String, List<Pair<String, Provider<RegularFile>>>> =
    mappings.mapValues { (_, mappingVariants) ->
        mappingVariants.map { label(it) to project(":kmongo-dsl-benchmark:$it").layout.buildDirectory.file(path(it)) }
    }

// result series: -Pjmh.jdk=<version> / -Pjmh.tag=<name> (see the kmongo-dsl.benchmark plugin)
val jmhSuffix: String = (providers.gradleProperty("jmh.tag").orNull
    ?: providers.gradleProperty("jmh.jdk").orNull?.let { "jdk$it" })?.let { "-$it" }.orEmpty()

fun registerReport(
    name: String,
    fileSuffix: String,
    mappings: Map<String, List<String>>,
    taskDescription: String,
) = tasks.register(name) {
    group = "benchmark"
    description = taskDescription
    val results = resultFiles(mappings) { "results/jmh/$it$fileSuffix.json" }
    val report = layout.buildDirectory.file("reports/benchmark$fileSuffix.md")
    mustRunAfter(
        variants.flatMap { variant ->
            listOf("jmh", "jmhNominal", "jmhQuick").map { ":kmongo-dsl-benchmark:$variant:$it" }
        }
    )
    outputs.file(report)
    outputs.upToDateWhen { false }
    doLast {
        val text = Benchmarks.report(Benchmarks.resolve(results))
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(text)
        logger.lifecycle(text)
        logger.lifecycle("Report: ${report.get().asFile}")
    }
}

val benchmarkReport = registerReport(
    "benchmarkReport",
    jmhSuffix,
    mappingVariants,
    "Writes the KMongo 5.12 vs kmongo-dsl comparison (build/reports/benchmark[-<tag>].md) from the JMH results.",
)
val benchmarkNominalReport = registerReport(
    "benchmarkNominalReport",
    "-nominal$jmhSuffix",
    nominalMappingVariants,
    "Writes the benchmarkNominal comparison (build/reports/benchmark-nominal[-<tag>].md) from the JMH results.",
)

tasks.register("benchmark") {
    group = "benchmark"
    description = "Runs the full benchmark of the variants, then writes the report."
    dependsOn(variants.map { ":kmongo-dsl-benchmark:$it:jmh" })
    finalizedBy(benchmarkReport)
}

tasks.register("benchmarkNominal") {
    group = "benchmark"
    description = "Runs the main benchmarks of the variants (short run), then writes the nominal report."
    dependsOn(nominalVariants.map { ":kmongo-dsl-benchmark:$it:jmhNominal" })
    finalizedBy(benchmarkNominalReport)
}

tasks.register("benchmarkQuick") {
    group = "benchmark"
    description = "Runs a quick benchmark of the variants (checks the setup), then writes the report."
    dependsOn(variants.map { ":kmongo-dsl-benchmark:$it:jmhQuick" })
    finalizedBy(benchmarkReport)
}

tasks.register("benchmarkSmokeCheck") {
    group = "benchmark"
    description = "Checks that KMongo 5.12 and kmongo-dsl produce the same benchmark results."
    dependsOn(variants.map { ":kmongo-dsl-benchmark:$it:benchmarkSmoke" })
    val results = resultFiles(path = { "results/smoke/$it.txt" })
    doLast {
        Benchmarks.checkSmoke(Benchmarks.resolve(results))
        logger.lifecycle("KMongo 5.12 and kmongo-dsl produce the same benchmark results")
    }
}
