plugins {
    id("kmongo-dsl.kotlin-serialization")
}

description = "KMongo DSL coroutine integration tests (not published)"

dependencies {
    testImplementation(project(":kmongo-dsl-coroutine"))
    testImplementation(project(":kmongo-dsl-test"))
    testImplementation(project(":kmongo-dsl-jackson-mapping"))
    testImplementation(project(":kmongo-dsl-serialization-mapping"))
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlinx.coroutines.test)
}

// the same integration test suite runs for each object mapping
val mappings = mapOf(
    "Jackson" to "org.litote.kmongo.jackson.JacksonClassMappingTypeService",
    "Serialization" to "org.litote.kmongo.serialization.SerializationClassMappingTypeService",
)

tasks.named<Test>("test") {
    useJUnitPlatform { includeTags("JacksonMappingCategory") }
    systemProperty("org.litote.mongo.mapping.service", mappings.getValue("Jackson"))
}

val testSerialization = tasks.register<Test>("testSerialization") {
    description = "Runs the integration tests with the kotlinx.serialization mapping."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("SerializationMappingCategory") }
    systemProperty("org.litote.mongo.mapping.service", mappings.getValue("Serialization"))
}

tasks.named("check") { dependsOn(testSerialization) }
