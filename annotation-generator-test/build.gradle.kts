plugins {
    id("kmongo-dsl.kotlin-jvm")
    id("org.jetbrains.kotlin.kapt")
}

description = "Tests of the KMongo DSL Jackson serializers generator (not published)"

dependencies {
    testImplementation(project(":kmongo-dsl-sync"))
    testImplementation(project(":kmongo-dsl-jackson-mapping"))
    testImplementation(project(":kmongo-dsl-testcontainers"))
    testImplementation(project(":kmongo-dsl-jackson-data"))
    testImplementation(project(":kmongo-dsl-jackson-module-loader"))
    testImplementation(project(":kmongo-dsl-data"))
    testImplementation(libs.kotlin.reflect)
    // the two kmongo-dsl processors share the KAPT classpath, as in a Maven build
    kaptTest(project(":kmongo-dsl-annotation-generator"))
    kaptTest(project(":kmongo-dsl-annotation-processor"))
}
