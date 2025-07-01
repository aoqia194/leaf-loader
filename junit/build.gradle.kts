plugins {
    `maven-publish`
}

base {
	archivesName = "${rootProject.name}-junit"
}

version = rootProject.version
group = rootProject.group

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

publishing {
	publications {
		register<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.base.archivesName.get()
            version = project.version.toString()

            pom {
                name = rootProject.name
                group = rootProject.group.toString()
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
                    url = "${property("url")}/issues"
                }

                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://spdx.org/licenses/Apache-2.0.html"
                    }
                }

                scm {
                    connection = "scm:git:${property("url").toString()}.git"
                    developerConnection = "scm:git:${property("url").toString().replace("https", "ssh")}.git"
                    url = rootProject.property("url").toString()
                }
            }

            artifact(tasks.jar)
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))
		}
	}

    repositories {
        maven {
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }
    }
}
