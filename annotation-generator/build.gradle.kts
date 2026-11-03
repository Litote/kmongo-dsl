plugins {
    id("kmongo-dsl.kapt-processor")
}

description = "KMongo DSL KAPT annotation processor: generates Jackson serializers and deserializers for @JacksonData classes " +
        "(replaces org.litote.jackson:jackson-generator)"

dependencies {
    // embedded and relocated in the processor jar (see kmongo-dsl.kapt-processor)
    bundled(project(":kmongo-dsl-codegen"))
}
