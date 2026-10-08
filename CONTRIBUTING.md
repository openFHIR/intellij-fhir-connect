# Contributing

## Prerequisites

- JDK 21 (the Gradle toolchain downloads one automatically if none is installed, via the Foojay resolver)
- No IntelliJ installation is needed: Gradle downloads IntelliJ IDEA Community 2024.2 into its cache on the
  first build
- No credentials are needed to build, test or run the plugin. Signing and publishing read the environment
  variables `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` and `PUBLISH_TOKEN` and are only
  used by the release workflow

## Everyday commands

| Command | What it does |
|---|---|
| `./gradlew build` | compiles and runs the tests |
| `./gradlew buildPlugin` | packages `build/distributions/FHIRConnect-<version>.zip` |
| `./gradlew test` | runs the tests only |
| `./gradlew runIde` | starts a sandbox IDE with the plugin installed |
| `./gradlew runIde --debug-jvm` | same, but waits for a debugger on port 5005 |
| `./gradlew verifyPlugin` | runs the JetBrains Plugin Verifier against the recommended IDE releases (downloads several IDEs on first use) |
| `./gradlew printProductsReleases` | lists the IDE releases the verifier would use |

To try the plugin on real mappings, open the
[FHIRconnect-mapping-lib](https://github.com/SevKohler/FHIRconnect-mapping-lib) checkout as a project in the
sandbox IDE started by `runIde` (File → Open, pick the repository root). Every file there has a top-level
`grammar: FHIRConnect/...` key, which is how the plugin recognises its files.

## Code layout

```
src/main/java/com/openfhir/fhirconnect
├── FhirConnectBundle                 message bundle (messages/FhirConnectBundle.properties)
├── psi/FhirConnectPsiUtil            pure PSI helpers: file detection, metadata.name, reference sites
├── psi/ReferenceKind                 the reference sites and the file type each one points to
├── index/FhirConnectNameIndex        mapper name (and openEHR archetype id) -> declaring file
├── index/FhirConnectReferenceIndex   referenced name -> referencing files
├── navigation/FhirConnectMapperTarget      the declaration (metadata.name) as a renameable PomTarget
├── navigation/FhirConnectResolver          name -> declaring file(s), nearest directory wins
├── navigation/FhirConnectMapperReference   the PsiReference attached to every reference site
├── navigation/FhirConnectReferenceSearcher find usages via the reference index
├── navigation/FhirConnectUsagesGotoHandler Ctrl+click on metadata.name opens the usages
└── completion/FhirConnectNameCompletionContributor
```

All extension points are registered in `src/main/resources/META-INF/plugin.xml`.

## Index version bump rule

`FhirConnectNameIndex` and `FhirConnectReferenceIndex` are file-based indexes. Their contents are persisted in
the user's IDE caches and are only rebuilt when the index version changes. **Whenever you change what an
indexer stores** (keys, values, the value format, or the detection in `FhirConnectIndexUtil`), increment the
`VERSION` constant of the affected index. Forgetting this leaves users with stale data until they invalidate
caches.

## Tests

Tests use `BasePlatformTestCase` (light fixture, JUnit 4) and live in `src/test/java`. The test data in
`src/test/testData` is a miniature mapping library:

```
model/                                  three model mappers (CLUSTER, INSTRUCTION, COMPOSITION)
projects/a/KDS_composition.yml          extension; the same name exists in projects/b (duplicate-name tests)
projects/a/KDS_laborauftrag.yml         extension with nested followedBy/reference mappings and slotContext
projects/a/KDS_laborauftrag.context.yaml  context using archetypes, extensions, contexts and start
projects/a/KDS_sub.context.yaml         sub-context referenced by slotContext / contexts
projects/b/KDS_composition.yml          duplicate name, farther away from projects/a
other.yaml                              looks similar but has no grammar key; must be ignored
```

`FhirConnectTestBase` copies the whole directory into the test project before each test. Test data files
contain no `<caret>` markup; tests locate positions by text (`moveCaretTo`, `referenceAt`) so that the same
files serve every test. Files that only one test needs are created inline with `myFixture.addFileToProject`.

## Releasing

1. Move the entries from `[Unreleased]` in `CHANGELOG.md` under a new version heading and set the same
   version in `gradle.properties` (`pluginVersion`).
2. Tag the commit `vX.Y.Z` and push the tag. The release workflow builds, verifies, signs, publishes the
   plugin to the JetBrains Marketplace and creates a GitHub release with the changelog section.
3. The workflow needs the repository secrets `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`
   and `PUBLISH_TOKEN` (see the [plugin signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)
   and [publishing](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html) guides).
