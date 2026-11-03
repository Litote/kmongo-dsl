// an artifact without code that only depends on a flavor and an object mapping (KMongo 4/5 like artifacts)
plugins {
    `java-library`
    id("kmongo-dsl.publishing")
}

mavenPublishing {
    configure(
        com.vanniktech.maven.publish.JavaLibrary(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
            sourcesJar = true,
        )
    )
}
