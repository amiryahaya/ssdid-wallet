package my.ssdid.sdk.api

import my.ssdid.sdk.domain.sdjwt.SdJwtParser
import my.ssdid.sdk.domain.sdjwt.SdJwtVc
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc
import my.ssdid.sdk.domain.vault.Vault

class SdJwtApi internal constructor(private val vault: Vault) {
    fun parse(compactSdJwt: String): SdJwtVc = SdJwtParser.parse(compactSdJwt)
    suspend fun store(sdJwtVc: StoredSdJwtVc): Result<Unit> = vault.storeStoredSdJwtVc(sdJwtVc)
    suspend fun list(): List<StoredSdJwtVc> = vault.listStoredSdJwtVcs()
}
