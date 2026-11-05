# Changelog

## 5.14.0 (2026-11-05)


### Features

* kmongo-dsl, Kotlin extensions and DSL for the official MongoDB driver ([a5a0df8](https://github.com/Litote/kmongo-dsl/commit/a5a0df8b280fc201266016e9cc2db43684f9ac2f))


### Bug Fixes

* allow the kotlinx.serialization conversions before the creation of a client ([5909614](https://github.com/Litote/kmongo-dsl/commit/590961489e979565303b382f732c6aea926abe5a))
* repair the Gradle wrapper checksum, the verification metadata and the release-please bootstrap ([9bace4c](https://github.com/Litote/kmongo-dsl/commit/9bace4cc0d97dd381764d8a8d8d2b3f198cc8574))
* write org.bson.types.Decimal128 as a BSON decimal in both mappings ([3e21d03](https://github.com/Litote/kmongo-dsl/commit/3e21d036dea4091fa880218a193ec187bae743a6))

## 5.14.0 (unreleased)

First KMongo DSL version: KMongo on top of the official MongoDB Kotlin drivers.
The KMongo DSL version follows the version of the MongoDB driver it is built on (5.14.0: driver 5.14.0).
See the [migration guide](docs/migration-kmongo-to-kmongo-dsl.md).

Requirements: JDK 17+, Kotlin 2.4+: the published libraries use the Kotlin 2.4 language & API versions,
standard library and `kotlin-reflect`. The Jackson mapping uses Jackson 3, with the same BSON format as KMongo.

### Added

- `kmongo-dsl-sync` and `kmongo-dsl-coroutine`: KMongo extensions for the official Kotlin sync & coroutine drivers
  (the coroutine extensions are generated from the sync ones).
- KMongo 4/5 coroutine compatibility layer (deprecated type aliases and functions).
- `kmongo-dsl-serialization-mapping` on top of the official `bson-kotlinx` (replaces kbson).
- `kmongo-dsl-ksp`: KSP property paths generator (replaces the KAPT processor), incremental,
  with `@file:DataRegistry` support.
- `kmongo-dsl-annotation-processor`: KAPT processor generating the same code as `kmongo-dsl-ksp` (for Maven:
  `kotlin-maven-plugin` `kapt` goal), the generator is shared (`kmongo-dsl-codegen`) and a test checks that both outputs are identical.
- `kmongo-dsl-annotation-generator`: KAPT processor generating the Jackson serializers of the `@JacksonData` classes
  (replaces `org.litote.jackson:jackson-generator`, same generated classes, no `kreflect` dependency).
- the KAPT processors embed and relocate their dependencies (KotlinPoet, `kotlin-metadata-jvm`): they can share
  the KAPT classpath with processors using other versions of these libraries.
- `kmongo-dsl-testcontainers`: test MongoDB server (Testcontainers single node replica set), replaces `kmongo-flapdoodle`.
- `kmongo-dsl-sync-jackson`, `kmongo-dsl-sync-serialization`, `kmongo-dsl-coroutine-jackson`, `kmongo-dsl-coroutine-serialization`:
  a flavor + a mapping in one dependency.
- `watchIndefinitely` (coroutine) and `listenIndefinitely` (sync): change streams that survive the errors
  and the invalidations (resume token kept, retry with backoff).
- `KMongo.createJavaClient` / `KMongo.createClient(javaClient)` (sync): Java driver client with the KMongo mapping,
  for GridFS.
- KMongo 4/5 source compatibility: deprecated `org.litote.kmongo.reactivestreams` `getCollection` / `getCollectionOfName`,
  sync `MongoIterable.iterator()` / `into`, `org.litote.kmongo.withDocumentClass` import.
- `tools/kmongo-dsl-migration-check.sh`: lists the code to review when migrating (including the sync `first()` calls
  that the compiler can not detect), `--add-imports` adds the explicit imports of the kmongo-dsl extensions
  from the compiler errors.
- `kmongo-dsl-id`, `kmongo-dsl-data`, `kmongo-dsl-id-jackson`, `kmongo-dsl-id-serialization`: the KMongo 5.12
  `kmongo-id*` / `kmongo-data` classes (same packages), with the Gradle capabilities of the KMongo artifacts.
- `kmongo-dsl-bom`.
- BSON format compatibility tests against KMongo 5.12 (`kmongo-dsl-golden`).
- the KMongo documentation code samples are compiled and run against kmongo-dsl.

### Changed (compared to KMongo 5.12)

- `save` returns `UpdateResult?`, `deleteOne` accepts `DeleteOptions` (sync).
- single property `projection` returns nullable values.
- `ChangeStreamIterable.listen` returns an `AutoCloseable` and does not leak its thread any more.
- the collection-like `MongoIterable` extensions moved from `kotlin.collections` to `org.litote.kmongo`.
- coroutine: the nullable KMongo 4/5 `first()` is deprecated with the ERROR level.
- the published API is in Kotlin explicit API mode.

### Removed

- async, rxjava2, reactor and native mapping flavors.
- map-reduce helpers.
- legacy `DBObject` codecs, `MongoClientProvider` SPI.

### Fixed

- generated property paths: the code generated for the collections and maps of `@Data` classes with nullable elements
  (`List<Address?>`, `Map<String, Address?>`), the `Iterable<Data>` properties, the star projected maps (`Map<String, *>`)
  and the type aliases now compiles.
- generated property paths: the protected properties and the internal properties of the classes of other modules
  (`@DataRegistry`) are accessed through `kotlin-reflect` (the generated code did not compile).
- `@JacksonData` classes: the property paths are generated only if `kmongo-dsl-core` is in the classpath.
