package my.ssdid.wallet.domain.oid4vp

import com.upokecenter.cbor.CBORObject

/**
 * Builds the SessionTranscript CBOR array per ISO 18013-7 §9.1 for online presentation.
 *
 * SessionTranscript = [null, null, [clientId, responseUri, nonce]]
 *
 * The first two null elements correspond to DeviceEngagementBytes and
 * EReaderKeyBytes from ISO 18013-5 proximity flow. For the OID4VP online
 * profile (ISO 18013-7), these are null since there is no NFC/BLE handshake.
 * The third element is the OID4VP handover array.
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
