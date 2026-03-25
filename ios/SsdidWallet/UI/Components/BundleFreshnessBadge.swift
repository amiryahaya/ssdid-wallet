import SwiftUI

/// Shows a badge indicating the freshness state of a verification bundle.
/// - freshnessRatio < 0.5: bundle is fresh — no badge shown
/// - 0.5 ≤ freshnessRatio < 1.0: bundle is aging — yellow badge
/// - freshnessRatio ≥ 1.0: bundle is expired — red badge
struct BundleFreshnessBadge: View {
    let freshnessRatio: Double

    var body: some View {
        if freshnessRatio < 0.5 {
            EmptyView()
        } else if freshnessRatio < 1.0 {
            Text("Bundle aging")
                .font(.caption2)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.yellow.opacity(0.15))
                .foregroundColor(.yellow)
                .cornerRadius(4)
        } else {
            Text("Bundle expired")
                .font(.caption2)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.red.opacity(0.15))
                .foregroundColor(.red)
                .cornerRadius(4)
        }
    }
}

#Preview {
    VStack(spacing: 12) {
        BundleFreshnessBadge(freshnessRatio: 0.3)
        BundleFreshnessBadge(freshnessRatio: 0.7)
        BundleFreshnessBadge(freshnessRatio: 1.2)
    }
    .padding()
    .background(Color.bgPrimary)
}
