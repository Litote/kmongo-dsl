# Object Mapping Performance

Bson deserialization is a key factor to measure performance of KMongo DSL,
as usually you retrieve more data from MongoDB than you send to the db.

## Benchmark

The [JMH](https://github.com/openjdk/jmh) benchmarks are available in the
[kmongo-dsl-benchmark](https://github.com/Litote/kmongo-dsl/tree/main/benchmark) module.
They compare KMongo 5.12 and KMongo DSL, in memory (no MongoDB server), with the same MongoDB driver version:

| benchmark              | what                                                                                      |
|------------------------|-------------------------------------------------------------------------------------------|
| `CodecBenchmark`       | decode / encode `Friend` and `FriendWithBuddies` (see below), codec lookup; the `driver*` benchmarks (plain driver `Document` + manual mapping) are the baseline |
| `GoldenCodecBenchmark` | decode / encode samples with ids, enums, collections, `java.time`, `kotlinx.datetime`...  |
| `MixedBenchmark`       | decode / encode all these models in the same JVM, as an application does: the reference to compare the mappings |
| `DslBenchmark`         | type-safe filters, updates, projection / sort, aggregation pipeline, property paths, JSON queries |

for the Jackson and the kotlinx.serialization mappings.

The `CodecBenchmark` models:

```kotlin
data class FriendWithBuddies(
     val _id: ObjectId? = null,
     val name: String? = null,
     val address: String? = null,
     val coordinate: Coordinate? = null,
     val gender: Gender? = null,
     val buddies: List<FriendWithBuddies> = emptyList()
)

data class Friend(
     val _id: ObjectId? = null,
     val name: String? = null,
     val address: String? = null,
     val coordinate: Coordinate? = null,
     val gender: Gender? = null
)
```

Run it with:

```shell
./gradlew :kmongo-dsl-benchmark:benchmark
```

The report (`benchmark/build/reports/benchmark.md`) lists the average time and the allocations
per operation of KMongo 5.12 and KMongo DSL, and their ratio.

`CodecBenchmark` and `GoldenCodecBenchmark` run one model per JVM: the JIT optimizes the shared code for this model,
so the times are optimistic and the ratios depend on the model. All these benchmarks run in memory, without network.

## Results

KMongo DSL compared to KMongo 5.12 (ratio: below 1.00, KMongo DSL is faster or allocates less), on JDK 17:

| Operation                                               | Jackson: time | Jackson: allocations | kotlinx.serialization: time | kotlinx.serialization: allocations |
|---------------------------------------------------------|--------------:|---------------------:|----------------------------:|-----------------------------------:|
| Decoding (`MixedBenchmark.decodeAll`)                   | 0.98          | 0.99                 | 1.20                        | 1.25                               |
| Decoding, `kotlinx-serialization-json` in the classpath | -             | -                    | 1.67                        | 1.70                               |
| Encoding (`MixedBenchmark.encodeAll`)                   | 0.98          | 1.01                 | 0.81                        | 0.95                               |
| Update (`DslBenchmark.updateEntity`)                    | 1.03          | 1.03                 | 0.71                        | 0.82                               |

- The Jackson mapping uses Jackson 3 in KMongo DSL and Jackson 2 in KMongo 5.12. Compared to KMongo DSL with Jackson 2,
  Jackson 3 has a small effect: encoding is 2 to 5% faster, decoding does not change significantly, and `updateEntity`
  is 1 to 5% slower and allocates 3% more (about 100 bytes per operation).
- kotlinx.serialization: the decoding overhead comes from the official `bson-kotlinx` decoder, used instead of kbson.
  A fix is proposed to the driver ([JAVA-6363](https://jira.mongodb.org/browse/JAVA-6363),
  [pull request #2096](https://github.com/mongodb/mongo-java-driver/pull/2096), not merged yet): with this pull request,
  the decoding time is the same as KMongo 5.12, with or without `kotlinx-serialization-json`, and the allocations are
  1.12 and 1.21 times the KMongo 5.12 ones.
- The ratios are similar on JDK 27, where the absolute allocations of KMongo 5.12 and KMongo DSL are about 20% lower.

## Which mapping?

- the kotlinx.serialization mapping involves almost no reflection: it is usually the fastest,
  and it **skips** the not mapped BSON data (partial deserialization of a `FriendWithBuddies` document into a `Friend`)
- the Jackson mapping is slower, but you can avoid the reflection for your hot classes with the generated Jackson serializers
  (`@JacksonData` + `kmongo-dsl-annotation-generator`, see [Typed Queries](typed-queries.md#jackson-serializers-jacksondata)),
  or with custom Jackson deserializers

If performance is critical for your use case, use the kotlinx.serialization mapping.
But the Jackson mapping performs well enough for almost all projects.

## kotlinx-serialization-json in the classpath

!!! note
    This issue only applies until [pull request #2096](https://github.com/mongodb/mongo-java-driver/pull/2096)
    is merged and released in a MongoDB driver version used by KMongo DSL: then you will not need this exclusion
    for the performance.

The KMongo DSL modules do not depend on `kotlinx-serialization-json`, but your application or another library
often does. When it is in the classpath, the official `bson-kotlinx` codec switches to its JSON aware decoders,
which build a `Json` instance for each decoded object: in the benchmarks, decoding is 1.5 to 1.9 times slower,
and allocates 1.5 to 1.7 times more memory.

If nothing in your application needs this library, you can exclude it:

```kotlin
configurations.all {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json")
}
```

!!! warning
    Exclude it only if neither your code nor your dependencies use it: `JsonElement` properties
    are not supported without it, and a library that needs it fails at runtime.
