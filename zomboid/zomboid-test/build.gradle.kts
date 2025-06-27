import groovy.json.JsonSlurper

val zomboidVersion: String by project
val zomboidMappings: String by project

plugins {
    alias(libs.plugins.loom)
}

loom {
    clientOnlyZomboidJar()

    runConfigs.configureEach {
        isIdeConfigGenerated = true
        property("leaf.debug.replaceVersion", "leafloader:${version}")
    }
}

repositories {
    mavenCentral()
}

configurations.register("productionRuntime") {
    extendsFrom(configurations.zomboidLibraries.get())
    extendsFrom(configurations.loaderLibraries.get())
    extendsFrom(configurations.zomboidRuntimeLibraries.get())
}

configurations.register("productionRuntimeMods") {
    isTransitive = false
}

dependencies {
    "zomboid"("com.theindiestone:zomboid:${zomboidVersion}")
    "mappings"("dev.aoqia.leaf:yarn:${zomboidMappings}:v2")

    implementation(project(":zomboid"))
    implementation(project(":zomboid", "mainSourceSetOutput"))
    implementation(project(":", "mainSourceSetOutput"))
    implementation(project(":mixinextras", "shadow"))

    testImplementation(project(":junit"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Include the external libraries on the classpath
    val installerJson = JsonSlurper().parse(
        rootProject.file("src/main/resources/leaf-installer.json")
    ) as Map<String, Any>
    val libraries = installerJson["libraries"] as Map<String, List<Map<String, Any>>>
    libraries["common"]!!.map { "productionRuntime"(it["name"].toString()) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(":mixinextras:shadowJar")

    options.encoding = "UTF-8"
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}
