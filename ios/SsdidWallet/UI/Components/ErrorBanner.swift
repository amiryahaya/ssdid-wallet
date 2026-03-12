import SwiftUI

struct ErrorBanner: View {
    let message: String
    var onDismiss: (() -> Void)?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.danger)
                .font(.system(size: 18))

            Text(message)
                .font(.ssdidCaption)
                .foregroundColor(.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let onDismiss {
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .foregroundColor(.textTertiary)
                        .font(.system(size: 12, weight: .bold))
                }
            }
        }
        .padding(12)
        .background(Color.dangerDim)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.danger.opacity(0.3), lineWidth: 1)
        )
    }
}

#Preview {
    VStack(spacing: 16) {
        ErrorBanner(message: "Failed to connect to the registry server.")
        ErrorBanner(
            message: "Biometric authentication failed. Please try again.",
            onDismiss: {}
        )
    }
    .padding()
    .background(Color.bgPrimary)
}
