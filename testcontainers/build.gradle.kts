plugins {
    id("kmongo-dsl.kotlin-library")
}

description = "KMongo DSL test MongoDB server (Testcontainers single node replica set) - replaces kmongo-flapdoodle"

dependencies {
    api(libs.testcontainers.mongodb)
}
