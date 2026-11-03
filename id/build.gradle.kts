plugins {
    id("kmongo-dsl.kotlin-library")
    id("kmongo-dsl.legacy-capability")
}

description = "KMongo DSL ids (Id<T>, newId()...) - replaces org.litote.kmongo:kmongo-id (same packages)"

dependencies {
    api(libs.kotlin.reflect)
}
