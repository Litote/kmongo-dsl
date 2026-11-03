# AGENTS.md — KMongo DSL

> Instructions for AI agents working on this codebase.
> Usage: [`README.md`](README.md) and [`docs/`](docs). Build, checks, compatibility rules and release process:
> [`CONTRIBUTING.md`](CONTRIBUTING.md) — read it before changing code.

## Never

| Category | Forbidden |
|---|---|
| **Deploy** | `publishToMavenCentral`, `publishAndReleaseToMavenCentral`, creating tags or releases (the CI publishes) |
| **Commit** | Commit or push unless explicitly asked |
| **Generated code** | Edit `coroutine/src/main/kotlin/org/litote/kmongo/coroutine` extensions generated from the sync sources (edit `sync`, then `generateCoroutineSources`), edit the `api/*.api` dumps by hand (`updateKotlinAbi`), edit the golden BSON documents (`generateGoldenFiles`) |
| **Compatibility** | Use a Kotlin 2.5+ API in the published code, change the published packages (`org.litote.kmongo...`: KMongo 4/5 source compatibility), remove a public API without a deprecation cycle |
| **Dependencies** | Add or upgrade a dependency without an explicit request; a dependency change requires the regeneration of `gradle/verification-metadata.xml` |

## Always

| Category | Required |
|---|---|
| **Validation** | `./gradlew build` after every change (tests, formatting, license headers, ABI, coroutine sources) |
| **Tests** | Bug fix: write the failing test first. Integration tests: `kmongo-dsl-sync-tests` / `kmongo-dsl-coroutine-tests` (run with both mappings: `test` and `testSerialization`) |
| **API change** | Review the `api/*.api` diff (`./gradlew updateKotlinAbi`), `explicitApi()` is enabled in the published modules |
| **Language** | Code, comments and documentation in English |
| **Documentation** | User-facing changes → `README.md` / `docs/` (the `CHANGELOG.md` is written by release-please from the commits); contributor changes → `CONTRIBUTING.md`; agent rules → `AGENTS.md` |
| **Migration tooling** | After a public API change: `tools/update-migration-symbols.sh` |
