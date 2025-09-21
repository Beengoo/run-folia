package xyz.beengoo.runfolia

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ListProperty
import org.gradle.api.file.DirectoryProperty
import javax.inject.Inject

abstract class RunFoliaExtension @Inject constructor(objects: ObjectFactory) {
    /** Minecraft version, e.g. 1.21.6 */
    val mcVersion: Property<String> = objects.property(String::class.java).convention("1.21.6")

    /** Managed run directory (default is build/run-folia) */
    val runDir: DirectoryProperty = objects.directoryProperty()

    /** Auto-accept EULA on first run */
    val agreeEula: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /** Try to link plugin jar into plugins/ (Windows: hardlink, *nix: symlink). Fallback to copy. */
    val linkPluginJar: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /** Force re-download server jar even if it already exists */
    val forceUpdate: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /** Extra JVM args for server process */
    val jvmArgs: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    /** Extra server args (e.g. --nogui) */
    val serverArgs: ListProperty<String> = objects.listProperty(String::class.java).convention(listOf("--nogui"))
}
