plugins {
    id("kmongo-dsl.kotlin-library")
    id("kmongo-dsl.kotlin-serialization")
}

description = "KMongo DSL kotlinx.serialization object mapping (on top of the official bson-kotlinx)"

kotlin {
    compilerOptions {
        optIn.addAll(
            "kotlinx.serialization.ExperimentalSerializationApi",
            "kotlinx.serialization.InternalSerializationApi",
        )
    }
}

dependencies {
    api(project(":kmongo-dsl-core"))
    api(libs.mongodb.bson.kotlinx)
    api(libs.kotlinx.serialization.core)
    api(project(":kmongo-dsl-id-serialization"))
    compileOnly(libs.kotlinx.datetime)

    testImplementation(project(":kmongo-dsl-test"))
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlinx.serialization.json)
}

// golden BSON documents generated with KMongo 5.12 (see kmongo-dsl-golden)
sourceSets.test {
    kotlin.srcDir(rootProject.file("golden/src/models/kotlin"))
    kotlin.srcDir(rootProject.file("golden/src/check/kotlin"))
    resources.srcDir(rootProject.file("golden/src/golden-resources"))
}
