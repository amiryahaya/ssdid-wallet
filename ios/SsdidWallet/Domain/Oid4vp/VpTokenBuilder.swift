import Foundation

class VpTokenBuilder {

    func build(
        sdJwtVc: SdJwtVc,
        selectedClaimNames: Set<String>,
        audience: String,
        nonce: String,
        algorithm: String,
        signer: @escaping (Data) -> Data
    ) throws -> String {
        // Filter disclosures to selected claims
        let selectedDisclosures = sdJwtVc.disclosures.filter { selectedClaimNames.contains($0.claimName) }

        // Build SD-JWT without KB-JWT first (for sd_hash)
        let sdJwtWithDisclosures = try sdJwtVc.present(selectedDisclosures: selectedDisclosures)

        // Create KB-JWT
        let kbJwt = try KeyBindingJwt.create(
            sdJwtWithDisclosures: sdJwtWithDisclosures,
            audience: audience,
            nonce: nonce,
            algorithm: algorithm,
            signer: signer
        )

        // Assemble final presentation with KB-JWT
        return try sdJwtVc.present(selectedDisclosures: selectedDisclosures, kbJwt: kbJwt)
    }

    func buildPresentationSubmission(
        definitionId: String,
        descriptorId: String
    ) -> PresentationSubmission {
        PresentationSubmission(
            id: "submission-\(Int(Date().timeIntervalSince1970))",
            definitionId: definitionId,
            descriptorMap: [
                DescriptorMapEntry(id: descriptorId, format: "vc+sd-jwt", path: "$")
            ]
        )
    }
}
