// KMongo 5.12 + serialization mapping (see kmongo-dsl-benchmark)
plugins {
    id("kmongo-dsl.benchmark")
}

dependencies {
    implementation("org.litote.kmongo:kmongo-serialization-mapping:${libs.versions.kmongo.golden.get()}")
    implementation(libs.mongodb.driver.core)
}
