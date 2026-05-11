val mainSourceSetOutput by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
}

dependencies {
    api(project(":"))

    // Logger wrapper
    compileOnly(libs.log4j.api)
    compileOnly(libs.slf4j.api)

    // implementation("net.sf.jopt-simple:jopt-simple:5.0.3")

    // Unit testing for semver
    implementation(rootProject.sourceSets.main.get().output)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platformlauncher)
    testImplementation(libs.gson)
}

sourceSets {
    main {
        java.srcDirs("src/main/java", "src/main/legacyJava")
    }
}

artifacts {
    val main = sourceSets.main.get()
    main.output.classesDirs.forEach {
        println("Adding dir (${it}) to mainSourceSetOutput!")
        add(mainSourceSetOutput.name, it) {
            builtBy(tasks.compileJava)
        }
    }
    println("Adding dir (${main.output.resourcesDir}) to mainSourceSetOutput!")
    add(mainSourceSetOutput.name, main.output.resourcesDir!!) {
        builtBy(tasks.processResources)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (JavaVersion.current().isJava9Compatible) {
        options.release = 8
    }
}

tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}
