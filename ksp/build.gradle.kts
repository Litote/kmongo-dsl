plugins {
    id("kmongo-dsl.kotlin-library")
}

description = "KMongo DSL KSP processor: generates type-safe property paths for @Data classes"

dependencies {
    implementation(libs.ksp.api)
    implementation(project(":kmongo-dsl-codegen"))
    implementation(libs.kotlinpoet.ksp)
}
