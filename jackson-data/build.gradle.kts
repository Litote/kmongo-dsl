plugins {
    id("kmongo-dsl.kotlin-library")
}

description = "KMongo DSL Jackson serializers annotations (@JacksonData, @JacksonDataRegistry) - " +
        "replaces org.litote.jackson:jackson-data (same packages)"

// Gradle reports a conflict if org.litote.jackson:jackson-data is also in the classpath (instead of duplicate classes)
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.capability("${project.group}:${project.name}:${project.version}")
        outgoing.capability("org.litote.jackson:jackson-data:${project.version}")
    }
}
