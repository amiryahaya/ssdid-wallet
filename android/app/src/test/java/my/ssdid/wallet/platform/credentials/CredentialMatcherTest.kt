package my.ssdid.wallet.platform.credentials

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.oid4vp.CredentialRef
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Before
import org.junit.Test

class CredentialMatcherTest {

    private lateinit var matcher: CredentialMatcher

    private val sdJwtVc1 = StoredSdJwtVc(
        id = "sd-jwt-1",
        compact = "header.payload~disc1~disc2~",
        issuer = "https://issuer.example.com",
        subject = "did:ssdid:abc123",
        type = "IdentityCredential",
        claims = mapOf("name" to "Alice", "email" to "alice@example.com"),
        disclosableClaims = listOf("name", "email"),
        issuedAt = 1700000000L
    )

    private val sdJwtVc2 = StoredSdJwtVc(
        id = "sd-jwt-2",
        compact = "header.payload~disc1~",
        issuer = "https://university.example.com",
        subject = "did:ssdid:def456",
        type = "UniversityDegreeCredential",
        claims = mapOf("degree" to "Computer Science"),
        disclosableClaims = listOf("degree"),
        issuedAt = 1700000000L,
        expiresAt = 1800000000L
    )

    private val mdoc1 = StoredMDoc(
        id = "mdoc-1",
        docType = "org.iso.18013.5.1.mDL",
        issuerSignedCbor = byteArrayOf(0x01, 0x02),
        deviceKeyId = "key-1",
        issuedAt = 1700000000L,
        nameSpaces = mapOf(
            "org.iso.18013.5.1" to listOf("family_name", "given_name", "birth_date")
        )
    )

    private val mdoc2 = StoredMDoc(
        id = "mdoc-2",
        docType = "org.iso.23220.1.pid",
        issuerSignedCbor = byteArrayOf(0x03, 0x04),
        deviceKeyId = "key-2",
        issuedAt = 1700000000L,
        nameSpaces = mapOf(
            "org.iso.23220.1" to listOf("family_name", "given_name", "nationality")
        )
    )

    @Before
    fun setUp() {
        matcher = CredentialMatcher()
    }

    @Test
    fun `matchSdJwtCredentials returns all when no type filter`() {
        val results = matcher.matchSdJwtCredentials(listOf(sdJwtVc1, sdJwtVc2))

        assertThat(results).hasSize(2)
        assertThat(results[0].id).isEqualTo("sd-jwt-1")
        assertThat(results[1].id).isEqualTo("sd-jwt-2")
    }

    @Test
    fun `matchSdJwtCredentials filters by type`() {
        val results = matcher.matchSdJwtCredentials(
            listOf(sdJwtVc1, sdJwtVc2),
            requestedType = "UniversityDegreeCredential"
        )

        assertThat(results).hasSize(1)
        assertThat(results[0].id).isEqualTo("sd-jwt-2")
        assertThat(results[0].title).isEqualTo("UniversityDegreeCredential")
        assertThat(results[0].subtitle).isEqualTo("https://university.example.com")
    }

    @Test
    fun `matchSdJwtCredentials returns empty for unmatched type`() {
        val results = matcher.matchSdJwtCredentials(
            listOf(sdJwtVc1, sdJwtVc2),
            requestedType = "NonExistentType"
        )

        assertThat(results).isEmpty()
    }

    @Test
    fun `matchSdJwtCredentials wraps in SdJwt CredentialRef`() {
        val results = matcher.matchSdJwtCredentials(listOf(sdJwtVc1))

        assertThat(results).hasSize(1)
        val ref = results[0].credentialRef
        assertThat(ref).isInstanceOf(CredentialRef.SdJwt::class.java)
        assertThat((ref as CredentialRef.SdJwt).credential).isEqualTo(sdJwtVc1)
    }

    @Test
    fun `matchMDocCredentials returns all when no docType filter`() {
        val results = matcher.matchMDocCredentials(listOf(mdoc1, mdoc2))

        assertThat(results).hasSize(2)
        assertThat(results[0].id).isEqualTo("mdoc-1")
        assertThat(results[1].id).isEqualTo("mdoc-2")
    }

    @Test
    fun `matchMDocCredentials filters by docType`() {
        val results = matcher.matchMDocCredentials(
            listOf(mdoc1, mdoc2),
            requestedDocType = "org.iso.18013.5.1.mDL"
        )

        assertThat(results).hasSize(1)
        assertThat(results[0].id).isEqualTo("mdoc-1")
        assertThat(results[0].title).isEqualTo("mDL")
        assertThat(results[0].subtitle).isEqualTo("org.iso.18013.5.1.mDL")
    }

    @Test
    fun `matchMDocCredentials returns empty for unmatched docType`() {
        val results = matcher.matchMDocCredentials(
            listOf(mdoc1, mdoc2),
            requestedDocType = "org.example.unknown"
        )

        assertThat(results).isEmpty()
    }

    @Test
    fun `matchMDocCredentials wraps in MDoc CredentialRef`() {
        val results = matcher.matchMDocCredentials(listOf(mdoc1))

        assertThat(results).hasSize(1)
        val ref = results[0].credentialRef
        assertThat(ref).isInstanceOf(CredentialRef.MDoc::class.java)
        assertThat((ref as CredentialRef.MDoc).credential).isEqualTo(mdoc1)
    }

    @Test
    fun `matchAll returns both SD-JWT and mdoc credentials`() {
        val results = matcher.matchAll(
            sdJwtVcs = listOf(sdJwtVc1, sdJwtVc2),
            mdocs = listOf(mdoc1, mdoc2)
        )

        assertThat(results).hasSize(4)
        val sdJwtResults = results.filter { it.credentialRef is CredentialRef.SdJwt }
        val mdocResults = results.filter { it.credentialRef is CredentialRef.MDoc }
        assertThat(sdJwtResults).hasSize(2)
        assertThat(mdocResults).hasSize(2)
    }

    @Test
    fun `matchAll returns empty when no credentials stored`() {
        val results = matcher.matchAll(
            sdJwtVcs = emptyList(),
            mdocs = emptyList()
        )

        assertThat(results).isEmpty()
    }

    @Test
    fun `matchAll returns only SD-JWT when no mdocs`() {
        val results = matcher.matchAll(
            sdJwtVcs = listOf(sdJwtVc1),
            mdocs = emptyList()
        )

        assertThat(results).hasSize(1)
        assertThat(results[0].credentialRef).isInstanceOf(CredentialRef.SdJwt::class.java)
    }

    @Test
    fun `matchAll returns only mdocs when no SD-JWTs`() {
        val results = matcher.matchAll(
            sdJwtVcs = emptyList(),
            mdocs = listOf(mdoc1)
        )

        assertThat(results).hasSize(1)
        assertThat(results[0].credentialRef).isInstanceOf(CredentialRef.MDoc::class.java)
    }

    @Test
    fun `matchSdJwtCredentials extracts readable title from type`() {
        val vcWithUrl = sdJwtVc1.copy(type = "https://credentials.example.com/IdentityCredential")
        val results = matcher.matchSdJwtCredentials(listOf(vcWithUrl))

        assertThat(results[0].title).isEqualTo("IdentityCredential")
    }

    @Test
    fun `matchMDocCredentials extracts short title from docType`() {
        val results = matcher.matchMDocCredentials(listOf(mdoc1))

        assertThat(results[0].title).isEqualTo("mDL")
    }

    @Test
    fun `matchSdJwtCredentials returns empty for empty input`() {
        val results = matcher.matchSdJwtCredentials(emptyList())
        assertThat(results).isEmpty()
    }

    @Test
    fun `matchMDocCredentials returns empty for empty input`() {
        val results = matcher.matchMDocCredentials(emptyList())
        assertThat(results).isEmpty()
    }
}
