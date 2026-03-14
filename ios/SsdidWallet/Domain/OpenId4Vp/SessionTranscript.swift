import Foundation

/// Builds the SessionTranscript CBOR array per ISO 18013-7 section 9.1 for online presentation.
///
/// SessionTranscript = [null, null, [clientId, responseUri, nonce]]
///
/// The first two null elements correspond to DeviceEngagementBytes and
/// EReaderKeyBytes from ISO 18013-5 proximity flow. For the OID4VP online
/// profile (ISO 18013-7), these are null since there is no NFC/BLE handshake.
/// The third element is the OID4VP handover array.
enum SessionTranscript {

    static func build(clientId: String, responseUri: String, nonce: String) -> Data {
        // Build the handover array: [clientId, responseUri, nonce]
        let handover: [Any] = [clientId, responseUri, nonce]

        // Build the transcript: [null, null, handover]
        // CborCodec encodes nil/NSNull as the CBOR null (0xf6) via the default branch,
        // so we use a sentinel approach: encode manually.
        var buffer = Data()

        // CBOR array of 3 items
        buffer.append(0x83) // major type 4 (array) with additional info 3

        // null (0xf6)
        buffer.append(0xf6)
        // null (0xf6)
        buffer.append(0xf6)

        // Encode the handover array using CborCodec
        let handoverBytes = CborCodec.encodeDataElement(handover)
        buffer.append(handoverBytes)

        return buffer
    }
}
