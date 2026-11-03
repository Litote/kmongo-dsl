pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kmongo-dsl"

include(
    "kmongo-dsl-bom",
    "kmongo-dsl-id",
    "kmongo-dsl-data",
    "kmongo-dsl-jackson-data",
    "kmongo-dsl-jackson-module-loader",
    "kmongo-dsl-id-jackson",
    "kmongo-dsl-id-serialization",
    "kmongo-dsl-core",
    "kmongo-dsl-serialization-mapping",
    "kmongo-dsl-jackson-mapping",
    "kmongo-dsl-sync",
    "kmongo-dsl-sync-tests",
    "kmongo-dsl-coroutine",
    "kmongo-dsl-sync-jackson",
    "kmongo-dsl-sync-serialization",
    "kmongo-dsl-coroutine-jackson",
    "kmongo-dsl-coroutine-serialization",
    "kmongo-dsl-coroutine-tests",
    "kmongo-dsl-codegen",
    "kmongo-dsl-ksp",
    "kmongo-dsl-annotation-processor",
    "kmongo-dsl-annotation-generator",
    "kmongo-dsl-kapt-test",
    "kmongo-dsl-annotation-generator-test",
    "kmongo-dsl-ksp-test",
    "kmongo-dsl-test",
    "kmongo-dsl-testcontainers",
    "kmongo-dsl-samples",
    "kmongo-dsl-golden",
    // JMH benchmark KMongo 5.12 vs kmongo-dsl (not published, not in check)
    "kmongo-dsl-benchmark",
    "kmongo-dsl-benchmark:kmongo5-jackson",
    "kmongo-dsl-benchmark:kmongo5-serialization",
    "kmongo-dsl-benchmark:kmongo-dsl-jackson",
    "kmongo-dsl-benchmark:kmongo-dsl-serialization",
    "kmongo-dsl-benchmark:kmongo-dsl-serialization-json",
)

// The project names are the published artifact ids: only the directories drop the kmongo-dsl- prefix
fun ProjectDescriptor.removeDirectoryPrefix() {
    projectDir = parent!!.projectDir.resolve(name.removePrefix("kmongo-dsl-"))
    children.forEach { it.removeDirectoryPrefix() }
}
rootProject.children.forEach { it.removeDirectoryPrefix() }
