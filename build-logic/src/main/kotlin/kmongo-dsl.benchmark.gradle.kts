// A JMH benchmark variant (see kmongo-dsl-benchmark): the shared benchmark sources are compiled against the
// variant classpath (KMongo 5.12 or kmongo-dsl, Jackson or kotlinx.serialization mapping: KMongo 5 and kmongo-dsl
// share their packages, so each one has its own project), then the JMH bytecode generator produces the harness.
//
// Tasks: jmh (full run), jmhNominal (short run of the main benchmarks, separate result files), jmhQuick (quick check,
// the numbers are not meaningful), benchmarkSmoke (each benchmark once, results written to build/results/smoke).
// Not published, not analyzed (no jacoco), not in check.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    sourceSets.named("main") {
        kotlin.srcDir(rootProject.file("benchmark/src/common/kotlin"))
        // golden samples (types, ids, dates, maps...), compiled with KMongo 5.12 and kmongo-dsl
        kotlin.srcDir(rootProject.file("golden/src/models/kotlin"))
    }
}

val jmhGenerator: Configuration = configurations.create("jmhGenerator")

dependencies {
    // same MongoDB driver version for all the variants: only the KMongo layer differs
    "implementation"(platform(libs.findLibrary("mongodb-bom").get()))
    "implementation"(libs.findLibrary("jmh-core").get())
    "implementation"(libs.findLibrary("kotlinx-serialization-core").get())
    "implementation"(libs.findLibrary("kotlinx-datetime").get())
    // driverKotlinx* baseline benchmarks (official bson-kotlinx codec, without KMongo)
    "implementation"(libs.findLibrary("mongodb-bson-kotlinx").get())
    jmhGenerator(libs.findLibrary("jmh-generator-bytecode").get())
}

val main: SourceSet = the<SourceSetContainer>().getByName("main")
val compileKotlin = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin")
val generatedJmh = layout.buildDirectory.dir("generated/jmh")

val jmhGenerate = tasks.register<JavaExec>("jmhGenerate") {
    description = "Generates the JMH harness from the compiled benchmarks."
    val classesDir = compileKotlin.flatMap { it.destinationDirectory }
    val sourcesDir = generatedJmh.map { it.dir("java") }
    val resourcesDir = generatedJmh.map { it.dir("resources") }
    inputs.dir(classesDir)
    outputs.dir(generatedJmh)
    classpath = jmhGenerator + main.runtimeClasspath
    mainClass.set("org.openjdk.jmh.generators.bytecode.JmhBytecodeGenerator")
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                classesDir.get().asFile.absolutePath,
                sourcesDir.get().asFile.absolutePath,
                resourcesDir.get().asFile.absolutePath,
                "default",
            )
        }
    )
    val output = generatedJmh
    doFirst { output.get().asFile.deleteRecursively() }
}

val jmh: SourceSet = the<SourceSetContainer>().create("jmh") {
    java.srcDir(jmhGenerate.map { generatedJmh.get().dir("java") })
    resources.srcDir(jmhGenerate.map { generatedJmh.get().dir("resources") })
    compileClasspath += main.output + main.runtimeClasspath
    runtimeClasspath += output + compileClasspath
}

tasks.named<JavaCompile>("compileJmhJava") {
    dependsOn(jmhGenerate)
    options.compilerArgs.add("-proc:none")
}
tasks.named("processJmhResources") { dependsOn(jmhGenerate) }

val variant = project.name

// -Pjmh.jdk=<version>: runs the benchmarks on this JDK (Gradle toolchain, provisioned by foojay if not installed;
// the benchmarks are compiled for JDK 17). Default: the JDK 17 toolchain.
// -Pjmh.tag=<name>: name of the result series (default: jdk<version> if jmh.jdk is set), see benchmarkReport.
val jmhJdk: String? = providers.gradleProperty("jmh.jdk").orNull
val jmhTag: String? = providers.gradleProperty("jmh.tag").orNull ?: jmhJdk?.let { "jdk$it" }
val jmhTagSuffix: String = jmhTag?.let { "-$it" }.orEmpty()
val jmhResult = layout.buildDirectory.file("results/jmh/$variant$jmhTagSuffix.json")
// not the full run results: a nominal run must not replace them
val jmhNominalResult = layout.buildDirectory.file("results/jmh/$variant-nominal$jmhTagSuffix.json")

val toolchainService: JavaToolchainService = extensions.getByType<JavaToolchainService>()

fun registerJmh(
    name: String,
    defaultArgs: String,
    taskDescription: String,
    result: Provider<RegularFile> = jmhResult,
    defaultInclude: String? = null,
) =
    tasks.register<JavaExec>(name) {
        group = "benchmark"
        description = taskDescription
        classpath = jmh.runtimeClasspath
        mainClass.set("org.openjdk.jmh.Main")
        if (jmhJdk != null) {
            javaLauncher.set(
                toolchainService.launcherFor { languageVersion.set(JavaLanguageVersion.of(jmhJdk)) }
            )
        }
        // -Pjmh.args="..." replaces the default JMH options, -Pjmh.include=<regex> selects the benchmarks,
        // -Pjmh.jvmArgs="..." adds JVM options to the benchmark JVMs (e.g. -XX:+UseCompactObjectHeaders)
        val options = providers.gradleProperty("jmh.args").orElse(defaultArgs)
        val include = providers.gradleProperty("jmh.include").let { property ->
            defaultInclude?.let { property.orElse(it) } ?: property
        }
        val jvmArgs = providers.gradleProperty("jmh.jvmArgs")
        outputs.file(result)
        outputs.upToDateWhen { false }
        argumentProviders.add(
            CommandLineArgumentProvider {
                options.get().split(" ").filter { it.isNotBlank() } +
                    listOf("-prof", "gc", "-rf", "json", "-rff", result.get().asFile.absolutePath) +
                    jvmArgs.orNull?.let { listOf("-jvmArgsAppend", it) }.orEmpty() +
                    listOfNotNull(include.orNull)
            }
        )
        doFirst { result.get().asFile.parentFile.mkdirs() }
    }

registerJmh("jmh", "-wi 3 -w 2s -i 5 -r 2s -f 2", "Runs the JMH benchmarks of the $variant variant.")
registerJmh(
    "jmhNominal",
    // the times are stable after ~6s of warmup
    "-wi 3 -w 2s -i 3 -r 1s -f 2",
    "Runs the main JMH benchmarks of the $variant variant (short run: detects the large regressions).",
    jmhNominalResult,
    """MixedBenchmark\.|DslBenchmark\.updateEntity$""",
)
registerJmh(
    "jmhQuick",
    "-wi 1 -w 1s -i 1 -r 1s -f 1",
    "Runs the JMH benchmarks of the $variant variant quickly (checks the setup, the numbers are not meaningful)."
)

tasks.register<JavaExec>("benchmarkSmoke") {
    group = "benchmark"
    description = "Runs each benchmark of the $variant variant once and writes the results."
    classpath = main.runtimeClasspath
    mainClass.set("org.litote.kmongo.benchmark.BenchmarkSmokeKt")
    val result = layout.buildDirectory.file("results/smoke/$variant.txt")
    outputs.file(result)
    outputs.upToDateWhen { false }
    argumentProviders.add(CommandLineArgumentProvider { listOf(result.get().asFile.absolutePath) })
}
