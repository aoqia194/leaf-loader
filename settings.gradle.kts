rootProject.name = "loader"

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.fabricmc.net")
            name = "FabricMC"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

include("mixinextras", "zomboid", "junit", "zomboid:zomboid-test")

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
	throw UnsupportedOperationException("Leaf Loader requires Java 17+ to build.")
}
