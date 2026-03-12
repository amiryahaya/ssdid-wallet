import SwiftUI

struct LoadingView: View {
    var message: String?

    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .ssdidAccent))
                .scaleEffect(1.2)

            if let message {
                Text(message)
                    .font(.ssdidBody)
                    .foregroundColor(.textSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.bgPrimary)
    }
}

#Preview {
    VStack {
        LoadingView()
        LoadingView(message: "Creating your identity...")
    }
}
