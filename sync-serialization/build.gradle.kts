plugins {
    id("kmongo-dsl.aggregate")
}

description = "KMongo DSL sync flavor with the kotlinx.serialization object mapping (kmongo-dsl-sync + kmongo-dsl-serialization-mapping)"

dependencies {
    api(project(":kmongo-dsl-sync"))
    api(project(":kmongo-dsl-serialization-mapping"))
}
