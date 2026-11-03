// Generates the "golden" BSON documents with KMongo 5.12 (kmongo 5 and kmongo-dsl can not share a classpath).
// The golden files (src/golden-resources/golden) are checked by the kmongo-dsl mapping tests (GoldenFormatTest).
//   ./gradlew :kmongo-dsl-golden:generateGoldenFiles
// This project does not depend on kmongo-dsl and is not published.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

description = "KMongo 5.12 golden BSON documents generator (not published)"

kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDir("src/models/kotlin")
    }
}

val kmongo5 = libs.versions.kmongo.golden.get()

val jacksonMapping: Configuration = configurations.create("jacksonMapping")
val serializationMapping: Configuration = configurations.create("serializationMapping")

dependencies {
    compileOnly("org.litote.kmongo:kmongo-shared:$kmongo5")
    compileOnly("org.litote.kmongo:kmongo-id:$kmongo5")
    compileOnly("org.mongodb:mongodb-driver-core:$kmongo5")
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.datetime)

    jacksonMapping("org.litote.kmongo:kmongo-jackson-mapping:$kmongo5")
    jacksonMapping("org.mongodb:mongodb-driver-sync:$kmongo5")
    serializationMapping("org.litote.kmongo:kmongo-serialization-mapping:$kmongo5")
    serializationMapping("org.mongodb:mongodb-driver-sync:$kmongo5")
}

val generateGoldenFiles = tasks.register("generateGoldenFiles") {
    description = "Generates the golden BSON documents with KMongo $kmongo5."
    group = "build"
}

listOf("jackson" to jacksonMapping, "serialization" to serializationMapping).forEach { (mapping, configuration) ->
    val task = tasks.register<JavaExec>("generateGoldenFiles${mapping.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "Generates the golden BSON documents of the $mapping mapping."
        classpath = sourceSets.main.get().runtimeClasspath + configuration
        mainClass.set("org.litote.kmongo.golden.GoldenGeneratorKt")
        args(layout.projectDirectory.dir("src/golden-resources/golden/$mapping").asFile.absolutePath, mapping)
    }
    generateGoldenFiles.configure { dependsOn(task) }
}
