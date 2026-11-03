// kmongo-dsl + serialization mapping (see kmongo-dsl-benchmark)
plugins {
    id("kmongo-dsl.benchmark")
}

dependencies {
    implementation(project(":kmongo-dsl-serialization-mapping"))
}
