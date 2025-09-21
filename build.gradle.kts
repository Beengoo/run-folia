plugins {
    kotlin("jvm") version "2.0.20"
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "xyz.beengoo"
version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    implementation(gradleApi())
    implementation(localGroovy()) // for groovy.json.JsonSlurper
}

gradlePlugin {
    website.set("https://github.com/beengoo/run-folia")
    vcsUrl.set("https://github.com/beengoo/run-folia")
    plugins {
        create("runFoliaServer") {
            id = "xyz.beengoo.run-folia"
            implementationClass = "xyz.beengoo.runfolia.RunFoliaPlugin"
            displayName = "Run Folia"
            description = "Download and run Folia server with your plugin staged automatically."
            tags.set(listOf("minecraft", "folia", "paper", "server", "run"))
        }
    }
}
