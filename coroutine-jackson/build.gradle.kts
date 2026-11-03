plugins {
    id("kmongo-dsl.aggregate")
}

description = "KMongo DSL coroutine flavor with the Jackson object mapping (kmongo-dsl-coroutine + kmongo-dsl-jackson-mapping)"

dependencies {
    api(project(":kmongo-dsl-coroutine"))
    api(project(":kmongo-dsl-jackson-mapping"))
}
