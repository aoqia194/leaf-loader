val isCiBuild = providers.environmentVariable("CI").map { it.toBoolean() }.orElse(false).get()
val isSnapshot = providers.gradleProperty("isSnapshot").map { it.toBoolean() }.orElse(false).get()

version = rootProject.version
group = rootProject.group

plugins {
    `maven-publish`
    signing
}

base {
    archivesName = "${rootProject.name}-junit"
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":"))

    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.engine)
    implementation(libs.junit.platformlauncher)
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "${project.group}.loader.junit")
    }
}

// Workaround for https://youtrack.jetbrains.com/issue/KT-46466
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}

tasks.withType<Sign>().configureEach {
    enabled = isCiBuild && !isSnapshot
}

publishing {
    publications {
        create<MavenPublication>("junitMaven") {
            groupId = project.group.toString()
            artifactId = project.base.archivesName.get()
            version = project.version.toString()

            artifact(tasks.jar)
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))

            pom {
                name = rootProject.name
                group = rootProject.group
                description = rootProject.description
                url = property("url").toString()
                inceptionYear = "2024"

                developers {
                    developer {
                        id = "aoqia"
                        name = "aoqia"
                        email = "aoqia@aoqia.dev"
                    }
                }

                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://spdx.org/licenses/Apache-2.0.html"
                    }
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
