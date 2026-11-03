plugins {
    id("kmongo-dsl.kotlin-library")
}

description = "KMongo DSL extensions for the official MongoDB Kotlin sync driver"

dependencies {
    api(project(":kmongo-dsl-core"))
    api(libs.mongodb.driver.kotlin.sync)
}
