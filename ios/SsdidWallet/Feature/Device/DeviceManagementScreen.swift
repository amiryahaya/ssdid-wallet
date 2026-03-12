import SwiftUI

struct DeviceManagementScreen: View {
    @Environment(AppRouter.self) private var router

    let keyId: String

    struct DeviceInfo: Identifiable {
        let id = UUID()
        let name: String
        let platform: String
        let keyId: String
        let isPrimary: Bool
    }

    @State private var identity: Identity?
    @State private var devices: [DeviceInfo] = []
    @State private var revokeError: String?
    @State private var pendingRevokeKey: String?

    private var primaryDevice: DeviceInfo? { devices.first(where: { $0.isPrimary }) }
    private var otherDevices: [DeviceInfo] { devices.filter { !$0.isPrimary } }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 4) {
                Button { router.pop() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.textPrimary)
                        .font(.system(size: 20))
                }
                .padding(.leading, 8)

                Text("Devices")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            ScrollView {
                LazyVStack(spacing: 10) {
                    // This Device
                    Text("THIS DEVICE")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text(primaryDevice?.name ?? UIDevice.current.model)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Color.textPrimary)
                            Spacer()
                            Text("Primary")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(Color.success)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color.successDim)
                                .cornerRadius(4)
                        }
                        Spacer().frame(height: 12)

                        if let id = identity {
                            Text("KEY ID")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.textTertiary)
                            Text(id.keyId)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundStyle(Color.textSecondary)
                                .lineLimit(1)
                            Spacer().frame(height: 8)
                            Text("ALGORITHM")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.textTertiary)
                            Text(id.algorithm.rawValue.replacingOccurrences(of: "_", with: "-"))
                                .font(.system(size: 12))
                                .foregroundStyle(Color.textSecondary)
                            Spacer().frame(height: 8)
                            Text("ENROLLED")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.textTertiary)
                            Text(String(id.createdAt.prefix(10)))
                                .font(.system(size: 12))
                                .foregroundStyle(Color.textSecondary)
                        }
                    }
                    .padding(16)
                    .background(Color.bgCard)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.ssdidAccent, lineWidth: 1)
                    )

                    // Other Devices
                    Spacer().frame(height: 8)
                    Text("OTHER DEVICES")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if otherDevices.isEmpty {
                        Text("No other devices enrolled")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.textSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(24)
                            .background(Color.bgCard)
                            .cornerRadius(12)
                    } else {
                        ForEach(otherDevices) { device in
                            VStack(alignment: .leading, spacing: 0) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(device.name)
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundStyle(Color.textPrimary)
                                        Text(device.platform)
                                            .font(.system(size: 11))
                                            .foregroundStyle(Color.textTertiary)
                                    }
                                    Spacer()
                                    Button {
                                        pendingRevokeKey = device.keyId
                                    } label: {
                                        Text("Revoke")
                                            .font(.system(size: 12, weight: .semibold))
                                            .foregroundStyle(Color.danger)
                                    }
                                }
                                Spacer().frame(height: 4)
                                Text("KEY ID")
                                    .font(.system(size: 11))
                                    .foregroundStyle(Color.textTertiary)
                                Text(device.keyId)
                                    .font(.system(size: 12, design: .monospaced))
                                    .foregroundStyle(Color.textSecondary)
                                    .lineLimit(1)
                            }
                            .ssdidCard()
                        }
                    }

                    // Revoke error
                    if let error = revokeError {
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.danger)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.dangerDim)
                            .cornerRadius(12)
                    }

                    // Enroll button
                    Spacer().frame(height: 16)
                    Button {
                        router.push(.deviceEnroll(keyId: keyId, mode: "primary"))
                    } label: {
                        Text("Enroll New Device")
                    }
                    .buttonStyle(.ssdidPrimary)

                    // Info note
                    Spacer().frame(height: 16)
                    Text("Only the primary device can modify the DID Document. Secondary devices can authenticate but not manage the identity.")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.ssdidAccent)
                        .lineSpacing(4)
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.accentDim)
                        .cornerRadius(12)

                    Spacer().frame(height: 20)
                }
                .padding(.horizontal, 20)
            }
        }
        .background(Color.bgPrimary)
        .alert("Revoke Device", isPresented: Binding(
            get: { pendingRevokeKey != nil },
            set: { if !$0 { pendingRevokeKey = nil } }
        )) {
            Button("Cancel", role: .cancel) { pendingRevokeKey = nil }
            Button("Revoke", role: .destructive) {
                if let key = pendingRevokeKey {
                    devices.removeAll(where: { $0.keyId == key })
                    pendingRevokeKey = nil
                }
            }
        } message: {
            Text("This will permanently remove this device from your identity. Continue?")
        }
    }
}
