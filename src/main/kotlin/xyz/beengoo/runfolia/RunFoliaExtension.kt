package xyz.beengoo.runfolia

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import javax.inject.Inject

abstract class RunFoliaExtension @Inject constructor(objects: ObjectFactory) {
    /** Minecraft version, e.g. 1.21.6 */
    val mcVersion: Property<String> = objects.property(String::class.java).convention("1.21.6")

    /** Managed run directory (like run-paper). Default: build/run-folia */
    val runDir: DirectoryProperty = objects.directoryProperty()

    /** Agree EULA automatically */
    val agreeEula: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /** Use links instead of copying plugin jar into plugins/ */
    val linkPluginJar: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /** Extra JVM args for server launch */
    val jvmArgs: Property<List<String>> = objects.listProperty(String::class.java).convention(emptyList())

    /** Extra server args, e.g. ["--nogui"] */
    val serverArgs: Property<List<String>> = objects.listProperty(String::class.java).convention(listOf("--nogui"))

    init {
        runDir.convention(objects.directoryProperty().fileValue(null)
            .orElse(objects.directoryProperty()))
    }
}
