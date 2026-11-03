# About

## Release Notes

Available on [GitHub](https://github.com/Litote/kmongo-dsl/releases)
and in the [CHANGELOG](https://github.com/Litote/kmongo-dsl/blob/main/CHANGELOG.md).

## Versioning scheme

KMongo DSL follows the MongoDB Kotlin driver versioning.

So a KMongo DSL 5.14.x version uses a 5.14+ MongoDB Kotlin driver version, a 5.15.x uses a 5.15+ and so on.

The .x is used for KMongo DSL patch releases.

## Migrating from KMongo

KMongo DSL is the successor of [KMongo](https://litote.org/kmongo/) (4.x / 5.x), on top of the official MongoDB Kotlin drivers.
The packages are unchanged: read the [migration guide](migration-kmongo-to-kmongo-dsl.md).

## KMongo DSL dependencies

- MongoDB Kotlin driver [sync](https://www.mongodb.com/docs/languages/kotlin/kotlin-sync-driver/current/)
  or [coroutine](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/)
- [Kotlin stdlib](https://kotlinlang.org/api/core/kotlin-stdlib/)
- [Kotlin reflect lib](https://kotlinlang.org/api/core/kotlin-reflect/)

For the Jackson mapping:

- [Jackson](https://github.com/FasterXML/jackson-databind)
- [Jackson for Kotlin](https://github.com/FasterXML/jackson-module-kotlin)
- [Bson4Jackson](https://www.michel-kraemer.com/binary-json-with-bson4jackson/)

For the kotlinx.serialization mapping:

- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
- the official [bson-kotlinx](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/fundamentals/data-formats/serialization/) codec

## License

[Apache License 2.0](https://github.com/Litote/kmongo-dsl/blob/main/LICENSE.txt)
