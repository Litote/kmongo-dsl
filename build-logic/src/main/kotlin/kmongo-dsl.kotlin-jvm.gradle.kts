import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    // test coverage, aggregated in the root project for SonarCloud (jacocoAggregatedReport)
    jacoco
}

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // api (not implementation): the MongoDB dependencies are declared without version, the BOM must also be
    // in the published compile variant (apiElements) - else a Gradle project that does not import a BOM
    // cannot resolve its compile classpath ("Could not find org.mongodb:bson:.")
    "api"(platform(libs.findLibrary("mongodb-bom").get()))
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("kotlin-test-junit5").get())
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

// The tests run on the toolchain JDK (17) by default; -Pkmongo.test.java.version=25 runs them on another JDK
// (the code is still compiled for Java 17): the CI checks the supported JDKs this way
val testJavaLauncher = providers.gradleProperty("kmongo.test.java.version").map { version ->
    the<JavaToolchainService>().launcherFor { languageVersion.set(JavaLanguageVersion.of(version)) }.get()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    if (testJavaLauncher.isPresent) javaLauncher.set(testJavaLauncher)
    // forward the test MongoDB server configuration to the test JVM
    listOf("kmongo.test.mongodb.image", "kmongo.test.mongodb.uri").forEach { key ->
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
