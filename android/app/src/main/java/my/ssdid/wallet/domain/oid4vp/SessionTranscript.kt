package my.ssdid.wallet.domain.oid4vp

import com.upokecenter.cbor.CBORObject

/**
 * Builds the SessionTranscript CBOR array per ISO 18013-7 §9.1 for online presentation.
 *
 * SessionTranscript = [null, null, [clientId, responseUri, nonce]]
 */
object SessionTranscript {
    fun build(clientId: String, responseUri: String, nonce: String): ByteArray {
        val handover = CBORObject.NewArray()
        handover.Add(CBORObject.FromObject(clientId))
        handover.Add(CBORObject.FromObject(responseUri))
        handover.Add(CBORObject.FromObject(nonce))

        val transcript = CBORObject.NewArray()
        transcript.Add(CBORObject.Null)
        transcript.Add(CBORObject.Null)
        transcript.Add(handover)

        return transcript.EncodeToBytes()
    }
}
