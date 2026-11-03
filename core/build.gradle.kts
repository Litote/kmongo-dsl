plugins {
    id("kmongo-dsl.kotlin-library")
}

description = "KMongo DSL core: type-safe query DSL, JSON helpers and mapping SPI (no driver flavor dependency)"

kotlin {
    compilerOptions {
        // kotlin.internal.OnlyInputTypes trick used by the DSL
        freeCompilerArgs.add("-Xallow-kotlin-package")
    }
}

dependencies {
    api(libs.mongodb.bson)
    api(libs.mongodb.driver.core)
    api(project(":kmongo-dsl-id"))
    api(project(":kmongo-dsl-data"))
    api(libs.kotlin.reflect)
    compileOnly(libs.kotlinx.serialization.core)
}

// kotlin.internal.OnlyInputTypes is only needed at compile time (it is provided by the stdlib at runtime)
tasks.jar {
    exclude("kotlin/internal/**")
}
