plugins {
    id("kmongo-dsl.kotlin-serialization")
    `java-library`
}

description = "KMongo DSL shared test fixtures (models + Mongo test container), not published"

dependencies {
    api(platform(libs.mongodb.bom))
    api(project(":kmongo-dsl-core"))
    api(libs.kotlinx.serialization.core)
    api(project(":kmongo-dsl-testcontainers"))
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    api(libs.kotlin.test.junit5)
    runtimeOnly(libs.slf4j.simple)
}
