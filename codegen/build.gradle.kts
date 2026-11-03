plugins {
    id("kmongo-dsl.kotlin-library")
}

description = "KMongo DSL code generators, shared by the KSP and the KAPT processors"

dependencies {
    api(libs.kotlinpoet)
    api(libs.kotlin.metadata.jvm)
}
