plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "my.ssdid.sdk.pqc"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":sdk:ssdid-core"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = findProperty("SDK_GROUP") as String? ?: "my.ssdid.sdk"
                artifactId = "ssdid-pqc"
                version = findProperty("SDK_VERSION") as String? ?: "0.1.0"

                pom {
                    name.set("SSDID PQC SDK")
                    description.set("Post-quantum cryptography module for the SSDID SDK")
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
