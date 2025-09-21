plugins {

    id("com.gradle.plugin-publish") version "2.0.0"
    kotlin("jvm") version "2.0.20"
}

group = "xyz.beengoo"
version = "0.1.0"

repositories {
    mavenCentral()
}

gradlePlugin {
    website.set("https://github.com/beengoo/run-folia")
    vcsUrl.set("https://github.com/beengoo/run-folia")

    plugins {
        create("runFoliaPlugin") {
            id = "xyz.beengoo.run-folia"
            implementationClass = "xyz.beengoo.runfolia.RunFoliaPlugin"
            displayName = "Run Folia"
            description = "Gradle plugin to download and run Folia with your plugin staged automatically."
            tags.set(listOf("minecraft", "folia", "paper", "server", "run"))
        }
    }
}
