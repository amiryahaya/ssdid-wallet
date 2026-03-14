# Phase 3: mdoc, Digital Credentials API & DIDComm v2 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add mdoc/mDL (ISO 18013-5) credential support, Digital Credentials API integration, and DIDComm v2 messaging to the SSDID wallet on both Android and iOS.

**Architecture:** Four sequential sub-phases. 3a adds mdoc core (CBOR codec, MDoc model, MSO, vault storage). 3b extends existing OpenID4VP/VCI for mdoc format. 3c registers the wallet as a system credential provider. 3d adds X25519 key agreement and DIDComm v2 messaging.

**Tech Stack:** Kotlin + com.upokecenter:cbor (Android), Swift + SwiftCBOR (iOS), BouncyCastle X25519, Android CredentialManager API, iOS ASAuthorizationController

---

## Phase 3a: mdoc/mDL Core

### Task 1: Add CBOR dependency

**Files:**
- Modify: `android/app/build.gradle.kts`

**Step 1: Add dependency**

In `build.gradle.kts`, after the BouncyCastle line (`implementation("org.bouncycastle:bcprov-jdk18on:1.80")`), add:

```kotlin
    // CBOR (mdoc/mDL credential format)
    implementation("com.upokecenter:cbor:5.0.0-alpha2")
```

**Step 2: Verify it compiles**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/build.gradle.kts
git commit -m "chore: add CBOR dependency for mdoc support"
```

---

### Task 2: CborCodec — CBOR encode/decode utility

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/mdoc/CborCodec.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/mdoc/CborCodecTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CborCodecTest {

    @Test
    fun roundTripStringValue() {
        val encoded = CborCodec.encodeDataElement("hello")
        val decoded = CborCodec.decodeDataElement(encoded)
        assertThat(decoded).isEqualTo("hello")
    }

    @Test
    fun roundTripIntValue() {
        val encoded = CborCodec.encodeDataElement(42)
        val decoded = CborCodec.decodeDataElement(encoded)
        assertThat(decoded).isEqualTo(42)
    }

    @Test
    fun roundTripByteArray() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val encoded = CborCodec.encodeDataElement(bytes)
        val decoded = CborCodec.decodeDataElement(encoded) as ByteArray
        assertThat(decoded).isEqualTo(bytes)
    }

    @Test
    fun roundTripMap() {
        val map = mapOf("name" to "Alice", "age" to 30)
        val encoded = CborCodec.encodeMap(map)
        val decoded = CborCodec.decodeMap(encoded)
        assertThat(decoded["name"]).isEqualTo("Alice")
        assertThat(decoded["age"]).isEqualTo(30)
    }

    @Test
    fun roundTripNestedMap() {
        val map = mapOf(
            "outer" to mapOf("inner" to "value")
        )
        val encoded = CborCodec.encodeMap(map)
        val decoded = CborCodec.decodeMap(encoded)
        @Suppress("UNCHECKED_CAST")
        val inner = decoded["outer"] as Map<String, Any>
        assertThat(inner["inner"]).isEqualTo("value")
    }

    @Test
    fun encodedBytesAreValidCbor() {
        val encoded = CborCodec.encodeDataElement("test")
        assertThat(encoded).isNotEmpty()
        // Should not throw
        CborCodec.decodeDataElement(encoded)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.CborCodecTest"`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
package my.ssdid.wallet.domain.mdoc

import com.upokecenter.cbor.CBORObject

object CborCodec {

    fun encodeDataElement(value: Any): ByteArray {
        return toCborObject(value).EncodeToBytes()
    }

    fun decodeDataElement(bytes: ByteArray): Any {
        return fromCborObject(CBORObject.DecodeFromBytes(bytes))
    }

    fun encodeMap(map: Map<String, Any>): ByteArray {
        val cbor = CBORObject.NewMap()
        for ((key, value) in map) {
            cbor[key] = toCborObject(value)
        }
        return cbor.EncodeToBytes()
    }

    fun decodeMap(bytes: ByteArray): Map<String, Any> {
        val cbor = CBORObject.DecodeFromBytes(bytes)
        return cborMapToMap(cbor)
    }

    fun toCborObject(value: Any): CBORObject {
        return when (value) {
            is String -> CBORObject.FromObject(value)
            is Int -> CBORObject.FromObject(value)
            is Long -> CBORObject.FromObject(value)
            is Boolean -> CBORObject.FromObject(value)
            is ByteArray -> CBORObject.FromObject(value)
            is Map<*, *> -> {
                val cbor = CBORObject.NewMap()
                @Suppress("UNCHECKED_CAST")
                for ((k, v) in value as Map<String, Any>) {
                    cbor[k] = toCborObject(v)
                }
                cbor
            }
            is List<*> -> {
                val cbor = CBORObject.NewArray()
                for (item in value) {
                    cbor.Add(toCborObject(item ?: ""))
                }
                cbor
            }
            is CBORObject -> value
            else -> CBORObject.FromObject(value.toString())
        }
    }

    fun fromCborObject(cbor: CBORObject): Any {
        return when {
            cbor.isNumber -> if (cbor.CanValueFitInInt32()) cbor.AsInt32Value() else cbor.AsInt64Value()
            cbor.type == com.upokecenter.cbor.CBORType.TextString -> cbor.AsString()
            cbor.type == com.upokecenter.cbor.CBORType.ByteString -> cbor.GetByteString()
            cbor.type == com.upokecenter.cbor.CBORType.Boolean -> cbor.AsBoolean()
            cbor.type == com.upokecenter.cbor.CBORType.Map -> cborMapToMap(cbor)
            cbor.type == com.upokecenter.cbor.CBORType.Array -> cbor.values.map { fromCborObject(it) }
            else -> cbor.toString()
        }
    }

    private fun cborMapToMap(cbor: CBORObject): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for (key in cbor.keys) {
            val keyStr = key.AsString()
            result[keyStr] = fromCborObject(cbor[key])
        }
        return result
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.CborCodecTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/mdoc/CborCodec.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/mdoc/CborCodecTest.kt
git commit -m "feat: add CborCodec for mdoc CBOR encode/decode"
```

---

### Task 3: MDoc model classes

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/mdoc/MDoc.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/mdoc/MDocModelTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MDocModelTest {

    @Test
    fun storedMDocHasCorrectFields() {
        val mdoc = StoredMDoc(
            id = "mdoc-001",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = byteArrayOf(1, 2, 3),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L,
            expiresAt = 1731536000L,
            nameSpaces = mapOf(
                "org.iso.18013.5.1" to listOf("family_name", "given_name", "birth_date")
            )
        )
        assertThat(mdoc.docType).isEqualTo("org.iso.18013.5.1.mDL")
        assertThat(mdoc.nameSpaces).containsKey("org.iso.18013.5.1")
        assertThat(mdoc.nameSpaces["org.iso.18013.5.1"]).hasSize(3)
    }

    @Test
    fun issuerSignedItemHoldsElements() {
        val item = IssuerSignedItem(
            digestId = 0,
            random = byteArrayOf(0xA, 0xB),
            elementIdentifier = "family_name",
            elementValue = "Smith"
        )
        assertThat(item.elementIdentifier).isEqualTo("family_name")
        assertThat(item.elementValue).isEqualTo("Smith")
    }

    @Test
    fun validityInfoHoldsDateStrings() {
        val validity = ValidityInfo(
            signed = "2024-01-01T00:00:00Z",
            validFrom = "2024-01-01T00:00:00Z",
            validUntil = "2025-01-01T00:00:00Z"
        )
        assertThat(validity.validFrom).contains("2024")
    }

    @Test
    fun mobileSecurityObjectHoldsDigests() {
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = mapOf(
                "org.iso.18013.5.1" to mapOf(0 to byteArrayOf(1, 2, 3))
            ),
            deviceKeyInfo = DeviceKeyInfo(deviceKey = byteArrayOf(4, 5, 6)),
            validityInfo = ValidityInfo(
                signed = "2024-01-01T00:00:00Z",
                validFrom = "2024-01-01T00:00:00Z",
                validUntil = "2025-01-01T00:00:00Z"
            )
        )
        assertThat(mso.digestAlgorithm).isEqualTo("SHA-256")
        assertThat(mso.valueDigests).containsKey("org.iso.18013.5.1")
    }

    @Test
    fun issuerSignedHoldsNamespacesAndAuth() {
        val issuerSigned = IssuerSigned(
            nameSpaces = mapOf(
                "org.iso.18013.5.1" to listOf(
                    IssuerSignedItem(0, byteArrayOf(), "family_name", "Smith")
                )
            ),
            issuerAuth = byteArrayOf(0xD2.toByte())
        )
        assertThat(issuerSigned.nameSpaces).hasSize(1)
        assertThat(issuerSigned.issuerAuth).isNotEmpty()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.MDocModelTest"`
Expected: FAIL

**Step 3: Write minimal implementation**

```kotlin
package my.ssdid.wallet.domain.mdoc

data class StoredMDoc(
    val id: String,
    val docType: String,
    val issuerSignedCbor: ByteArray,
    val deviceKeyId: String,
    val issuedAt: Long,
    val expiresAt: Long? = null,
    val nameSpaces: Map<String, List<String>> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredMDoc) return false
        return id == other.id
    }

    override fun hashCode() = id.hashCode()
}

data class IssuerSigned(
    val nameSpaces: Map<String, List<IssuerSignedItem>>,
    val issuerAuth: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IssuerSigned) return false
        return nameSpaces == other.nameSpaces && issuerAuth.contentEquals(other.issuerAuth)
    }

    override fun hashCode() = nameSpaces.hashCode()
}

data class IssuerSignedItem(
    val digestId: Int,
    val random: ByteArray,
    val elementIdentifier: String,
    val elementValue: Any
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IssuerSignedItem) return false
        return digestId == other.digestId && elementIdentifier == other.elementIdentifier
    }

    override fun hashCode() = 31 * digestId + elementIdentifier.hashCode()
}

data class MobileSecurityObject(
    val version: String,
    val digestAlgorithm: String,
    val valueDigests: Map<String, Map<Int, ByteArray>>,
    val deviceKeyInfo: DeviceKeyInfo,
    val validityInfo: ValidityInfo
)

data class DeviceKeyInfo(
    val deviceKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceKeyInfo) return false
        return deviceKey.contentEquals(other.deviceKey)
    }

    override fun hashCode() = deviceKey.contentHashCode()
}

data class ValidityInfo(
    val signed: String,
    val validFrom: String,
    val validUntil: String
)
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.MDocModelTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/mdoc/MDoc.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/mdoc/MDocModelTest.kt
git commit -m "feat: add mdoc model classes (StoredMDoc, IssuerSigned, MSO)"
```

---

### Task 4: MsoParser — parse COSE_Sign1 and extract IssuerSigned

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/mdoc/MsoParser.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/mdoc/MsoParserTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import com.upokecenter.cbor.CBORObject
import org.junit.Test

class MsoParserTest {

    private fun buildTestIssuerSignedItem(
        digestId: Int,
        elementId: String,
        elementValue: String
    ): CBORObject {
        val item = CBORObject.NewMap()
        item["digestID"] = CBORObject.FromObject(digestId)
        item["random"] = CBORObject.FromObject(byteArrayOf(0xA, 0xB, 0xC))
        item["elementIdentifier"] = CBORObject.FromObject(elementId)
        item["elementValue"] = CBORObject.FromObject(elementValue)
        return item
    }

    private fun buildTestIssuerSigned(): ByteArray {
        val nameSpaces = CBORObject.NewMap()
        val items = CBORObject.NewArray()
        items.Add(CBORObject.FromObjectAndTag(
            buildTestIssuerSignedItem(0, "family_name", "Smith").EncodeToBytes(), 24
        ))
        items.Add(CBORObject.FromObjectAndTag(
            buildTestIssuerSignedItem(1, "given_name", "Alice").EncodeToBytes(), 24
        ))
        nameSpaces["org.iso.18013.5.1"] = items

        // Build a minimal COSE_Sign1 (tag 18) for issuerAuth
        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))  // protected header
        coseSign1.Add(CBORObject.NewMap())                    // unprotected header
        // payload = MSO CBOR
        val mso = CBORObject.NewMap()
        mso["version"] = CBORObject.FromObject("1.0")
        mso["digestAlgorithm"] = CBORObject.FromObject("SHA-256")
        val digests = CBORObject.NewMap()
        val nsDigests = CBORObject.NewMap()
        nsDigests[CBORObject.FromObject(0)] = CBORObject.FromObject(byteArrayOf(1, 2, 3))
        nsDigests[CBORObject.FromObject(1)] = CBORObject.FromObject(byteArrayOf(4, 5, 6))
        digests["org.iso.18013.5.1"] = nsDigests
        mso["valueDigests"] = digests
        val deviceKeyInfo = CBORObject.NewMap()
        deviceKeyInfo["deviceKey"] = CBORObject.FromObject(byteArrayOf(7, 8, 9))
        mso["deviceKeyInfo"] = deviceKeyInfo
        val validityInfo = CBORObject.NewMap()
        validityInfo["signed"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validFrom"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validUntil"] = CBORObject.FromObject("2025-01-01T00:00:00Z")
        mso["validityInfo"] = validityInfo
        coseSign1.Add(CBORObject.FromObject(mso.EncodeToBytes()))  // payload
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))        // signature

        val issuerSigned = CBORObject.NewMap()
        issuerSigned["nameSpaces"] = nameSpaces
        issuerSigned["issuerAuth"] = CBORObject.FromObjectAndTag(coseSign1, 18)

        return issuerSigned.EncodeToBytes()
    }

    @Test
    fun parseIssuerSignedExtractsNamespaces() {
        val cbor = buildTestIssuerSigned()
        val parsed = MsoParser.parseIssuerSigned(cbor)
        assertThat(parsed.nameSpaces).containsKey("org.iso.18013.5.1")
        assertThat(parsed.nameSpaces["org.iso.18013.5.1"]).hasSize(2)
    }

    @Test
    fun parseIssuerSignedExtractsElementIdentifiers() {
        val cbor = buildTestIssuerSigned()
        val parsed = MsoParser.parseIssuerSigned(cbor)
        val items = parsed.nameSpaces["org.iso.18013.5.1"]!!
        assertThat(items.map { it.elementIdentifier }).containsExactly("family_name", "given_name")
    }

    @Test
    fun parseMsoFromIssuerAuth() {
        val cbor = buildTestIssuerSigned()
        val parsed = MsoParser.parseIssuerSigned(cbor)
        val mso = MsoParser.parseMso(parsed.issuerAuth)
        assertThat(mso.version).isEqualTo("1.0")
        assertThat(mso.digestAlgorithm).isEqualTo("SHA-256")
        assertThat(mso.valueDigests).containsKey("org.iso.18013.5.1")
    }

    @Test
    fun parseMsoExtractsValidityInfo() {
        val cbor = buildTestIssuerSigned()
        val parsed = MsoParser.parseIssuerSigned(cbor)
        val mso = MsoParser.parseMso(parsed.issuerAuth)
        assertThat(mso.validityInfo.validFrom).isEqualTo("2024-01-01T00:00:00Z")
        assertThat(mso.validityInfo.validUntil).isEqualTo("2025-01-01T00:00:00Z")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.MsoParserTest"`
Expected: FAIL

**Step 3: Write minimal implementation**

```kotlin
package my.ssdid.wallet.domain.mdoc

import com.upokecenter.cbor.CBORObject

object MsoParser {

    fun parseIssuerSigned(cborBytes: ByteArray): IssuerSigned {
        val cbor = CBORObject.DecodeFromBytes(cborBytes)

        val nameSpacesMap = mutableMapOf<String, List<IssuerSignedItem>>()
        val nameSpacesCbor = cbor["nameSpaces"]
        for (nsKey in nameSpacesCbor.keys) {
            val namespace = nsKey.AsString()
            val itemsArray = nameSpacesCbor[nsKey]
            val items = mutableListOf<IssuerSignedItem>()
            for (i in 0 until itemsArray.size()) {
                val taggedItem = itemsArray[i]
                // Items are tagged with tag 24 (encoded CBOR data item)
                val itemBytes = taggedItem.GetByteString()
                val itemCbor = CBORObject.DecodeFromBytes(itemBytes)
                items.add(parseIssuerSignedItem(itemCbor))
            }
            nameSpacesMap[namespace] = items
        }

        val issuerAuth = cbor["issuerAuth"]
        return IssuerSigned(
            nameSpaces = nameSpacesMap,
            issuerAuth = issuerAuth.EncodeToBytes()
        )
    }

    fun parseMso(issuerAuthBytes: ByteArray): MobileSecurityObject {
        val coseSign1 = CBORObject.DecodeFromBytes(issuerAuthBytes)
        // COSE_Sign1 = [protected, unprotected, payload, signature]
        val inner = if (coseSign1.isTagged) coseSign1.UntagOne() else coseSign1
        val payloadBytes = inner[2].GetByteString()
        val mso = CBORObject.DecodeFromBytes(payloadBytes)

        val version = mso["version"].AsString()
        val digestAlgorithm = mso["digestAlgorithm"].AsString()

        val valueDigests = mutableMapOf<String, Map<Int, ByteArray>>()
        val digestsCbor = mso["valueDigests"]
        for (nsKey in digestsCbor.keys) {
            val namespace = nsKey.AsString()
            val nsDigests = mutableMapOf<Int, ByteArray>()
            val nsDigestsCbor = digestsCbor[nsKey]
            for (dKey in nsDigestsCbor.keys) {
                val digestId = dKey.AsInt32Value()
                nsDigests[digestId] = nsDigestsCbor[dKey].GetByteString()
            }
            valueDigests[namespace] = nsDigests
        }

        val deviceKeyInfo = DeviceKeyInfo(
            deviceKey = mso["deviceKeyInfo"]["deviceKey"].GetByteString()
        )

        val validityInfoCbor = mso["validityInfo"]
        val validityInfo = ValidityInfo(
            signed = validityInfoCbor["signed"].AsString(),
            validFrom = validityInfoCbor["validFrom"].AsString(),
            validUntil = validityInfoCbor["validUntil"].AsString()
        )

        return MobileSecurityObject(
            version = version,
            digestAlgorithm = digestAlgorithm,
            valueDigests = valueDigests,
            deviceKeyInfo = deviceKeyInfo,
            validityInfo = validityInfo
        )
    }

    private fun parseIssuerSignedItem(cbor: CBORObject): IssuerSignedItem {
        return IssuerSignedItem(
            digestId = cbor["digestID"].AsInt32Value(),
            random = cbor["random"].GetByteString(),
            elementIdentifier = cbor["elementIdentifier"].AsString(),
            elementValue = CborCodec.fromCborObject(cbor["elementValue"])
        )
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.MsoParserTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/mdoc/MsoParser.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/mdoc/MsoParserTest.kt
git commit -m "feat: add MsoParser for COSE_Sign1 and IssuerSigned decoding"
```

---

### Task 5: MsoVerifier — validate digests and validity

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/mdoc/MsoVerifier.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/mdoc/MsoVerifierTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import com.upokecenter.cbor.CBORObject
import org.junit.Test
import java.security.MessageDigest

class MsoVerifierTest {

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun buildItemCbor(digestId: Int, elementId: String, value: String): ByteArray {
        val item = CBORObject.NewMap()
        item["digestID"] = CBORObject.FromObject(digestId)
        item["random"] = CBORObject.FromObject(byteArrayOf(0xA, 0xB))
        item["elementIdentifier"] = CBORObject.FromObject(elementId)
        item["elementValue"] = CBORObject.FromObject(value)
        return item.EncodeToBytes()
    }

    @Test
    fun verifyDigestPassesForValidItem() {
        val itemCbor = buildItemCbor(0, "family_name", "Smith")
        val digest = sha256(itemCbor)

        val item = IssuerSignedItem(0, byteArrayOf(0xA, 0xB), "family_name", "Smith")
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = mapOf("ns1" to mapOf(0 to digest)),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )

        assertThat(MsoVerifier.verifyDigest(item, mso, "ns1")).isTrue()
    }

    @Test
    fun verifyDigestFailsForTamperedValue() {
        val itemCbor = buildItemCbor(0, "family_name", "Smith")
        val digest = sha256(itemCbor)

        // Tampered item with different value
        val item = IssuerSignedItem(0, byteArrayOf(0xA, 0xB), "family_name", "Jones")
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = mapOf("ns1" to mapOf(0 to digest)),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )

        assertThat(MsoVerifier.verifyDigest(item, mso, "ns1")).isFalse()
    }

    @Test
    fun verifyValidityPassesForCurrentDate() {
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = emptyMap(),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )
        assertThat(MsoVerifier.verifyValidity(mso)).isTrue()
    }

    @Test
    fun verifyValidityFailsForExpiredMso() {
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = emptyMap(),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2020-01-01T00:00:00Z", "2020-01-01T00:00:00Z", "2021-01-01T00:00:00Z")
        )
        assertThat(MsoVerifier.verifyValidity(mso)).isFalse()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.MsoVerifierTest"`
Expected: FAIL

**Step 3: Write minimal implementation**

```kotlin
package my.ssdid.wallet.domain.mdoc

import com.upokecenter.cbor.CBORObject
import java.security.MessageDigest
import java.time.Instant

object MsoVerifier {

    fun verifyDigest(
        item: IssuerSignedItem,
        mso: MobileSecurityObject,
        namespace: String
    ): Boolean {
        val expectedDigest = mso.valueDigests[namespace]?.get(item.digestId)
            ?: return false

        // Reconstruct the item CBOR and hash it
        val itemCbor = CBORObject.NewMap()
        itemCbor["digestID"] = CBORObject.FromObject(item.digestId)
        itemCbor["random"] = CBORObject.FromObject(item.random)
        itemCbor["elementIdentifier"] = CBORObject.FromObject(item.elementIdentifier)
        itemCbor["elementValue"] = CborCodec.toCborObject(item.elementValue)
        val itemBytes = itemCbor.EncodeToBytes()

        val digest = when (mso.digestAlgorithm) {
            "SHA-256" -> MessageDigest.getInstance("SHA-256").digest(itemBytes)
            "SHA-384" -> MessageDigest.getInstance("SHA-384").digest(itemBytes)
            "SHA-512" -> MessageDigest.getInstance("SHA-512").digest(itemBytes)
            else -> return false
        }

        return digest.contentEquals(expectedDigest)
    }

    fun verifyValidity(mso: MobileSecurityObject): Boolean {
        val now = Instant.now()
        val validFrom = Instant.parse(mso.validityInfo.validFrom)
        val validUntil = Instant.parse(mso.validityInfo.validUntil)
        return !now.isBefore(validFrom) && !now.isAfter(validUntil)
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.MsoVerifierTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/mdoc/MsoVerifier.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/mdoc/MsoVerifierTest.kt
git commit -m "feat: add MsoVerifier for digest and validity checks"
```

---

### Task 6: MDocPresenter — selective disclosure

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/mdoc/MDocPresenter.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/mdoc/MDocPresenterTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MDocPresenterTest {

    private val fullIssuerSigned = IssuerSigned(
        nameSpaces = mapOf(
            "org.iso.18013.5.1" to listOf(
                IssuerSignedItem(0, byteArrayOf(1), "family_name", "Smith"),
                IssuerSignedItem(1, byteArrayOf(2), "given_name", "Alice"),
                IssuerSignedItem(2, byteArrayOf(3), "birth_date", "1990-01-01"),
                IssuerSignedItem(3, byteArrayOf(4), "issue_date", "2024-01-01")
            )
        ),
        issuerAuth = byteArrayOf(0xD2.toByte())
    )

    @Test
    fun presentReturnsOnlyRequestedElements() {
        val requested = mapOf("org.iso.18013.5.1" to listOf("family_name", "birth_date"))
        val presented = MDocPresenter.present(fullIssuerSigned, requested)

        val items = presented.nameSpaces["org.iso.18013.5.1"]!!
        assertThat(items).hasSize(2)
        assertThat(items.map { it.elementIdentifier }).containsExactly("family_name", "birth_date")
    }

    @Test
    fun presentPreservesIssuerAuth() {
        val requested = mapOf("org.iso.18013.5.1" to listOf("given_name"))
        val presented = MDocPresenter.present(fullIssuerSigned, requested)
        assertThat(presented.issuerAuth).isEqualTo(fullIssuerSigned.issuerAuth)
    }

    @Test
    fun presentExcludesUnrequestedElements() {
        val requested = mapOf("org.iso.18013.5.1" to listOf("family_name"))
        val presented = MDocPresenter.present(fullIssuerSigned, requested)

        val items = presented.nameSpaces["org.iso.18013.5.1"]!!
        assertThat(items.map { it.elementIdentifier }).doesNotContain("given_name")
        assertThat(items.map { it.elementIdentifier }).doesNotContain("birth_date")
    }

    @Test
    fun presentEmptyRequestedReturnsEmptyNamespace() {
        val requested = mapOf("org.iso.18013.5.1" to emptyList<String>())
        val presented = MDocPresenter.present(fullIssuerSigned, requested)
        assertThat(presented.nameSpaces["org.iso.18013.5.1"]).isEmpty()
    }

    @Test
    fun presentUnknownNamespaceIgnored() {
        val requested = mapOf("unknown.namespace" to listOf("field"))
        val presented = MDocPresenter.present(fullIssuerSigned, requested)
        assertThat(presented.nameSpaces).doesNotContainKey("unknown.namespace")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.MDocPresenterTest"`
Expected: FAIL

**Step 3: Write minimal implementation**

```kotlin
package my.ssdid.wallet.domain.mdoc

object MDocPresenter {

    fun present(
        issuerSigned: IssuerSigned,
        requestedElements: Map<String, List<String>>
    ): IssuerSigned {
        val filteredNameSpaces = mutableMapOf<String, List<IssuerSignedItem>>()

        for ((namespace, requestedIds) in requestedElements) {
            val items = issuerSigned.nameSpaces[namespace] ?: continue
            val filtered = items.filter { it.elementIdentifier in requestedIds }
            filteredNameSpaces[namespace] = filtered
        }

        return IssuerSigned(
            nameSpaces = filteredNameSpaces,
            issuerAuth = issuerSigned.issuerAuth
        )
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.MDocPresenterTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/mdoc/MDocPresenter.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/mdoc/MDocPresenterTest.kt
git commit -m "feat: add MDocPresenter for selective disclosure"
```

---

### Task 7: Extend VaultStorage and Vault with mdoc CRUD

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultStorage.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreVaultStorage.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/mdoc/VaultMDocTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.vault.VaultStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.Test

class VaultMDocTest {

    private val storage = mockk<VaultStorage>(relaxed = true)

    @Test
    fun saveMDocCallsStorage() = runTest {
        val mdoc = StoredMDoc(
            id = "mdoc-001",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = byteArrayOf(1, 2, 3),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L
        )
        storage.saveMDoc(mdoc)
        coVerify { storage.saveMDoc(mdoc) }
    }

    @Test
    fun listMDocsReturnsStoredDocs() = runTest {
        val mdoc = StoredMDoc(
            id = "mdoc-001",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = byteArrayOf(1, 2, 3),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L
        )
        coEvery { storage.listMDocs() } returns listOf(mdoc)
        val result = storage.listMDocs()
        assertThat(result).hasSize(1)
        assertThat(result[0].docType).isEqualTo("org.iso.18013.5.1.mDL")
    }

    @Test
    fun getMDocReturnsById() = runTest {
        val mdoc = StoredMDoc(
            id = "mdoc-001",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = byteArrayOf(1, 2, 3),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L
        )
        coEvery { storage.getMDoc("mdoc-001") } returns mdoc
        val result = storage.getMDoc("mdoc-001")
        assertThat(result?.id).isEqualTo("mdoc-001")
    }

    @Test
    fun deleteMDocRemovesFromStorage() = runTest {
        storage.deleteMDoc("mdoc-001")
        coVerify { storage.deleteMDoc("mdoc-001") }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.VaultMDocTest"`
Expected: FAIL — methods don't exist

**Step 3: Write minimal implementation**

Add to `VaultStorage.kt` (after the SD-JWT VC section):

```kotlin
    // mdoc/mDL storage
    suspend fun saveMDoc(mdoc: my.ssdid.wallet.domain.mdoc.StoredMDoc)
    suspend fun listMDocs(): List<my.ssdid.wallet.domain.mdoc.StoredMDoc>
    suspend fun getMDoc(id: String): my.ssdid.wallet.domain.mdoc.StoredMDoc?
    suspend fun deleteMDoc(id: String)
```

Add to `Vault.kt`:

```kotlin
    suspend fun storeMDoc(mdoc: my.ssdid.wallet.domain.mdoc.StoredMDoc)
    suspend fun listMDocs(): List<my.ssdid.wallet.domain.mdoc.StoredMDoc>
    suspend fun getMDoc(id: String): my.ssdid.wallet.domain.mdoc.StoredMDoc?
    suspend fun deleteMDoc(id: String)
```

Add implementations to `VaultImpl.kt`:

```kotlin
    override suspend fun storeMDoc(mdoc: StoredMDoc) {
        storage.saveMDoc(mdoc)
    }

    override suspend fun listMDocs(): List<StoredMDoc> = storage.listMDocs()

    override suspend fun getMDoc(id: String): StoredMDoc? = storage.getMDoc(id)

    override suspend fun deleteMDoc(id: String) = storage.deleteMDoc(id)
```

Add implementations to `DataStoreVaultStorage.kt` using Base64-encoded CBOR bytes in a JSON wrapper stored in DataStore (following the same pattern as `saveSdJwtVc`).

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.mdoc.VaultMDocTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultStorage.kt \
        android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt \
        android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt \
        android/app/src/main/java/my/ssdid/wallet/platform/storage/DataStoreVaultStorage.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/mdoc/VaultMDocTest.kt
git commit -m "feat: extend Vault with mdoc CRUD operations"
```

---

### Task 8: iOS mdoc core mirror

**Files:**
- Create: `ios/SsdidWallet/Domain/MDoc/CborCodec.swift`
- Create: `ios/SsdidWallet/Domain/MDoc/MDoc.swift`
- Create: `ios/SsdidWallet/Domain/MDoc/MsoParser.swift`
- Create: `ios/SsdidWallet/Domain/MDoc/MsoVerifier.swift`
- Create: `ios/SsdidWallet/Domain/MDoc/MDocPresenter.swift`
- Modify: `ios/SsdidWallet/Platform/Storage/VaultStorage.swift`
- Modify: `ios/SsdidWallet/Domain/Vault/Vault.swift`
- Test: `ios/SsdidWalletTests/Domain/MDoc/CborCodecTests.swift`
- Test: `ios/SsdidWalletTests/Domain/MDoc/MDocModelTests.swift`
- Test: `ios/SsdidWalletTests/Domain/MDoc/MsoParserTests.swift`
- Test: `ios/SsdidWalletTests/Domain/MDoc/MsoVerifierTests.swift`
- Test: `ios/SsdidWalletTests/Domain/MDoc/MDocPresenterTests.swift`

Mirror Android Tasks 2-7 in Swift. Use `SwiftCBOR` library or Foundation's built-in CBOR support via `CBORCoding`. Models mirror Android exactly. Vault protocol gets `saveMDoc`, `listMDocs`, `getMDoc`, `deleteMDoc`.

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Domain/MDoc/ ios/SsdidWalletTests/Domain/MDoc/ \
        ios/SsdidWallet/Platform/Storage/VaultStorage.swift \
        ios/SsdidWallet/Domain/Vault/Vault.swift
git commit -m "feat(ios): add mdoc core domain layer mirroring Android"
```

---

## Phase 3b: mdoc via OpenID4VP/VCI

### Task 9: Refactor MatchResult to use CredentialRef

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/CredentialRef.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcher.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcher.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandler.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/VpTokenBuilder.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/presentation/PresentationRequestViewModel.kt`
- Modify: all OID4VP test files
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/CredentialRefTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class CredentialRefTest {

    @Test
    fun sdJwtRefWrapsCredential() {
        val vc = StoredSdJwtVc("id", "compact", "iss", "sub", "type", emptyMap(), emptyList(), 0)
        val ref = CredentialRef.SdJwt(vc)
        assertThat(ref).isInstanceOf(CredentialRef.SdJwt::class.java)
        assertThat((ref as CredentialRef.SdJwt).credential.id).isEqualTo("id")
    }

    @Test
    fun mdocRefWrapsCredential() {
        val mdoc = StoredMDoc("id", "org.iso.18013.5.1.mDL", byteArrayOf(), "key", 0)
        val ref = CredentialRef.MDoc(mdoc)
        assertThat(ref).isInstanceOf(CredentialRef.MDoc::class.java)
        assertThat((ref as CredentialRef.MDoc).credential.docType).isEqualTo("org.iso.18013.5.1.mDL")
    }

    @Test
    fun matchResultUsesCredentialRef() {
        val vc = StoredSdJwtVc("id", "compact", "iss", "sub", "type", emptyMap(), emptyList(), 0)
        val result = MatchResult(CredentialRef.SdJwt(vc), "desc-1", listOf("name"), emptyList())
        assertThat(result.credentialRef).isInstanceOf(CredentialRef.SdJwt::class.java)
    }
}
```

**Step 3: Write implementation**

Create `CredentialRef.kt`:

```kotlin
package my.ssdid.wallet.domain.oid4vp

import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

sealed class CredentialRef {
    data class SdJwt(val credential: StoredSdJwtVc) : CredentialRef()
    data class MDoc(val credential: StoredMDoc) : CredentialRef()
}
```

Update `MatchResult` in `PresentationDefinitionMatcher.kt`:

```kotlin
data class MatchResult(
    val credentialRef: CredentialRef,
    val descriptorId: String,
    val requiredClaims: List<String>,
    val optionalClaims: List<String>
)
```

Update all callers to wrap SD-JWT credentials with `CredentialRef.SdJwt(cred)` and unwrap with `(matchResult.credentialRef as CredentialRef.SdJwt).credential`.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/
git commit -m "refactor: introduce CredentialRef sealed class for multi-format support"
```

---

### Task 10: Add mdoc format matching to PresentationDefinitionMatcher

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcher.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcherMDocTest.kt`

Add `matchMDoc(pd, mdocs)` method that matches `"mso_mdoc"` format descriptors against `StoredMDoc` by `docType`. Return `MatchResult` with `CredentialRef.MDoc`.

Add `matchAll(pd, sdJwtVcs, mdocs)` method that combines both format matches.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcher.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcherMDocTest.kt
git commit -m "feat: add mdoc format matching to PresentationDefinitionMatcher"
```

---

### Task 11: Add mdoc format matching to DcqlMatcher

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcher.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcherMDocTest.kt`

Add `matchAll(dcql, sdJwtVcs, mdocs)` that handles `format: "mso_mdoc"` credential specs, matching by `doctype_value`.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcher.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcherMDocTest.kt
git commit -m "feat: add mdoc format matching to DcqlMatcher"
```

---

### Task 12: SessionTranscript and MDocVpTokenBuilder

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/SessionTranscript.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/MDocVpTokenBuilder.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/SessionTranscriptTest.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/MDocVpTokenBuilderTest.kt`

`SessionTranscript.build(clientId, responseUri, nonce)` builds CBOR array `[null, null, [clientId, responseUri, nonce]]` per ISO 18013-7 §9.1.

`MDocVpTokenBuilder.build(storedMDoc, requestedElements, sessionTranscript, algorithm, signer)` builds CBOR `DeviceResponse` containing `DeviceSigned` with device signature over `DeviceAuthentication` CBOR.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/SessionTranscript.kt \
        android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/MDocVpTokenBuilder.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/SessionTranscriptTest.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/MDocVpTokenBuilderTest.kt
git commit -m "feat: add SessionTranscript and MDocVpTokenBuilder for mdoc presentation"
```

---

### Task 13: Extend OpenId4VpHandler for mdoc presentation

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandler.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandlerMDocTest.kt`

Update `processRequest()` to also call `vault.listMDocs()` and pass both credential types to matchers.

Update `submitPresentation()` to branch on `CredentialRef`:
- `CredentialRef.SdJwt` → `VpTokenBuilder.build()` (existing)
- `CredentialRef.MDoc` → `MDocVpTokenBuilder.build()` (new)

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandler.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandlerMDocTest.kt
git commit -m "feat: extend OpenId4VpHandler for mdoc credential presentation"
```

---

### Task 14: Extend OpenId4VciHandler for mdoc issuance

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciHandler.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciHandlerMDocTest.kt`

In `requestCredential()`, check credential config format. If `mso_mdoc`:
- Send `format: "mso_mdoc"` + `doctype` in request body
- Parse response credential as CBOR bytes
- Store as `StoredMDoc` via `vault.storeMDoc()`

New sealed class for issuance result: `IssuanceResult.MDocSuccess(StoredMDoc)`.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciHandler.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciHandlerMDocTest.kt
git commit -m "feat: extend OpenId4VciHandler for mdoc credential issuance"
```

---

### Task 15: iOS Phase 3b mirror

**Files:**
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/CredentialRef.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/SessionTranscript.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/MDocVpTokenBuilder.swift`
- Modify: `ios/SsdidWallet/Domain/OpenId4Vp/PresentationDefinitionMatcher.swift`
- Modify: `ios/SsdidWallet/Domain/OpenId4Vp/DcqlMatcher.swift`
- Modify: `ios/SsdidWallet/Domain/OpenId4Vp/OpenId4VpHandler.swift`
- Modify: `ios/SsdidWallet/Domain/OpenId4Vci/OpenId4VciHandler.swift`
- Tests: mirror all Android 3b tests

Mirror Tasks 9-14 in Swift.

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Domain/OpenId4Vp/ ios/SsdidWallet/Domain/OpenId4Vci/ \
        ios/SsdidWalletTests/
git commit -m "feat(ios): add mdoc OpenID4VP/VCI support mirroring Android"
```

---

## Phase 3c: Digital Credentials API

### Task 16: Add Android CredentialManager dependency and provider skeleton

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/my/ssdid/wallet/platform/credentials/SsdidCredentialProviderService.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/xml/credential_provider_config.xml`

Add dependency: `implementation("androidx.credentials:credentials:1.5.0-alpha05")`

Create skeleton `SsdidCredentialProviderService` extending `CredentialProviderService` with stub implementations.

Register in manifest with `<service>` + `<meta-data>` pointing to config XML.

**Step 5: Commit**

```bash
git add android/app/build.gradle.kts \
        android/app/src/main/java/my/ssdid/wallet/platform/credentials/ \
        android/app/src/main/AndroidManifest.xml \
        android/app/src/main/res/xml/credential_provider_config.xml
git commit -m "feat: add Android CredentialProviderService skeleton"
```

---

### Task 17: Android credential matching and entry building

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/credentials/SsdidCredentialProviderService.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/platform/credentials/CredentialProviderTest.kt`

Implement `onBeginGetCredentialRequest()`:
- Parse digital credential request type
- Match against stored SD-JWT VCs and MDocs
- Return `CredentialEntry` list

Implement `onGetCredentialRequest()`:
- Build VP token for selected credential
- Return `GetCredentialResponse`

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/platform/credentials/ \
        android/app/src/test/java/my/ssdid/wallet/platform/credentials/
git commit -m "feat: implement credential matching and VP token building in CredentialProvider"
```

---

### Task 18: iOS ASAuthorizationController credential provider

**Files:**
- Create: `ios/SsdidWallet/Platform/Credentials/SsdidCredentialProvider.swift`
- Modify: `ios/SsdidWallet/Info.plist`
- Test: `ios/SsdidWalletTests/Platform/Credentials/CredentialProviderTests.swift`

Create credential provider conforming to iOS credential provider protocol. Register entitlement. Mark as experimental since iOS DC API is newer.

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Platform/Credentials/ ios/SsdidWallet/Info.plist \
        ios/SsdidWalletTests/Platform/Credentials/
git commit -m "feat(ios): add ASAuthorizationController credential provider (experimental)"
```

---

## Phase 3d: DIDComm v2 + DID Document Extensions

### Task 19: Add X25519 to Algorithm enum

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/Algorithm.kt`
- Modify: `ios/SsdidWallet/Domain/Model/Algorithm.swift`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/model/AlgorithmX25519Test.kt`

Add `X25519` variant with `isKeyAgreement = true` property. Not a signing algorithm — key agreement only.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/model/Algorithm.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/model/AlgorithmX25519Test.kt \
        ios/SsdidWallet/Domain/Model/Algorithm.swift
git commit -m "feat: add X25519 key agreement algorithm"
```

---

### Task 20: KeyAgreementProvider and X25519 implementation

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/KeyAgreementProvider.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/crypto/X25519Provider.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/crypto/X25519ProviderTest.kt`

```kotlin
interface KeyAgreementProvider {
    fun generateKeyPair(): KeyPairResult
    fun deriveSharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray
}
```

Android: BouncyCastle `X25519Agreement` + `X25519KeyPairGenerator`.
iOS: CryptoKit `Curve25519.KeyAgreement.PrivateKey()`.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/crypto/KeyAgreementProvider.kt \
        android/app/src/main/java/my/ssdid/wallet/domain/crypto/X25519Provider.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/crypto/X25519ProviderTest.kt
git commit -m "feat: add X25519 key agreement provider"
```

---

### Task 21: Add keyAgreement and service to DidDocument

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/DidDocument.kt`
- Modify: `ios/SsdidWallet/Domain/Model/DidDocument.swift`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/model/DidDocumentServiceTest.kt`

Add to `DidDocument`:
```kotlin
val keyAgreement: List<String> = emptyList(),
val service: List<Service> = emptyList()
```

New data class:
```kotlin
@Serializable
data class Service(
    val id: String,
    val type: String,
    val serviceEndpoint: String
)
```

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/model/DidDocument.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/model/DidDocumentServiceTest.kt \
        ios/SsdidWallet/Domain/Model/DidDocument.swift
git commit -m "feat: add keyAgreement and service to DidDocument"
```

---

### Task 22: DIDComm v2 message model

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/didcomm/DIDCommMessage.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/didcomm/DIDCommMessageTest.kt`

```kotlin
package my.ssdid.wallet.domain.didcomm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class DIDCommMessage(
    val id: String,
    val type: String,
    val from: String? = null,
    val to: List<String>,
    val createdTime: Long? = null,
    val body: JsonObject,
    val attachments: List<DIDCommAttachment> = emptyList()
)

@Serializable
data class DIDCommAttachment(
    val id: String,
    val mediaType: String? = null,
    val data: DIDCommAttachmentData
)

@Serializable
data class DIDCommAttachmentData(
    val base64: String? = null,
    val json: JsonObject? = null
)
```

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/didcomm/ \
        android/app/src/test/java/my/ssdid/wallet/domain/didcomm/
git commit -m "feat: add DIDComm v2 message model"
```

---

### Task 23: DIDComm packer — authcrypt encryption

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/didcomm/DIDCommPacker.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/didcomm/DIDCommPackerTest.kt`

Authcrypt flow:
1. Resolve recipient DID → get `keyAgreement` verification method → extract public key
2. ECDH: sender private key + recipient public key → shared secret
3. HKDF derive AES-256 key
4. AES-256-GCM encrypt message JSON
5. Build JWE compact serialization

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/didcomm/DIDCommPacker.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/didcomm/DIDCommPackerTest.kt
git commit -m "feat: add DIDComm authcrypt packer (X25519 + AES-256-GCM)"
```

---

### Task 24: DIDComm unpacker — authcrypt decryption

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/didcomm/DIDCommUnpacker.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/didcomm/DIDCommUnpackerTest.kt`

Reverse of packer: parse JWE → ECDH → decrypt → parse JSON → `DIDCommMessage`.

Test: round-trip pack → unpack with test keys.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/didcomm/DIDCommUnpacker.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/didcomm/DIDCommUnpackerTest.kt
git commit -m "feat: add DIDComm authcrypt unpacker"
```

---

### Task 25: DIDComm transport — HTTP POST

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/didcomm/DIDCommTransport.kt`
- Test: `android/app/src/test/java/my/ssdid/wallet/domain/didcomm/DIDCommTransportTest.kt`

```kotlin
class DIDCommTransport(private val httpClient: OkHttpClient) {
    fun send(packed: ByteArray, serviceEndpoint: String): Result<Unit>
}
```

HTTP POST with `Content-Type: application/didcomm-encrypted+json`. Uses OkHttp (same as rest of project).

Test with MockWebServer.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/didcomm/DIDCommTransport.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/didcomm/DIDCommTransportTest.kt
git commit -m "feat: add DIDComm HTTP transport"
```

---

### Task 26: DI wiring for Phase 3 components

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`

Add Hilt providers for:
- `X25519Provider`
- `DIDCommPacker`
- `DIDCommUnpacker`
- `DIDCommTransport`

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt
git commit -m "chore: add DI wiring for Phase 3 components"
```

---

### Task 27: iOS DIDComm mirror

**Files:**
- Create: `ios/SsdidWallet/Domain/Crypto/X25519Provider.swift`
- Create: `ios/SsdidWallet/Domain/DIDComm/DIDCommMessage.swift`
- Create: `ios/SsdidWallet/Domain/DIDComm/DIDCommPacker.swift`
- Create: `ios/SsdidWallet/Domain/DIDComm/DIDCommUnpacker.swift`
- Create: `ios/SsdidWallet/Domain/DIDComm/DIDCommTransport.swift`
- Modify: `ios/SsdidWallet/App/ServiceContainer.swift`
- Tests: `ios/SsdidWalletTests/Domain/DIDComm/` — all corresponding tests

Mirror Tasks 19-25 in Swift. X25519 uses CryptoKit `Curve25519.KeyAgreement`. AES-GCM uses CryptoKit `AES.GCM`.

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Domain/Crypto/X25519Provider.swift \
        ios/SsdidWallet/Domain/DIDComm/ \
        ios/SsdidWalletTests/Domain/DIDComm/ \
        ios/SsdidWallet/App/ServiceContainer.swift
git commit -m "feat(ios): add DIDComm v2 + X25519 mirroring Android"
```

---

### Task 28: Run full test suite and verify

**Step 1: Run all Android tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 2: Verify no compilation errors**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Final commit if any fixes needed**

```bash
git commit -m "fix: address test failures from Phase 3 integration"
```
