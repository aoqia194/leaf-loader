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
