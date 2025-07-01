version = libs.versions.mixinextras.get()

plugins {
    java
    alias(libs.plugins.shadow)
}

configurations.implementation {
    extendsFrom(configurations.shadow.get())
}

dependencies {
    compileOnly(libs.bundles.asm)

    compileOnly(libs.mixin) {
        exclude(module = "guava")
    }

    shadow(libs.mixinextras)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.jar {
    enabled = false

    archiveBaseName = project.name
    archiveVersion = project.version.toString()
    manifest.attributes("Leaf-Loom-Remap" to "false")
}

tasks.shadowJar {
    dependsOn(tasks.jar)

    archiveBaseName = project.name
    archiveClassifier = ""
    archiveVersion = project.version.toString()

    configurations = listOf(project.configurations.shadow.get())
    // I think I will keep it in the default 'namespace' unless issues arise.
    // relocate('com.llamalad7.mixinextras', 'dev.aoqia.leaf.mixinextras')
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    inputs.properties("id" to project.name, "version" to project.version)

    filesMatching("leaf.mod.json") {
        expand("id" to project.name, "version" to project.version.toString())
    }
}
