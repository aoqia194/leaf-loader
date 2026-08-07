pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net")
        }
        maven {
            name = "aoqia"
            url = uri("https://maven.aoqia.dev/releases")

            mavenContent {
                releasesOnly()
            }
        }
        maven {
            name = "aoqia-snapshots"
            url = uri("https://maven.aoqia.dev/snapshots")

            mavenContent {
                snapshotsOnly()
            }
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
	throw UnsupportedOperationException("Leaf Loader requires Java 21+ to build.")
}

// Because Gradle makes it read-only in build scripts
val name: String by settings
rootProject.name = name

include("junit")
include("zomboid")

if (!providers.gradleProperty("leaf.loom.minimalSetup").orNull.toBoolean()) {
    include("zomboid:zomboid-test")
}
