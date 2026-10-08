---
title: FHIRConnect IntelliJ plugin – modernisation plan
status: completed
created: 2026-10-08
---

# FHIRConnect IntelliJ plugin – modernisation plan

## Context

The plugin (3 Java classes, ~470 lines) lets authors of FHIRConnect mapping files jump between a context
file and the model/extension mappers it references, and from a mapper's `metadata.name` to every file
that uses it. It was written without IntelliJ SDK experience and works by brute force. The review found
these concrete problems:

**Compatibility (most urgent)**
- Marketplace lists 1.0.1 as compatible with **231.0 – 233.\*** only. `pluginUntilBuild` in
  `gradle.properties` is never read (the line in buildSrc is commented out), so the Gradle plugin defaulted
  `untilBuild` to the major of `platformVersion = 2023.3.5`. Nobody on 2024.x–2026.x can install it.
- IntelliJ Platform Gradle Plugin 2.4.0 is 15 releases behind (current 2.19.0). `instrumentationTools()`
  was removed in 2.12.0. Gradle plugin 2.19.0 needs Gradle 9.x.
- The Gradle wrapper is **git-ignored** (`gradle/`, `gradlew*` in `.gitignore`), so a fresh clone cannot
  build. `buildSrc` also hard-applies `jetbrainsCredentials.gradle` whenever `CI != true`, so a clone
  without that secret file fails at configuration time.

**Navigation implementation**
- `YamlGotoDeclarationHandler` returns the clicked `YAMLKeyValue` itself (navigates to itself) and adds a
  `RangeHighlighter` on every call that is never removed. Ctrl+click therefore fires both the platform's
  own Go-to-Declaration and the custom mouse listener.
- `YamlEditorMouseListener.mousePressed` runs on every click in every editor. On Ctrl+click it walks
  `project.getBaseDir()` (deprecated, single-root only), reads **every** YAML file's bytes from disk
  (ignores unsaved editor contents, bypasses PSI/VFS caches), lowercases, strips all whitespace and
  regex-matches. Cost is O(all YAML in project) per click and each file read logs at WARN level.
- `start` navigation never worked: the matcher looks for `starts:`; the spec key is `start`.
- List-item matching uses `-<name>` which matches any YAML list anywhere; archetype and extension strings
  are identical; the multi-target popup re-finds the file by `getName()` so same-named files in different
  folders pick the wrong one (the mapping lib has `KDS_composition` defined 6 times).
- Matching is case-insensitive while the openFHIR engine compares names with exact `equals`.
- Leftovers: `System.out.println()`, unused `isContextFile`/`isFhirConnectContext`, mutable `RelevantFile`
  DTO, empty `messages/user_facing.properties`, obsolete `-Xdebug -Xrunjdwp` JVM flags, 470 KB of
  screenshots packaged into the plugin jar, `<version>`/`<description>` in plugin.xml that Gradle overrides.

**Spec facts the rewrite must honour** (verified against the JSON schemas in
`../FHIRconnect-spec/modules/ROOT/attachments/`, the engine classes in
`../openfhir/fhirconnect/src/main/java/com/syntaric/openfhir/fc/schema/`, and 122 files in
`../FHIRconnect-mapping-lib/`):

| Reference site (file type) | Resolves to |
|---|---|
| `mappings[*].slotArchetype` (model/extension, also nested `followedBy.mappings`, `reference.mappings`) | model `metadata.name` (engine also accepts `spec.openEhrConfig.archetype`) |
| `spec.extends` (extension) | model `metadata.name` |
| `context.archetypes[*]` (context) | model `metadata.name` |
| `context.extensions[*]` (context) | extension `metadata.name` |
| `context.start` (context, scalar) | model `metadata.name` |
| `mappings[*].slotContext`, `context.contexts[*]` (engine-only) | context `metadata.name` |

All FHIRConnect files have top-level `grammar: FHIRConnect/v…` and `type: model|extension|context`.
Values may be quoted or unquoted, files use `.yml` or `.yaml`, CRLF endings, mixed case, and names are
**not** globally unique (resolution must prefer the nearest directory).

**Decisions taken with the user**
- Minimum IDE **2024.2 (sinceBuild 242)**, Java 21, no `untilBuild`.
- Rewrite navigation on PSI references + file-based index; add **completion** for mapper names.
  Inspection and gutter markers are deferred (see "Deferred").
- GitHub Actions: build + verify on push/PR, signed publish on tag.

Outcome: a plugin that installs on every current IDE, navigates instantly via the platform's own
Go-to-Declaration / Find Usages machinery, has tests and CI, and is documented well enough for outside
contributors.

---

## Phase 1 – Build system and compatibility

### 1.1 Replace `buildSrc` with a single `build.gradle.kts` + version catalog
Follow the JetBrains `intellij-platform-plugin-template` layout. Delete `buildSrc/` entirely.

- `gradle/libs.versions.toml`: `intellijPlatform = "2.19.0"`, `changelog = "2.5.0"`, `junit = "4.13.2"`.
- `build.gradle.kts`: `java`, `org.jetbrains.intellij.platform`, `org.jetbrains.changelog` plugins;
  `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }`;
  `intellijPlatform { create(platformType, platformVersion); bundledPlugin("org.jetbrains.plugins.yaml");
  testFramework(TestFrameworkType.Platform); pluginVerifier(); zipSigner() }` (no `instrumentationTools()`);
  `pluginConfiguration { ideaVersion { sinceBuild = pluginSinceBuild; untilBuild = provider { null } } }`;
  description from README markers and change notes from CHANGELOG exactly as today (keep that logic);
  `signing`/`publishing` read **only** environment variables `CERTIFICATE_CHAIN`, `PRIVATE_KEY`,
  `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN` via `providers.environmentVariable(...)` (no local credentials
  file, no `CI` branching);
  `pluginVerification { ides { recommended() } }`;
  `-Xlint` flags kept; JUnit 4 kept for the platform test framework; drop testng, mockito, task-tree, the
  `listProductsReleases` / `generateIdeVersionsList` / `runIdeForUiTests` tasks.
- `gradle.properties` (trimmed to what is used):
  `pluginGroup=com.openfhir`, `pluginName=FHIRConnect`, `pluginVersion=1.1.0`, `pluginSinceBuild=242`,
  `platformType=IC`, `platformVersion=2024.2.6` (lowest supported line, confirm exact latest 2024.2 patch
  with `./gradlew printProductsReleases`), `platformBundledPlugins=org.jetbrains.plugins.yaml`,
  `org.gradle.jvmargs=-Xmx2g`, `org.gradle.configuration-cache=true`, `org.gradle.caching=true`,
  `kotlin.stdlib.default.dependency=false`. Remove `pluginUntilBuild`, `javaVersion`, the verifier
  mute/exclude properties (re-add only if `verifyPlugin` actually complains), `systemProp.idea.jdk.secondary`,
  `ide.recursive`, `org.gradle.configureondemand`.
- `settings.gradle.kts`: keep `rootProject.name`; add `plugins { id("org.gradle.toolchains.foojay-resolver-convention") }`
  so CI/JDK 21 resolves automatically.
- Commit the Gradle wrapper at **9.8.1** (`gradle wrapper --gradle-version 9.8.1` using the locally cached
  8.14.4 distribution at `~/.gradle/wrapper/dists/gradle-8.14.4-bin/*/gradle-8.14.4/bin/gradle`).
- `.gitignore`: remove `gradle/` and `gradlew*`; add `.kotlin/`, `*.hprof`, `.intellijPlatform/` stays.

### 1.2 `src/main/resources/META-INF/plugin.xml`
- Keep `<id>com.openfhir</id>` unchanged (changing it would orphan existing Marketplace installs).
- Remove `<version>`, `<description>`, hard-coded vendor email duplication; Gradle patches these.
- Keep `<depends>com.intellij.modules.platform</depends>` and `<depends>org.jetbrains.plugins.yaml</depends>`.
- Add `<resource-bundle>messages.FhirConnectBundle</resource-bundle>`.
- Register the new extensions listed in Phase 2; remove `editorFactoryMouseListener`.

### 1.3 Resources
- Move `screenshot_*.png` and `openfhir.png` to `docs/images/`; they are README assets, not plugin resources.
- Rename `messages/user_facing.properties` → `messages/FhirConnectBundle.properties` and use it.

---

## Phase 2 – Navigation rewrite (package `com.openfhir.fhirconnect`)

Delete `YamlEditorMouseListener`, `YamlGotoDeclarationHandler`, `RelevantFile`. New classes:

### 2.1 `psi/FhirConnectPsiUtil` (static helpers, pure PSI, no I/O)
- `isFhirConnectFile(PsiFile)`: top-level `grammar` key value starts with `FHIRConnect/`.
- `getFileType(YAMLFile)`: `MODEL | EXTENSION | CONTEXT` from top-level `type`.
- `getMapperName(YAMLFile)`: value of `metadata.name` (trimmed, unquoted) and the `YAMLScalar` element.
- `getArchetypeId(YAMLFile)`: `spec.openEhrConfig.archetype`.
- `getReferenceKind(YAMLScalar)`: returns which of the six reference sites in the table above the scalar
  is in, or null. Implemented with `YAMLUtil.getConfigFullName` style key-path checks
  (`spec.extends`, `context.start`, parent sequence under `context.archetypes` / `context.extensions` /
  `context.contexts`, key `slotArchetype` / `slotContext` anywhere under `mappings`).

### 2.2 Indexes (`index/`)
Two `ScalarIndexExtension<String>` registered as `<fileBasedIndex>`; input filter
`DefaultFileTypeSpecificInputFilter(YAMLFileType.YML)`; indexer bails out early unless the file content
contains `FHIRConnect/` (cheap `CharSequence` check before touching PSI).
- `FhirConnectNameIndex` – keys: `metadata.name` and `spec.openEhrConfig.archetype` of the file.
- `FhirConnectReferenceIndex` – keys: every referenced name found at the reference sites.
Both use `FileContent.getPsiFile()`; version constant bumped whenever the indexer changes.

### 2.3 `navigation/FhirConnectResolver`
`List<PsiElement> resolve(Project, String name, PsiFile from, ReferenceKind kind)`:
1. `FileBasedIndex.getContainingFiles(NAME_INDEX, name, projectScope)`.
2. Filter by expected target type (`slotArchetype`/`archetypes`/`extends`/`start` → model;
   `extensions` → extension; `slotContext`/`contexts` → context). If filtering empties the list keep
   the unfiltered one (lenient, like the engine).
3. If more than one file matches, prefer files sharing the longest common ancestor directory with the
   source file; keep all ties (platform shows a chooser popup).
4. Return the `metadata.name` value scalars (`YAMLScalar`) of those files.
5. Exact, case-sensitive match first; if empty, fall back to a case-insensitive scan of
   `FileBasedIndex.getAllKeys` so nothing that worked in 1.0.1 stops working.
Wrap in `DumbService.isDumb` guard (return empty while indexing).

### 2.4 `navigation/FhirConnectReferenceContributor` + `FhirConnectMapperReference`
- `PsiReferenceContributor` for `YAMLScalar` in YAML files; provider returns one
  `FhirConnectMapperReference extends PsiPolyVariantReferenceBase<YAMLScalar>` when
  `getReferenceKind` is non-null and the file is a FHIRConnect file.
- `multiResolve` → `FhirConnectResolver`; `isSoft()` = true (no red squiggles until an inspection exists);
  `getVariants` = empty (completion is a dedicated contributor); `handleElementRename` updates the scalar
  text so *Rename* on `metadata.name` propagates.
- Registered via `<psi.referenceContributor language="yaml" implementation=…/>`.
This alone gives Ctrl+click, Ctrl+B, Ctrl+hover underline, and the multi-target chooser.

### 2.5 Find usages of `metadata.name`
- `navigation/FhirConnectReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>`
  registered as `<referencesSearch>`: when the element is a `metadata.name` scalar (or its `YAMLKeyValue`),
  look up `FhirConnectReferenceIndex` for that name, walk the candidate files' PSI for scalars with that
  text, and feed references that `isReferenceTo(target)`.
- `navigation/FhirConnectFindUsagesProvider` registered as `<lang.findUsagesProvider language="yaml">`
  only if the YAML plugin's own provider does not already return `canFindUsagesFor` true for the scalar
  (check in `runIde`; use `order="first"` and delegate otherwise).
- `navigation/FhirConnectUsagesGotoHandler implements GotoDeclarationHandler`: when the caret is on a
  `metadata.name` value, return all referencing scalars (via `ReferencesSearch.search`). Keeps today's UX:
  Ctrl+click on the name opens the list of files using it. `getActionText` from the bundle.

### 2.6 `completion/FhirConnectNameCompletionContributor`
`CompletionContributor` for YAML, pattern = `YAMLScalar` whose `getReferenceKind` is non-null; variants
= `FileBasedIndex.getAllKeys(NAME_INDEX)` filtered by target type, with the defining file's relative path
as tail text and the `type` as type text. Registered via `<completion.contributor language="yaml">`.

### 2.7 Bundle
`FhirConnectBundle extends DynamicBundle` with keys for the goto action text, popup title and completion
type texts.

---

## Phase 3 – Tests (`src/test/java`, `src/test/testData`)
Use `BasePlatformTestCase` (`testFramework(TestFrameworkType.Platform)`, JUnit 4).
Test data: a mini mapping lib copied/adapted from `../FHIRconnect-mapping-lib`:
`model/CLUSTER.case_identification.v0.yml`, `model/INSTRUCTION.service_request.v1.yml`,
`projects/a/KDS_composition.yml`, `projects/b/KDS_composition.yml` (duplicate name),
`projects/a/KDS_laborauftrag.context.yaml`, plus one non-FHIRConnect `other.yaml` to prove it is ignored.

- `FhirConnectReferenceTest`: for each reference kind, `myFixture.getReferenceAtCaretPosition` resolves to
  the right `metadata.name` scalar; `start` resolves (regression for the `starts:` bug); duplicate name from
  `projects/a` prefers `projects/a/KDS_composition.yml`; unquoted vs quoted values both resolve;
  unknown name resolves to empty without error.
- `FhirConnectFindUsagesTest`: `myFixture.findUsages(nameScalar)` returns the context, slot and extends
  usages.
- `FhirConnectCompletionTest`: `myFixture.completeBasic()` at `slotArchetype: "<caret>"` lists model
  names only; at `extensions: - <caret>` lists extension names only.
- `FhirConnectIndexTest`: `other.yaml` has no keys in either index.
- `FhirConnectRenameTest`: renaming `metadata.name` updates all referencing scalars.

---

## Phase 4 – Documentation and repo hygiene
- `README.md`: keep the `<!-- Plugin description -->` block (it feeds the Marketplace); add sections:
  Features (with the three screenshots from `docs/images/`), Supported keys table (the table above),
  Installation (Marketplace link + manual zip), Compatibility (2024.2+), Building from source
  (`./gradlew build`, `runIde`, `verifyPlugin`, `test`), Releasing (tag `v1.1.0`, required secrets),
  Related projects (spec, openfhir, mapping lib), License.
- `CHANGELOG.md`: fill `[Unreleased]` → `[1.1.0]`: Added completion, find usages, rename support;
  Changed navigation engine, compatibility 2024.2+; Fixed `start` navigation, duplicate-name resolution,
  Marketplace compatibility range; Removed bundled screenshots. Fix the `[1.0.0] - YYYY-MM-DD` placeholder
  (Marketplace date: 2025-01-17; 1.0.1: 2025-01-25).
- `CONTRIBUTING.md`: prerequisites (JDK 21), run/debug (`./gradlew runIde --debug-jvm`), test data layout,
  index version bump rule, how to open the mapping lib as a sample project.
- `.editorconfig` (4-space Java, LF, UTF-8, trim trailing whitespace).
- `.github/workflows/build.yml`: on push/PR → `./gradlew build verifyPlugin` with `actions/setup-java` 21
  + `gradle/actions/setup-gradle`; upload the zip and verifier report as artifacts.
- `.github/workflows/release.yml`: on tag `v*` → `./gradlew publishPlugin` with the four secrets; create a
  GitHub release with the changelog section via `./gradlew getChangelog`.
- `.github/dependabot.yml`: `gradle` and `github-actions` ecosystems, weekly.
- `pluginIcon.svg` keep; drop `pluginIcon-full.svg` from META-INF if unused (move to `docs/images/`).

---

## Deferred (not in this iteration, listed so nothing is lost)
- `LocalInspectionTool` "Unresolved FHIRConnect mapper" (flip `isSoft()` to false once added).
- `RelatedItemLineMarkerProvider` gutter icons on `metadata.name` and reference sites.
- `ChooseByNameContributorEx` for *Go to Symbol* on mapper names.
- README roadmap items (AQL / FHIRPath completion, flat-path conversion) stay roadmap.

---

## Verification
1. `./gradlew build` on a fresh clone with no credentials and no `jetbrainsCredentials.gradle` succeeds.
2. `./gradlew test` – all tests in Phase 3 pass.
3. `./gradlew verifyPlugin` – no errors against 2024.2, 2025.x, 2026.1, 2026.2 (`recommended()` set);
   `build/distributions/FHIRConnect-1.1.0.zip` contains no PNGs; patched plugin.xml has `since-build="242"`
   and **no** `until-build`.
4. `./gradlew runIde`, open `../FHIRconnect-mapping-lib` as a project and check manually:
   - Ctrl+click / Ctrl+B on `slotArchetype`, `extends`, an `archetypes` item, an `extensions` item and
     `start` jump to the right `metadata.name`; `KDS_composition` from `projects/org.highmed/KDS/person/`
     resolves to the sibling file first.
   - Alt+F7 and Ctrl+click on `metadata.name` of `CLUSTER.case_identification.v0` list the contexts and
     mappers using it.
   - Ctrl+Space inside `slotArchetype: "` offers model names.
   - Shift+F6 on a `metadata.name` renames references.
   - Clicking around in a 5 000-line unrelated YAML causes no lag; `idea.log` has no per-click WARN lines.
   - Plain click (no Ctrl) does nothing.
5. CI workflow green on the PR; tag `v1.1.0` publishes (requires the four repo secrets to be set).
