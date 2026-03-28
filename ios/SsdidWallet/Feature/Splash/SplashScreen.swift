import SwiftUI
import SsdidCore

struct SplashScreen: View {
    @State private var iconScale: CGFloat = 0.8
    @State private var iconOpacity: Double = 0
    @State private var textOpacity: Double = 0

    var body: some View {
        ZStack {
            // Gradient background matching Android launcher background
            LinearGradient(
                colors: [
                    Color(hex: 0x061449),
                    Color(hex: 0x103B91),
                    Color(hex: 0x0D8CB5)
                ],
                startPoint: .bottomLeading,
                endPoint: .topTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 24) {
                // App icon
                Image("SplashIcon")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 120, height: 120)
                    .clipShape(RoundedRectangle(cornerRadius: 28))
                    .shadow(color: .black.opacity(0.3), radius: 20, y: 10)
                    .scaleEffect(iconScale)
                    .opacity(iconOpacity)

                // App name
                VStack(spacing: 6) {
                    Text("SSDID Wallet")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(.white)

                    Text("Self-Sovereign Identity")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.white.opacity(0.7))
                }
                .opacity(textOpacity)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.5)) {
                iconScale = 1.0
                iconOpacity = 1.0
            }
            withAnimation(.easeOut(duration: 0.5).delay(0.2)) {
                textOpacity = 1.0
            }
        }
    }
}
