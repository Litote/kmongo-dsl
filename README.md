# KMongo DSL

[![CI](https://github.com/Litote/kmongo-dsl/actions/workflows/ci.yml/badge.svg)](https://github.com/Litote/kmongo-dsl/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.litote.kmongo/kmongo-dsl-core)](https://central.sonatype.com/artifact/org.litote.kmongo/kmongo-dsl-core)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Apache2 license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

**KMongo DSL** is the Kotlin glue on top of the **official MongoDB Kotlin drivers**
([sync](https://www.mongodb.com/docs/languages/kotlin/kotlin-sync-driver/current/) and
[coroutine](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/)).

It brings the [KMongo](https://litote.org/kmongo/) features that the official drivers do not provide:

- a type-safe query DSL: `col.findOne(Hero::address / Address::city eq "Gotham")`
- JSON queries: `col.find("{age: {\$lt: 40}}")`
- collection helpers: `findOneById`, `save`, `updateOne(entity)`, typed `projection`, `ensureIndex`...
- default collection names: `database.getCollection<Hero>()`
- `Id<T>` typed ids
- Jackson and kotlinx.serialization object mappings
- generated property paths (KSP or KAPT): `Hero_.Address.city`
- change streams that survive the errors: `col.watchIndefinitely()`

KMongo DSL does **not** wrap the driver: you use the official `MongoClient` / `MongoCollection` classes,
KMongo DSL adds extension functions in the KMongo packages (`org.litote.kmongo`, `org.litote.kmongo.coroutine`).

Documentation: **https://kmongo-dsl.litote.org/** (sources in [docs](docs), built with `tools/build-docs.sh`).

Migrating from KMongo? Read the [migration guide](docs/migration-kmongo-to-kmongo-dsl.md)
and run [`tools/kmongo-dsl-migration-check.sh`](tools/kmongo-dsl-migration-check.sh) on your project. Changes: [CHANGELOG](CHANGELOG.md).

Requirements: JDK 17+, Kotlin 2.4+, MongoDB Kotlin driver 5.14+, MongoDB server 5.0+.

Contributing: read [CONTRIBUTING](CONTRIBUTING.md).
