plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "my.ssdid.sdk.testing"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":sdk:ssdid-core"))
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = findProperty("SDK_GROUP") as String? ?: "my.ssdid.sdk"
                artifactId = "ssdid-core-testing"
                version = findProperty("SDK_VERSION") as String? ?: "0.1.0"

                pom {
                    name.set("SSDID Core Testing SDK")
                    description.set("Test doubles and utilities for the SSDID SDK")
                    url.set("https://github.com/amiryahaya/ssdid-wallet")
                    licenses {
                        license {
                            name.set("Proprietary")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/amiryahaya/ssdid-wallet")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String? ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.token") as String? ?: ""
                }
            }
        }
    }
}
