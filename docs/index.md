# ![KMongo logo](assets/images/kmongo.png) **KMongo DSL** - the Kotlin toolkit for the official MongoDB drivers

```kotlin
data class Jedi(val name: String, val age: Int)

col.insertOne(Jedi("Luke Skywalker", 19)) // official driver method
col.insertOne("{name:'Yoda',age:896}")    // KMongo DSL extension function

// object mapping & mongo shell query format are supported ->
val yoda: Jedi? = col.findOne("{name: {\$regex: 'Yo.*'}}")

// type-safe query style is recommended for complex apps ->
val luke = col.aggregate<Jedi>(
    match(Jedi::age lt yoda?.age),
    sample(1)
).firstOrNull()
```

## From KMongo to KMongo DSL

[KMongo](https://litote.org/kmongo/) was created in *2016*, when there was no official MongoDB Kotlin driver.
Since 2023, MongoDB provides [official Kotlin drivers](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/)
(sync and coroutine), and KMongo is deprecated in favor of them.

The official drivers do not provide everything KMongo did - in particular the type-safe query DSL,
which is [less intuitive](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/migrate-kmongo/#construct-queries)
with the driver builders. **KMongo DSL** brings these KMongo features **on top of the official drivers**:

- the [type-safe query DSL](typed-queries.md): `col.findOne(Jedi::name eq "Yoda")`
- the [mongo shell queries](mongo-shell-support.md): `col.find("{age: {\$lt: 40}}")`
- the [collection extensions](extensions-overview.md): `findOneById`, `save`, `updateOne(entity)`, typed `projection`, `ensureIndex`...
- the [object mappings](object-mapping.md): Jackson or kotlinx.serialization, `Id<T>` typed ids
- the [generated property paths](typed-queries.md#generated-property-paths) (KSP or KAPT): `Friend_.Coordinate.lat`
- [change streams that survive the errors](extensions-overview.md#change-streams)

KMongo DSL does **not** wrap the driver: you use the official `MongoClient` / `MongoCollection` classes,
KMongo DSL adds extension functions. The packages are the KMongo ones (`org.litote.kmongo`, `org.litote.kmongo.coroutine`...):
migrating a KMongo project is mostly a dependency update - see the [migration guide](migration-kmongo-to-kmongo-dsl.md).

## Native and Lightweight

KMongo DSL features are [Kotlin extensions](https://kotlinlang.org/docs/extensions.html) of the official
[Kotlin sync driver](https://www.mongodb.com/docs/languages/kotlin/kotlin-sync-driver/current/) and
[Kotlin coroutine driver](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/) API.

The coroutine flavor extends the official coroutine driver: the queries return
[Kotlin flows](https://kotlinlang.org/docs/flow.html).

## Built-in Object Mapping

Object oriented programming is usually better - use Objects, not Maps. Powered by the
[Jackson library](https://github.com/FasterXML/jackson) or
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
(with the official [bson-kotlinx](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/fundamentals/data-formats/serialization/) codec).

## Type-safe queries

Have you already queried an `Int` field with a `String` value?
The KMongo DSL type-safe query system removes the query type errors.
Provided with optional annotation processors (KSP or KAPT).

## Mongo shell queries

You can copy/paste your queries from the Mongo shell in your IDE. Write readable source code!

## Get started

Read the [Quick Start](quick-start.md) (JDK 17+, Kotlin 2.4+, MongoDB server 5.0+),
or the [migration guide](migration-kmongo-to-kmongo-dsl.md) if you already use KMongo.
