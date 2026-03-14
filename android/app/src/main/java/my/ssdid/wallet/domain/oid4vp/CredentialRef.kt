package my.ssdid.wallet.domain.oid4vp

import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

sealed class CredentialRef {
    data class SdJwt(val credential: StoredSdJwtVc) : CredentialRef()
    data class MDoc(val credential: StoredMDoc) : CredentialRef()
}
