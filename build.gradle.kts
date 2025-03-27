plugins {
    id("build-standard-jetbrains-plugin-build")
}

tasks.runIde {
    jvmArgs = listOf(
            "-Xdebug",
            "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005"
    )
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:-serial")
}
