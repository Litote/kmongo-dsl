plugins {
    id("kmongo-dsl.kotlin-library")
}

description = "KMongo DSL Jackson object mapping (bson4jackson)"

dependencies {
    api(project(":kmongo-dsl-core"))
    api(project(":kmongo-dsl-id-jackson"))
    api(libs.jackson.databind)
    api(libs.jackson.module.kotlin)
    api(libs.bson4jackson)
    implementation(project(":kmongo-dsl-jackson-module-loader"))
    compileOnly(libs.kotlinx.datetime)

    testImplementation(project(":kmongo-dsl-test"))
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlinx.serialization.core)
}

// golden BSON documents generated with KMongo 5.12 (see kmongo-dsl-golden)
sourceSets.test {
    kotlin.srcDir(rootProject.file("golden/src/models/kotlin"))
    kotlin.srcDir(rootProject.file("golden/src/check/kotlin"))
    resources.srcDir(rootProject.file("golden/src/golden-resources"))
}
