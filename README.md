# Run Folia Server Gradle Plugin
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/xyz.beengoo.run-folia.svg)](https://plugins.gradle.org/plugin/xyz.beengoo.run-folia)

Gradle plugin to easily download and run a [Folia](https://github.com/PaperMC/Folia) server directly from your build.
## Usage

Add the plugin to your `plugins` block:



### Kotlin
```kotlin
plugins {
  id("xyz.beengoo.run-folia") version "0.1.0"
}
```

## Configuration

You can configure the plugin via the runFolia extension

```kotlin
runFolia {
  mcVersion.set("1.21.1")           // Folia server version to run
  runDir.set(layout.projectDirectory.dir("run")) // Server run directory
  jvmArgs.addAll("-Xmx2G", "-Xms2G") // JVM args
  serverArgs.add("--nogui")        // Server launch arguments
  agreeEula.set(true)              // Auto-accept EULA
}
```

## Run Tasks

- `runFolia` – Runs the Folia server with your plugin on the classpath.
- `downloadFoliaServer` – Downloads the Folia server jar if not already installed.

## License

This project is licensed under the [MIT License](LICENSE).


