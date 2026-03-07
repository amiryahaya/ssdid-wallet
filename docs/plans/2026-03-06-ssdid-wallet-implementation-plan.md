# SSDID Mobile App Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build native SSDID identity wallet apps for Android, iOS, and HarmonyOS NEXT with KAZ-Sign PQC crypto, hardware-backed key storage, biometric auth, and full SSDID protocol support.

**Architecture:** 3 native codebases sharing the same 4-layer architecture (UI → Feature → Domain → Platform). Android is the reference implementation built first with full TDD. iOS and HarmonyOS follow the same domain patterns ported to Swift/ArkTS.

**Tech Stack:**
- Android: Kotlin, Jetpack Compose, Hilt, Retrofit, CameraX, Android Keystore
- iOS: Swift, SwiftUI, Combine, URLSession, AVFoundation, Secure Enclave
- HarmonyOS NEXT: ArkTS, ArkUI, N-API for KAZ-Sign C FFI, HUKS

**Reference:** Design doc at `docs/plans/2026-03-06-ssdid-wallet-app-design.md`
**Backend spec:** `/Users/amirrudinyahaya/Workspace/SSDID/docs/`
**KAZ-Sign C lib:** `/Users/amirrudinyahaya/Workspace/PQC-KAZ/SIGN/`
**Existing bindings:** `/Users/amirrudinyahaya/Workspace/PQC-KAZ/SIGN/bindings/`

---

## Phase 1: Android (Reference Implementation)

### Task 1: Project Scaffolding

**Files:**
- Create: `android/` (Android Studio project root)
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`

**Step 1: Create Android project with Compose**

```bash
cd /Users/amirrudinyahaya/Workspace/ssdid-wallet
mkdir -p android
```

Create `android/settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ssdid-wallet"
include(":app")
```

Create `android/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.53.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
```

Create `android/app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("kapt")
}

android {
    namespace = "my.ssdid.wallet"
    compileSdk = 35
    defaultConfig {
        applicationId = "my.ssdid.wallet"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }
}

dependencies {
    // Compose
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

    // Biometric
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Camera (QR scan)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Crypto
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

**Step 2: Create package structure**

```bash
mkdir -p android/app/src/main/java/my/ssdid/wallet/{domain/{model,crypto,vault,verifier,transport},feature/{onboarding,identity,credentials,scan,registration,auth,transaction,history,settings},platform/{keystore,biometric,deeplink,i18n}}
mkdir -p android/app/src/main/cpp
mkdir -p android/app/src/main/res/{values,values-ms,values-zh}
mkdir -p android/app/src/test/java/my/ssdid/wallet/{domain,feature}
```

**Step 3: Create Application class with Hilt**

Create `android/app/src/main/java/my/ssdid/wallet/SsdidApp.kt`:
```kotlin
package my.ssdid.wallet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SsdidApp : Application()
```

**Step 4: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:name=".SsdidApp"
        android:label="SSDID"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <!-- Deep link -->
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="ssdid" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Step 5: Verify project builds**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add android/
git commit -m "feat(android): scaffold project with Compose, Hilt, Retrofit, CameraX"
```

---

### Task 2: Domain Models (DID, DID Document, VC, Proof)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/model/Did.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/model/DidDocument.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/model/VerifiableCredential.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/model/Proof.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/model/Algorithm.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/model/Identity.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/model/DidTest.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/model/DidDocumentTest.kt`

**Step 1: Write failing test for DID generation**

Create `android/app/src/test/java/my/ssdid/wallet/domain/model/DidTest.kt`:
```kotlin
package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class DidTest {
    @Test
    fun `generate creates valid did-ssdid format`() {
        val did = Did.generate()
        assertThat(did.value).startsWith("did:ssdid:")
        val methodSpecificId = did.value.removePrefix("did:ssdid:")
        // Base64url decoded should be 16 bytes
        val decoded = Base64.getUrlDecoder().decode(methodSpecificId)
        assertThat(decoded).hasLength(16)
    }

    @Test
    fun `keyId appends fragment`() {
        val did = Did("did:ssdid:7KmVwPq9RtXzN3Fy")
        assertThat(did.keyId(1)).isEqualTo("did:ssdid:7KmVwPq9RtXzN3Fy#key-1")
    }

    @Test
    fun `parse extracts DID from key ID`() {
        val keyId = "did:ssdid:7KmVwPq9RtXzN3Fy#key-1"
        val did = Did.fromKeyId(keyId)
        assertThat(did.value).isEqualTo("did:ssdid:7KmVwPq9RtXzN3Fy")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew test --tests "my.ssdid.wallet.domain.model.DidTest"`
Expected: FAIL — `Did` class not found

**Step 3: Implement DID model**

Create `android/app/src/main/java/my/ssdid/wallet/domain/model/Did.kt`:
```kotlin
package my.ssdid.wallet.domain.model

import java.security.SecureRandom
import java.util.Base64

@JvmInline
value class Did(val value: String) {
    fun keyId(keyIndex: Int = 1): String = "$value#key-$keyIndex"

    fun methodSpecificId(): String = value.removePrefix("did:ssdid:")

    companion object {
        fun generate(): Did {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            val id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            return Did("did:ssdid:$id")
        }

        fun fromKeyId(keyId: String): Did {
            val didPart = keyId.substringBefore("#")
            return Did(didPart)
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew test --tests "my.ssdid.wallet.domain.model.DidTest"`
Expected: PASS

**Step 5: Write Algorithm enum**

Create `android/app/src/main/java/my/ssdid/wallet/domain/model/Algorithm.kt`:
```kotlin
package my.ssdid.wallet.domain.model

enum class Algorithm(
    val w3cType: String,
    val proofType: String,
    val isPostQuantum: Boolean,
    val kazSignLevel: Int? = null
) {
    ED25519(
        w3cType = "Ed25519VerificationKey2020",
        proofType = "Ed25519Signature2020",
        isPostQuantum = false
    ),
    ECDSA_P256(
        w3cType = "EcdsaSecp256r1VerificationKey2019",
        proofType = "EcdsaSecp256r1Signature2019",
        isPostQuantum = false
    ),
    ECDSA_P384(
        w3cType = "EcdsaSecp384VerificationKey2019",
        proofType = "EcdsaSecp384Signature2019",
        isPostQuantum = false
    ),
    KAZ_SIGN_128(
        w3cType = "KazSignVerificationKey2024",
        proofType = "KazSignSignature2024",
        isPostQuantum = true,
        kazSignLevel = 128
    ),
    KAZ_SIGN_192(
        w3cType = "KazSignVerificationKey2024",
        proofType = "KazSignSignature2024",
        isPostQuantum = true,
        kazSignLevel = 192
    ),
    KAZ_SIGN_256(
        w3cType = "KazSignVerificationKey2024",
        proofType = "KazSignSignature2024",
        isPostQuantum = true,
        kazSignLevel = 256
    );
}
```

**Step 6: Write DID Document, VC, Proof, Identity models**

Create `android/app/src/main/java/my/ssdid/wallet/domain/model/DidDocument.kt`:
```kotlin
package my.ssdid.wallet.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DidDocument(
    @SerialName("@context") val context: List<String> = listOf("https://www.w3.org/ns/did/v1"),
    val id: String,
    val controller: String,
    val verificationMethod: List<VerificationMethod>,
    val authentication: List<String>,
    val assertionMethod: List<String>,
    val capabilityInvocation: List<String> = emptyList()
) {
    companion object {
        fun build(did: Did, keyId: String, algorithm: Algorithm, publicKeyMultibase: String): DidDocument {
            return DidDocument(
                id = did.value,
                controller = did.value,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = keyId,
                        type = algorithm.w3cType,
                        controller = did.value,
                        publicKeyMultibase = publicKeyMultibase
                    )
                ),
                authentication = listOf(keyId),
                assertionMethod = listOf(keyId),
                capabilityInvocation = listOf(keyId)
            )
        }
    }
}

@Serializable
data class VerificationMethod(
    val id: String,
    val type: String,
    val controller: String,
    val publicKeyMultibase: String
)
```

Create `android/app/src/main/java/my/ssdid/wallet/domain/model/Proof.kt`:
```kotlin
package my.ssdid.wallet.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Proof(
    val type: String,
    val created: String,
    val verificationMethod: String,
    val proofPurpose: String,
    val proofValue: String,
    val domain: String? = null,
    val challenge: String? = null
)
```

Create `android/app/src/main/java/my/ssdid/wallet/domain/model/VerifiableCredential.kt`:
```kotlin
package my.ssdid.wallet.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifiableCredential(
    @SerialName("@context") val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    val id: String,
    val type: List<String>,
    val issuer: String,
    val issuanceDate: String,
    val expirationDate: String? = null,
    val credentialSubject: CredentialSubject,
    val proof: Proof
)

@Serializable
data class CredentialSubject(
    val id: String,
    val claims: Map<String, String> = emptyMap()
)
```

Create `android/app/src/main/java/my/ssdid/wallet/domain/model/Identity.kt`:
```kotlin
package my.ssdid.wallet.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Identity(
    val name: String,
    val did: String,
    val keyId: String,
    val algorithm: Algorithm,
    val publicKeyMultibase: String,
    val createdAt: String,
    val isActive: Boolean = true
)
```

**Step 7: Write DID Document test**

Create `android/app/src/test/java/my/ssdid/wallet/domain/model/DidDocumentTest.kt`:
```kotlin
package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DidDocumentTest {
    @Test
    fun `build creates W3C compliant document`() {
        val did = Did("did:ssdid:7KmVwPq9RtXzN3Fy")
        val keyId = did.keyId(1)
        val doc = DidDocument.build(did, keyId, Algorithm.KAZ_SIGN_192, "uhaXgBZDq8R2mNvK4t")

        assertThat(doc.context).contains("https://www.w3.org/ns/did/v1")
        assertThat(doc.id).isEqualTo("did:ssdid:7KmVwPq9RtXzN3Fy")
        assertThat(doc.controller).isEqualTo(doc.id)
        assertThat(doc.verificationMethod).hasSize(1)
        assertThat(doc.verificationMethod[0].type).isEqualTo("KazSignVerificationKey2024")
        assertThat(doc.authentication).containsExactly(keyId)
        assertThat(doc.assertionMethod).containsExactly(keyId)
    }
}
```

**Step 8: Run all model tests**

Run: `cd android && ./gradlew test --tests "my.ssdid.wallet.domain.model.*"`
Expected: ALL PASS

**Step 9: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/model/
git add android/app/src/test/java/my/ssdid/wallet/domain/model/
git commit -m "feat(android): add W3C DID/VC/Proof domain models"
```

---

### Task 3: Crypto Provider Abstraction + Classical Provider

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/CryptoProvider.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/KeyPairResult.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/ClassicalProvider.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/Multibase.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/crypto/ClassicalProviderTest.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/crypto/MultibaseTest.kt`

**Step 1: Write CryptoProvider interface and KeyPairResult**

Create `android/app/src/main/java/my/ssdid/wallet/domain/crypto/CryptoProvider.kt`:
```kotlin
package my.ssdid.wallet.domain.crypto

import my.ssdid.wallet.domain.model.Algorithm

interface CryptoProvider {
    fun supportsAlgorithm(algorithm: Algorithm): Boolean
    fun generateKeyPair(algorithm: Algorithm): KeyPairResult
    fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray
    fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean
}
```

Create `android/app/src/main/java/my/ssdid/wallet/domain/crypto/KeyPairResult.kt`:
```kotlin
package my.ssdid.wallet.domain.crypto

data class KeyPairResult(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyPairResult) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }
    override fun hashCode(): Int = publicKey.contentHashCode() * 31 + privateKey.contentHashCode()
}
```

**Step 2: Write failing test for ClassicalProvider**

Create `android/app/src/test/java/my/ssdid/wallet/domain/crypto/ClassicalProviderTest.kt`:
```kotlin
package my.ssdid.wallet.domain.crypto

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.model.Algorithm
import org.junit.Test

class ClassicalProviderTest {
    private val provider = ClassicalProvider()

    @Test
    fun `supports classical algorithms only`() {
        assertThat(provider.supportsAlgorithm(Algorithm.ED25519)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.ECDSA_P256)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.ECDSA_P384)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.KAZ_SIGN_192)).isFalse()
    }

    @Test
    fun `ed25519 generate-sign-verify round trip`() {
        val keyPair = provider.generateKeyPair(Algorithm.ED25519)
        assertThat(keyPair.publicKey).hasLength(32)
        assertThat(keyPair.privateKey).hasLength(32)

        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ED25519, keyPair.privateKey, message)
        assertThat(signature).hasLength(64)

        val valid = provider.verify(Algorithm.ED25519, keyPair.publicKey, signature, message)
        assertThat(valid).isTrue()
    }

    @Test
    fun `ed25519 verify rejects tampered message`() {
        val keyPair = provider.generateKeyPair(Algorithm.ED25519)
        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ED25519, keyPair.privateKey, message)

        val tampered = "Tampered".toByteArray()
        val valid = provider.verify(Algorithm.ED25519, keyPair.publicKey, signature, tampered)
        assertThat(valid).isFalse()
    }

    @Test
    fun `ecdsa p256 generate-sign-verify round trip`() {
        val keyPair = provider.generateKeyPair(Algorithm.ECDSA_P256)
        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ECDSA_P256, keyPair.privateKey, message)
        val valid = provider.verify(Algorithm.ECDSA_P256, keyPair.publicKey, signature, message)
        assertThat(valid).isTrue()
    }
}
```

**Step 3: Run test to verify it fails**

Run: `cd android && ./gradlew test --tests "my.ssdid.wallet.domain.crypto.ClassicalProviderTest"`
Expected: FAIL — `ClassicalProvider` not found

**Step 4: Implement ClassicalProvider**

Create `android/app/src/main/java/my/ssdid/wallet/domain/crypto/ClassicalProvider.kt`:
```kotlin
package my.ssdid.wallet.domain.crypto

import my.ssdid.wallet.domain.model.Algorithm
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.*

class ClassicalProvider : CryptoProvider {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override fun supportsAlgorithm(algorithm: Algorithm): Boolean = !algorithm.isPostQuantum

    override fun generateKeyPair(algorithm: Algorithm): KeyPairResult {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        return when (algorithm) {
            Algorithm.ED25519 -> generateEd25519()
            Algorithm.ECDSA_P256 -> generateEcdsa("secp256r1")
            Algorithm.ECDSA_P384 -> generateEcdsa("secp384r1")
            else -> throw IllegalArgumentException("Unsupported: $algorithm")
        }
    }

    override fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray {
        return when (algorithm) {
            Algorithm.ED25519 -> signEd25519(privateKey, data)
            Algorithm.ECDSA_P256 -> signEcdsa("SHA256withECDSA", "secp256r1", privateKey, data)
            Algorithm.ECDSA_P384 -> signEcdsa("SHA384withECDSA", "secp384r1", privateKey, data)
            else -> throw IllegalArgumentException("Unsupported: $algorithm")
        }
    }

    override fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        return when (algorithm) {
            Algorithm.ED25519 -> verifyEd25519(publicKey, signature, data)
            Algorithm.ECDSA_P256 -> verifyEcdsa("SHA256withECDSA", "secp256r1", publicKey, signature, data)
            Algorithm.ECDSA_P384 -> verifyEcdsa("SHA384withECDSA", "secp384r1", publicKey, signature, data)
            else -> false
        }
    }

    private fun generateEd25519(): KeyPairResult {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        val kp = kpg.generateKeyPair()
        val pubBytes = kp.public.encoded.takeLast(32).toByteArray()
        val privBytes = kp.private.encoded.takeLast(32).toByteArray()
        return KeyPairResult(publicKey = pubBytes, privateKey = privBytes)
    }

    private fun signEd25519(privateKey: ByteArray, data: ByteArray): ByteArray {
        val pkcs8 = buildEd25519Pkcs8(privateKey)
        val keySpec = PKCS8EncodedKeySpec(pkcs8)
        val kf = KeyFactory.getInstance("Ed25519", "BC")
        val privKey = kf.generatePrivate(keySpec)
        val sig = Signature.getInstance("Ed25519", "BC")
        sig.initSign(privKey)
        sig.update(data)
        return sig.sign()
    }

    private fun verifyEd25519(publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        val x509 = buildEd25519X509(publicKey)
        val keySpec = X509EncodedKeySpec(x509)
        val kf = KeyFactory.getInstance("Ed25519", "BC")
        val pubKey = kf.generatePublic(keySpec)
        val sig = Signature.getInstance("Ed25519", "BC")
        sig.initVerify(pubKey)
        sig.update(data)
        return sig.verify(signature)
    }

    // Ed25519 raw key wrapping helpers (ASN.1 DER)
    private fun buildEd25519Pkcs8(raw: ByteArray): ByteArray {
        // PKCS#8 wrapper for Ed25519: SEQUENCE { SEQUENCE { OID 1.3.101.112 }, OCTET STRING { OCTET STRING { raw } } }
        val oid = byteArrayOf(0x06, 0x03, 0x2b, 0x65, 0x70) // OID 1.3.101.112
        val algoSeq = byteArrayOf(0x30, oid.size.toByte()) + oid
        val innerOctet = byteArrayOf(0x04, raw.size.toByte()) + raw
        val outerOctet = byteArrayOf(0x04, innerOctet.size.toByte()) + innerOctet
        val total = algoSeq + outerOctet
        return byteArrayOf(0x30, total.size.toByte()) + total
    }

    private fun buildEd25519X509(raw: ByteArray): ByteArray {
        // X.509 SubjectPublicKeyInfo for Ed25519
        val oid = byteArrayOf(0x06, 0x03, 0x2b, 0x65, 0x70)
        val algoSeq = byteArrayOf(0x30, oid.size.toByte()) + oid
        val bitString = byteArrayOf(0x03, (raw.size + 1).toByte(), 0x00) + raw
        val total = algoSeq + bitString
        return byteArrayOf(0x30, total.size.toByte()) + total
    }

    private fun generateEcdsa(curveName: String): KeyPairResult {
        val kpg = KeyPairGenerator.getInstance("EC", "BC")
        kpg.initialize(ECGenParameterSpec(curveName))
        val kp = kpg.generateKeyPair()
        return KeyPairResult(publicKey = kp.public.encoded, privateKey = kp.private.encoded)
    }

    private fun signEcdsa(sigAlgo: String, curveName: String, privateKey: ByteArray, data: ByteArray): ByteArray {
        val keySpec = PKCS8EncodedKeySpec(privateKey)
        val kf = KeyFactory.getInstance("EC", "BC")
        val privKey = kf.generatePrivate(keySpec)
        val sig = Signature.getInstance(sigAlgo, "BC")
        sig.initSign(privKey)
        sig.update(data)
        return sig.sign()
    }

    private fun verifyEcdsa(sigAlgo: String, curveName: String, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        val keySpec = X509EncodedKeySpec(publicKey)
        val kf = KeyFactory.getInstance("EC", "BC")
        val pubKey = kf.generatePublic(keySpec)
        val sig = Signature.getInstance(sigAlgo, "BC")
        sig.initVerify(pubKey)
        sig.update(data)
        return sig.verify(signature)
    }
}
```

**Step 5: Write and implement Multibase encoding**

Create `android/app/src/test/java/my/ssdid/wallet/domain/crypto/MultibaseTest.kt`:
```kotlin
package my.ssdid.wallet.domain.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MultibaseTest {
    @Test
    fun `encode uses u prefix for base64url`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val encoded = Multibase.encode(data)
        assertThat(encoded).startsWith("u")
    }

    @Test
    fun `round trip encode-decode`() {
        val data = "Hello SSDID".toByteArray()
        val encoded = Multibase.encode(data)
        val decoded = Multibase.decode(encoded)
        assertThat(decoded).isEqualTo(data)
    }
}
```

Create `android/app/src/main/java/my/ssdid/wallet/domain/crypto/Multibase.kt`:
```kotlin
package my.ssdid.wallet.domain.crypto

import java.util.Base64

object Multibase {
    private const val BASE64URL_PREFIX = 'u'

    fun encode(data: ByteArray): String {
        return "$BASE64URL_PREFIX${Base64.getUrlEncoder().withoutPadding().encodeToString(data)}"
    }

    fun decode(encoded: String): ByteArray {
        require(encoded.isNotEmpty() && encoded[0] == BASE64URL_PREFIX) {
            "Only base64url multibase (u prefix) is supported"
        }
        return Base64.getUrlDecoder().decode(encoded.substring(1))
    }
}
```

**Step 6: Run all crypto tests**

Run: `cd android && ./gradlew test --tests "my.ssdid.wallet.domain.crypto.*"`
Expected: ALL PASS

**Step 7: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/crypto/
git add android/app/src/test/java/my/ssdid/wallet/domain/crypto/
git commit -m "feat(android): add CryptoProvider abstraction, ClassicalProvider, Multibase"
```

---

### Task 4: KAZ-Sign PQC Provider (JNI Integration)

**Files:**
- Copy: KAZ-Sign Kotlin binding from `/Users/amirrudinyahaya/Workspace/PQC-KAZ/SIGN/bindings/` to `android/app/src/main/java/my/ssdid/wallet/domain/crypto/kazsign/`
- Create: `android/app/src/main/cpp/CMakeLists.txt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/PqcProvider.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/crypto/PqcProviderTest.kt`

**Step 1: Copy KAZ-Sign C source and Kotlin binding**

```bash
# Copy C source for NDK build
cp -r /Users/amirrudinyahaya/Workspace/PQC-KAZ/SIGN/include android/app/src/main/cpp/
cp -r /Users/amirrudinyahaya/Workspace/PQC-KAZ/SIGN/src android/app/src/main/cpp/
# Copy existing Kotlin binding
cp -r /Users/amirrudinyahaya/Workspace/PQC-KAZ/SIGN/bindings/kotlin/* android/app/src/main/java/my/ssdid/wallet/domain/crypto/kazsign/
```

Note: Examine the existing binding at copy time. Adjust package name to `my.ssdid.wallet.domain.crypto.kazsign` and ensure JNI method signatures match.

**Step 2: Create CMakeLists.txt for NDK build**

Create `android/app/src/main/cpp/CMakeLists.txt`:
```cmake
cmake_minimum_required(VERSION 3.22)
project(kazsign)

# OpenSSL - use prebuilt for Android
# You will need to provide prebuilt OpenSSL .so for arm64-v8a and x86_64
set(OPENSSL_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/openssl/${ANDROID_ABI})

add_library(kazsign SHARED
    src/internal/sign.c
    src/internal/kdf.c
    src/internal/nist_wrapper.c
    src/internal/security.c
)

target_include_directories(kazsign PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/internal
    ${OPENSSL_ROOT}/include
)

target_link_libraries(kazsign
    ${OPENSSL_ROOT}/lib/libcrypto.a
    log
)

target_compile_definitions(kazsign PRIVATE
    KAZ_SECURITY_LEVEL=0  # Runtime level selection (unified build)
)
```

**Step 3: Implement PqcProvider wrapping KAZ-Sign JNI**

Create `android/app/src/main/java/my/ssdid/wallet/domain/crypto/PqcProvider.kt`:
```kotlin
package my.ssdid.wallet.domain.crypto

import my.ssdid.wallet.domain.model.Algorithm

class PqcProvider : CryptoProvider {

    init {
        System.loadLibrary("kazsign")
    }

    override fun supportsAlgorithm(algorithm: Algorithm): Boolean = algorithm.isPostQuantum

    override fun generateKeyPair(algorithm: Algorithm): KeyPairResult {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        val level = algorithm.kazSignLevel ?: throw IllegalArgumentException("No KAZ-Sign level for $algorithm")
        return nativeKeyPair(level)
    }

    override fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray {
        val level = algorithm.kazSignLevel ?: throw IllegalArgumentException("No KAZ-Sign level for $algorithm")
        return nativeSign(level, privateKey, data)
    }

    override fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        val level = algorithm.kazSignLevel ?: throw IllegalArgumentException("No KAZ-Sign level for $algorithm")
        return nativeVerify(level, publicKey, signature, data)
    }

    // JNI methods — implemented in C via kazsign binding
    private external fun nativeKeyPair(level: Int): KeyPairResult
    private external fun nativeSign(level: Int, secretKey: ByteArray, message: ByteArray): ByteArray
    private external fun nativeVerify(level: Int, publicKey: ByteArray, signature: ByteArray, message: ByteArray): Boolean
}
```

Note: The actual JNI C bridge file should be adapted from the existing Kotlin binding at `/Users/amirrudinyahaya/Workspace/PQC-KAZ/SIGN/bindings/kotlin/`. Adjust function signatures to match `PqcProvider`'s `native` method names.

**Step 4: Write unit test (will run as instrumented test on device/emulator since JNI)**

Create `android/app/src/test/java/my/ssdid/wallet/domain/crypto/PqcProviderTest.kt`:
```kotlin
package my.ssdid.wallet.domain.crypto

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.model.Algorithm
import org.junit.Test

/**
 * Note: Full PQC tests require Android instrumented test (JNI).
 * This test verifies the provider interface contract only.
 */
class PqcProviderTest {
    @Test
    fun `supports only post-quantum algorithms`() {
        // Cannot instantiate PqcProvider without native lib in unit tests.
        // Test algorithm support logic directly.
        assertThat(Algorithm.KAZ_SIGN_128.isPostQuantum).isTrue()
        assertThat(Algorithm.KAZ_SIGN_192.isPostQuantum).isTrue()
        assertThat(Algorithm.KAZ_SIGN_256.isPostQuantum).isTrue()
        assertThat(Algorithm.KAZ_SIGN_128.kazSignLevel).isEqualTo(128)
        assertThat(Algorithm.KAZ_SIGN_192.kazSignLevel).isEqualTo(192)
        assertThat(Algorithm.KAZ_SIGN_256.kazSignLevel).isEqualTo(256)
        assertThat(Algorithm.ED25519.isPostQuantum).isFalse()
    }
}
```

**Step 5: Run test**

Run: `cd android && ./gradlew test --tests "my.ssdid.wallet.domain.crypto.PqcProviderTest"`
Expected: PASS

**Step 6: Commit**

```bash
git add android/app/src/main/cpp/ android/app/src/main/java/my/ssdid/wallet/domain/crypto/kazsign/
git add android/app/src/main/java/my/ssdid/wallet/domain/crypto/PqcProvider.kt
git add android/app/src/test/java/my/ssdid/wallet/domain/crypto/PqcProviderTest.kt
git commit -m "feat(android): add KAZ-Sign PQC provider with JNI bridge"
```

---

### Task 5: Vault (Key Management + Hardware Keystore)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/vault/CredentialStore.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/keystore/KeystoreManager.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/keystore/AndroidKeystoreManager.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/vault/VaultTest.kt`

**Step 1: Write Vault interface**

Create `android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt`:
```kotlin
package my.ssdid.wallet.domain.vault

import my.ssdid.wallet.domain.model.*

interface Vault {
    suspend fun createIdentity(name: String, algorithm: Algorithm): Result<Identity>
    suspend fun getIdentity(keyId: String): Identity?
    suspend fun listIdentities(): List<Identity>
    suspend fun deleteIdentity(keyId: String): Result<Unit>
    suspend fun sign(keyId: String, data: ByteArray): Result<ByteArray>
    suspend fun buildDidDocument(keyId: String): Result<DidDocument>
    suspend fun createProof(keyId: String, document: Map<String, Any>, proofPurpose: String, challenge: String? = null): Result<Proof>
    suspend fun storeCredential(credential: VerifiableCredential): Result<Unit>
    suspend fun listCredentials(): List<VerifiableCredential>
    suspend fun getCredentialForDid(did: String): VerifiableCredential?
    suspend fun deleteCredential(credentialId: String): Result<Unit>
}
```

**Step 2: Write KeystoreManager interface (platform abstraction)**

Create `android/app/src/main/java/my/ssdid/wallet/platform/keystore/KeystoreManager.kt`:
```kotlin
package my.ssdid.wallet.platform.keystore

interface KeystoreManager {
    fun generateWrappingKey(alias: String)
    fun encrypt(alias: String, data: ByteArray): ByteArray
    fun decrypt(alias: String, encryptedData: ByteArray): ByteArray
    fun deleteKey(alias: String)
    fun hasKey(alias: String): Boolean
}
```

**Step 3: Implement AndroidKeystoreManager**

Create `android/app/src/main/java/my/ssdid/wallet/platform/keystore/AndroidKeystoreManager.kt`:
```kotlin
package my.ssdid.wallet.platform.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreManager : KeystoreManager {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun generateWrappingKey(alias: String) {
        if (hasKey(alias)) return
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(300, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .build()
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(spec)
        keyGen.generateKey()
    }

    override fun encrypt(alias: String, data: ByteArray): ByteArray {
        val key = keyStore.getKey(alias, null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        // Prepend IV (12 bytes) to ciphertext
        return iv + encrypted
    }

    override fun decrypt(alias: String, encryptedData: ByteArray): ByteArray {
        val key = keyStore.getKey(alias, null)
        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    override fun deleteKey(alias: String) {
        if (hasKey(alias)) keyStore.deleteEntry(alias)
    }

    override fun hasKey(alias: String): Boolean = keyStore.containsAlias(alias)
}
```

**Step 4: Implement VaultImpl**

Create `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`:
```kotlin
package my.ssdid.wallet.domain.vault

import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.platform.keystore.KeystoreManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class VaultImpl(
    private val classicalProvider: CryptoProvider,
    private val pqcProvider: CryptoProvider,
    private val keystoreManager: KeystoreManager,
    private val storage: VaultStorage
) : Vault {

    private fun providerFor(algorithm: Algorithm): CryptoProvider {
        return if (algorithm.isPostQuantum) pqcProvider else classicalProvider
    }

    override suspend fun createIdentity(name: String, algorithm: Algorithm): Result<Identity> = runCatching {
        val provider = providerFor(algorithm)
        val keyPair = provider.generateKeyPair(algorithm)
        val did = Did.generate()
        val keyId = did.keyId(1)
        val publicKeyMultibase = Multibase.encode(keyPair.publicKey)

        // Store private key encrypted via hardware keystore
        val wrappingAlias = "ssdid_wrap_${did.methodSpecificId()}"
        keystoreManager.generateWrappingKey(wrappingAlias)
        val encryptedPrivateKey = keystoreManager.encrypt(wrappingAlias, keyPair.privateKey)

        // Zero private key from memory
        keyPair.privateKey.fill(0)

        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val identity = Identity(
            name = name,
            did = did.value,
            keyId = keyId,
            algorithm = algorithm,
            publicKeyMultibase = publicKeyMultibase,
            createdAt = now
        )

        storage.saveIdentity(identity, encryptedPrivateKey)
        identity
    }

    override suspend fun getIdentity(keyId: String): Identity? = storage.getIdentity(keyId)

    override suspend fun listIdentities(): List<Identity> = storage.listIdentities()

    override suspend fun deleteIdentity(keyId: String): Result<Unit> = runCatching {
        val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
        val did = Did(identity.did)
        keystoreManager.deleteKey("ssdid_wrap_${did.methodSpecificId()}")
        storage.deleteIdentity(keyId)
    }

    override suspend fun sign(keyId: String, data: ByteArray): Result<ByteArray> = runCatching {
        val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
        val did = Did(identity.did)
        val wrappingAlias = "ssdid_wrap_${did.methodSpecificId()}"
        val encryptedPrivateKey = storage.getEncryptedPrivateKey(keyId)
            ?: throw IllegalStateException("Private key not found for: $keyId")
        val privateKey = keystoreManager.decrypt(wrappingAlias, encryptedPrivateKey)

        try {
            val provider = providerFor(identity.algorithm)
            provider.sign(identity.algorithm, privateKey, data)
        } finally {
            privateKey.fill(0) // Zero from memory
        }
    }

    override suspend fun buildDidDocument(keyId: String): Result<DidDocument> = runCatching {
        val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
        DidDocument.build(
            did = Did(identity.did),
            keyId = identity.keyId,
            algorithm = identity.algorithm,
            publicKeyMultibase = identity.publicKeyMultibase
        )
    }

    override suspend fun createProof(keyId: String, document: Map<String, Any>, proofPurpose: String, challenge: String?): Result<Proof> = runCatching {
        val identity = storage.getIdentity(keyId) ?: throw IllegalArgumentException("Identity not found: $keyId")
        val canonicalJson = Json.encodeToString(document)
        val dataToSign = if (challenge != null) {
            challenge.toByteArray() + canonicalJson.toByteArray()
        } else {
            canonicalJson.toByteArray()
        }
        val signature = sign(keyId, dataToSign).getOrThrow()
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        Proof(
            type = identity.algorithm.proofType,
            created = now,
            verificationMethod = identity.keyId,
            proofPurpose = proofPurpose,
            proofValue = Multibase.encode(signature),
            challenge = challenge
        )
    }

    override suspend fun storeCredential(credential: VerifiableCredential): Result<Unit> = runCatching {
        storage.saveCredential(credential)
    }

    override suspend fun listCredentials(): List<VerifiableCredential> = storage.listCredentials()

    override suspend fun getCredentialForDid(did: String): VerifiableCredential? {
        return storage.listCredentials().firstOrNull { it.credentialSubject.id == did }
    }

    override suspend fun deleteCredential(credentialId: String): Result<Unit> = runCatching {
        storage.deleteCredential(credentialId)
    }
}
```

Create `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultStorage.kt`:
```kotlin
package my.ssdid.wallet.domain.vault

import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.model.VerifiableCredential

interface VaultStorage {
    suspend fun saveIdentity(identity: Identity, encryptedPrivateKey: ByteArray)
    suspend fun getIdentity(keyId: String): Identity?
    suspend fun listIdentities(): List<Identity>
    suspend fun deleteIdentity(keyId: String)
    suspend fun getEncryptedPrivateKey(keyId: String): ByteArray?
    suspend fun saveCredential(credential: VerifiableCredential)
    suspend fun listCredentials(): List<VerifiableCredential>
    suspend fun deleteCredential(credentialId: String)
}
```

**Step 5: Write Vault test with mock keystore**

Create `android/app/src/test/java/my/ssdid/wallet/domain/vault/VaultTest.kt`:
```kotlin
package my.ssdid.wallet.domain.vault

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.platform.keystore.KeystoreManager
import org.junit.Before
import org.junit.Test

class VaultTest {
    private lateinit var vault: VaultImpl
    private lateinit var keystore: KeystoreManager
    private lateinit var storage: FakeVaultStorage

    @Before
    fun setup() {
        keystore = mockk(relaxed = true)
        // Pass-through encrypt/decrypt for testing
        every { keystore.encrypt(any(), any()) } answers { secondArg<ByteArray>() }
        every { keystore.decrypt(any(), any()) } answers { secondArg<ByteArray>() }

        storage = FakeVaultStorage()

        val pqcProvider = mockk<CryptoProvider>()
        every { pqcProvider.supportsAlgorithm(any()) } returns false

        vault = VaultImpl(
            classicalProvider = ClassicalProvider(),
            pqcProvider = pqcProvider,
            keystoreManager = keystore,
            storage = storage
        )
    }

    @Test
    fun `createIdentity generates DID and stores key`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()

        assertThat(identity.did).startsWith("did:ssdid:")
        assertThat(identity.keyId).contains("#key-1")
        assertThat(identity.algorithm).isEqualTo(Algorithm.ED25519)
        assertThat(identity.publicKeyMultibase).startsWith("u")
        assertThat(identity.name).isEqualTo("Test")

        // Verify stored
        val retrieved = vault.getIdentity(identity.keyId)
        assertThat(retrieved).isEqualTo(identity)

        // Verify wrapping key was generated
        verify { keystore.generateWrappingKey(any()) }
    }

    @Test
    fun `sign produces valid signature`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
        val message = "Hello".toByteArray()
        val signature = vault.sign(identity.keyId, message).getOrThrow()
        assertThat(signature).isNotEmpty()
    }

    @Test
    fun `listIdentities returns all created`() = runTest {
        vault.createIdentity("ID1", Algorithm.ED25519)
        vault.createIdentity("ID2", Algorithm.ECDSA_P256)
        val identities = vault.listIdentities()
        assertThat(identities).hasSize(2)
    }

    @Test
    fun `storeCredential and listCredentials round trip`() = runTest {
        val vc = my.ssdid.wallet.domain.model.VerifiableCredential(
            id = "urn:uuid:test",
            type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer",
            issuanceDate = "2026-03-06T00:00:00Z",
            credentialSubject = my.ssdid.wallet.domain.model.CredentialSubject(id = "did:ssdid:subject"),
            proof = my.ssdid.wallet.domain.model.Proof(
                type = "Ed25519Signature2020",
                created = "2026-03-06T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "uABC123"
            )
        )
        vault.storeCredential(vc)
        val creds = vault.listCredentials()
        assertThat(creds).hasSize(1)
        assertThat(creds[0].id).isEqualTo("urn:uuid:test")
    }
}
```

Create `android/app/src/test/java/my/ssdid/wallet/domain/vault/FakeVaultStorage.kt`:
```kotlin
package my.ssdid.wallet.domain.vault

import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.model.VerifiableCredential

class FakeVaultStorage : VaultStorage {
    private val identities = mutableMapOf<String, Identity>()
    private val privateKeys = mutableMapOf<String, ByteArray>()
    private val credentials = mutableMapOf<String, VerifiableCredential>()

    override suspend fun saveIdentity(identity: Identity, encryptedPrivateKey: ByteArray) {
        identities[identity.keyId] = identity
        privateKeys[identity.keyId] = encryptedPrivateKey
    }

    override suspend fun getIdentity(keyId: String) = identities[keyId]
    override suspend fun listIdentities() = identities.values.toList()
    override suspend fun deleteIdentity(keyId: String) { identities.remove(keyId); privateKeys.remove(keyId) }
    override suspend fun getEncryptedPrivateKey(keyId: String) = privateKeys[keyId]
    override suspend fun saveCredential(credential: VerifiableCredential) { credentials[credential.id] = credential }
    override suspend fun listCredentials() = credentials.values.toList()
    override suspend fun deleteCredential(credentialId: String) { credentials.remove(credentialId) }
}
```

**Step 6: Run Vault tests**

Run: `cd android && ./gradlew test --tests "my.ssdid.wallet.domain.vault.*"`
Expected: ALL PASS

**Step 7: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/vault/
git add android/app/src/main/java/my/ssdid/wallet/platform/keystore/
git add android/app/src/test/java/my/ssdid/wallet/domain/vault/
git commit -m "feat(android): add Vault with hardware-backed key storage"
```

---

### Task 6: Transport Layer (HTTP Client)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/RegistryApi.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/ServerApi.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/SsdidHttpClient.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/transport/dto/` (all DTOs)
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/transport/SsdidHttpClientTest.kt`

**Step 1: Create DTO classes**

Create `android/app/src/main/java/my/ssdid/wallet/domain/transport/dto/RegistryDtos.kt`:
```kotlin
package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.Serializable
import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.Proof

@Serializable data class RegisterDidRequest(val did_document: DidDocument, val proof: Proof)
@Serializable data class RegisterDidResponse(val did: String, val status: String)
@Serializable data class ChallengeResponse(val challenge: String, val expires_at: String? = null)
```

Create `android/app/src/main/java/my/ssdid/wallet/domain/transport/dto/ServerDtos.kt`:
```kotlin
package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.Serializable
import my.ssdid.wallet.domain.model.VerifiableCredential

@Serializable data class RegisterStartRequest(val did: String, val key_id: String)
@Serializable data class RegisterStartResponse(
    val challenge: String,
    val server_did: String,
    val server_key_id: String,
    val server_signature: String
)
@Serializable data class RegisterVerifyRequest(val did: String, val key_id: String, val signed_challenge: String)
@Serializable data class RegisterVerifyResponse(val credential: VerifiableCredential)
@Serializable data class AuthenticateRequest(val credential: VerifiableCredential)
@Serializable data class AuthenticateResponse(
    val session_token: String,
    val server_did: String,
    val server_key_id: String,
    val server_signature: String? = null
)
@Serializable data class TxChallengeRequest(val session_token: String)
@Serializable data class TxChallengeResponse(val challenge: String)
@Serializable data class TxSubmitRequest(
    val session_token: String,
    val did: String,
    val key_id: String,
    val signed_challenge: String,
    val transaction: Map<String, String>
)
@Serializable data class TxSubmitResponse(val transaction_id: String, val status: String)
```

**Step 2: Create Retrofit interfaces**

Create `android/app/src/main/java/my/ssdid/wallet/domain/transport/RegistryApi.kt`:
```kotlin
package my.ssdid.wallet.domain.transport

import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.transport.dto.*
import retrofit2.http.*

interface RegistryApi {
    @POST("api/did")
    suspend fun registerDid(@Body request: RegisterDidRequest): RegisterDidResponse

    @GET("api/did/{did}")
    suspend fun resolveDid(@Path("did") did: String): DidDocument

    @POST("api/did/{did}/challenge")
    suspend fun createChallenge(@Path("did") did: String): ChallengeResponse
}
```

Create `android/app/src/main/java/my/ssdid/wallet/domain/transport/ServerApi.kt`:
```kotlin
package my.ssdid.wallet.domain.transport

import my.ssdid.wallet.domain.transport.dto.*
import retrofit2.http.Body
import retrofit2.http.POST

interface ServerApi {
    @POST("api/register")
    suspend fun registerStart(@Body request: RegisterStartRequest): RegisterStartResponse

    @POST("api/register/verify")
    suspend fun registerVerify(@Body request: RegisterVerifyRequest): RegisterVerifyResponse

    @POST("api/authenticate")
    suspend fun authenticate(@Body request: AuthenticateRequest): AuthenticateResponse

    @POST("api/transaction/challenge")
    suspend fun requestChallenge(@Body request: TxChallengeRequest): TxChallengeResponse

    @POST("api/transaction/submit")
    suspend fun submitTransaction(@Body request: TxSubmitRequest): TxSubmitResponse
}
```

**Step 3: Create SsdidHttpClient facade**

Create `android/app/src/main/java/my/ssdid/wallet/domain/transport/SsdidHttpClient.kt`:
```kotlin
package my.ssdid.wallet.domain.transport

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class SsdidHttpClient(
    registryUrl: String,
    private val serverUrlProvider: () -> String? = { null }
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val registry: RegistryApi = Retrofit.Builder()
        .baseUrl(registryUrl.trimEnd('/') + "/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RegistryApi::class.java)

    fun serverApi(serverUrl: String): ServerApi {
        return Retrofit.Builder()
            .baseUrl(serverUrl.trimEnd('/') + "/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ServerApi::class.java)
    }
}
```

**Step 4: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/transport/
git commit -m "feat(android): add transport layer with Registry and Server APIs"
```

---

### Task 7: Verifier (DID Resolution + Signature Verification)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/verifier/Verifier.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/verifier/VerifierImpl.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/verifier/VerifierTest.kt`

**Step 1: Write Verifier interface**

Create `android/app/src/main/java/my/ssdid/wallet/domain/verifier/Verifier.kt`:
```kotlin
package my.ssdid.wallet.domain.verifier

import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.Proof
import my.ssdid.wallet.domain.model.VerifiableCredential

interface Verifier {
    suspend fun resolveDid(did: String): Result<DidDocument>
    suspend fun verifySignature(did: String, keyId: String, signature: ByteArray, data: ByteArray): Result<Boolean>
    suspend fun verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String): Result<Boolean>
    suspend fun verifyCredential(credential: VerifiableCredential): Result<Boolean>
}
```

**Step 2: Implement VerifierImpl**

Create `android/app/src/main/java/my/ssdid/wallet/domain/verifier/VerifierImpl.kt`:
```kotlin
package my.ssdid.wallet.domain.verifier

import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.transport.RegistryApi
import java.time.Instant

class VerifierImpl(
    private val registryApi: RegistryApi,
    private val classicalProvider: CryptoProvider,
    private val pqcProvider: CryptoProvider
) : Verifier {

    override suspend fun resolveDid(did: String): Result<DidDocument> = runCatching {
        registryApi.resolveDid(did)
    }

    override suspend fun verifySignature(did: String, keyId: String, signature: ByteArray, data: ByteArray): Result<Boolean> = runCatching {
        val doc = resolveDid(did).getOrThrow()
        val vm = doc.verificationMethod.find { it.id == keyId }
            ?: throw IllegalArgumentException("Key $keyId not found in DID Document")
        val publicKey = Multibase.decode(vm.publicKeyMultibase)
        val algorithm = algorithmFromW3cType(vm.type)
        val provider = if (algorithm.isPostQuantum) pqcProvider else classicalProvider
        provider.verify(algorithm, publicKey, signature, data)
    }

    override suspend fun verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String): Result<Boolean> {
        val signature = Multibase.decode(signedChallenge)
        return verifySignature(did, keyId, signature, challenge.toByteArray())
    }

    override suspend fun verifyCredential(credential: VerifiableCredential): Result<Boolean> = runCatching {
        // Check expiration
        credential.expirationDate?.let { exp ->
            val expInstant = Instant.parse(exp)
            if (Instant.now().isAfter(expInstant)) throw SecurityException("Credential expired")
        }
        // Verify issuer signature
        val proof = credential.proof
        val issuerDid = Did.fromKeyId(proof.verificationMethod)
        verifyChallengeResponse(
            issuerDid.value,
            proof.verificationMethod,
            "", // VC proof has no challenge
            proof.proofValue
        ).getOrThrow()
    }

    private fun algorithmFromW3cType(type: String): Algorithm {
        return Algorithm.entries.first { it.w3cType == type }
    }
}
```

**Step 3: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/verifier/
git commit -m "feat(android): add Verifier with DID resolution and signature verification"
```

---

### Task 8: SSDID Client (Orchestrates All Flows)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/SsdidClientTest.kt`

**Step 1: Implement SsdidClient**

Create `android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt`:
```kotlin
package my.ssdid.wallet.domain

import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.transport.ServerApi
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.*
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.verifier.Verifier
import org.bouncycastle.jcajce.provider.digest.SHA3
import java.util.Base64

class SsdidClient(
    private val vault: Vault,
    private val verifier: Verifier,
    private val httpClient: SsdidHttpClient
) {
    /** Flow 1: Create identity and publish DID to Registry */
    suspend fun initIdentity(name: String, algorithm: Algorithm): Result<Identity> = runCatching {
        val identity = vault.createIdentity(name, algorithm).getOrThrow()
        val didDoc = vault.buildDidDocument(identity.keyId).getOrThrow()
        val proof = vault.createProof(identity.keyId, mapOf("id" to didDoc.id), "assertionMethod").getOrThrow()
        httpClient.registry.registerDid(RegisterDidRequest(didDoc, proof))
        identity
    }

    /** Flow 2: Register with a service (mutual auth) */
    suspend fun registerWithService(identity: Identity, serverUrl: String): Result<VerifiableCredential> = runCatching {
        val serverApi = httpClient.serverApi(serverUrl)

        // Step 1: Start registration
        val startResp = serverApi.registerStart(RegisterStartRequest(identity.did, identity.keyId))

        // Step 2: Verify server (mutual auth)
        verifier.verifyChallengeResponse(
            startResp.server_did, startResp.server_key_id,
            startResp.challenge, startResp.server_signature
        ).getOrThrow()

        // Step 3: Sign challenge
        val signatureBytes = vault.sign(identity.keyId, startResp.challenge.toByteArray()).getOrThrow()
        val signedChallenge = Multibase.encode(signatureBytes)

        // Step 4: Complete registration
        val verifyResp = serverApi.registerVerify(
            RegisterVerifyRequest(identity.did, identity.keyId, signedChallenge)
        )

        // Step 5: Store credential
        val vc = verifyResp.credential
        vault.storeCredential(vc).getOrThrow()
        vc
    }

    /** Flow 3: Authenticate with a service */
    suspend fun authenticate(credential: VerifiableCredential, serverUrl: String): Result<AuthenticateResponse> = runCatching {
        val serverApi = httpClient.serverApi(serverUrl)
        val resp = serverApi.authenticate(AuthenticateRequest(credential))

        // Verify server's session token signature (mutual auth)
        if (resp.server_signature != null) {
            verifier.verifyChallengeResponse(
                resp.server_did, resp.server_key_id,
                resp.session_token, resp.server_signature
            ).getOrThrow()
        }
        resp
    }

    /** Flow 4: Sign a transaction */
    suspend fun signTransaction(
        sessionToken: String,
        identity: Identity,
        transaction: Map<String, String>,
        serverUrl: String
    ): Result<TxSubmitResponse> = runCatching {
        val serverApi = httpClient.serverApi(serverUrl)

        // Step 1: Get challenge
        val challengeResp = serverApi.requestChallenge(TxChallengeRequest(sessionToken))

        // Step 2: Hash transaction body
        val txJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(transaction.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })
        )
        val txHash = SHA3.Digest256().digest(txJson.toByteArray())
        val txHashBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(txHash)

        // Step 3: Sign challenge || txHash (transaction binding)
        val payload = (challengeResp.challenge + txHashBase64).toByteArray()
        val signatureBytes = vault.sign(identity.keyId, payload).getOrThrow()
        val signedChallenge = Multibase.encode(signatureBytes)

        // Step 4: Submit
        serverApi.submitTransaction(
            TxSubmitRequest(
                session_token = sessionToken,
                did = identity.did,
                key_id = identity.keyId,
                signed_challenge = signedChallenge,
                transaction = transaction
            )
        )
    }
}
```

**Step 2: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt
git commit -m "feat(android): add SsdidClient orchestrating all 4 SSDID flows"
```

---

### Task 9: Hilt Dependency Injection Module

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`

**Step 1: Create DI module**

Create `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`:
```kotlin
package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.PqcProvider
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.vault.VaultImpl
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.domain.verifier.Verifier
import my.ssdid.wallet.domain.verifier.VerifierImpl
import my.ssdid.wallet.platform.keystore.AndroidKeystoreManager
import my.ssdid.wallet.platform.keystore.KeystoreManager
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideKeystoreManager(): KeystoreManager = AndroidKeystoreManager()

    @Provides @Singleton @Named("classical")
    fun provideClassicalProvider(): CryptoProvider = ClassicalProvider()

    @Provides @Singleton @Named("pqc")
    fun providePqcProvider(): CryptoProvider = PqcProvider()

    @Provides @Singleton
    fun provideHttpClient(): SsdidHttpClient {
        // Default registry URL — configurable via Settings
        return SsdidHttpClient(registryUrl = "https://registry.ssdid.my")
    }

    @Provides @Singleton
    fun provideVault(
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider,
        keystoreManager: KeystoreManager,
        storage: VaultStorage
    ): Vault = VaultImpl(classical, pqc, keystoreManager, storage)

    @Provides @Singleton
    fun provideVerifier(
        httpClient: SsdidHttpClient,
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider
    ): Verifier = VerifierImpl(httpClient.registry, classical, pqc)

    @Provides @Singleton
    fun provideSsdidClient(vault: Vault, verifier: Verifier, httpClient: SsdidHttpClient): SsdidClient {
        return SsdidClient(vault, verifier, httpClient)
    }
}
```

**Step 2: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/di/
git commit -m "feat(android): add Hilt DI module wiring all components"
```

---

### Task 10: UI — Theme, Navigation, and All 13 Screens

This is a large task. Each screen corresponds to a Composable. Create them following the mockup at `mockup/index.html`.

**Files to create:**
- `android/app/src/main/java/my/ssdid/wallet/ui/theme/Theme.kt`
- `android/app/src/main/java/my/ssdid/wallet/ui/theme/Color.kt`
- `android/app/src/main/java/my/ssdid/wallet/ui/theme/Type.kt`
- `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`
- `android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`
- `android/app/src/main/java/my/ssdid/wallet/MainActivity.kt`
- One file per screen under `android/app/src/main/java/my/ssdid/wallet/feature/*/`
- One ViewModel per feature under same directories

**Key implementation notes:**
- Theme colors from mockup CSS: `--bg-primary: #0a0c10`, `--accent: #4a9eff`, `--pqc: #a78bfa`, etc.
- Font: DM Sans (via Google Fonts for Compose or bundled)
- Monospace: JetBrains Mono for DID/key values
- Navigation: Compose Navigation with bottom tab bar (Home, Credentials, Scan, History, Settings)
- Each ViewModel injects `SsdidClient` via Hilt and exposes UI state via `StateFlow`

This task should be broken into sub-commits:
1. Theme + navigation shell + MainActivity
2. Onboarding screens (3 slides)
3. Create Identity + Biometric Setup screens
4. Wallet Home screen
5. Identity Detail screen
6. Credentials + Credential Detail screens
7. Scan QR screen (CameraX + ML Kit)
8. Registration flow screen
9. Authentication flow screen
10. Transaction signing screen
11. Settings screen
12. Activity History screen

Each sub-commit follows the pattern: create ViewModel → create Composable → wire into NavGraph → commit.

---

### Task 11: QR Code Scanner (CameraX + ML Kit)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/scan/QrScanner.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/scan/ScanViewModel.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/scan/ScanScreen.kt`

QR payload format:
```json
{"server_url": "https://...", "server_did": "did:ssdid:...", "action": "register|authenticate|sign"}
```

Parse QR → route to Registration, Auth, or TX Signing screen with extracted params.

---

### Task 12: Deep Links

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/MainActivity.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt`

URI scheme: `ssdid://action?server_url=...&server_did=...&action=register`

Parse intent URI → route to appropriate screen (same as QR handler).

---

### Task 13: i18n Strings

**Files:**
- Create: `android/app/src/main/res/values/strings.xml` (English)
- Create: `android/app/src/main/res/values-ms/strings.xml` (Malay)
- Create: `android/app/src/main/res/values-zh/strings.xml` (Chinese)

All user-visible strings extracted to resources. English first, Malay and Chinese stubs.

---

### Task 14: Biometric Prompt Integration

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/biometric/BiometricAuthenticator.kt`

Uses `androidx.biometric.BiometricPrompt` to gate vault operations. Wraps the Android Keystore `setUserAuthenticationRequired(true)` so that `keystoreManager.decrypt()` triggers the biometric prompt automatically.

---

### Task 15: Persistent Storage (DataStore + Encrypted Files)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreVaultStorage.kt`

Implements `VaultStorage` interface using:
- DataStore Preferences for identity metadata
- Encrypted files for private keys (already encrypted by hardware keystore)
- DataStore or Room for credential and activity history

---

## Phase 1B: Gap Remediation (Android)

> These tasks implement features identified in `docs/12.SSDID-Gap-Analysis-And-Remediation.md`. They build on the Phase 1 codebase and follow the same architecture patterns (UI → Feature → Domain → Platform).

### Task 16: Model Prerequisites (Gap 9 & Gap 10)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/DidDocument.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/VerifiableCredential.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/model/CredentialStatus.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/verifier/VerifierImpl.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/model/CredentialStatusTest.kt`

**Step 1: Add `nextKeyHash` to DidDocument**

```kotlin
// In DidDocument.kt — add field:
@Serializable
data class DidDocument(
    val id: String,
    val verificationMethod: List<VerificationMethod>,
    val authentication: List<String>,
    val capabilityInvocation: List<String> = emptyList(),
    val nextKeyHash: String? = null  // SHA3-256 hash of pre-committed next key (KERI)
)
```

**Step 2: Create CredentialStatus model**

```kotlin
// CredentialStatus.kt
@Serializable
data class CredentialStatus(
    val id: String,                    // e.g. "https://registry.example/api/status/1#42"
    val type: String,                  // "BitstringStatusListEntry"
    val statusPurpose: String,         // "revocation"
    val statusListIndex: String,       // "42"
    val statusListCredential: String   // "https://registry.example/api/status/1"
)
```

**Step 3: Add `credentialStatus` to VerifiableCredential**

```kotlin
// In VerifiableCredential.kt — add optional field:
val credentialStatus: CredentialStatus? = null
```

**Step 4: Ensure VerifierImpl uses `ignoreUnknownKeys = true`**

```kotlin
// In VerifierImpl.kt — ensure the Json instance handles new fields:
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
```

**Step 5: Write tests**

```kotlin
@Test
fun `DidDocument deserializes with nextKeyHash`() {
    val json = """{"id":"did:ssdid:abc","verificationMethod":[],"authentication":[],"nextKeyHash":"uSHA3hash"}"""
    val doc = Json { ignoreUnknownKeys = true }.decodeFromString<DidDocument>(json)
    assertThat(doc.nextKeyHash).isEqualTo("uSHA3hash")
}

@Test
fun `CredentialStatus serialization round-trip`() {
    val status = CredentialStatus(
        id = "https://reg.example/api/status/1#42",
        type = "BitstringStatusListEntry",
        statusPurpose = "revocation",
        statusListIndex = "42",
        statusListCredential = "https://reg.example/api/status/1"
    )
    val json = Json.encodeToString(status)
    val decoded = Json.decodeFromString<CredentialStatus>(json)
    assertThat(decoded).isEqualTo(status)
}
```

**Step 6: Run tests, commit**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*.CredentialStatusTest"
git add -A && git commit -m "feat(android): add nextKeyHash and credentialStatus model fields (Gap 9, 10)"
```

---

### Task 17: Recovery Key Generation (Gap 1 — Tier 1)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/Identity.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/recovery/RecoveryManager.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/recovery/RecoverySetupScreen.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/recovery/RecoveryManagerTest.kt`

**Step 1: Add recovery key fields to Identity**

```kotlin
// In Identity.kt — add:
val recoveryKeyId: String? = null,
val hasRecoveryKey: Boolean = false
```

**Step 2: Create RecoveryManager**

```kotlin
@Singleton
class RecoveryManager @Inject constructor(
    private val vault: Vault,
    private val classicalProvider: ClassicalProvider
) {
    /**
     * Generate a recovery keypair for the given identity.
     * Returns the recovery private key bytes — caller must export/store offline.
     * The recovery public key is added to the DID Document.
     */
    suspend fun generateRecoveryKey(identity: Identity): Result<ByteArray> {
        val algo = identity.algorithm
        val recoveryKeyPair = classicalProvider.generateKeyPair(algo)
        // Store recovery public key reference in identity metadata
        val recoveryKeyId = "${identity.keyId}-recovery"
        vault.storeRecoveryPublicKey(recoveryKeyId, recoveryKeyPair.publicKey, algo)
        return Result.success(recoveryKeyPair.privateKey)
    }

    /**
     * Recover identity using offline recovery key.
     * Signs a DID Document update removing old device key, adding new device key.
     */
    suspend fun recoverWithKey(
        did: String,
        recoveryPrivateKey: ByteArray,
        newDevicePublicKey: ByteArray
    ): Result<Unit> {
        // Build rotation request signed by recovery key
        // Submit to registry
        return Result.success(Unit)
    }
}
```

**Step 3: Add Vault interface methods**

```kotlin
// In Vault.kt — add:
suspend fun storeRecoveryPublicKey(keyId: String, publicKey: ByteArray, algorithm: Algorithm)
suspend fun getRecoveryPublicKey(keyId: String): ByteArray?
```

**Step 4: Create RecoverySetupScreen**

- Header with back arrow
- Three tier cards matching mockup screen 14
- Tier 1 button calls `viewModel.generateRecoveryKey()`
- On success, display recovery key as QR code or copyable text for offline storage
- Tiers 2 and 3 show "Coming Soon" disabled state

**Step 5: Add navigation route**

```kotlin
// In NavGraph.kt — add route:
composable("recovery-setup/{keyId}") { backStackEntry ->
    val keyId = backStackEntry.arguments?.getString("keyId") ?: return@composable
    RecoverySetupScreen(
        keyId = keyId,
        onBack = { navController.popBackStack() }
    )
}
```

**Step 6: Write tests**

```kotlin
@Test
fun `generateRecoveryKey returns private key bytes`() = runTest {
    val identity = Identity(keyId = "key1", did = "did:ssdid:abc", name = "Test", ...)
    val result = recoveryManager.generateRecoveryKey(identity)
    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrNull()).isNotEmpty()
}
```

**Step 7: Run tests, commit**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*.RecoveryManagerTest"
git add -A && git commit -m "feat(android): add recovery key generation (Gap 1 Tier 1)"
```

---

### Task 18: Encrypted Backup & Export (Gap 5)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/backup/BackupManager.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/backup/BackupFormat.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/backup/BackupScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/backup/BackupManagerTest.kt`

**Step 1: Create BackupFormat data classes**

```kotlin
@Serializable
data class BackupPackage(
    val version: Int = 1,
    val salt: String,       // base64url
    val nonce: String,      // base64url
    val ciphertext: String, // base64url
    val algorithms: List<String>,
    val dids: List<String>,
    val createdAt: String,
    val hmac: String        // base64url, HMAC-SHA256 over package (using mac_key)
)

@Serializable
data class BackupPayload(
    val identities: List<BackupIdentity>
)

@Serializable
data class BackupIdentity(
    val keyId: String,
    val did: String,
    val name: String,
    val algorithm: String,
    val privateKey: String,  // base64url
    val publicKey: String,   // base64url
    val createdAt: String
)
```

**Step 2: Create BackupManager**

```kotlin
@Singleton
class BackupManager @Inject constructor(
    private val vault: Vault,
    private val biometricAuthenticator: BiometricAuthenticator
) {
    companion object {
        private const val ARGON2_TIME = 3
        private const val ARGON2_MEMORY = 65536 // 64MB
        private const val ARGON2_PARALLELISM = 1
        private const val SALT_LENGTH = 32
        private const val NONCE_LENGTH = 12
    }

    /**
     * Create encrypted backup of all identities.
     * Requires biometric auth to unlock hardware keystore.
     * Passphrase encrypts the backup via Argon2id + AES-256-GCM.
     * Uses HKDF to derive separate enc_key and mac_key.
     */
    suspend fun createBackup(passphrase: String): Result<ByteArray>

    /**
     * Restore identities from encrypted backup.
     */
    suspend fun restoreBackup(backupData: ByteArray, passphrase: String): Result<Int>
}
```

**Step 3: Create BackupScreen** matching mockup screen 16

- Passphrase and confirm inputs
- Strength indicator
- Create Backup button (triggers biometric → export)
- Import Backup File button (file picker → passphrase prompt → restore)

**Step 4: Add route and DI**

**Step 5: Write tests**

```kotlin
@Test
fun `createBackup and restoreBackup round-trip`() = runTest {
    // Setup: create an identity in vault
    // Create backup with passphrase
    // Restore backup with same passphrase
    // Verify identity is restored
}

@Test
fun `restoreBackup fails with wrong passphrase`() = runTest {
    // Create backup
    // Attempt restore with different passphrase
    // Verify failure
}

@Test
fun `backup HMAC verification catches tampering`() = runTest {
    // Create backup
    // Tamper with ciphertext
    // Verify HMAC check fails before decryption
}
```

**Step 6: Run tests, commit**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*.BackupManagerTest"
git add -A && git commit -m "feat(android): add encrypted backup and restore (Gap 5)"
```

---

### Task 19: Key Rotation with Pre-Commitment (Gap 2)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/rotation/KeyRotationManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/rotation/KeyRotationScreen.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/rotation/KeyRotationManagerTest.kt`

**Dependencies:** Task 16 (nextKeyHash model field)

**Step 1: Create KeyRotationManager**

```kotlin
@Singleton
class KeyRotationManager @Inject constructor(
    private val vault: Vault,
    private val client: SsdidClient
) {
    /**
     * Prepare rotation: generate next keypair, compute SHA3-256 hash,
     * store pre-rotated key securely. Returns the hash to publish.
     */
    suspend fun prepareRotation(identity: Identity): Result<String>

    /**
     * Execute rotation: reveal pre-committed key, generate next pre-commitment,
     * sign DID Document update with current key, submit to registry.
     */
    suspend fun executeRotation(identity: Identity): Result<Identity>

    /**
     * Get pre-rotation status for an identity.
     */
    suspend fun getRotationStatus(identity: Identity): RotationStatus
}

data class RotationStatus(
    val hasPreCommitment: Boolean,
    val nextKeyHash: String?,
    val lastRotatedAt: String?,
    val rotationHistory: List<RotationEntry>
)

data class RotationEntry(
    val timestamp: String,
    val oldKeyIdFragment: String,
    val newKeyIdFragment: String
)
```

**Step 2: Add Vault methods for pre-rotated keys**

```kotlin
// In Vault.kt — add:
suspend fun storePreRotatedKey(identityKeyId: String, preRotatedKeyPair: KeyPair)
suspend fun getPreRotatedKey(identityKeyId: String): KeyPair?
suspend fun promotePreRotatedKey(identityKeyId: String): Identity
```

**Step 3: Add SsdidClient rotation endpoint**

```kotlin
// In SsdidClient.kt — add:
suspend fun submitKeyRotation(
    identity: Identity,
    newPublicKey: ByteArray,
    nextKeyHash: String,
    signedUpdate: ByteArray
): Result<Unit>
```

**Step 4: Create KeyRotationScreen** matching mockup screen 15

- Current key info card
- Pre-commitment status
- "Rotate Now" button
- Warning about grace period
- Rotation history

**Step 5: Write tests**

```kotlin
@Test
fun `prepareRotation generates pre-commitment hash`() = runTest { ... }

@Test
fun `executeRotation promotes pre-rotated key`() = runTest { ... }

@Test
fun `executeRotation fails without pre-commitment`() = runTest { ... }
```

**Step 6: Run tests, commit**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*.KeyRotationManagerTest"
git add -A && git commit -m "feat(android): add key rotation with KERI pre-commitment (Gap 2)"
```

---

### Task 20: Activity History Persistence (Gap 9)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/history/ActivityRepository.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/history/ActivityRepositoryImpl.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/model/ActivityRecord.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/history/TxHistoryScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/history/ActivityRepositoryTest.kt`

**Step 1: Create ActivityRecord model**

```kotlin
@Serializable
data class ActivityRecord(
    val id: String,                    // UUID
    val type: ActivityType,
    val did: String,
    val serviceDid: String? = null,
    val serviceUrl: String? = null,
    val timestamp: String,             // ISO 8601
    val status: ActivityStatus,
    val details: Map<String, String> = emptyMap()
)

@Serializable
enum class ActivityType {
    IDENTITY_CREATED, KEY_ROTATED, DEVICE_ENROLLED, DEVICE_REMOVED,
    SERVICE_REGISTERED, AUTHENTICATED, TX_SIGNED,
    CREDENTIAL_RECEIVED, CREDENTIAL_PRESENTED, BACKUP_CREATED
}

@Serializable
enum class ActivityStatus { SUCCESS, FAILED }
```

**Step 2: Create ActivityRepository interface and DataStore-backed implementation**

**Step 3: Wire into SsdidClient** — record activity after each operation

**Step 4: Update TxHistoryScreen** — read from ActivityRepository instead of empty list

**Step 5: Write tests, commit**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*.ActivityRepositoryTest"
git add -A && git commit -m "feat(android): add persistent activity history (Gap 9)"
```

---

### Task 21: Device Management UI (Gap 3 — Partial)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/device/DeviceManagementScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`

**Note:** This task adds the Device Management UI and navigation only. The actual multi-device enrollment protocol (QR pairing, registry relay) requires backend work and is deferred to a future task. The screen shows current device info and a "Coming Soon" state for enrollment.

**Step 1: Create DeviceManagementScreen** matching mockup screen 17

- "This Device" card with device name, primary badge, key ID
- "Other Devices" section (empty with "No other devices enrolled" message)
- "Enroll New Device" button (disabled with "Coming Soon" tooltip)

**Step 2: Add navigation route**

```kotlin
composable("device-management/{keyId}") { backStackEntry ->
    val keyId = backStackEntry.arguments?.getString("keyId") ?: return@composable
    DeviceManagementScreen(
        keyId = keyId,
        onBack = { navController.popBackStack() }
    )
}
```

**Step 3: Link from IdentityDetailScreen** — add "Devices" action button

**Step 4: Commit**

```bash
git add -A && git commit -m "feat(android): add device management screen placeholder (Gap 3)"
```

---

### Task 22: Settings Navigation to Gap Features

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`

**Step 1: Add settings entries for gap features**

Add these items to the Settings screen:
- "Recovery Setup" → navigates to `recovery-setup/{keyId}` (requires selecting identity first)
- "Backup & Export" → navigates to `backup-export`
- "Key Rotation" → navigates to `key-rotation/{keyId}` (requires selecting identity first)

**Step 2: Wire navigation routes** for all new screens in NavGraph

**Step 3: Commit**

```bash
git add -A && git commit -m "feat(android): wire settings navigation to gap feature screens"
```

---

## Phase 2: iOS (Swift / SwiftUI)

Mirror the Android architecture. Key differences:

| Android | iOS |
|---------|-----|
| Kotlin | Swift |
| Jetpack Compose | SwiftUI |
| Hilt | Swift Package structure + manual DI or Swinject |
| Retrofit + OkHttp | URLSession + async/await |
| Android Keystore | Secure Enclave + Keychain |
| BiometricPrompt | LocalAuthentication (LAContext) |
| CameraX + ML Kit | AVFoundation + Vision framework |
| JNI for KAZ-Sign | C interop (bridging header) |
| DataStore | UserDefaults + Keychain + FileManager |

**Tasks (same domain layer, different platform layer):**
1. Xcode project scaffolding (`ios/` directory)
2. Domain models (port from Kotlin to Swift structs/enums)
3. ClassicalProvider (via CryptoKit: Curve25519, P256, P384)
4. PqcProvider (KAZ-Sign via C bridging header — existing Swift binding)
5. Vault (Secure Enclave wrapping key + Keychain storage)
6. Transport (URLSession + Codable DTOs)
7. Verifier
8. SsdidClient
9. SwiftUI screens (17 screens matching mockup — 13 core + 4 gap feature screens)
10. QR Scanner (AVFoundation)
11. Deep Links (Universal Links)
12. i18n (Localizable.strings)

---

## Phase 3: HarmonyOS NEXT (ArkTS / ArkUI)

Mirror the same architecture. Key differences:

| Android | HarmonyOS NEXT |
|---------|---------------|
| Kotlin | ArkTS (TypeScript-like) |
| Jetpack Compose | ArkUI declarative |
| Android Keystore | HUKS |
| CameraX | @ohos.multimedia.camera |
| JNI | N-API (Node-like C addon interface) |
| ML Kit | @ohos.ai.ocr or custom barcode |

**Additional tasks unique to HarmonyOS:**
1. **New KAZ-Sign N-API binding** — write C++ N-API wrapper around KAZ-Sign C lib, compile as `.so` for HarmonyOS
2. HUKS key management integration
3. ArkUI component library matching the enterprise dark theme

---

## Execution Order Summary

```
Phase 1:  Android (Tasks 1-15)   ← Reference implementation, full TDD
Phase 1B: Gap Remediation (Tasks 16-22) ← Recovery, backup, rotation, history, device mgmt
Phase 2:  iOS (Tasks 1-12)       ← Port domain layer, native platform layer
Phase 3:  HarmonyOS (Tasks 1-12 + N-API binding)
```

Each phase is independently deployable. Start Phase 2 after Phase 1 Tasks 1-8 (domain layer complete). Phase 3 can start after Phase 1 Tasks 1-8 as well. UI tasks (10+) can be parallelized across platforms.

Phase 1B depends on Phase 1 completion. Task 19 (key rotation) depends on Task 16 (model prerequisites). Task 17 (recovery) and Task 18 (backup) are independent of each other.
