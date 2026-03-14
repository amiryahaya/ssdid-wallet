import Foundation

/// Selective disclosure presenter for mdoc credentials.
///
/// Filters IssuerSignedItems to only include elements requested by the verifier,
/// preserving the issuerAuth intact so the verifier can still validate digests.
enum MDocPresenter {

    /// Filter an IssuerSigned structure to include only requested elements.
    ///
    /// - Parameters:
    ///   - issuerSigned: The full IssuerSigned structure with all data elements.
    ///   - requestedElements: Map of namespace to requested element identifiers.
    /// - Returns: A new IssuerSigned containing only the requested elements.
    static func present(
        issuerSigned: IssuerSigned,
        requestedElements: [String: [String]]
    ) -> IssuerSigned {
        var filteredNameSpaces = [String: [IssuerSignedItem]]()

        for (namespace, requestedIds) in requestedElements {
            guard let items = issuerSigned.nameSpaces[namespace] else { continue }
            let requestedSet = Set(requestedIds)
            let filtered = items.filter { requestedSet.contains($0.elementIdentifier) }
            filteredNameSpaces[namespace] = filtered
        }

        return IssuerSigned(
            nameSpaces: filteredNameSpaces,
            issuerAuth: issuerSigned.issuerAuth
        )
    }
}
