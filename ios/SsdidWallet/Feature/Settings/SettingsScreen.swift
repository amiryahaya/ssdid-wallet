import SwiftUI

struct SettingsScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    @State private var primaryKeyId: String?
    @State private var biometricEnabled = true
    @State private var autoLockMinutes = 5
    @State private var language = "en"
    @State private var showLanguageDialog = false

    private let languages: [(tag: String, name: String)] = [
        ("en", "English"),
        ("ms", "Bahasa Melayu"),
        ("zh", "Chinese")
    ]

    private var languageDisplay: String {
        languages.first(where: { $0.tag == language })?.name ?? "English"
    }

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

                Text("Settings")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 4) {
                    // Account
                    sectionHeader("ACCOUNT")
                    settingsItem("Profile", subtitle: "Name, email") {
                        if let keyId = primaryKeyId {
                            router.push(.profileEdit(keyId: keyId))
                        }
                    }

                    Spacer().frame(height: 16)

                    // Security
                    sectionHeader("SECURITY")
                    settingsToggle("Biometric Authentication", subtitle: "Face ID / Fingerprint", isOn: $biometricEnabled)
                    settingsItem("Auto-Lock", subtitle: "After \(autoLockMinutes) minutes")
                    settingsItem("Change Password", subtitle: "Update vault password")
                    settingsItem("Backup & Export", subtitle: "Encrypted backup of all identities") {
                        router.push(.backupExport())
                    }

                    Spacer().frame(height: 16)

                    // Network
                    sectionHeader("NETWORK")
                    settingsItem("Registry URL", subtitle: "registry.ssdid.my")

                    Spacer().frame(height: 16)

                    // Preferences
                    sectionHeader("PREFERENCES")
                    settingsItem("Appearance", subtitle: "Dark")
                    settingsItem("Language", subtitle: languageDisplay) {
                        showLanguageDialog = true
                    }
                    settingsItem("Default Algorithm", subtitle: "KAZ-Sign-192")

                    Spacer().frame(height: 16)

                    // About
                    sectionHeader("ABOUT")
                    settingsItem("Version", subtitle: "1.0.0 (Build 1)")
                    settingsItem("W3C DID 1.1", subtitle: "Compliant")
                }
                .padding(.horizontal, 20)
            }
        }
        .background(Color.bgPrimary)
        .task {
            if let first = await services.vault.listIdentities().first {
                primaryKeyId = first.keyId
            }
        }
        .sheet(isPresented: $showLanguageDialog) {
            languagePicker
        }
    }

    @ViewBuilder
    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.ssdidCaption)
            .foregroundStyle(Color.textTertiary)
            .padding(.bottom, 8)
    }

    @ViewBuilder
    private func settingsItem(_ title: String, subtitle: String, action: (() -> Void)? = nil) -> some View {
        Button {
            action?()
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Color.textPrimary)
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textTertiary)
                }
                Spacer()
                if action != nil {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.textTertiary)
                }
            }
            .padding(14)
            .background(Color.bgCard)
            .cornerRadius(12)
        }
        .disabled(action == nil)
    }

    @ViewBuilder
    private func settingsToggle(_ title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textTertiary)
            }
            Spacer()
            Toggle("", isOn: isOn)
                .labelsHidden()
                .tint(Color.ssdidAccent)
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(12)
    }

    private var languagePicker: some View {
        NavigationStack {
            List {
                ForEach(languages, id: \.tag) { lang in
                    Button {
                        language = lang.tag
                        showLanguageDialog = false
                    } label: {
                        HStack {
                            Text(lang.name)
                                .foregroundStyle(Color.textPrimary)
                            Spacer()
                            if language == lang.tag {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.ssdidAccent)
                            }
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.bgPrimary)
            .navigationTitle("Select Language")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showLanguageDialog = false }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
