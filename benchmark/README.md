# KMongo 5.12 vs kmongo-dsl benchmark

[JMH](https://github.com/openjdk/jmh) benchmarks comparing KMongo 5.12 and kmongo-dsl, in memory (no MongoDB server):

| class                  | what                                                                                      |
|------------------------|-------------------------------------------------------------------------------------------|
| `CodecBenchmark`       | decode / encode `Friend` and `FriendWithBuddies` (models of the KMongo 4 benchmark), codec lookup; `driver*` = plain driver `Document` + manual mapping, the baseline (no KMongo) |
| `GoldenCodecBenchmark` | decode / encode the golden samples of `kmongo-dsl-golden` (ids, enums, collections, java.time, kotlinx.datetime...) |
| `MixedBenchmark`       | decode / encode all the models of `CodecBenchmark` and `GoldenCodecBenchmark` in the same JVM, as an application does: the reference to compare the mappings |
| `DslBenchmark`         | type-safe filters, updates (`set`, `updateOne(entity)`), projection / sort, aggregation pipeline, property paths, JSON queries, rendered to a `BsonDocument` as the driver does |

KMongo 5 and kmongo-dsl share their packages, so the sources (`src/common/kotlin`) are compiled by 5 projects,
one per classpath: `kmongo5-jackson`, `kmongo-dsl-jackson`, `kmongo5-serialization`, `kmongo-dsl-serialization`,
`kmongo-dsl-serialization-json` (with `kotlinx-serialization-json` in the classpath)
(`kmongo-dsl.benchmark` plugin of `build-logic`). All of them use the same MongoDB driver version
(`mongodb-bom` of the version catalog): only the KMongo layer differs.

## Run

```shell
# checks that KMongo 5.12 and kmongo-dsl produce the same results (same work measured)
./gradlew :kmongo-dsl-benchmark:benchmarkSmokeCheck
# quick run (checks the setup, the numbers are not meaningful)
./gradlew :kmongo-dsl-benchmark:benchmarkQuick
# nominal run (< 5 min: 3 main benchmarks, 3 x 2s warmup, 3 x 1s measurement, 2 forks, 4 variants)
./gradlew :kmongo-dsl-benchmark:benchmarkNominal
# full run (~75 min: 3 x 2s warmup, 5 x 2s measurement, 2 forks, 5 variants)
./gradlew :kmongo-dsl-benchmark:benchmark
```

`benchmarkNominal` is the day-to-day check: it covers `MixedBenchmark` (`decodeAll`, `encodeAll`) and `updateEntity`,
and leaves out `kmongo-dsl-serialization-json`. Its allocations are
reliable, its times only show large regressions: use the full run before a release or to quantify a change.
Its results are separate (`<variant>-nominal.json`, report `build/reports/benchmark-nominal.md`).

Options:

- `-Pjmh.include=<regex>`: only the matching benchmarks, e.g. `-Pjmh.include=DslBenchmark`
- `-Pjmh.args="<JMH options>"`: replaces the default JMH options (`-wi 3 -w 2s -i 5 -r 2s -f 2`)
- `-Pjmh.jvmArgs="<JVM options>"`: adds JVM options to the benchmark JVMs, e.g. `-XX:+UseCompactObjectHeaders`
- `-Pjmh.jdk=<version>`: runs the benchmarks on this JDK (Gradle toolchain, downloaded by foojay if it is not
  installed; the code is compiled for JDK 17, the default). The results go to a separate series (`-jdk<version>`
  suffix, report `build/reports/benchmark-jdk<version>.md`), e.g. `./gradlew :kmongo-dsl-benchmark:benchmark -Pjmh.jdk=27`
- `-Pjmh.tag=<name>`: names the result series (default `jdk<version>` with `-Pjmh.jdk`), e.g. to compare JVM options

The report (`build/reports/benchmark.md`) lists, for each mapping, the average time per operation (ns/op) and the
allocations per operation (B/op, JMH `gc` profiler) of KMongo 5.12 and kmongo-dsl, and their ratio.
The raw JMH results are in `<variant directory>/build/results/jmh/<variant>.json` (the directory of the `kmongo-dsl-*`
variants has no `kmongo-dsl-` prefix)
(they can be charted with [JMH Visualizer](https://jmh.morethan.io/)).

Run the full benchmark on a quiet machine (no IDE indexing, no other build).

Not published, not part of `check`.
