plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.vanniktech.publish.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.shadow.gradle.plugin)
}
