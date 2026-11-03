import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// A KAPT (JSR 269) annotation processor published as a self-contained jar.
//
// The KAPT classpath is shared by all the annotation processors of a compilation: a processor depending on another
// version of KotlinPoet (or of kotlin-metadata-jvm) breaks the other processors (NoSuchMethodError, wrong code...).
// The dependencies declared in the `bundled` configuration are embedded in the processor jar and relocated
// in a kmongo-dsl package - they are not dependencies of the published artifact.
plugins {
    id("kmongo-dsl.kotlin-library")
    id("com.gradleup.shadow")
}

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

/** The relocation prefix - the same for all the kmongo-dsl processors, so that they can share the KAPT classpath. */
val shadedPackage = "org.litote.kmongo.kapt.shaded"

val bundled: Configuration = configurations.create("bundled") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
configurations.named("compileOnly") { extendsFrom(bundled) }
configurations.named("testImplementation") { extendsFrom(bundled) }

dependencies {
    // used by KotlinPoet
    "runtimeOnly"("org.jetbrains.kotlin:kotlin-reflect:${libs.findVersion("kotlin").get().requiredVersion}")
}

shadow {
    addShadowVariantIntoJavaComponent.set(false)
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    configurations.set(listOf(bundled))
    dependencies {
        // regular dependencies of the published artifact
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
        exclude(dependency("org.jetbrains:annotations"))
    }
    relocate("com.squareup.kotlinpoet", "$shadedPackage.kotlinpoet")
    relocate("kotlin.metadata", "$shadedPackage.kotlin.metadata")
    // relocates the kotlin-metadata-jvm service files (JVM metadata extensions)
    mergeServiceFiles()
    exclude("META-INF/versions/*/module-info.class", "module-info.class")
    exclude("META-INF/*.kotlin_module", "META-INF/maven/**", "META-INF/proguard/**")
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}

// the published (and the project dependencies) artifact is the self-contained jar
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.artifacts.clear()
        outgoing.artifact(shadowJar)
        // no "classes" / "resources" secondary variants: the project dependencies use the self-contained jar too
        outgoing.variants.clear()
    }
}

tasks.named("assemble") { dependsOn(shadowJar) }
