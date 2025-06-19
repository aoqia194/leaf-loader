import groovy.json.JsonOutput
import java.net.URL

plugins {
    java
}

tasks.register<GenerateInstallerJsonTask>("generateInstallerJson") {
    configurations = mapOf("common" to "installer", "development" to "development")
    options = mapOf(
        "mainClass" to mapOf(
            "client" to "dev.aoqia.leaf.loader.impl.launch.knot.KnotClient",
            "server" to "dev.aoqia.leaf.loader.impl.launch.knot.KnotServer"
        )
    )

    outputFile = file("src/main/resources/leaf-installer.json")
}

abstract class GenerateInstallerJsonTask : DefaultTask() {
    @get:Input
    abstract val configurations: MapProperty<String, String>

    @get:Input
    abstract val options: MapProperty<String, Any>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val json = mutableMapOf(
            "version" to 2,
            "min_java_version" to 17,
            "libraries" to mapOf<String, MutableList<Any>>(
                "client" to mutableListOf(),
                "common" to mutableListOf(),
                "server" to mutableListOf(),
                "development" to mutableListOf(),
            )
        )
        val libraries = json["libraries"] as Map<String, MutableList<Any>>

        configurations.get().forEach { (side, name) ->
            val resolvedArtifacts = project
                .configurations
                .getByName(name)
                .resolvedConfiguration
                .resolvedArtifacts

            // Makes sure that no artifacts are duplicated.
            // Sometimes the same artifact is under 2 repositories.
            val processed = mutableListOf<String>()

            for (artifact in resolvedArtifacts) {
                if (artifact.moduleVersion.toString() in processed) {
                    continue
                }

                val id = artifact.moduleVersion.id

                // If it's a development dependency, add it to JiJ and go to next artifact.
                if (side == "development") {
                    libraries[side]?.add(
                        mapOf(
                            "name" to artifact.moduleVersion.toString(),
                            "file" to "loader://META-INF/jars/${id.name}-${id.version}.jar"
                        )
                    )
                    processed.add(artifact.moduleVersion.toString())
                    continue
                }

                // Otherwise, we need to get the repository URL that the dep is located at.
                for (repo in project.repositories) {
                    val mavenRepo = repo as MavenArtifactRepository

                    if (artifact.moduleVersion.toString() in processed) {
                        continue
                    }

                    val url = mavenRepo.url

                    try {
                        val jarUrl = String.format(
                            "%s%s/%s/%s/%s-%s.jar",
                            url.toString(),
                            id.group.replace(".", "/"),
                            id.name,
                            id.version,
                            id.name,
                            id.version
                        )

                        // Check if the repository is valid.
                        val jarFile = URL(jarUrl).openStream()
                        val correctRepo = jarFile != null
                        jarFile.close()

                        if (correctRepo) {
                            val library = mutableMapOf<String, Any>(
                                "name" to artifact.moduleVersion.toString(),
                                "url" to url.toString()
                            )
                            library.putAll(resolveHashes(artifact.moduleVersion.toString()))
                            libraries[side]?.add(library)

                            // Add it to a processed list to prevent duplicates.
                            processed.add(artifact.moduleVersion.toString())
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        json.putAll(options.get())
        outputFile.get().asFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)))
    }

    fun resolveHashes(artifact: String): Map<String, Any> {
        return mapOf<String, Any?>(
            "md5" to resolveHash(artifact, "md5", false),
            "sha1" to resolveHash(artifact, "sha1", true),
            "sha256" to resolveHash(artifact, "sha256", false),
            "sha512" to resolveHash(artifact, "sha512", false),
            "size" to resolveSize(artifact)
        ).filterValues { it != null } as Map<String, Any>
    }

    fun resolveHash(artifact: String, hash: String, required: Boolean): String? {
        try {
            val config = project.configurations.detachedConfiguration(
                project.dependencies.create("${artifact}@jar.${hash}")
            )
            return config.singleFile.readText()
        } catch (_: ResolveException) {
            val msg = "$hash hash was required but not found for artifact $artifact"
            if (required) {
                // e.printStackTrace()
                throw RuntimeException(msg)
            }
        }

        return null
    }

    fun resolveSize(artifact: String): Long {
        val config = project.configurations.detachedConfiguration(
            project.dependencies.create("${artifact}@jar")
        )
        return config.singleFile.length()
    }
}
