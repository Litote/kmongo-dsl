// KMongo 5.12 + jackson mapping (see kmongo-dsl-benchmark)
plugins {
    id("kmongo-dsl.benchmark")
}

dependencies {
    implementation("org.litote.kmongo:kmongo-jackson-mapping:${libs.versions.kmongo.golden.get()}")
    implementation(libs.mongodb.driver.core)
}
