# Contributing

## Prerequisites

- JDK 17+ (the Gradle toolchain is JDK 17)
- Docker: the integration tests start a MongoDB server with Testcontainers
- Maven 3.9+ (optional): the Maven + KAPT sample (`maven-sample`)
- Python 3 + `pip install -r requirements-docs.txt` (optional): the documentation site

## Quick reference

```bash
# after every change
./gradlew build

# a module, a test
./gradlew :kmongo-dsl-core:test
./gradlew :kmongo-dsl-sync-tests:test --tests "*.AggregateTest"
# the kotlinx.serialization run of the integration tests (the Jackson run is `test`)
./gradlew :kmongo-dsl-sync-tests:testSerialization

# another MongoDB server / another test JDK (the CI checks MongoDB 5.0 to 8.2, JDK 17, 21 and 25)
./gradlew test -Dkmongo.test.mongodb.image=mongo:5.0
./gradlew test -Pkmongo.test.java.version=25

# missing license headers
./gradlew spotlessApply

# the Maven + KAPT setup
./gradlew publishToMavenLocal
mvn -f maven-sample/pom.xml verify

# the migration tooling
tools/update-migration-symbols.sh --check
tools/test/kmongo-dsl-migration-check-test.sh

# the documentation site (build/site)
tools/build-docs.sh

# SonarCloud (systemProp.sonar.token=<token> in ~/.gradle/gradle.properties)
# `sonar` waits for the quality gate and fails if it is not OK
./gradlew build jacocoAggregatedReport sonar
```

## What `./gradlew build` checks

| Check | Task | When it fails |
|---|---|---|
| Tests (Jackson & kotlinx.serialization mappings) | `test`, `testSerialization` | |
| License headers, ktlint formatting | `spotlessCheck` | `./gradlew spotlessApply` |
| Public API (binary compatibility) | `checkKotlinAbi` | intended API change: `./gradlew updateKotlinAbi` and commit the `api/*.api` files |
| Coroutine sources | `:kmongo-dsl-coroutine:checkCoroutineSources` | edit the sync sources (`kmongo-dsl-sync`), then `./gradlew :kmongo-dsl-coroutine:generateCoroutineSources` |
| Duplicate classes | `:kmongo-dsl-samples:checkDuplicateClasses` | a class is provided by two jars of the runtime classpath |

ktlint is not enforced yet: the code has to be reformatted first, in a dedicated commit.

1. `./gradlew spotlessApply -Pktlint`, then `./gradlew :kmongo-dsl-coroutine:generateCoroutineSources`
2. remove the `-Pktlint` condition of `build.gradle.kts` and this section
3. commit, then add the commit to `.git-blame-ignore-revs`

## Project layout

The Gradle project names are the published artifact ids (`:kmongo-dsl-core` → `org.litote.kmongo:kmongo-dsl-core`).
Their directories do not have the `kmongo-dsl-` prefix (`core/`, `benchmark/jackson/`): see `settings.gradle.kts`.
Use the project name in Gradle commands and the directory in file paths.

## Compatibility rules

- **Kotlin**: the published libraries require Kotlin 2.4+ (language & API versions, standard library and
  `kotlin-reflect`). Do not use a Kotlin 2.5+ API in the published code.
- **JDK**: the code is compiled for Java 17.
- **Versions**: the MongoDB dependencies are declared without version (the MongoDB driver BOM is an `api` platform).

## Dependencies

Declare the versions in the version catalog (`gradle/libs.versions.toml`).

The downloaded artifacts are verified (`gradle/verification-metadata.xml`). After a dependency change, regenerate the
checksums with the tasks run by the CI:

```bash
./gradlew updateVerificationMetadata
```

The sources, javadoc and `src.zip` artifacts are only downloaded by the IDEs: they are trusted without checksum
(`trusted-artifacts`). The `.pom` and `.module` files take part in the dependency resolution and stay verified.

The Dependabot pull requests are updated automatically by the `dependabot-gradle-verification` workflow.

The minor and patch Gradle updates are then approved and merged by the `dependabot-auto-merge` workflow, once the
required checks pass. These updates stay manual: major versions, `github-actions`, `pip` and `maven`, the MongoDB
driver (see [Versions & releases](#versions--releases)), and any dependency with `kotlin` in its name (a minor update can
raise the Kotlin version required by the published libraries).

## Conventional Commits

The PR titles **must** follow the [Conventional Commits](https://www.conventionalcommits.org/) specification
(checked by the CI): `feat:`, `fix:`, `perf:`, `docs:`, `test:`, `refactor:`, `chore:`, `ci:` (`feat!:` for a breaking change).
The PR branches use the same prefix: `fix/enum-null-value-parsing`, `chore/update-mongodb-driver`.

## Versions & releases

The kmongo-dsl version follows the version of the MongoDB Kotlin driver it is built on (`5.14.x` → driver `5.14`).
Releases are made by [release-please](https://github.com/googleapis/release-please) (`release-please.yml`):

1. the commits merged into `main` update a release PR (CHANGELOG, version in `gradle.properties` and `maven-sample/pom.xml`)
2. merging the release PR creates the tag and the GitHub release, then publishes the artifacts on Maven Central
3. release-please then opens a snapshot PR (`x.y.z+1-SNAPSHOT`): merge it to resume the snapshot publication

Each release bumps the patch version (`"versioning": "always-bump-patch"`, whatever the commit types). After an
upgrade of the MongoDB driver to a new minor version, set the next version with `"release-as": "<driver version>"`
in `release-please-config.json` (and remove it after the release), and update the version in `docs/`:
release-please does not manage these files, as the snapshot PR would write its `-SNAPSHOT` version in the documentation.

The `main` snapshots are published on Maven Central by the CI. Never publish manually.

### GitHub configuration

| Secret | Used by |
|---|---|
| `SONAR_TOKEN` | ci, release |
| `GRADLE_ENCRYPTION_KEY` | Gradle configuration cache encryption (ci, docs, release) |
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | snapshot, release |
| `SIGNING_IN_MEMORY_KEY`, `SIGNING_IN_MEMORY_KEY_ID`, `SIGNING_IN_MEMORY_KEY_PASSWORD` | snapshot, release |
| `RELEASE_PLEASE_APP_ID`, `RELEASE_PLEASE_APP_PRIVATE_KEY` | release-please, dependabot-gradle-verification, dependabot-auto-merge |

release-please uses a GitHub App (Contents and Pull requests: read & write, installed on the repository): the events
created with the `GITHUB_TOKEN` do not trigger the workflows. The documentation site is deployed on GitHub Pages
(Settings > Pages > source "GitHub Actions").

`dependabot-auto-merge` needs the "Allow auto-merge" repository setting and a ruleset on `main` with required status
checks: without them, a pull request is merged without waiting for the CI. A check of a reusable workflow is named
`<caller job> / <called job>`:

- `zizmor / zizmor`
- `pr-title / Validate PR title (Conventional Commits)`
- `Check`
- `Tests (mongo:5.0, JDK 17)`, `Tests (mongo:6.0, JDK 17)`, `Tests (mongo:7.0, JDK 17)`
- `Tests (mongo:8.2, JDK 21)`, `Tests (mongo:8.2, JDK 25)`

## CI workflows

The workflows are built from the reusable workflows and composite actions of [Litote/ci](https://github.com/Litote/ci),
pinned by SHA and updated by Dependabot.

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | pull request, push on `main` | PR title, full check (MongoDB 8.2, JDK 17), Maven sample, migration tooling, SonarCloud, snapshot publication; tests with MongoDB 5.0/6.0/7.0 and JDK 21/25 |
| `docs.yml` | pull request, push on `main` (docs & sources) | builds the documentation site (`mkdocs build --strict`), deploys it from `main` |
| `release-please.yml` | push on `main`, manual | release PR, then release & Maven Central publication |
| `dependabot-gradle-verification.yml` | Dependabot pull requests | regenerates `gradle/verification-metadata.xml` |
| `dependabot-auto-merge.yml` | Dependabot pull request opened or reopened | approves the minor and patch Gradle updates and enables squash auto-merge |
| `zizmor.yml` | pull request, push on `main` | GitHub Actions security analysis |
