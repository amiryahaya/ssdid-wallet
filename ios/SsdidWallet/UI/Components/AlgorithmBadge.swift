import SwiftUI

struct AlgorithmBadge: View {
    let name: String
    let isPostQuantum: Bool

    var body: some View {
        Text(name)
            .font(.ssdidCaption)
            .foregroundColor(isPostQuantum ? .pqc : .classical)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(isPostQuantum ? Color.pqcDim : Color.classicalDim)
            .cornerRadius(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(
                        (isPostQuantum ? Color.pqc : Color.classical).opacity(0.3),
                        lineWidth: 1
                    )
            )
    }
}

#Preview {
    VStack(spacing: 12) {
        // Classical
        AlgorithmBadge(name: "Ed25519", isPostQuantum: false)
        AlgorithmBadge(name: "ECDSA P-256", isPostQuantum: false)
        AlgorithmBadge(name: "ECDSA P-384", isPostQuantum: false)

        // PQC
        AlgorithmBadge(name: "KAZ-Sign 128", isPostQuantum: true)
        AlgorithmBadge(name: "KAZ-Sign 192", isPostQuantum: true)
        AlgorithmBadge(name: "KAZ-Sign 256", isPostQuantum: true)
        AlgorithmBadge(name: "ML-DSA-44", isPostQuantum: true)
        AlgorithmBadge(name: "SLH-DSA-SHA2-128s", isPostQuantum: true)
    }
    .padding()
    .background(Color.bgPrimary)
}
