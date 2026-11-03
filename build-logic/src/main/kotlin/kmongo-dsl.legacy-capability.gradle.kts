// A kmongo-dsl artifact replacing a KMongo 4/5 artifact with the same classes (same packages):
// kmongo-dsl-id replaces org.litote.kmongo:kmongo-id...
//
// The artifact also provides the capability of the KMongo artifact (Gradle module metadata): Gradle reports
// a conflict if both are in the classpath (instead of duplicate classes), solved by
//   configurations.all { resolutionStrategy.capabilitiesResolution.withCapability("org.litote.kmongo:kmongo-id") { selectHighestVersion() } }
// (the kmongo-dsl version is higher than the KMongo 5.12 one).
plugins {
    java
}

val legacyArtifact = project.name.replace("kmongo-dsl-", "kmongo-")

listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.capability("${project.group}:${project.name}:${project.version}")
        outgoing.capability("org.litote.kmongo:$legacyArtifact:${project.version}")
    }
}
