plugins {
    id("kmongo-dsl.kotlin-library")
}

description = "KMongo DSL Jackson modules loader (runtime of the generated Jackson serializers) - " +
        "replaces org.litote.jackson:jackson-module-loader (same packages)"

dependencies {
    api(libs.jackson.databind)
}

// Gradle reports a conflict if org.litote.jackson:jackson-module-loader is also in the classpath (instead of duplicate
// classes)
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.capability("${project.group}:${project.name}:${project.version}")
        outgoing.capability("org.litote.jackson:jackson-module-loader:${project.version}")
    }
}
