import java.util.zip.ZipFile

plugins {
    id("kmongo-dsl.ksp")
    id("kmongo-dsl.kotlin-serialization")
}

description = "KMongo DSL end-to-end samples (not published)"

dependencies {
    testImplementation(project(":kmongo-dsl-sync"))
    testImplementation(project(":kmongo-dsl-coroutine"))
    testImplementation(project(":kmongo-dsl-jackson-mapping"))
    testImplementation(project(":kmongo-dsl-serialization-mapping"))
    testImplementation(project(":kmongo-dsl-test"))
    kspTest(project(":kmongo-dsl-ksp"))
}

tasks.named<Test>("test") {
    useJUnitPlatform { includeTags("jackson") }
    systemProperty("org.litote.mongo.mapping.service", "org.litote.kmongo.jackson.JacksonClassMappingTypeService")
}

val testSerialization = tasks.register<Test>("testSerialization") {
    group = "verification"
    description = "Runs the samples with the kotlinx.serialization mapping."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("serialization") }
    systemProperty(
        "org.litote.mongo.mapping.service",
        "org.litote.kmongo.serialization.SerializationClassMappingTypeService"
    )
}

tasks.named("check") { dependsOn(testSerialization) }

// kmongo-dsl keeps the kmongo package names: check that no class is provided twice on the full runtime classpath
// (for example by a kmongo 4/5 artifact and a kmongo-dsl artifact)
val checkDuplicateClasses = tasks.register("checkDuplicateClasses") {
    group = "verification"
    description = "Fails if a class is provided by several jars of the runtime classpath."
    // a FileCollection (not the configuration): compatible with the configuration cache
    val classpath: FileCollection = files(configurations.testRuntimeClasspath)
    inputs.files(classpath)
    doLast {
        val owners = mutableMapOf<String, MutableList<String>>()
        classpath.files.filter { it.isFile && it.name.endsWith(".jar") }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") && !it.startsWith("META-INF/") && it != "module-info.class" }
                    .forEach { owners.getOrPut(it) { mutableListOf() }.add(jar.name) }
            }
        }
        val duplicates = owners.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw GradleException(
                "Duplicate classes:\n" + duplicates.entries.take(50).joinToString("\n") { "${it.key} -> ${it.value}" }
            )
        }
        logger.lifecycle("No duplicate class in ${owners.size} classes")
    }
}

tasks.named("check") { dependsOn(checkDuplicateClasses) }
