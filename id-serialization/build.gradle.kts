plugins {
    id("kmongo-dsl.kotlin-library")
    id("kmongo-dsl.kotlin-serialization")
    id("kmongo-dsl.legacy-capability")
}

description = "KMongo DSL kotlinx.serialization serializers of the ids - replaces org.litote.kmongo:kmongo-id-serialization (same packages)"

dependencies {
    api(project(":kmongo-dsl-id"))
    api(libs.kotlinx.serialization.core)
    testImplementation(libs.kotlinx.serialization.json)
}
