plugins {
    // Resolves the JDK declared in the Java toolchain automatically (CI, fresh clones).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "intellij-fhir-connect-plugin"
