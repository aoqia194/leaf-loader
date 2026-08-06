import groovy.json.JsonSlurper

val zomboidVersion: String by project

plugins {
    alias(libs.plugins.loom)
}

loom {
    runConfigs.configureEach {
        isIdeConfigGenerated = true
        property("leaf.debug.replaceVersion", "leafloader:${version}")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    "zomboid"("com.theindiestone:zomboid:${zomboidVersion}")

    implementation(project(":zomboid"))
    implementation(project(":zomboid", "mainSourceSetOutput"))
    implementation(project(":", "mainSourceSetOutput"))

//    annotationProcessor(libs.bundles.asm)

    // If we ever do remapping again, ow2.asm and mixin AP needs to be added here too.
//    annotationProcessor(libs.mixin) {
//        exclude(module = "launchwrapper")
//        exclude(module = "guava")
//    }
//    annotationProcessor(libs.mixinextras)

    testImplementation(project(":junit"))
    testRuntimeOnly(libs.junit.platformlauncher)

    // Include the external libraries on the classpath
    val installerJson = JsonSlurper().parse(
        rootProject.file("src/main/resources/leaf-installer.json")
    ) as Map<String, Any>
    val libraries = installerJson["libraries"] as Map<String, List<Map<String, Any>>>
    libraries["common"]!!.map { "zomboidTestClientRuntimeLibraries"(it["name"].toString()) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}
