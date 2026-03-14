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

        // Sign DeviceAuthentication
        let signature = signer(deviceAuthBytes)

        // Build COSE_Sign1 for deviceSignature: [protectedHeaders, unprotectedHeaders, null, signature]
        // Encode as CBOR array with tag 18
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

    // MARK: - Private

    private static func encodeCoseSign1(signature: Data) -> Data {
        // COSE_Sign1 = [bstr, {}, null, bstr] as a CBOR array
        var buffer = Data()
        // Array of 4 items
        buffer.append(0x84)
        // protected: empty bstr
        buffer.append(0x40)
        // unprotected: empty map
        buffer.append(0xa0)
        // payload: null (detached)
        buffer.append(0xf6)
        // signature: bstr
        let sigBytes = CborCodec.encodeDataElement(signature)
        buffer.append(sigBytes)
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
                // Each item is tagged bstr (tag 24) containing the encoded item
                let itemBytes = CborCodec.encodeMap(itemMap)
                encodedItems.append(itemBytes)
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
