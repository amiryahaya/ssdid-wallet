import Foundation

/// Parser for IssuerSigned and MobileSecurityObject CBOR structures.
///
/// Decodes ISO 18013-5 IssuerSigned (containing nameSpaces + issuerAuth)
/// and the MSO payload inside a COSE_Sign1 envelope.
enum MsoParser {

    /// Parse an IssuerSigned CBOR structure.
    ///
    /// Expected layout:
    /// ```
    /// {
    ///   "nameSpaces": { <ns>: [bstr-tagged IssuerSignedItem, ...], ... },
    ///   "issuerAuth": COSE_Sign1
    /// }
    /// ```
    static func parseIssuerSigned(_ cborBytes: Data) -> IssuerSigned? {
        guard let root = CborCodec.decodeDataElement(cborBytes) as? [String: Any] else {
            return nil
        }

        guard let nameSpacesRaw = root["nameSpaces"] as? [String: Any],
              let issuerAuthRaw = root["issuerAuth"] else {
            return nil
        }

        var nameSpaces = [String: [IssuerSignedItem]]()
        for (namespace, itemsAny) in nameSpacesRaw {
            guard let itemsArray = itemsAny as? [Any] else { continue }
            var items = [IssuerSignedItem]()
            for itemAny in itemsArray {
                // Each item may be a tagged bstr containing an encoded IssuerSignedItem map,
                // or already decoded as a map.
                if let itemData = itemAny as? Data,
                   let itemMap = CborCodec.decodeDataElement(itemData) as? [String: Any] {
                    if let item = parseIssuerSignedItem(itemMap) {
                        items.append(item)
                    }
                } else if let itemMap = itemAny as? [String: Any] {
                    if let item = parseIssuerSignedItem(itemMap) {
                        items.append(item)
                    }
                }
            }
            nameSpaces[namespace] = items
        }

        // issuerAuth is a COSE_Sign1 — keep as CBOR bytes
        let issuerAuth: Data
        if let authData = issuerAuthRaw as? Data {
            issuerAuth = authData
        } else {
            issuerAuth = CborCodec.encodeDataElement(issuerAuthRaw)
        }

        return IssuerSigned(nameSpaces: nameSpaces, issuerAuth: issuerAuth)
    }

    /// Parse the MobileSecurityObject from a COSE_Sign1 issuerAuth payload.
    ///
    /// COSE_Sign1 is a CBOR array: [protected, unprotected, payload, signature].
    /// The payload (index 2) contains the MSO.
    static func parseMso(_ issuerAuthBytes: Data) -> MobileSecurityObject? {
        guard let coseArray = CborCodec.decodeDataElement(issuerAuthBytes) as? [Any],
              coseArray.count >= 3 else {
            return nil
        }

        // payload is element at index 2 — should be bstr containing MSO CBOR
        guard let payloadBytes = coseArray[2] as? Data,
              let mso = CborCodec.decodeDataElement(payloadBytes) as? [String: Any] else {
            return nil
        }

        guard let version = mso["version"] as? String,
              let digestAlgorithm = mso["digestAlgorithm"] as? String,
              let digestsRaw = mso["valueDigests"] as? [String: Any],
              let deviceKeyInfoRaw = mso["deviceKeyInfo"] as? [String: Any],
              let validityInfoRaw = mso["validityInfo"] as? [String: Any] else {
            return nil
        }

        // Parse valueDigests: { namespace: { digestId: bstr } }
        var valueDigests = [String: [Int: Data]]()
        for (namespace, nsDigestsAny) in digestsRaw {
            guard let nsDigests = nsDigestsAny as? [String: Any] else { continue }
            var digests = [Int: Data]()
            for (key, value) in nsDigests {
                guard let digestId = Int(key), let digestBytes = value as? Data else { continue }
                digests[digestId] = digestBytes
            }
            valueDigests[namespace] = digests
        }

        // Parse deviceKeyInfo
        let deviceKey = deviceKeyInfoRaw["deviceKey"] as? Data ?? Data()
        let deviceKeyInfo = DeviceKeyInfo(deviceKey: deviceKey)

        // Parse validityInfo
        guard let signed = validityInfoRaw["signed"] as? String,
              let validFrom = validityInfoRaw["validFrom"] as? String,
              let validUntil = validityInfoRaw["validUntil"] as? String else {
            return nil
        }
        let validityInfo = ValidityInfo(signed: signed, validFrom: validFrom, validUntil: validUntil)

        return MobileSecurityObject(
            version: version,
            digestAlgorithm: digestAlgorithm,
            valueDigests: valueDigests,
            deviceKeyInfo: deviceKeyInfo,
            validityInfo: validityInfo
        )
    }

    // MARK: - Private

    private static func parseIssuerSignedItem(_ map: [String: Any]) -> IssuerSignedItem? {
        guard let digestId = map["digestID"] as? Int,
              let random = map["random"] as? Data,
              let elementIdentifier = map["elementIdentifier"] as? String else {
            return nil
        }
        let elementValue = map["elementValue"] ?? ""
        return IssuerSignedItem(
            digestId: digestId,
            random: random,
            elementIdentifier: elementIdentifier,
            elementValue: elementValue
        )
    }
}
