import SwiftUI
import UniformTypeIdentifiers

struct BackupScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let restoreUri: String?

    enum BackupState {
        case idle
        case creating
        case success(Data)
        case restoring
        case restoreSuccess(Int)
        case error(String)
    }

    @State private var state: BackupState = .idle
    @State private var passphrase = ""
    @State private var confirmPassphrase = ""
    @State private var restorePassphrase = ""
    @State private var loadedFileData: Data?
    @State private var showFileExporter = false
    @State private var showFileImporter = false
    @State private var showShareSheet = false
    @State private var backupDataForExport: Data?

    private var passphrasesMatch: Bool {
        passphrase == confirmPassphrase && passphrase.count >= 8
    }

    private var canCreate: Bool {
        passphrasesMatch && !isCreating
    }

    private var isCreating: Bool {
        if case .creating = state { return true }
        return false
    }

    private var strengthColor: Color {
        if passphrase.count >= 12 { return .success }
        if passphrase.count >= 8 { return .warning }
        return .danger
    }

    private var strengthFraction: CGFloat {
        if passphrase.isEmpty { return 0 }
        if passphrase.count >= 12 { return 1 }
        if passphrase.count >= 8 { return 0.66 }
        return 0.33
    }

    private var strengthLabel: String {
        if passphrase.isEmpty { return "" }
        if passphrase.count >= 12 { return "Strong passphrase" }
        if passphrase.count >= 8 { return "Moderate passphrase" }
        return "Weak passphrase"
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

                Text("Backup & Export")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // CREATE section
                    Text("CREATE ENCRYPTED BACKUP")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textSecondary)

                    Spacer().frame(height: 12)

                    // Passphrase
                    SecureField("Passphrase", text: $passphrase)
                        .foregroundStyle(Color.textPrimary)
                        .padding(14)
                        .background(Color.bgCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

                    Spacer().frame(height: 8)

                    // Confirm
                    SecureField("Confirm Passphrase", text: $confirmPassphrase)
                        .foregroundStyle(Color.textPrimary)
                        .padding(14)
                        .background(Color.bgCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

                    Spacer().frame(height: 8)

                    // Strength indicator
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(Color.bgCard)
                                .frame(height: 4)
                            RoundedRectangle(cornerRadius: 2)
                                .fill(strengthColor)
                                .frame(width: geo.size.width * strengthFraction, height: 4)
                        }
                    }
                    .frame(height: 4)

                    Spacer().frame(height: 4)

                    Text(strengthLabel)
                        .font(.system(size: 11))
                        .foregroundStyle(strengthColor)

                    Spacer().frame(height: 16)

                    // Create button
                    Button {
                        createBackup()
                    } label: {
                        if isCreating {
                            HStack(spacing: 10) {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                Text("Creating Backup...")
                            }
                        } else {
                            Text("Create Backup")
                        }
                    }
                    .buttonStyle(.ssdidPrimary(enabled: canCreate))
                    .disabled(!canCreate)

                    Spacer().frame(height: 16)

                    // Success card
                    if case .success(let data) = state {
                        VStack(alignment: .leading, spacing: 0) {
                            Text("Backup Created Successfully")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Color.success)
                            Spacer().frame(height: 4)
                            Text("Size: \(formatBytes(data.count))")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.textSecondary)
                            Spacer().frame(height: 12)
                            Button {
                                backupDataForExport = data
                                showFileExporter = true
                            } label: {
                                Text("Save to File")
                                    .font(.ssdidBody.weight(.semibold))
                                    .foregroundStyle(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(Color.success)
                                    .cornerRadius(12)
                            }
                            Spacer().frame(height: 8)
                            Button {
                                backupDataForExport = data
                                showShareSheet = true
                            } label: {
                                Text("Share Backup")
                            }
                            .buttonStyle(.ssdidPrimary)
                        }
                        .padding(16)
                        .background(Color.successDim)
                        .cornerRadius(12)

                        Spacer().frame(height: 16)
                    }

                    // Error card
                    if case .error(let message) = state {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Error")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Color.danger)
                            Text(message)
                                .font(.system(size: 13))
                                .foregroundStyle(Color.textSecondary)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.dangerDim)
                        .cornerRadius(12)

                        Spacer().frame(height: 16)
                    }

                    // Restore success
                    if case .restoreSuccess(let count) = state {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Restore Successful")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Color.success)
                            Text("\(count) identities restored")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.textSecondary)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.successDim)
                        .cornerRadius(12)

                        Spacer().frame(height: 16)
                    }

                    // RESTORE section
                    Spacer().frame(height: 8)
                    Text("RESTORE")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textSecondary)

                    Spacer().frame(height: 12)

                    Button {
                        showFileImporter = true
                    } label: {
                        if case .restoring = state {
                            HStack(spacing: 10) {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: Color.ssdidAccent))
                                Text("Restoring...")
                                    .foregroundStyle(Color.ssdidAccent)
                            }
                        } else {
                            Text("Import Backup File")
                                .foregroundStyle(Color.ssdidAccent)
                        }
                    }
                    .buttonStyle(.ssdidSecondary)

                    Spacer().frame(height: 16)

                    // Restore passphrase input
                    if loadedFileData != nil {
                        VStack(alignment: .leading, spacing: 0) {
                            Text("Backup file loaded")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Color.textPrimary)
                            Spacer().frame(height: 4)
                            Text(formatBytes(loadedFileData?.count ?? 0))
                                .font(.system(size: 13))
                                .foregroundStyle(Color.textSecondary)
                            Spacer().frame(height: 12)
                            SecureField("Restore Passphrase", text: $restorePassphrase)
                                .foregroundStyle(Color.textPrimary)
                                .padding(14)
                                .background(Color.bgPrimary)
                                .cornerRadius(12)
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))
                            Spacer().frame(height: 12)
                            Button {
                                restoreBackup()
                            } label: {
                                Text("Restore")
                            }
                            .buttonStyle(.ssdidPrimary(enabled: restorePassphrase.count >= 8))
                            .disabled(restorePassphrase.count < 8)
                        }
                        .ssdidCard()
                    }

                    Spacer().frame(height: 24)

                    // Info card
                    Text("Backups are encrypted with AES-256-GCM. Your passphrase is never stored.")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.textSecondary)
                        .lineSpacing(4)
                        .ssdidCard()

                    Spacer().frame(height: 20)
                }
                .padding(.horizontal, 20)
            }
        }
        .background(Color.bgPrimary)
        .fileExporter(
            isPresented: $showFileExporter,
            document: BackupFileDocument(data: backupDataForExport ?? Data()),
            contentType: .data,
            defaultFilename: "ssdid-backup-\(dateString()).enc"
        ) { result in
            if case .failure(let error) = result {
                state = .error(error.localizedDescription)
            }
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.data],
            allowsMultipleSelection: false
        ) { result in
            handleFileImport(result)
        }
        .sheet(isPresented: $showShareSheet) {
            if let data = backupDataForExport {
                ShareSheet(items: [data])
            }
        }
    }

    private func createBackup() {
        state = .creating
        Task {
            let biometricResult = await services.biometricAuthenticator.authenticateWithPasscodeFallback(
                reason: "Authenticate to create backup"
            )
            guard case .success = biometricResult else {
                state = .idle
                return
            }

            do {
                let backupData = try await services.backupManager.createBackup(passphrase: passphrase)
                HapticManager.notification(.success)
                state = .success(backupData)
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }

    private func restoreBackup() {
        guard let fileData = loadedFileData else { return }
        state = .restoring
        Task {
            let biometricResult = await services.biometricAuthenticator.authenticateWithPasscodeFallback(
                reason: "Authenticate to restore backup"
            )
            guard case .success = biometricResult else {
                state = .idle
                return
            }

            do {
                let count = try await services.backupManager.restoreBackup(
                    backupData: fileData,
                    passphrase: restorePassphrase
                )
                state = .restoreSuccess(count)
                loadedFileData = nil
            } catch {
                state = .error(error.localizedDescription)
            }
        }
    }

    private func handleFileImport(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            guard url.startAccessingSecurityScopedResource() else {
                state = .error("Cannot access file")
                return
            }
            defer { url.stopAccessingSecurityScopedResource() }

            do {
                let data = try Data(contentsOf: url)
                guard data.count <= 10_000_000 else {
                    state = .error("Backup file too large (max 10 MB)")
                    return
                }
                loadedFileData = data
            } catch {
                state = .error("Failed to read file: \(error.localizedDescription)")
            }
        case .failure(let error):
            state = .error(error.localizedDescription)
        }
    }

    private func formatBytes(_ bytes: Int) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        if bytes < 1024 * 1024 { return "\(bytes / 1024) KB" }
        return String(format: "%.1f MB", Double(bytes) / (1024.0 * 1024.0))
    }

    private func dateString() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}

// MARK: - File Document for Export

struct BackupFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }

    let data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        self.data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
