package xyz.beengoo.runfolia

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import java.io.File
import java.net.URL

class RunFoliaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // ---- Extension (Java-style API, no Kotlin-DSL sugar)
        val ext = project.extensions.create("runFolia", RunFoliaExtension::class.java)
        ext.runDir.convention(project.layout.buildDirectory.dir("run-folia"))

        // ---- Configuration for extra runtime plugins a user wants to stage into plugins/
        val runtimePlugins = project.configurations.create("runFoliaPlugins")
        runtimePlugins.isCanBeResolved = true
        runtimePlugins.isCanBeConsumed = false
        runtimePlugins.description = "Additional plugins to place into the server plugins directory"

        // ---- Determine main artifact: prefer shadowJar if present, otherwise jar
        val mainArtifact: Provider<RegularFile> = project.providers.provider {
            val shadow = project.tasks.findByName("shadowJar") as? Jar
            val jarTask = shadow ?: (project.tasks.named("jar").get() as Jar)
            jarTask.archiveFile.get()
        }

        // ---- Destination for Folia server jar inside managed run dir
        val serverJar = ext.runDir.file(project.providers.provider { "folia-${ext.mcVersion.get()}.jar" })

        // ---- Task: downloadFolia (skips if jar exists and forceUpdate=false)
        val downloadFolia = project.tasks.register("downloadFolia") { t ->
            t.group = "run-folia"
            t.description = "Download Folia server jar into the managed run directory"
            t.outputs.file(serverJar)

            // Skip if server jar already exists and forceUpdate=false
            t.onlyIf {
                val dst = serverJar.get().asFile
                val skip = dst.exists() && !ext.forceUpdate.get()
                if (skip) {
                    project.logger.lifecycle("Folia server present: ${dst.absolutePath} (skip download)")
                    false
                } else true
            }

            t.doLast {
                val runDirFile = ext.runDir.get().asFile
                runDirFile.mkdirs()

                // If forceUpdate and file exists, remove before download
                val dst = serverJar.get().asFile
                if (dst.exists()) dst.delete()

                val version = ext.mcVersion.get()
                val api = URL("https://api.papermc.io/v2/projects/folia/versions/$version/builds")
                val json = JsonSlurper().parse(api) as Map<*, *>
                val builds = (json["builds"] as? List<Map<String, *>>).orEmpty()
                if (builds.isEmpty()) throw GradleException("No Folia builds found for $version")

                val latest = builds.maxBy { it["build"] as Int }
                val build = latest["build"] as Int
                val fileName = ((latest["downloads"] as Map<*, *>)["application"] as Map<*, *>)["name"] as String

                val dl = URL("https://api.papermc.io/v2/projects/folia/versions/$version/builds/$build/downloads/$fileName")
                project.logger.lifecycle("Downloading Folia $version build $build -> ${dst.absolutePath}")
                dl.openStream().use { ins -> dst.outputStream().use { outs -> ins.copyTo(outs) } }
            }
        }

        // ---- Task: stageOwnPlugin (link or copy your built jar into run-dir/plugins)
        val stageOwnPlugin = project.tasks.register("stageOwnPlugin") { t ->
            t.group = "run-folia"
            t.description = "Stage the built plugin jar into run-dir/plugins"
            t.dependsOn(project.providers.provider { project.tasks.findByName("shadowJar") ?: project.tasks.named("jar").get() })

            t.doLast {
                val runDirFile = ext.runDir.get().asFile
                val pluginsDir = File(runDirFile, "plugins")
                pluginsDir.mkdirs()

                val src = mainArtifact.get().asFile
                val dst = File(pluginsDir, src.name)
                if (dst.exists()) dst.delete()

                val tryLinks = ext.linkPluginJar.get()
                val isWindows = System.getProperty("os.name").lowercase().contains("win")

                if (tryLinks) {
                    try {
                        if (isWindows) {
                            project.exec { it.commandLine("cmd", "/c", "mklink", "/H", dst.absolutePath, src.absolutePath) }
                        } else {
                            project.exec { it.commandLine("ln", "-sf", src.absolutePath, dst.absolutePath) }
                        }
                    } catch (e: Exception) {
                        project.logger.warn("Linking failed, copying instead: ${e.message}")
                        src.copyTo(dst, overwrite = true)
                    }
                    if (!dst.exists()) src.copyTo(dst, overwrite = true)
                } else {
                    src.copyTo(dst, overwrite = true)
                }

                project.logger.lifecycle("Staged plugin: ${dst.absolutePath}")
            }
        }

        // ---- Task: stageRuntimePlugins (copy user-declared plugins into plugins/)
        project.tasks.register("stageRuntimePlugins", Copy::class.java) { t ->
            t.group = "run-folia"
            t.description = "Stage runFoliaPlugins configuration into run-dir/plugins"
            t.from(runtimePlugins)
            t.into(ext.runDir.dir("plugins"))
            t.doFirst { ext.runDir.dir("plugins").get().asFile.mkdirs() }
        }

        // ---- Task: cleanFoliaJar (optional helper to remove downloaded server jar)
        project.tasks.register("cleanFoliaJar") { t ->
            t.group = "run-folia"
            t.description = "Remove downloaded Folia server jar"
            t.doLast {
                val dst = serverJar.get().asFile
                if (dst.exists()) {
                    dst.delete()
                    project.logger.lifecycle("Removed: ${dst.absolutePath}")
                } else {
                    project.logger.lifecycle("Nothing to remove: ${dst.absolutePath} not found")
                }
            }
        }

        // ---- Task: runFolia (like run-paper, using managed run dir)
        project.tasks.register("runFolia", JavaExec::class.java) { t ->
            t.group = "run-folia"
            t.description = "Run Folia in the managed run directory"
            t.dependsOn(downloadFolia)
            t.dependsOn(stageOwnPlugin)
            t.dependsOn(project.tasks.named("stageRuntimePlugins"))

            t.workingDir = ext.runDir.get().asFile
            t.mainClass.set("-jar")
            t.args(serverJar.get().asFile.name)
            t.args(ext.serverArgs.get())

            val jvm = mutableListOf<String>()
            if (ext.agreeEula.get()) jvm += "-Dcom.mojang.eula.agree=true"
            jvm.addAll(ext.jvmArgs.get())
            t.jvmArgs(jvm)
        }

        // ---- Helpful repos for consumers (safe to add)
        project.repositories.mavenCentral()
        project.repositories.maven { it.setUrl("https://repo.papermc.io/repository/maven-public/") }
    }
}
