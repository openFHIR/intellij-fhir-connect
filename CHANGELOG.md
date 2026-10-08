# Changelog

## [Unreleased]

### Added

- Completion of mapper names at every reference site (`slotArchetype`, `slotContext`, `spec.extends`,
  `context.archetypes`, `context.extensions`, `context.contexts`, `context.start`), filtered by the file type
  the site expects
- Find Usages (Alt+F7) and Show Usages for a mapper's `metadata.name`
- Rename (Shift+F6) of a mapper's `metadata.name` updates every file that references it
- Tests and GitHub Actions workflows (build + verify on push and pull request, signed publish on `v*` tags)

### Changed

- Navigation is now built on PSI references and file-based indexes instead of scanning every YAML file on each
  click. Ctrl+click, Ctrl+B and Ctrl+hover work out of the box and the IDE no longer lags in large projects
- Minimum supported IDE is 2024.2; there is no upper compatibility bound any more
- Build moved to IntelliJ Platform Gradle Plugin 2.19 and Gradle 9.8 with a committed wrapper; no local
  credentials file is required to build

### Fixed

- Navigation from `context.start` (the plugin looked for `starts`)
- Mapper names that exist in several directories resolve to the file closest to the referencing file; equal
  candidates are offered in a chooser instead of silently picking the wrong file
- Marketplace compatibility range ended at 2023.3, so the plugin could not be installed on current IDEs

### Removed

- Screenshots are no longer packaged into the plugin archive

## [1.0.1] - 2025-01-25

### Changed

- upper version of compatibility set to 243.*

### Fixed

- Performance issues resolved

## [1.0.0] - 2025-01-17

### Added

- Initial release of the FHIRConnect Plugin
- Ability to navigate between model mapping names referenced in files (context or model)

[Unreleased]: https://github.com/openFHIR/intellij-fhir-connect/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/openFHIR/intellij-fhir-connect/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/openFHIR/intellij-fhir-connect/commits/v1.0.0
