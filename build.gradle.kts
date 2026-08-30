import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.aoqia.leaf.loom.build.nesting.JarNester
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChildren
import proguard.gradle.ProGuardTask
import java.io.FileNotFoundException
import java.net.URI

val isCiBuild = providers.environmentVariable("CI").map { it.toBoolean() }.orElse(false).get()
val isSnapshot = providers.gradleProperty("isSnapshot").map { it.toBoolean() }.orElse(false).get()

var groupUrl = rootProject.group.toString().replace(".", "/")

val baseVersion = project.version.toString()
project.version = if (isSnapshot) "$baseVersion-SNAPSHOT" else if (!isCiBuild) "$baseVersion.local" else baseVersion

val proguardTmpFile = file("build/tmp/loader-${version}.jar")

buildscript {
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.7.0")
    }
}

plugins {
    java
    `java-library`
    eclipse

    // checkstyle
//    alias(libs.plugins.spotless)

    alias(libs.plugins.shadow)
    alias(libs.plugins.loom) apply false

    // Publishing to Maven Central
    `maven-publish`
    signing

    id("installerjson")
}

base {
    archivesName = project.name
}

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "eclipse")
//    apply(plugin = "com.diffplug.spotless")

    version = rootProject.version

    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral {
            content {
                // Force ASM to come from the fabric maven.
                // This ensures that the version has been mirrored for use by the launcher/installer.
                excludeGroupByRegex("org.ow2.asm")
                 excludeGroupByRegex("io.github.llamalad7")
            }
        }
    }

    dependencies {
        compileOnly("org.jetbrains:annotations:23.0.0")
    }

//    spotless {
//        java {
//            licenseHeaderFile(rootProject.file("HEADER"))
//            targetExclude("**/lib/gson/*.java")
//        }
//    }
}

val mainSourceSetOutput = configurations.dependencyScope("mainSourceSetOutput")

val include = configurations.dependencyScope("include") {
    isTransitive = false
}

val installer = configurations.dependencyScope("installer") {
    isTransitive = false
}

val development = configurations.dependencyScope("development") {
    isTransitive = false
}

configurations.implementation {
    extendsFrom(include.get())
}

configurations.api {
    extendsFrom(installer.get())
    extendsFrom(development.get())
}

dependencies {
    // leaf-loader dependencies
    "installer"(libs.bundles.asm)
    "installer"(libs.mixin)
    "installer"(libs.apache.commons.codec)

    "development"(libs.mixinextras)

    // impl dependencies
    "include"(libs.bundles.sat4j)
    "include"(libs.tinyremapper)
    "include"(libs.clazztweaker)
    "include"(libs.mappingio)

    testCompileOnly(libs.annotations)

    // Unit testing for mod metadata
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platformlauncher)

    testImplementation(libs.mockito.core)
}

sourceSets {
    main {
        java.srcDirs("src/main/java", "src/main/legacyJava")
    }

    register("java17")
}

artifacts {
    val main = sourceSets.main.get()
    main.output.classesDirs.forEach {
        add(mainSourceSetOutput.name, provider { it }) {
            builtBy(tasks.compileJava)
        }
    }
    add(mainSourceSetOutput.name, provider { main.output.resourcesDir }) {
        builtBy(tasks.processResources)
    }
}

java {
    withSourcesJar()
    // Added separately for some reason..
    // withJavadocJar()

    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.build {
    dependsOn(generateBuildInfo)
    dependsOn(finalJar)
    dependsOn(javadocJar)
}

tasks.compileJava {
    dependsOn(generateBuildInfo)
}

tasks.processResources {
    dependsOn(copyJson)

    inputs.property("version", project.version)

    filesMatching("leaf.mod.json") {
        expand("version" to inputs.properties["version"].toString().replace(".local", ""))
    }
}

tasks.jar {
    enabled = false
    // Set the classifier to fix gradle task validation confusion.
    archiveClassifier = "disabled"
}

tasks.shadowJar {
    // Has stupid defaults, make our own.
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}

tasks.publish {
    mustRunAfter(checkVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = if (name.contains("Java17")) 17 else 8
}

// Causes more trouble than its worth.
tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Workaround for https://youtrack.jetbrains.com/issue/KT-46466
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}

tasks.withType<Sign>().configureEach {
    enabled = isCiBuild && !isSnapshot
}

val generatedDir = layout.projectDirectory.dir("src/${sourceSets.main.name}/generated/")
val generateBuildInfo = tasks.register("generateBuildInfo") {
    description = "Generates build info used by loader classes"

    outputs.dir(generatedDir)

    doLast {
        val file = generatedDir.asFile.resolve("dev/aoqia/leaf/loader/impl/util/BuildInfo.java")
        file.parentFile.mkdirs()
        file.writeText(
            """
            package dev.aoqia.leaf.loader.impl.util;
            
            public final class BuildInfo {
                public static final String VERSION = "$baseVersion";
                private BuildInfo() {}
            }
            """.trimIndent()
        )
    }
}

sourceSets.main {
    java.srcDir(generateBuildInfo)
}

val getLoaderVersion = tasks.register("getLoaderVersion") {
    description = "A task to get the raw loader version, used for GitHub workflows."
    println(version)
}

// Renaming in the shadow jar task doesnt seem to work, so do it here
val getSat4jAbout = tasks.register<Copy>("getSat4jAbout") {
    description = "_"

    dependsOn(include.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        include.get().map {
            zipTree(it).matching {
                include("about.html")
            }
        }
    })
    rename("about.html", "${groupUrl}/${project.name}/impl/lib/sat4j/about-sat4j.html")

    into(layout.buildDirectory.dir("sat4j"))
}

val fatJar = tasks.register<ShadowJar>("fatJar") {
    description = "Creates a fat jar"

    dependsOn(getSat4jAbout)

    from(sourceSets.main.get().output)
    from(project(":zomboid").sourceSets.main.get().output)
    from(getSat4jAbout.map { it.outputs.files })

    inputs.property("archivesName", project.base.archivesName.get())
    from("LICENSE") {
        rename { "${it}_${inputs.properties["archivesName"]}" }
    }

    manifest {
        attributes(
            "Main-Class" to "${project.group}.${project.name}.impl.launch.server.LeafServerLauncher",
            "Leaf-Loom-Remap" to "false",
            "Automatic-Module-Name" to "${project.group}.${project.name}",
            "Multi-Release" to "true"
        )
    }

    archiveClassifier = "fat"
    configurations = listOf(include.get())

    relocate("org.sat4j", "${project.group}.${project.name}.impl.lib.sat4j")
    relocate("net.fabricmc.classtweaker", "${project.group}.${project.name}.impl.lib.classtweaker")
    relocate("net.fabricmc.tinyremapper", "${project.group}.${project.name}.impl.lib.tinyremapper")
    relocate("net.fabricmc.mappingio", "${project.group}.${project.name}.impl.lib.mappingio")

    exclude("about.html")
    exclude("sat4j.version")
    exclude("META-INF/maven/org.ow2.sat4j/*/**")
    exclude("META-INF/*.RSA")
    exclude("META-INF/*.SF")

    inputs.files(development.get())
    val devFiles = development.get().files

    doLast {
        JarNester.nestJars(devFiles, archiveFile.get().asFile)
    }

    outputs.upToDateWhen { false }
}

val proguardJar = tasks.register<ProGuardTask>("proguardJar") {
    dependsOn(fatJar)

    val classpath = project(":zomboid").configurations.compileClasspath.get()

    inputs.files(fatJar, classpath)
    outputs.files(proguardTmpFile)

    doFirst {
        classpath.resolve().forEach {
            libraryjars(it)
        }
    }

    val java8 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(8) }.get()
    libraryjars(java8.metadata.installationPath.file("jre/lib/rt.jar"))

    injars(fatJar.get().archiveFile.get())
    outjars(proguardTmpFile)
    configuration(file("proguard.conf"))
}

// As proguard does not support MRJ's we must add the MRJ classes to the final jar
// Use a Zip task to not alter the manifest
val finalJar = tasks.register<Zip>("finalJar") {
    dependsOn(proguardJar)

    destinationDirectory = file("build/libs")
    archiveExtension = "jar"

    from(zipTree(proguardTmpFile))
    into("META-INF/versions/17") {
        from(sourceSets.named("java17").get().output)
    }
}

val sourcesJar = tasks.named<Jar>("sourcesJar") {
    description = "Creates the sources jar"

    // Need to depend on JAR task because otherwise Gradle gets funky with the task graph.
    dependsOn(tasks.jar)

    from(sourceSets.main.get().allSource)
    from(project(":zomboid").sourceSets.main.get().allSource)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val testJar = tasks.register<Jar>("testJar") {
    description = "A useful task for creating a test mod jar"

    archiveClassifier = "test"
    from(sourceSets.test.get().output)
}

val copyJson = tasks.register("copyJson") {
    dependsOn(tasks.generateInstallerJson)

    val inJson = tasks.generateInstallerJson.get().outputFile.get().asFile
    val outJson = file("build/libs/${project.base.archivesName.get()}-${version}.json")

    inputs.files(inJson)
    outputs.files(outJson)

    doLast {
        outJson.writeText(inJson.readText())
    }
}

val javadoc = tasks.named<Javadoc>("javadoc") {
    (options as StandardJavadocDocletOptions).apply {
        if (file("README.html").exists()) {
            overview = "README.html"
        }

        source = "8"
        encoding = "UTF-8"
        docEncoding = "UTF-8"
        charSet = "UTF-8"
        memberLevel = JavadocMemberLevel.PACKAGE
        links(
            "https://asm.ow2.io/javadoc/",
            "https://docs.oracle.com/javase/8/docs/api/",
            "https://logging.apache.org/log4j/2.x/javadoc/log4j-api/"
        )
        // Disable the crazy super-strict doclint tool in Java 8.
        addStringOption("Xdoclint:none", "-quiet")
    }
    source(sourceSets.main.get().allJava.srcDirs)
    // Compile impl for dep as well.
    classpath = sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    include("**/api/**")
    // A workaround as one of the APIs use that package.
    isFailOnError = false
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn(javadoc)

    archiveClassifier = "javadoc"
    from(javadoc.get().destinationDir)
}

/*
 * A task to ensure that the version being released has not already been released.
 */
val checkVersion = tasks.register("checkVersion") {
    doFirst {
        val xml = try {
            URI.create("https://maven.aoqia.dev/${if (isSnapshot) "snapshots" else "releases"}/${
                rootProject.group.toString().replace(".", "/")
            }/${rootProject.name}/maven-metadata.xml").toURL().readText()
        } catch (_: FileNotFoundException) {
            null
        }

        if (xml != null) {
            val metadata = XmlSlurper().parseText(xml)

            val versioning = metadata.getProperty("versioning") as GPathResult
            val versions = versioning.getProperty("versions") as GPathResult
            val versionText = (versions.getProperty("version") as NodeChildren).map { it.toString() }

            if (versionText.contains(version)) {
                throw RuntimeException("$version has already been released!")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            artifact(finalJar)
            artifact(sourcesJar)
            artifact(javadocJar)

            artifact(tasks.generateInstallerJson) {
                builtBy(copyJson)
            }

            pom {
                name = rootProject.name
                group = rootProject.group
                description = rootProject.description
                url = property("url").toString()
                inceptionYear = "2025"

                developers {
                    developer {
                        id = "aoqia"
                        name = "aoqia"
                        email = "aoqia@aoqia.dev"
                    }
                }

                issueManagement {
                    system = "GitHub"
                    url = "${property("url").toString()}/issues"
                }

                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://spdx.org/licenses/Apache-2.0.html"
                    }
                }

                scm {
                    connection = "scm:git:${property("url").toString()}.git"
                    developerConnection = "scm:git:${property("url").toString().replace("https", "ssh")}.git"
                    url = property("url").toString()
                }
            }
        }
    }

    repositories {
        maven {
            name = "leaf"
            url = uri("https://maven.aoqia.dev/${if (isSnapshot) "snapshots" else "releases"}")

            credentials {
                username = providers.gradleProperty("mavenUsername").orNull
                password = providers.gradleProperty("mavenPassword").orNull
            }

            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

signing {
    isRequired = isCiBuild and !isSnapshot

    val signingKey = providers.gradleProperty("signingKey")
    val signingPassword = providers.gradleProperty("signingPassword")
    if (signingKey.isPresent && signingPassword.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.get())
    }

    sign(publishing.publications)
}
