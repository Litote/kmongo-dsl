plugins {
    `java-platform`
    id("kmongo-dsl.publishing")
}

description = "KMongo DSL BOM"

javaPlatform {
    allowDependencies()
}

// the published kmongo-dsl modules
val publishedModules = listOf(
    "kmongo-dsl-id",
    "kmongo-dsl-data",
    "kmongo-dsl-jackson-data",
    "kmongo-dsl-jackson-module-loader",
    "kmongo-dsl-id-jackson",
    "kmongo-dsl-id-serialization",
    "kmongo-dsl-core",
    "kmongo-dsl-sync",
    "kmongo-dsl-coroutine",
    "kmongo-dsl-sync-jackson",
    "kmongo-dsl-sync-serialization",
    "kmongo-dsl-coroutine-jackson",
    "kmongo-dsl-coroutine-serialization",
    "kmongo-dsl-serialization-mapping",
    "kmongo-dsl-jackson-mapping",
    "kmongo-dsl-codegen",
    "kmongo-dsl-ksp",
    "kmongo-dsl-annotation-processor",
    "kmongo-dsl-annotation-generator",
    "kmongo-dsl-testcontainers",
)

dependencies {
    api(platform(libs.mongodb.bom))
    constraints {
        publishedModules.forEach { api("${project.group}:$it:${project.version}") }
    }
}
