# Migrating from KMongo (4.x / 5.x) to KMongo DSL

KMongo DSL is a rewrite of KMongo on top of the **official MongoDB Kotlin drivers**
(`mongodb-driver-kotlin-sync` and `mongodb-driver-kotlin-coroutine`).

KMongo wrapped the Java drivers and re-implemented a Kotlin API
(`CoroutineCollection`, `CoroutineFindPublisher`, rx/reactor wrappers...).

**The packages are unchanged** (`org.litote.kmongo`, `org.litote.kmongo.coroutine`,
`org.litote.kmongo.serialization`, `org.litote.kmongo.jackson`...): most of the code compiles
after a dependency update. This guide lists the changes you may have to make.

> KMongo and KMongo DSL artifacts **cannot be mixed** in the same classpath:
> they provide the same classes. Remove all the `org.litote.kmongo:kmongo*` artifacts
> (including `kmongo-id*` and `kmongo-data`, see below) before adding the `kmongo-dsl-*` ones.

## Supported versions

| Component                 | Minimum version |
|---------------------------|-----------------|
| JDK                       | 17              |
| Kotlin                    | 2.4             |
| MongoDB Kotlin driver     | 5.14            |
| kotlinx.serialization     | 1.11            |
| Jackson                   | 3.2 (Jackson mapping) |
| MongoDB server            | 5.0 (the CI tests 5.0, 6.0, 7.0 and 8.2) |

## Checklist

1. Run `kmongo-dsl-migration-check.sh` ([§1](#1-migration-check-script)).
2. Replace the deprecated KMongo dependencies ([§2](#2-dependencies)) - remove the direct Java driver dependencies.
3. Sync: update the `com.mongodb.client.*` imports ([§4](#4-sync-flavor)).
4. Coroutine: add `import org.litote.kmongo.coroutine.*` where KMongo operations are used, then fix the deprecation warnings ([§5](#5-coroutine-flavor)).
5. Replace `first()` (nullable) by `firstOrNull()` (coroutine: compilation errors guide you; sync: not detected by the compiler, see the `REVIEW` items of the migration check script).
6. Explicit imports: add the imports of the kmongo-dsl extensions with `kmongo-dsl-migration-check.sh --add-imports` ([§1](#1-migration-check-script), [§4](#4-sync-flavor)).
7. kotlinx.serialization: check `configuration` usages, contextual `Id` map keys and external serializers ([§6](#6-object-mapping)).
8. Jackson: migrate the code using the Jackson API to Jackson 3 ([§6](#6-object-mapping)).
9. Replace `kmongo-annotation-processor` by `kmongo-dsl-annotation-processor` (Maven) or `kmongo-dsl-ksp` (Gradle),
   and `jackson-generator` by `kmongo-dsl-annotation-generator` ([§7](#7-code-generation-ksp-or-kapt)).
10. Long-running change streams: use `watchIndefinitely` / `listenIndefinitely` ([§9](#9-change-streams)).
11. Performance: remove the older `kotlin-reflect` versions (Jackson); exclude `kotlinx-serialization-json` if nothing needs it (kotlinx.serialization) ([§6](#6-object-mapping)).
12. Run your tests against a real MongoDB server (`kmongo-dsl-testcontainers`, [§10](#10-tests)).

## 1. Migration check script

[`tools/kmongo-dsl-migration-check.sh`](https://github.com/Litote/kmongo-dsl/blob/main/tools/kmongo-dsl-migration-check.sh) lists the code to review
(it does not depend on the build tool). Download it, then run it on your project:

```shell
curl -fsSLO https://raw.githubusercontent.com/Litote/kmongo-dsl/main/tools/kmongo-dsl-migration-check.sh
chmod +x kmongo-dsl-migration-check.sh
./kmongo-dsl-migration-check.sh [--fix-imports] [--add-imports <compiler output>] [directory...]
```

- `REVIEW`: behaviour changes **not detected by the compiler** (sync `first()` on a `find`/`aggregate`... call,
  change streams, GridFS, static mocks) - `first()` on a variable holding a `FindIterable` is not detected
- `FIX`: code that does not compile any more (untyped `getCollection("x")`, `DBObject`, map-reduce, deprecated KMongo artifacts,
  `jackson-generator`...)
- `DEPRECATED`: code that compiles with deprecation warnings (`reactivestreams` imports, `CoroutineCollection`, `into`...)

`--fix-imports` replaces the Java sync driver imports (`com.mongodb.client.MongoCollection`...) by the Kotlin sync driver ones.

`--add-imports` adds the explicit imports of the kmongo-dsl extensions reported by the compiler (unresolved references,
`in` / `for` on a `MongoIterable`, lambda passed to a driver member function), in the sorted imports
(the coroutine variant is chosen in the files importing the coroutine driver). Repeat until no import is added
(new errors may appear once the first ones are fixed), then format the imports if needed (`ktlint -F`):

```shell
mvn compile > build.log; ./kmongo-dsl-migration-check.sh --add-imports build.log   # Maven
./gradlew compileKotlin > build.log 2>&1; ./kmongo-dsl-migration-check.sh --add-imports build.log   # Gradle
```

## 2. Dependencies

Use the BOM to align the versions:

```kotlin
dependencies {
    implementation(platform("org.litote.kmongo:kmongo-dsl-bom:5.14.0"))
    // was org.litote.kmongo:kmongo
    implementation("org.litote.kmongo:kmongo-dsl-sync-jackson")
}
```

Maven:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.litote.kmongo</groupId>
            <artifactId>kmongo-dsl-bom</artifactId>
            <version>5.14.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<dependencies>
    <!-- was org.litote.kmongo:kmongo -->
    <dependency>
        <groupId>org.litote.kmongo</groupId>
        <artifactId>kmongo-dsl-sync-jackson</artifactId>
    </dependency>
</dependencies>
```

A KMongo DSL setup is one **flavor** (sync or coroutine) + one **object mapping** (Jackson or kotlinx.serialization).
The `kmongo-dsl-<flavor>-<mapping>` artifacts (`kmongo-dsl-sync-jackson`, `kmongo-dsl-coroutine-serialization`...) bring both,
like the deprecated KMongo artifacts:

| Deprecated KMongo artifact                         | KMongo DSL artifacts                                                                 |
|---------------------------------------------|-----------------------------------------------------------------------------------|
| `kmongo`                                    | `kmongo-dsl-sync-jackson` (= `kmongo-dsl-sync` + `kmongo-dsl-jackson-mapping`)             |
| `kmongo-serialization`                      | `kmongo-dsl-sync-serialization` (= `kmongo-dsl-sync` + `kmongo-dsl-serialization-mapping`) |
| `kmongo-native`                             | `kmongo-dsl-sync` + a mapping (the "native" POJO mapping is not supported, see [§6](#6-object-mapping))   |
| `kmongo-coroutine`                          | `kmongo-dsl-coroutine-jackson` (= `kmongo-dsl-coroutine` + `kmongo-dsl-jackson-mapping`)   |
| `kmongo-coroutine-serialization`            | `kmongo-dsl-coroutine-serialization` (= `kmongo-dsl-coroutine` + `kmongo-dsl-serialization-mapping`) |
| `kmongo-coroutine-native`                   | `kmongo-dsl-coroutine` + a mapping                                                   |
| `kmongo-core`, `kmongo-coroutine-core`      | `kmongo-dsl-sync`, `kmongo-dsl-coroutine`                                               |
| `kmongo-property`, `kmongo-shared`          | `kmongo-dsl-core` (transitive dependency of the flavors)                             |
| `kmongo-annotation-processor` (kapt)        | `kmongo-dsl-annotation-processor` (kapt, Maven) or `kmongo-dsl-ksp` (ksp, Gradle), see [§7](#7-code-generation-ksp-or-kapt) |
| `org.litote.jackson:jackson-generator` (kapt) | `kmongo-dsl-annotation-generator` (kapt, same annotations, the generated serializers use Jackson 3), see [§7](#7-code-generation-ksp-or-kapt) |
| `org.litote.jackson:jackson-data`, `org.litote.jackson:jackson-module-loader` | `kmongo-dsl-jackson-data`, `kmongo-dsl-jackson-module-loader` (same packages, Jackson 3 API) |
| `org.litote:kreflect`                       | not used by the code generated by the kmongo-dsl processors: remove it if your code does not use it |
| `kmongo-async`                              | `kmongo-dsl-coroutine` + a mapping, see [§8](#8-removed-flavors) (source compatibility layer for the `org.litote.kmongo.reactivestreams` imports) |
| `kmongo-rxjava2*`, `kmongo-reactor*`        | no equivalent, see [§8](#8-removed-flavors)                                                             |
| `kmongo-flapdoodle`                         | `kmongo-dsl-testcontainers` ([Testcontainers](https://java.testcontainers.org/modules/databases/mongodb/)), see [§10](#10-tests) |
| `kmongo-id`, `kmongo-data`, `kmongo-id-jackson`, `kmongo-id-serialization` | `kmongo-dsl-id`, `kmongo-dsl-data`, `kmongo-dsl-id-jackson`, `kmongo-dsl-id-serialization` (same classes, same packages) |

Replace the dependencies of the model modules too: the old and the new artifacts must not be in the same classpath.

With Gradle, a conflict is reported if both are in the classpath (the `kmongo-dsl-*` artifacts declare the capability
of the KMongo ones). To select the `kmongo-dsl-*` ones automatically (a library still depends on `kmongo-id`...):

```kotlin
configurations.all {
    resolutionStrategy.capabilitiesResolution {
        listOf("kmongo-id", "kmongo-data", "kmongo-id-jackson", "kmongo-id-serialization").forEach {
            withCapability("org.litote.kmongo:$it") { selectHighestVersion() }
        }
        listOf("jackson-data", "jackson-module-loader").forEach {
            withCapability("org.litote.jackson:$it") { selectHighestVersion() }
        }
    }
}
```

With Maven, exclude the KMongo artifacts brought by your dependencies (`<exclusions>`):
`mvn dependency:tree -Dincludes=org.litote.kmongo` lists them.

Do not depend directly on `mongodb-driver-sync`, `mongodb-driver-reactivestreams` or `mongodb-driver-legacy`
any more: the official Kotlin drivers bring what they need.

## 3. Client creation

| Deprecated KMongo                                                           | KMongo DSL                                                                   |
|-----------------------------------------------------------------------|---------------------------------------------------------------------------|
| `org.litote.kmongo.KMongo.createClient()` (sync)                      | unchanged - returns a `com.mongodb.kotlin.client.MongoClient`             |
| `org.litote.kmongo.reactivestreams.KMongo.createClient().coroutine`   | `org.litote.kmongo.coroutine.KMongo.createClient()` - returns a `com.mongodb.kotlin.client.coroutine.MongoClient` (the `reactivestreams` version is kept, deprecated) |

`KMongo.createClient(settings: MongoClientSettings)` still adds the KMongo mapping to the codec registry of the settings.
The `bson-kotlin` / `bson-kotlinx` codecs registered by default by the official driver are filtered out:
the KMongo mapping is always used.

If the sync and the coroutine flavors are both in the classpath, `KMongo` is ambiguous with the star imports
`org.litote.kmongo.*` and `org.litote.kmongo.coroutine.*`: import it explicitly
(`import org.litote.kmongo.coroutine.KMongo`).

If you create the client yourself, configure the registry with
`KMongoClientSettings.settings(settings)` (or `withKMongo()` on a database or a collection).

### Java sync client (GridFS...)

The official Kotlin drivers do not provide GridFS (and the Java client of a Kotlin client is not accessible):
see [GridFS](extensions-overview.md#gridfs).

## 4. Sync flavor

The KMongo sync extensions now apply to the classes of the official Kotlin sync driver.

### Imports

| Deprecated KMongo (Java driver)                     | KMongo DSL (official Kotlin sync driver)          |
|----------------------------------------------|------------------------------------------------|
| `com.mongodb.client.MongoClient`             | `com.mongodb.kotlin.client.MongoClient`        |
| `com.mongodb.client.MongoDatabase`           | `com.mongodb.kotlin.client.MongoDatabase`      |
| `com.mongodb.client.MongoCollection`         | `com.mongodb.kotlin.client.MongoCollection`    |
| `com.mongodb.client.FindIterable`, `AggregateIterable`, `DistinctIterable`, `MongoIterable`, `MongoCursor`, `ClientSession`, `ChangeStreamIterable`, `ListIndexesIterable` | same name in `com.mongodb.kotlin.client` |

The `com.mongodb.client.model.*` (`Filters`, `Updates`, options...) and `com.mongodb.client.result.*` classes are unchanged.

**Explicit imports.** The KMongo extensions are in the `org.litote.kmongo` package (sync) and in the
`org.litote.kmongo.coroutine` package (coroutine). The KMongo code with the star imports `org.litote.kmongo.*`
compiles; with explicit imports (ktlint...), each extension needs its import - including the extensions
that replace members of the Java driver classes:

| Code                                         | Import                                 |
|----------------------------------------------|----------------------------------------|
| `x in col.find()`                            | `org.litote.kmongo.contains`           |
| `for (x in col.find())`                      | `org.litote.kmongo.iterator`           |
| `col.find().filter { }` (the member `filter(Bson)` is not applicable to a lambda) | `org.litote.kmongo.filter` |
| `database.getCollection<T>()`                | `org.litote.kmongo.getCollection`      |

`kmongo-dsl-migration-check.sh --add-imports` adds these imports from the compiler errors (see [§1](#1-migration-check-script)).

A `sed` one-liner covers most cases:

```shell
grep -rl "import com.mongodb.client\." src | xargs sed -i -E \
  's/^import com\.mongodb\.client\.(MongoClient|MongoDatabase|MongoCollection|FindIterable|AggregateIterable|DistinctIterable|MongoIterable|MongoCursor|ClientSession|ChangeStreamIterable|ListIndexesIterable)$/import com.mongodb.kotlin.client.\1/'
```

### Behaviour changes

| Deprecated KMongo                                                    | KMongo DSL                                                                     |
|---------------------------------------------------------------|-----------------------------------------------------------------------------|
| `database.getCollection("name")` returns `MongoCollection<Document>` | the type is required: `database.getCollection<Document>("name")`     |
| `col.find().first()` returns `null` if there is no result     | `first()` throws a `MongoClientException`: use `firstOrNull()` (**not detected by the compiler**: `first()` is a member of the driver class, it can not be deprecated - see [§1](#1-migration-check-script)) |
| `iterable.iterator()` / `iterable.into(list)` / `for (x in iterable)` | `iterable.cursor()` / `iterable.toCollection(list)` (deprecated `iterator()` & `into` aliases are provided: the code compiles) |
| `MongoCollection<T>` with a nullable `T`                      | `T : Any`                                                                   |
| `import org.litote.kmongo.withDocumentClass`                  | `withDocumentClass<T>()` is a member of the driver collection: remove the import (a deprecated declaration keeps the import valid) |
| `mapReduce` / `mapReduceWith`                                 | removed (map-reduce is deprecated by MongoDB): use an aggregation pipeline  |
| `MongoIterable.withIndex()` returns a `MongoIterable`         | returns a `List<IndexedValue<T>>`                                           |
| the collection-like `MongoIterable` extensions (`filter`, `map`, `contains`, `associateBy`...) are in the `kotlin.collections` package | they are in the `org.litote.kmongo` package: replace the explicit `import kotlin.collections.xxx` imports of these functions by `import org.litote.kmongo.xxx` (the star import `org.litote.kmongo.*` is enough, `--add-imports` adds them) |
| `save(entity)` returns `Unit`                                 | returns the `UpdateResult` of the replacement (`null` if the entity had no id and was inserted) |
| `deleteOne(filter)`                                           | an optional `DeleteOptions` parameter is available (like the coroutine flavor) |
| `projection(property)` returns `MongoIterable<F>`             | returns `MongoIterable<F?>` (null if the field is missing or null)          |
| `changeStream.listen { }` returns `Unit` and leaks its thread | returns an `AutoCloseable` that stops the listening; the default executor is a daemon thread shut down when the change stream ends. To survive the errors, use `listenIndefinitely` (see [§9](#9-change-streams)) |
| `DBObject` / `BasicDBObject` properties and documents         | not supported (legacy API): use `Document` or `Map`. `DBRef` is still supported |
| `MongoClientProvider` / `MongoClientProviderService` SPI      | removed                                                                     |
| `count(...)` (shown in the KMongo documentation)              | `countDocuments(...)` (`count` was already removed in KMongo 4)             |

All the other KMongo extensions (`findOne`, `findOneById`, `save`, `updateOne`, `updateOneById`, `replaceOneById`,
`deleteOneById`, `projection`, `ensureIndex`, `bulkWrite(String)`, `aggregate<T>(String...)`, the `ClientSession`
variants, the collection-like operations on `MongoIterable` - `filter`, `map`, `associateBy`... - and `evaluate`)
are unchanged.

## 5. Coroutine flavor

The KMongo coroutine wrappers are replaced by the official Kotlin coroutine driver classes.
A **compatibility layer** keeps the KMongo names as **deprecated** type aliases,
so that the code compiles with deprecation warnings - use the IDE quick fix ("Replace with...") to migrate:

| Deprecated KMongo                                   | KMongo DSL                                                                  |
|----------------------------------------------|--------------------------------------------------------------------------|
| `CoroutineClient`                            | `com.mongodb.kotlin.client.coroutine.MongoClient`                        |
| `CoroutineDatabase`                          | `com.mongodb.kotlin.client.coroutine.MongoDatabase`                      |
| `CoroutineCollection<T>`                     | `com.mongodb.kotlin.client.coroutine.MongoCollection<T>`                 |
| `CoroutineFindPublisher<T>`                  | `com.mongodb.kotlin.client.coroutine.FindFlow<T>` (a `Flow<T>`)          |
| `CoroutineAggregatePublisher<T>`             | `AggregateFlow<T>`                                                       |
| `CoroutineDistinctPublisher<T>`              | `DistinctFlow<T>`                                                        |
| `CoroutineChangeStreamPublisher<T>`          | `ChangeStreamFlow<T>`                                                    |
| `CoroutineListIndexesPublisher<T>`           | `ListIndexesFlow<T>`                                                     |
| `CoroutineListCollectionsPublisher<T>`       | `ListCollectionsFlow<T>`                                                 |
| `col.findAndCast<R>(filter)`                 | `col.find<R>(filter)` (deprecated alias provided)                        |
| `publisher.consumeEach { }`                  | `flow.collect { }` (deprecated alias provided)                           |
| `client.coroutine`, `database.coroutine`, `collection.coroutine` | no-op (deprecated): remove it                        |
| `publisher.toFlow()`                         | no-op (deprecated): driver flows are `Flow`s                             |
| `clientSession.commitTransactionAndAwait()` / `abortTransactionAndAwait()` | `commitTransaction()` / `abortTransaction()` (deprecated aliases provided) |

### Behaviour changes

| Deprecated KMongo                                                    | KMongo DSL                                                                     |
|---------------------------------------------------------------|-----------------------------------------------------------------------------|
| KMongo operations were members of `CoroutineCollection`       | they are extension functions: add `import org.litote.kmongo.coroutine.*`    |
| `publisher.first()` returns `null` if there is no result      | use `firstOrNull()` (`kotlinx.coroutines.flow`). The `first(): T?` of `FindFlow`, `AggregateFlow` and `DistinctFlow` is deprecated with the **ERROR** level: the code does not compile until each call is reviewed (it would otherwise silently replace the throwing `kotlinx.coroutines.flow.first` of the new code). An explicit `import kotlinx.coroutines.flow.first` takes precedence and selects the throwing variant |
| `publisher.toList()`                                          | unchanged on the driver flows (`org.litote.kmongo.coroutine.toList`). Other flow operations come from `kotlinx.coroutines.flow` |
| `col.projection(...)` returns a publisher                     | returns a `Flow` (`Flow<F?>` for a single property)                        |
| `col.collection` (the reactive streams collection)            | removed: the driver collection has all the properties (`col.namespace`...) |
| `import org.litote.kmongo.reactivestreams.getCollection` / `getCollectionOfName` + `.coroutine` | compiles (deprecated aliases on the coroutine `MongoDatabase`): use `org.litote.kmongo.coroutine.*`. Do not star import both `org.litote.kmongo.reactivestreams.*` and `org.litote.kmongo.coroutine.*` (ambiguity) |
| `distinct(KProperty1<T, R>)`                                  | `R : Any`                                                                   |

## 6. Object mapping

The object mapping is still selected by the mapping artifact present in the classpath
(or with the `org.litote.mongo.mapping.service` system property, or `ClassMappingType.forceService`).

The BSON documents written and read by KMongo DSL are checked against documents generated with KMongo 5.12
for both mappings (simple types, nested & nullable objects, maps with enum / `String` / `Id` keys, dates,
`java.time`, `kotlinx-datetime`, `BigDecimal`, `ByteArray`, sealed classes, with and without `null` values):
see the `kmongo-dsl-golden` project. Note that the KMongo `UUID` format depends on the mapping
(Jackson: binary subtype 3 - legacy, kotlinx.serialization: binary subtype 4 - standard): KMongo DSL keeps both formats.

The code samples of the [KMongo documentation](https://litote.org/kmongo/) are compiled and run against KMongo DSL
(`kmongo-dsl-samples`, `DocumentationSnippetsTest`).

The base codec registry (`KMongoUtil.defaultCodecRegistry`) is the official driver default registry without the
`bson-kotlin` / `bson-kotlinx` codecs and without the legacy `DBObject` codecs.

### Jackson (`kmongo-dsl-jackson-mapping`)

KMongo DSL uses **Jackson 3** (`tools.jackson` packages), with the Jackson 2 settings: the BSON documents do not change.
The legacy `DBObject` support is removed.

If your code does not use the Jackson API, there is nothing to do: the `com.fasterxml.jackson.annotation` annotations
(`@JsonProperty`, `@JsonIgnore`...) are unchanged. Otherwise, migrate it to Jackson 3
(see the [Jackson 3 migration guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)):

| Deprecated KMongo (Jackson 2)                                 | KMongo DSL (Jackson 3)                                                      |
|---------------------------------------------------------------|-----------------------------------------------------------------------------|
| `com.fasterxml.jackson.core`, `.databind`, `.module` packages | `tools.jackson.core`, `.databind`, `.module`                                |
| `Module`, `JsonSerializer`, `JsonDeserializer`, `SerializerProvider` | `JacksonModule`, `ValueSerializer`, `ValueDeserializer`, `SerializationContext` |
| `KMongoConfiguration.bsonMapper`... are mutable `ObjectMapper`s | they are immutable `JsonMapper`s: use `registerBsonModule`, or `rebuild()` and assign the result |
| `registerBsonModule(module)` updates the mappers              | replaces the mappers: call it before creating the client, the existing codecs keep the previous mappers |
| `objectMapper.registerModule(IdJacksonModule())`              | `JsonMapper.builder().addModule(IdJacksonModule()).build()`                 |
| `objectMapper.registerModulesFromServiceLoader()`             | `JsonMapper.builder().registerModulesFromServiceLoader()`                   |
| `JacksonModuleServiceLoader.module()` returns a `Module`      | returns a `JacksonModule`                                                   |

Jackson 2 and Jackson 3 can be in the same classpath (their packages are different): the rest of your application can
stay on Jackson 2, but the Jackson 2 modules and serializers are not used by the KMongo DSL mapping.
The serializers generated for `@JacksonData` use Jackson 3.

### kotlinx.serialization (`kmongo-dsl-serialization-mapping`)

The mapping now uses the official [`bson-kotlinx`](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/fundamentals/data-formats/serialization/)
encoder/decoder instead of [kbson](https://github.com/jershell/kbson).
The BSON format is kept compatible with the deprecated KMongo (same class discriminator `___type`, same date, id, `BigDecimal`,
`UUID`... encodings), but:

| Deprecated KMongo                                                    | KMongo DSL                                                                     |
|---------------------------------------------------------------|-----------------------------------------------------------------------------|
| `configuration: com.github.jershell.kbson.Configuration`      | `configuration: org.bson.codecs.kotlinx.BsonConfiguration` (`nonEncodeNull = x` → `explicitNulls = !x`) |
| `com.github.jershell.kbson.ObjectIdSerializer`, `BigDecimalSerializer`, `DateSerializer` | `org.litote.kmongo.serialization.ObjectIdSerializer`, `BigDecimalSerializer`, `DateSerializer` |
| `Map<@Contextual Id<T>, V>` (contextual map keys)             | not supported by `bson-kotlinx` (only `STRING`/`ENUM` map keys): use `Map<@Serializable(with = IdBsonSerializer::class) Id<T>, V>` |
| `@Serializer(forClass = X::class) object XSerializer` generated for a class with non `@Serializable` properties | the generated descriptor is empty and is rejected by `bson-kotlinx`: write the serializer by hand (or annotate the class with `@Serializable`) |

`@BsonId`, `@BsonProperty`... annotations are still ignored by the kotlinx.serialization mapping
(they are often present for a Jackson mapping): use `@SerialName("_id")` or `@MongoId`.

### "Native" mapping (`kmongo-native-mapping`)

Not supported. Use the kotlinx.serialization mapping, the Jackson mapping, or the official driver
`bson-kotlin` data class codec directly (without KMongo DSL).

### Performance

The performance is close to KMongo 5.12 (see the [results](performance.md#results)), with two points to check.

**Jackson: keep `kotlin-reflect` 2.4+.** This is the reason for the Kotlin 2.4 requirement: with `kotlin-reflect`
2.2, the Jackson Kotlin module allocates about 47% more when decoding. KMongo DSL brings a recent version, but Maven
selects the nearest declaration: remove any older `kotlin-reflect` version from your `pom.xml` and from its
`dependencyManagement` section. With Gradle, check that no `strictly` constraint keeps an older version.

**kotlinx.serialization: decoding is slower than with kbson.** It takes about 1.2 times as long, and 1.7 times when
`kotlinx-serialization-json` is in the classpath, which is often the case through another library:
[exclude this dependency](performance.md#kotlinx-serialization-json-in-the-classpath) if nothing needs it.
A fix is proposed to the official driver, see the [results](performance.md#results).

## 7. Code generation: KSP or KAPT

The `kmongo-annotation-processor` KAPT processor is replaced by two processors that generate **the same code**
(the generator is shared, and a test checks that both outputs are identical): the property paths
(`MyClass_`, `MyClass_Col`, `MyClass_Map`) of the classes annotated with `@Data` / `@DataRegistry`
(and `@JacksonData` / `@JacksonDataRegistry` if `kmongo-dsl-core` is in the classpath: a module using `@JacksonData`
only for the Jackson serializers does not need kmongo-dsl).

| Build tool | Processor                                      |
|------------|------------------------------------------------|
| Gradle     | `kmongo-dsl-ksp` (KSP, recommended: faster, incremental) |
| Maven      | `kmongo-dsl-annotation-processor` (KAPT: the `kotlin-maven-plugin` supports it natively, KSP has no official Maven plugin) |

The generated classes are the same as the KMongo ones: the code using them does not change.

### Gradle (KSP)

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.13"
}

dependencies {
    ksp("org.litote.kmongo:kmongo-dsl-ksp")
}
```

The `kotlin-kapt` Gradle plugin also works with `kapt("org.litote.kmongo:kmongo-dsl-annotation-processor")`.

### Maven (KAPT)

Replace `kmongo-annotation-processor` by `kmongo-dsl-annotation-processor` (and `org.litote.jackson:jackson-generator`
by `kmongo-dsl-annotation-generator`) in the `kapt` execution of the `kotlin-maven-plugin`
(a complete project is in [`maven-sample`](https://github.com/Litote/kmongo-dsl/blob/main/maven-sample/pom.xml), built by the CI):

```xml
<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>kapt</id>
            <goals>
                <goal>kapt</goal>
            </goals>
            <configuration>
                <sourceDirs>
                    <sourceDir>src/main/kotlin</sourceDir>
                </sourceDirs>
                <annotationProcessorPaths>
                    <annotationProcessorPath>
                        <groupId>org.litote.kmongo</groupId>
                        <artifactId>kmongo-dsl-annotation-processor</artifactId>
                        <version>${kmongo-dsl.version}</version>
                    </annotationProcessorPath>
                    <!-- if you used org.litote.jackson:jackson-generator -->
                    <annotationProcessorPath>
                        <groupId>org.litote.kmongo</groupId>
                        <artifactId>kmongo-dsl-annotation-generator</artifactId>
                        <version>${kmongo-dsl.version}</version>
                    </annotationProcessorPath>
                </annotationProcessorPaths>
            </configuration>
        </execution>
        <!-- compile, test-compile executions... -->
    </executions>
</plugin>
```

Use the `test-kapt` goal for the `@Data` classes of the test sources.

The kmongo-dsl KAPT processors embed their dependencies (KotlinPoet 2, `kotlin-metadata-jvm`) in a relocated package:
they can share the KAPT classpath with processors using other versions of these libraries.
`jackson-generator` 0.4.0 (KotlinPoet 1.0) can not: in the same KAPT classpath as `kmongo-dsl-annotation-processor`,
one of them fails (`NoSuchMethodError`) or generates wrong code - replace it by `kmongo-dsl-annotation-generator`.

### Jackson serializers (`@JacksonData`)

`kmongo-dsl-annotation-generator` (KAPT) replaces `org.litote.jackson:jackson-generator`: it generates the same
`MyClass_Serializer` / `MyClass_Deserializer` classes for the classes annotated with `@JacksonData`
(or listed in a `@JacksonDataRegistry`), registered in `META-INF/services/org.litote.jackson.JacksonModuleServiceLoader`
and loaded by `kmongo-dsl-jackson-mapping`. `kmongo-dsl-jackson-data` replaces `org.litote.jackson:jackson-data`: same
annotations in the same package, without the `kreflect` dependency. `kmongo-dsl-jackson-module-loader` replaces the runtime
`org.litote.jackson:jackson-module-loader`: same classes and same service file, so the serializers already generated still work
(versions in the KMongo DSL BOM). `kmongo-dsl-jackson-mapping` depends on it: remove the old artifact from your build.

- supported classes: final or open Kotlin classes without superclass and without type parameters, built with their
  primary constructor - the other classes are skipped with a note (Jackson default serialization), as before
- serialized properties: the properties with a backing field, minus the `@JsonIgnore` and `@Transient` ones;
  `@JsonProperty("name")` (on the parameter, the field or the getter) sets the JSON name
- missing values use the default values of the constructor parameters; the mutable properties declared in the class body
  are deserialized too (they were ignored), the read-only ones are only serialized
- private and protected properties, and the internal properties of the classes of other modules (`@JacksonDataRegistry`),
  are read with `kotlin-reflect` (no `kreflect` dependency)
- the properties named with a Java keyword (`default`, `int`...) are serialized after the other properties
  (they have no field in the KAPT stubs)

### Notes

- private and protected properties, and the internal properties of the classes of other modules (`@DataRegistry`),
  are supported through `kotlin-reflect` (the deprecated KMongo used `kreflect`).
- the type aliases are expanded, the nullable values of the collections and maps of `@Data` classes are supported
  (`List<Address?>`, `Map<String, Address?>`).
- `@file:DataRegistry([...])` file annotations are supported (they were ignored by the deprecated KMongo KAPT processor).
  With KAPT, the file must contain at least one top-level function or property (KAPT puts the file annotations
  on the file facade class `XxxKt`, which only exists if the file has top-level declarations) - else annotate a class or an object.
- KSP: the processor is incremental (isolating outputs). KAPT: the processor is not incremental.
- the generated class names use the simple class name (as with KAPT): two nested classes with the same simple name
  in the same package now report a compilation error instead of overwriting each other.
- the members of the generated classes are sorted by property name.
- the Jackson serializers of the `@JacksonData` classes are generated by `kmongo-dsl-annotation-generator` (see above).

## 8. Removed flavors

`kmongo-async`, `kmongo-rxjava2` and `kmongo-reactor` have no equivalent: the official Kotlin drivers are sync and coroutine only.

- migrate to the coroutine flavor (Reactor and RxJava provide coroutine bridges: `kotlinx-coroutines-reactor`, `kotlinx-coroutines-rx2`).
  `kmongo-async` code (`org.litote.kmongo.reactivestreams` imports, `KMongo.createClient().coroutine`) compiles
  on the coroutine flavor thanks to deprecated aliases (see [§5](#5-coroutine-flavor))
- or use the official reactive streams driver directly, with the KMongo DSL query DSL (`kmongo-dsl-core`) to build the filters/updates
  and `KMongoClientSettings.configureRegistry(...)` for the mapping.

## 9. Change streams

The official drivers resume a change stream once after a resumable error. For long-running listeners, use
`watchIndefinitely` (coroutine) or `listenIndefinitely` (sync): they survive the errors and the invalidations,
see [Change streams](extensions-overview.md#change-streams).

## 10. Tests

`kmongo-flapdoodle` is replaced by `kmongo-dsl-testcontainers`, see [Testing](testing.md).

**Mocks.** The KMongo extensions are not in the same JVM classes as in the deprecated KMongo
(for instance the coroutine extensions are in `org.litote.kmongo.coroutine.MongoCollectionsKt`, not `CoroutineCollectionKt`),
and the driver classes changed (`FindFlow` instead of `CoroutineFindPublisher`...): the tests mocking KMongo with
`mockkStatic("org.litote.kmongo.coroutine.CoroutineCollectionKt")` or mocking publishers (`awaitFirst`...) must be rewritten.

## 11. Example

Deprecated KMongo:

```kotlin
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo

val client = KMongo.createClient().coroutine
val col: CoroutineCollection<Hero> = client.getDatabase("test").getCollection<Hero>()

suspend fun batman(): Hero? = col.find(Hero::name eq "Batman").first()
```

KMongo DSL:

```kotlin
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.eq

val client = KMongo.createClient()
val col: MongoCollection<Hero> = client.getDatabase("test").getCollection<Hero>()

suspend fun batman(): Hero? = col.find(Hero::name eq "Batman").firstOrNull()
```

The `kmongo-dsl-samples` module of the KMongo DSL repository contains KMongo style code compiled and tested against KMongo DSL.

## FAQ

**Can I use the official driver API and KMongo DSL together?**
Yes, KMongo DSL does not wrap the driver: the collections *are* the official driver collections,
KMongo DSL only adds extension functions.

**Is the Java driver still used?**
The official Kotlin drivers are implemented on top of the Java drivers, so they are still present as transitive
dependencies, but KMongo DSL does not use their API.

**Why are some KMongo names deprecated instead of removed?**
To make the migration incremental: the code compiles first, then the IDE quick fixes migrate it to the official API.
