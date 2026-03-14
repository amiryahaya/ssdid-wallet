import Foundation

/// Builds a CBOR DeviceResponse for mdoc presentation per ISO 18013-5.
///
/// DeviceResponse = {
///   "version": "1.0",
///   "documents": [{ docType, issuerSigned, deviceSigned }],
///   "status": 0
/// }
enum MDocVpTokenBuilder {

    /// Builds a VP token from a stored mdoc with selective disclosure.
    ///
    /// - Parameters:
    ///   - storedMDoc: The stored mdoc credential
    ///   - requestedElements: Map of namespace to requested element identifiers
    ///   - clientId: The verifier's client_id
    ///   - responseUri: The verifier's response_uri
    ///   - nonce: Verifier-provided nonce
    ///   - signer: Function that signs DeviceAuthentication bytes
    /// - Returns: Base64url-encoded CBOR DeviceResponse string
    static func build(
        storedMDoc: StoredMDoc,
        requestedElements: [String: [String]],
        clientId: String,
        responseUri: String,
        nonce: String,
        signer: (Data) -> Data
    ) -> String? {
        // Parse the stored IssuerSigned CBOR
        guard let issuerSigned = MsoParser.parseIssuerSigned(storedMDoc.issuerSignedCbor) else {
            return nil
        }

        // Apply selective disclosure
        let presented = MDocPresenter.present(
            issuerSigned: issuerSigned,
            requestedElements: requestedElements
        )

        // Build SessionTranscript
        let sessionTranscript = SessionTranscript.build(
            clientId: clientId,
            responseUri: responseUri,
            nonce: nonce
        )

        // Build DeviceAuthentication = ["DeviceAuthentication", SessionTranscript, docType, {}]
        let deviceAuth: [Any] = [
            "DeviceAuthentication",
            sessionTranscript, // raw CBOR bytes
            storedMDoc.docType,
            [String: Any]() // empty DeviceNameSpaces
        ]
        let deviceAuthBytes = CborCodec.encodeDataElement(deviceAuth)

        // Wrap DeviceAuthentication in CBOR tag 24 (encoded CBOR data item)
        // per ISO 18013-5 §9.1.3
        let deviceAuthTagged = cborTag24(deviceAuthBytes)

        // Build COSE Sig_structure per RFC 9052 §4.4 and ISO 18013-5 §9.1.3:
        // Sig_structure = ["Signature1", body_protected, external_aad, payload]
        // - external_aad is empty (SessionTranscript is already in DeviceAuthentication)
        // - payload is the tag-24-wrapped DeviceAuthentication bstr
        let sigStructure: [Any] = [
            "Signature1",
            Data(),              // body_protected: empty bstr
            Data(),              // external_aad: empty
            deviceAuthTagged     // payload: #6.24(DeviceAuthentication)
        ]
        let sigStructureBytes = CborCodec.encodeDataElement(sigStructure)

        // Sign the Sig_structure
        let signature = signer(sigStructureBytes)

        // Build COSE_Sign1 for deviceSignature with tag 18
        let coseSign1Bytes = encodeCoseSign1(signature: signature)

        // Build DeviceSigned map
        let emptyNameSpaces = CborCodec.encodeMap([:])
        let deviceSignedMap: [String: Any] = [
            "nameSpaces": emptyNameSpaces, // tagged bstr
            "deviceAuth": ["deviceSignature": coseSign1Bytes] as [String: Any]
        ]

        // Build IssuerSigned CBOR for response
        let issuerSignedCbor = encodeIssuerSigned(presented)

        // Build Document
        let document: [String: Any] = [
            "docType": storedMDoc.docType,
            "issuerSigned": issuerSignedCbor,
            "deviceSigned": deviceSignedMap
        ]

        // Build DeviceResponse
        let deviceResponse: [String: Any] = [
            "version": "1.0",
            "documents": [document],
            "status": 0
        ]

        let responseBytes = CborCodec.encodeMap(deviceResponse)
        return base64urlEncode(responseBytes)
    }

    // MARK: - CBOR Helpers

    /// Wrap data in CBOR tag 24 (encoded CBOR data item): tag(24, bstr(data))
    private static func cborTag24(_ data: Data) -> Data {
        var buffer = Data()
        // Tag 24 = major type 6 (0xc0) + additional 24 = 0xd8 0x18
        buffer.append(0xd8)
        buffer.append(0x18)
        // bstr header for the data
        appendBstrHeader(data.count, into: &buffer)
        buffer.append(data)
        return buffer
    }

    /// Append a CBOR bstr (major type 2) length header.
    private static func appendBstrHeader(_ count: Int, into buffer: inout Data) {
        if count < 24 {
            buffer.append(0x40 | UInt8(count))
        } else if count <= 0xFF {
            buffer.append(contentsOf: [0x58, UInt8(count)])
        } else if count <= 0xFFFF {
            buffer.append(0x59)
            var be = UInt16(count).bigEndian
            buffer.append(Data(bytes: &be, count: 2))
        } else {
            buffer.append(0x5a)
            var be = UInt32(count).bigEndian
            buffer.append(Data(bytes: &be, count: 4))
        }
    }

    /// Encode COSE_Sign1 = tag(18, [bstr, {}, null, bstr])
    private static func encodeCoseSign1(signature: Data) -> Data {
        var buffer = Data()
        // COSE tag 18 = major type 6 (0xc0) + additional 18 = 0xd2
        buffer.append(0xd2)
        // Array of 4 items
        buffer.append(0x84)
        // protected: empty bstr
        buffer.append(0x40)
        // unprotected: empty map
        buffer.append(0xa0)
        // payload: null (detached)
        buffer.append(0xf6)
        // signature: bstr
        appendBstrHeader(signature.count, into: &buffer)
        buffer.append(signature)
        return buffer
    }

    private static func encodeIssuerSigned(_ issuerSigned: IssuerSigned) -> Data {
        var nameSpacesDict = [String: Any]()
        for (ns, items) in issuerSigned.nameSpaces {
            var encodedItems = [Any]()
            for item in items {
                let itemMap: [String: Any] = [
                    "digestID": item.digestId,
                    "random": item.random,
                    "elementIdentifier": item.elementIdentifier,
                    "elementValue": item.elementValue
                ]
                // Each item must be wrapped in tag 24 per ISO 18013-5 §8.3.2.1.2
                let itemBytes = CborCodec.encodeMap(itemMap)
                let taggedItem = cborTag24(itemBytes)
                encodedItems.append(taggedItem)
            }
            nameSpacesDict[ns] = encodedItems
        }

        let result: [String: Any] = [
            "nameSpaces": nameSpacesDict,
            "issuerAuth": issuerSigned.issuerAuth
        ]
        return CborCodec.encodeMap(result)
    }

    private static func base64urlEncode(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .trimmingCharacters(in: CharacterSet(charactersIn: "="))
    }
}
