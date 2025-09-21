package xyz.beengoo.runfolia

import groovy.json.JsonSlurper
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.*
import java.io.File

class RunFoliaPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val ext = extensions.create<RunFoliaExtension>("runFolia")

        // Configuration for user-declared extra plugins to stage into plugins/
        val runtimePlugins: Configuration = configurations.create("runFoliaPlugins") {
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "Additional plugins to place into the server plugins directory"
        }

        // Sensible defaults
        ext.runDir.convention(layout.buildDirectory.dir("run-folia"))

        // Detect main artifact: prefer shadowJar if present, else jar
        val mainArtifact: Provider<RegularFile> = providers.provider {
            val shadow = tasks.findByName("shadowJar")
            val task = (shadow ?: tasks.named("jar").get())
            (task as org.gradle.api.tasks.bundling.Jar).archiveFile.get()
        }

        // Files/paths
        val serverJar = ext.runDir.map { it.file("folia-${ext.mcVersion.get()}.jar") }

        // Task: download Folia from PaperMC Builds API
        val downloadFolia = tasks.register("downloadFolia") {
            group = "run-folia"
            description = "Download Folia server jar into the managed run directory"
            outputs.file(serverJar)

            doLast {
                val runDirFile = ext.runDir.get().asFile
                runDirFile.mkdirs()
                val version = ext.mcVersion.get()

                val api = java.net.URL("https://api.papermc.io/v2/projects/folia/versions/$version/builds")
                val json = JsonSlurper().parse(api) as Map<*, *>
                val builds = json["builds"] as List<Map<String, *>>
                if (builds.isEmpty()) throw GradleException("No Folia builds found for $version")

                val latest = builds.maxBy { it["build"] as Int }
                val build = latest["build"] as Int
                val fileName = (latest["downloads"] as Map<*, *>)["application"]
                    .let { it as Map<*, *> }["name"] as String

                val dl = java.net.URL("https://api.papermc.io/v2/projects/folia/versions/$version/builds/$build/downloads/$fileName")
                val dst = serverJar.get().asFile
                logger.lifecycle("Downloading Folia $version build $build -> ${dst.absolutePath}")
                dl.openStream().use { ins -> dst.outputStream().use { outs -> ins.copyTo(outs) } }
            }
        }

        // Task: stage user's plugin jar into plugins/ (symlink/hardlink if enabled)
        val stageOwnPlugin = tasks.register("stageOwnPlugin") {
            group = "run-folia"
            description = "Stage the built plugin jar into run-dir/plugins"
            dependsOn({
                tasks.findByName("shadowJar") ?: tasks.named("jar").get()
            })

            doLast {
                val runDirFile = ext.runDir.get().asFile
                val pluginsDir = File(runDirFile, "plugins").apply { mkdirs() }

                val src = mainArtifact.get().asFile
                val dst = File(pluginsDir, src.name)
                if (dst.exists()) dst.delete()

                val tryLinks = ext.linkPluginJar.get()
                if (tryLinks) {
                    val isWindows = OperatingSystem.current().isWindows
                    try {
                        if (isWindows) {
                            // Hardlink fallback that doesn't require admin
                            exec {
                                commandLine("cmd", "/c", "mklink", "/H", dst.absolutePath, src.absolutePath)
                                isIgnoreExitValue = false
                            }
                        } else {
                            // Symlink on Unix
                            exec {
                                commandLine("ln", "-sf", src.absolutePath, dst.absolutePath)
                                isIgnoreExitValue = false
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Linking failed, copying instead: ${e.message}")
                        src.copyTo(dst, overwrite = true)
                    }
                    if (!dst.exists()) {
                        // If link command did nothing (e.g. no perms), copy
                        src.copyTo(dst, overwrite = true)
                    }
                } else {
                    src.copyTo(dst, overwrite = true)
                }
                logger.lifecycle("Staged plugin: ${dst.absolutePath}")
            }
        }

        // Task: stage extra runtime plugins declared by user
        val stageRuntimePlugins = tasks.register<Copy>("stageRuntimePlugins") {
            group = "run-folia"
            description = "Stage runFoliaPlugins configuration into run-dir/plugins"
            from(runtimePlugins)
            into(ext.runDir.map { it.dir("plugins") })
            doFirst { ext.runDir.get().file("plugins").asFile.mkdirs() }
        }

        // Task: run Folia (like run-paper)
        tasks.register<JavaExec>("runFolia") {
            group = "run-folia"
            description = "Run Folia in the managed run directory"
            dependsOn(downloadFolia, stageOwnPlugin, stageRuntimePlugins)

            workingDir = ext.runDir.get().asFile

            // java -jar <serverJar>
            mainClass.set("-jar")
            args(serverJar.get().asFile.name)
            args(ext.serverArgs.get())

            val jvm = mutableListOf<String>()
            if (ext.agreeEula.get()) jvm += "-Dcom.mojang.eula.agree=true"
            jvm += ext.jvmArgs.get()
            jvmArgs(jvm)
        }

        // Convenience: add Folia API repo if user forgot (doesn't hurt)
        repositories.apply {
            mavenCentral()
            maven("https://repo.papermc.io/repository/maven-public/")
        }

        // Tip: if user applies Java plugin later, fine; if present now, ensure UTF-8
        plugins.withType<JavaPlugin> {
            tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
                options.encoding = "UTF-8"
            }
        }
    }
}
