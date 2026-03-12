import SwiftUI

struct SsdidCard<Content: View>: View {
    var hasBorder: Bool
    @ViewBuilder var content: () -> Content

    init(border: Bool = false, @ViewBuilder content: @escaping () -> Content) {
        self.hasBorder = border
        self.content = content
    }

    var body: some View {
        content()
            .padding(16)
            .background(Color.bgCard)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(hasBorder ? Color.ssdidBorder : Color.clear, lineWidth: 1)
            )
    }
}

#Preview {
    VStack(spacing: 16) {
        SsdidCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Card Title")
                    .font(.ssdidHeadline)
                    .foregroundColor(.textPrimary)
                Text("Card description text goes here.")
                    .font(.ssdidBody)
                    .foregroundColor(.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }

        SsdidCard(border: true) {
            Text("Card with border")
                .font(.ssdidBody)
                .foregroundColor(.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
    .padding()
    .background(Color.bgPrimary)
}
