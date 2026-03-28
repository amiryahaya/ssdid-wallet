plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

android {
    namespace = "my.ssdid.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Network
    api("com.squareup.retrofit2:retrofit:2.11.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Crypto
    api("org.bouncycastle:bcprov-jdk18on:1.80")

    // DataStore (default storage)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager (default sync)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Biometric (default authenticator)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Coroutines (api — SDK interfaces use suspend functions)
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation(project(":sdk:ssdid-core-testing"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = findProperty("SDK_GROUP") as String? ?: "my.ssdid.sdk"
                artifactId = "ssdid-core"
                version = findProperty("SDK_VERSION") as String? ?: "0.1.0"

                pom {
                    name.set("SSDID Core SDK")
                    description.set("Self-sovereign decentralized identity SDK with post-quantum cryptography support")
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
