plugins {
    id("kmongo-dsl.kotlin-library")
    id("kmongo-dsl.legacy-capability")
}

description = "KMongo DSL Jackson serializers of the ids - replaces org.litote.kmongo:kmongo-id-jackson (same packages)"

dependencies {
    api(project(":kmongo-dsl-id"))
    api(libs.jackson.databind)
    api(libs.jackson.module.kotlin)
}
