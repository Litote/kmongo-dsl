plugins {
    id("kmongo-dsl.aggregate")
}

description = "KMongo DSL coroutine flavor with the kotlinx.serialization object mapping (kmongo-dsl-coroutine + kmongo-dsl-serialization-mapping)"

dependencies {
    api(project(":kmongo-dsl-coroutine"))
    api(project(":kmongo-dsl-serialization-mapping"))
}
