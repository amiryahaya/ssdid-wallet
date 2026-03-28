import SwiftUI
import SsdidCore

struct OnboardingScreen: View {
    @Environment(AppRouter.self) private var router

    @State private var currentPage = 0

    private let slides: [(icon: String, title: String, description: String, accent: Color)] = [
        (
            icon: "lock.shield.fill",
            title: "Self-Sovereign Identity",
            description: "Own your digital identity. No central authority controls your data. Your keys, your identity, your rules.",
            accent: .ssdidAccent
        ),
        (
            icon: "shield.checkered",
            title: "Post-Quantum Security",
            description: "Future-proof cryptography protects your identity against both classical and quantum computing threats.",
            accent: .pqc
        ),
        (
            icon: "hand.raised.fill",
            title: "Privacy First",
            description: "You control your data. Share only what you choose, when you choose. Zero-knowledge proofs keep your information private.",
            accent: .success
        )
    ]

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $currentPage) {
                ForEach(0..<slides.count, id: \.self) { index in
                    slideView(for: slides[index])
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            // Dot indicators
            HStack(spacing: 8) {
                ForEach(0..<slides.count, id: \.self) { index in
                    Circle()
                        .fill(currentPage == index ? Color.ssdidAccent : Color.textTertiary)
                        .frame(width: currentPage == index ? 10 : 8,
                               height: currentPage == index ? 10 : 8)
                }
            }
            .padding(.bottom, 24)

            // Get Started / Next button
            Button {
                if currentPage == slides.count - 1 {
                    router.push(.createIdentity())
                } else {
                    withAnimation {
                        currentPage += 1
                    }
                }
            } label: {
                Text(currentPage == slides.count - 1 ? "Get Started" : "Next")
            }
            .buttonStyle(.ssdidPrimary)
            .padding(.horizontal, 20)

            // Restore from Backup
            Button {
                router.push(.backupExport())
            } label: {
                Text("Restore from Backup")
            }
            .buttonStyle(.ssdidSecondary)
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
            .padding(.top, 8)
        }
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func slideView(for slide: (icon: String, title: String, description: String, accent: Color)) -> some View {
        VStack(spacing: 0) {
            Spacer()

            ZStack {
                RoundedRectangle(cornerRadius: 28)
                    .fill(slide.accent.opacity(0.12))
                    .frame(width: 100, height: 100)
                Image(systemName: slide.icon)
                    .font(.system(size: 42))
                    .foregroundStyle(slide.accent)
            }

            Spacer().frame(height: 40)

            Text(slide.title)
                .font(.ssdidTitle)
                .foregroundStyle(Color.textPrimary)
                .multilineTextAlignment(.center)

            Spacer().frame(height: 16)

            Text(slide.description)
                .font(.ssdidBody)
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(4)

            Spacer()
        }
        .padding(.horizontal, 32)
    }
}
