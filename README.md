# KMongo DSL

[![CI](https://github.com/Litote/kmongo-dsl/actions/workflows/ci.yml/badge.svg)](https://github.com/Litote/kmongo-dsl/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.litote.kmongo/kmongo-dsl-core)](https://central.sonatype.com/artifact/org.litote.kmongo/kmongo-dsl-core)
[![KDoc](https://img.shields.io/badge/KDoc-API_Reference-blue)](https://kmongo-dsl.litote.org/api/)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=bugs)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Litote_kmongo-dsl&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=Litote_kmongo-dsl)
[![Apache2 license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

**KMongo DSL** is a Kotlin glue on top of the **official MongoDB Kotlin drivers**
([sync](https://www.mongodb.com/docs/languages/kotlin/kotlin-sync-driver/current/) and
[coroutine](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/)).

It brings features that the official drivers do not provide:

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

## Quick look

```kotlin
import org.litote.kmongo.* // the KMongo DSL extensions

data class Address(val city: String)
data class Hero(val name: String, val age: Int, val address: Address)

val client = KMongo.createClient() // the official com.mongodb.kotlin.client.MongoClient
val col = client.getDatabase("test").getCollection<Hero>() // collection "hero"

col.insertOne(Hero("Batman", 40, Address("Gotham")))
val batman: Hero? = col.findOne(Hero::address / Address::city eq "Gotham")
col.updateOne(Hero::name eq "Batman", setValue(Hero::age, 41))
val heroes: List<Hero> = col.find(Hero::age gt 30).toList()
```

With the driver alone, the field names are strings, checked neither by the compiler nor by the IDE refactorings:

```kotlin
val col = client.getDatabase("test").getCollection<Hero>("hero")
val batman: Hero? = col.find(Filters.eq("address.city", "Gotham")).firstOrNull()
col.updateOne(Filters.eq("name", "Batman"), Updates.set("age", 41))
```

## Install

```kotlin
implementation(platform("org.litote.kmongo:kmongo-dsl-bom:<version>")) // latest: see the Maven Central badge
implementation("org.litote.kmongo:kmongo-dsl-sync-jackson")
```

Coroutine flavor, kotlinx.serialization mapping, Maven: see the [Quick Start](https://kmongo-dsl.litote.org/quick-start/).

Requirements: JDK 17+, Kotlin 2.4+, MongoDB Kotlin driver 5.14+, MongoDB server 5.0+.

## Documentation

**https://kmongo-dsl.litote.org/** (sources in [docs](docs), built with `tools/build-docs.sh`).

Migrating from KMongo? Read the [migration guide](https://kmongo-dsl.litote.org/migration-kmongo-to-kmongo-dsl/)
and run [`tools/kmongo-dsl-migration-check.sh`](tools/kmongo-dsl-migration-check.sh) on your project. Changes: [CHANGELOG](CHANGELOG.md).

## Community

- questions, ideas, feedback: [GitHub Discussions](https://github.com/Litote/kmongo-dsl/discussions)
- bugs: [GitHub issues](https://github.com/Litote/kmongo-dsl/issues)
- contributing: read [CONTRIBUTING](CONTRIBUTING.md)
