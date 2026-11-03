// kmongo-dsl + serialization mapping, with kotlinx-serialization-json in the classpath (see kmongo-dsl-benchmark):
// bson-kotlinx then uses its JsonElement aware encoder / decoder (JsonBsonEncoder, JsonBsonDecoderImpl).
// KMongo 5.12 always has kotlinx-serialization-json in its classpath, but uses kbson.
plugins {
    id("kmongo-dsl.benchmark")
}

dependencies {
    implementation(project(":kmongo-dsl-serialization-mapping"))
    implementation(libs.kotlinx.serialization.json)
}
