plugins {
    id("kmongo-dsl.ksp")
    id("kmongo-dsl.kotlin-serialization")
}

description = "Tests of the KMongo DSL KSP processor (not published)"

dependencies {
    testImplementation(project(":kmongo-dsl-core"))
    testImplementation(project(":kmongo-dsl-test"))
    testImplementation(project(":kmongo-dsl-jackson-mapping"))
    testImplementation(project(":kmongo-dsl-jackson-data"))
    kspTest(project(":kmongo-dsl-ksp"))
}
