import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.aoqia.leaf.loom.build.nesting.JarNester
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChildren
import org.gradle.kotlin.dsl.provideDelegate
import org.jreleaser.model.Active
import org.jreleaser.model.Http
import org.slf4j.LoggerFactory
import proguard.gradle.ProGuardTask
import java.net.URL

val env = System.getenv()!!
val isCiEnv = env["CI"].toBoolean()
val gpgKeyPassphrase = env["GPG_PASSPHRASE_KEY"]
val gpgKeyPublic = env["GPG_PUBLIC_KEY"]
val gpgKeyPrivate = env["GPG_PRIVATE_KEY"]
val mavenUsername = env["MAVEN_USERNAME"]
val mavenPassword = env["MAVEN_PASSWORD"]

val proguardTmpFile = file("build/tmp/loader-${version}.jar")

buildscript {
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

plugins {
    java
    `java-library`
    `maven-publish`
    id("eclipse")

    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.jreleaser)

    id("installerjson")
}

base {
    archivesName = project.name
}

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "eclipse")
    apply(plugin = "com.diffplug.spotless")

    val constantsSource =
        rootProject.file("src/main/java/dev/aoqia/leaf/loader/impl/LeafLoaderImpl.java").readText()
    version =
        Regex("""\s+VERSION\s*=\s*"(.*)";""").find(constantsSource)!!.groupValues[1] + if (isCiEnv) "" else ".local"

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
                // excludeGroupByRegex("io.github.llamalad7")
            }
        }
    }

    dependencies {
        compileOnly("org.jetbrains:annotations:23.0.0")
    }

    spotless {
        java {
            licenseHeaderFile(rootProject.file("HEADER"))
            targetExclude("**/lib/gson/*.java")
        }
    }
}

// Disable zomboid-test Java code from compiling if CI and ignoreMissingFiles.
project(":zomboid:zomboid-test") {
    tasks.compileJava {
        onlyIf {
            !isCiEnv || (isCiEnv && !project.hasProperty("leaf.loom.ignoreMissingFiles"))
        }
    }

    tasks.test {
        onlyIf {
            !isCiEnv || (isCiEnv && !project.hasProperty("leaf.loom.ignoreMissingFiles"))
        }
    }
}

val mainSourceSetOutput by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val include by configurations.registering {
    isTransitive = false
}

val installer by configurations.registering {
    isTransitive = false
}

val development by configurations.registering {
    isTransitive = false
    isVisible = false
}

configurations.implementation {
    extendsFrom(include.get())
}

configurations.api {
    extendsFrom(installer.get())
    // extendsFrom(development)
}

dependencies {
    // leaf-loader dependencies
    "installer"(libs.bundles.asm)
    "installer"(libs.mixin)

    // impl dependencies
    "include"(libs.bundles.sat4j)
    "include"(libs.tinyremapper)
    "include"(libs.accesswidener)
    "include"(libs.mappingio)

    // We JiJ this into the launcher jar directly! (thanks llamalad7 for the help)
    "development"(project(":mixinextras", "shadow"))

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

tasks {
    build {
        dependsOn(finalJar)
        dependsOn(javadocJar)
    }

    processResources {
        dependsOn(copyJson)

        inputs.property("version", project.version)

        filesMatching("leaf.mod.json") {
            expand("version" to project.version.toString().replace(".local", ""))
        }
    }

    jar {
        enabled = false
        // Set the classifier to fix gradle task validation confusion.
        archiveClassifier = "disabled"
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        // Has stupid defaults, make our own.
        enabled = false
    }

    publish {
        mustRunAfter(checkVersion)
    }
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

/**
 * A task to get the raw loader version, used for GitHub workflows.
 */
val getLoaderVersion by tasks.registering {
    println(version)
}

// Renaming in the shadow jar task doesnt seem to work, so do it here
val getSat4jAbout by tasks.registering(Copy::class) {
    dependsOn(include.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        include.get().map {
            zipTree(it).matching {
                include("about.html")
            }
        }
    })
    rename("about.html", "dev/aoqia/leaf/loader/impl/lib/sat4j/about-sat4j.html")

    into(layout.buildDirectory.dir("sat4j"))
}

val fatJar by tasks.registering(ShadowJar::class) {
    dependsOn(project(":mixinextras").tasks.shadowJar)
    dependsOn(getSat4jAbout)

    from(sourceSets.main.get().output)
    from(project(":zomboid").sourceSets.main.get().output)
    from(getSat4jAbout.map { it.outputs.files })
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }

    manifest {
        attributes(
            "Main-Class" to "${project.group}.loader.impl.launch.server.LeafServerLauncher",
            "Leaf-Loom-Remap" to "false",
            "Automatic-Module-Name" to "${project.group}.loader",
            "Multi-Release" to "true"
        )
    }

    archiveClassifier = "fat"
    configurations = listOf(include.get())

    relocate("org.sat4j", "${project.group}.loader.impl.lib.sat4j")
    relocate("net.fabricmc.accesswidener", "${project.group}.loader.impl.lib.accesswidener")
    relocate("net.fabricmc.tinyremapper", "${project.group}.loader.impl.lib.tinyremapper")
    relocate("net.fabricmc.mappingio", "${project.group}.loader.impl.lib.mappingio")

    exclude("about.html")
    exclude("sat4j.version")
    exclude("META-INF/maven/org.ow2.sat4j/*/**")
    exclude("META-INF/*.RSA")
    exclude("META-INF/*.SF")

    doLast {
        JarNester.nestJars(
            development.get().files,
            archiveFile.get().asFile,
            LoggerFactory.getLogger("JiJ")
        )
    }

    outputs.upToDateWhen { false }
}

val proguardJar by tasks.registering(ProGuardTask::class) {
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
val finalJar by tasks.registering(Zip::class) {
    dependsOn(proguardJar)

    destinationDirectory = file("build/libs")
    archiveExtension = "jar"

    from(zipTree(proguardTmpFile))
    into("META-INF/versions/17") {
        from(sourceSets.named("java17").get().output)
    }
}

val sourcesJar = tasks.named<Jar>("sourcesJar") {
    // Need to depend on JAR task because otherwise Gradle gets funky with the task graph.
    dependsOn(tasks.jar)

    from(sourceSets.main.get().allSource)
    from(project(":zomboid").sourceSets.main.get().allSource)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Useful for creating a test mod jar.
val testJar by tasks.registering(Jar::class) {
    archiveClassifier = "test"
    from(sourceSets.test.get().output)
}

val copyJson by tasks.registering {
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

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(javadoc)

    archiveClassifier = "javadoc"
    from(javadoc.get().destinationDir)
}

/*
 * A task to ensure that the version being released has not already been released.
 */
val checkVersion by tasks.registering {
    doFirst {
        val xml = URL(
            "https://repo.maven.apache.org/maven2/${
                rootProject.group.toString().replace(".", "/")
            }/${rootProject.name}/maven-metadata.xml"
        ).readText()
        val metadata = XmlSlurper().parseText(xml)

        val versioning = metadata.getProperty("versioning") as GPathResult
        val versions = versioning.getProperty("versions") as GPathResult
        val versionText = (versions.getProperty("version") as NodeChildren).map { it.toString() }

        if (versionText.contains(version)) {
            throw RuntimeException("$version has already been released!")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

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
                    developerConnection =
                        "scm:git:${property("url").toString().replace("https", "ssh")}.git"
                    url = property("url").toString()
                }
            }

            artifact(finalJar)
            artifact(sourcesJar)
            artifact(javadocJar)

            artifact(tasks.generateInstallerJson) {
                builtBy(copyJson)
            }
        }
    }

    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    project {
        name = rootProject.name
        version = rootProject.version.toString()
        versionPattern = "SEMVER"
        authors = listOf("aoqia194", "FabricMC")
        maintainers = listOf("aoqia194")
        license = "Apache-2.0"
        inceptionYear = "2025"

        links {
            homepage = property("url").toString()
            license = "https://spdx.org/licenses/Apache-2.0.html"
        }
    }

    signing {
        active = Active.ALWAYS
        armored = true
        passphrase = gpgKeyPassphrase
        publicKey = gpgKeyPublic
        secretKey = gpgKeyPrivate
    }

    deploy {
        maven {
            pomchecker {
                version = "1.14.0"
                failOnWarning = false // annoying
                failOnError = true
                strict = true
            }

            mavenCentral {
                create("sonatype") {
                    applyMavenCentralRules = true
                    active = Active.ALWAYS
                    snapshotSupported = true
                    authorization = Http.Authorization.BEARER
                    username = mavenUsername
                    password = mavenPassword
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                    verifyUrl = "https://repo1.maven.org/maven2/{{path}}/{{filename}}"
                    namespace = rootProject.group.toString()
                    retryDelay = 60
                    maxRetries = 30
                }
            }
        }
    }

    release {
        github {
            enabled = true
            repoOwner = "aoqia194"
            name = "leaf-loader"
            host = "github.com"
            releaseName = "{{tagName}}"
            sign = true
            overwrite = true

            changelog {
                formatted = Active.ALWAYS
                preset = "conventional-commits"
                extraProperties.put("categorizeScopes", "true")
            }
        }
    }
}
