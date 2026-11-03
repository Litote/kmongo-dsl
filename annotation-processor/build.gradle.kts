plugins {
    id("kmongo-dsl.kapt-processor")
}

description = "KMongo DSL KAPT annotation processor (for Maven): generates type-safe property paths for @Data classes"

dependencies {
    // embedded and relocated in the processor jar (see kmongo-dsl.kapt-processor)
    bundled(project(":kmongo-dsl-codegen"))
}
