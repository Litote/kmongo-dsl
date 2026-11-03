plugins {
    id("kmongo-dsl.aggregate")
}

description = "KMongo DSL sync flavor with the Jackson object mapping (kmongo-dsl-sync + kmongo-dsl-jackson-mapping)"

dependencies {
    api(project(":kmongo-dsl-sync"))
    api(project(":kmongo-dsl-jackson-mapping"))
}
