pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net")
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

// FIXME(leaf): Add back mixinextras subproject if needed.
include("junit")
include("zomboid")
// FIXME(leaf): Uncomment when loom is ready
//include("zomboid:zomboid-test")
