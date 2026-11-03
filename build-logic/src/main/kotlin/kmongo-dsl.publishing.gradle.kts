plugins {
    `maven-publish`
    id("com.vanniktech.maven.publish")
}

// local repository used to check the published artifacts: ./gradlew publishAllPublicationsToLocalRepoRepository
publishing {
    repositories {
        maven {
            name = "localRepo"
            url = uri(rootProject.layout.buildDirectory.dir("repo"))
        }
    }
}

mavenPublishing {
    coordinates("org.litote.kmongo", project.name, project.version.toString())
    pom {
        name.set(project.name)
        description.set(provider { project.description ?: "KMongo DSL - Kotlin glue on top of the official MongoDB Kotlin driver" })
        url.set("https://github.com/Litote/kmongo-dsl")
        inceptionYear.set("2016")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("zigzago")
                name.set("Julien Buret")
            }
        }
        scm {
            url.set("https://github.com/Litote/kmongo-dsl")
            connection.set("scm:git:git://github.com/Litote/kmongo-dsl.git")
            developerConnection.set("scm:git:ssh://git@github.com/Litote/kmongo-dsl.git")
        }
    }
    if (project.hasProperty("signingInMemoryKey") || project.hasProperty("signing.keyId")) {
        publishToMavenCentral()
        signAllPublications()
    }
}
