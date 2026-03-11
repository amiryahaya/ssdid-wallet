import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("kapt")
    id("org.jetbrains.kotlinx.kover")
    id("io.sentry.android.gradle")
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

val onesignalAppId = localProps.getProperty("onesignal.appId", System.getenv("ONESIGNAL_APP_ID") ?: "")
val sentryDsn = localProps.getProperty("sentry.dsn", System.getenv("SENTRY_DSN") ?: "")
if (sentryDsn.isBlank() && System.getenv("CI") != null) {
    logger.warn("WARNING: SENTRY_DSN is not set. Release build will have no crash reporting.")
}

val sentryEnv = localProps.getProperty(
    "sentry.environment",
    System.getenv("SENTRY_ENVIRONMENT") ?: "production"
)

val emailVerifyUrl = localProps.getProperty(
    "emailVerify.url",
    System.getenv("EMAIL_VERIFY_URL") ?: "https://email-verify.ssdid.my"
)

android {
    namespace = "my.ssdid.wallet"
    compileSdk = 35

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                val ksFile = localProps.getProperty("signing.storeFile")
                if (ksFile != null) {
                    storeFile = file(ksFile)
                    storePassword = localProps.getProperty("signing.storePassword")
                    keyAlias = localProps.getProperty("signing.keyAlias")
                    keyPassword = localProps.getProperty("signing.keyPassword")
                }
            }
        }
    }

    defaultConfig {
        applicationId = "my.ssdid.wallet"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"$onesignalAppId\"")
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
        buildConfigField("String", "SENTRY_ENVIRONMENT", "\"$sentryEnv\"")
        buildConfigField("String", "EMAIL_VERIFY_URL", "\"$emailVerifyUrl\"")
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.all {
            it.useJUnit()
            if (System.getenv("CI") != null) {
                it.exclude("my/ssdid/wallet/integration/**")
            }
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    kapt("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // AppCompat (for LocalizationManager / AppCompatDelegate locale support)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Camera (QR scan)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Crypto
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // OneSignal
    implementation("com.onesignal:OneSignal:5.1.20")

    // Sentry
    implementation("io.sentry:sentry-android:7.22.0")
    implementation("io.sentry:sentry-okhttp:7.22.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

sentry {
    org.set("ssdid")
    projectName.set("ssdid-wallet-android")
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(false)
    includeNativeSources.set(false)
    includeSourceContext.set(false)
    autoUploadSourceContext.set(false)
    tracingInstrumentation {
        enabled.set(true)
    }
    autoInstallation {
        enabled.set(false)
    }
}
