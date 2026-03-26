import SwiftUI

// MARK: - BundleUiItem

struct BundleUiItem: Identifiable {
    var id: String { issuerDid }
    let issuerDid: String
    let displayName: String
    let fetchedAt: String
    let freshnessRatio: Double
}

// MARK: - BundleManagementView

struct BundleManagementView: View {
    @EnvironmentObject var services: ServiceContainer
    @Environment(AppRouter.self) private var router

    @State private var bundles: [BundleUiItem] = []
    @State private var isRefreshing = false
    @State private var showAddDialog = false
    @State private var didInput = ""
    @State private var error: String?

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

                Text("Offline Bundles")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                // Refresh button
                Button {
                    Task { await refresh() }
                } label: {
                    if isRefreshing {
                        ProgressView()
                            .tint(Color.ssdidAccent)
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "arrow.clockwise")
                            .foregroundStyle(Color.ssdidAccent)
                            .font(.system(size: 18))
                    }
                }
                .disabled(isRefreshing)
                .padding(.trailing, 8)

                // Add button
                Button {
                    showAddDialog = true
                } label: {
                    Image(systemName: "plus")
                        .foregroundStyle(Color.ssdidAccent)
                        .font(.system(size: 18))
                }
                .padding(.trailing, 20)
            }
            .padding(.vertical, 12)

            // Error banner
            if let errorMsg = error {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(Color.danger)
                        .font(.system(size: 14))
                    Text(errorMsg)
                        .font(.system(size: 13))
                        .foregroundStyle(Color.danger)
                    Spacer()
                    Button {
                        error = nil
                    } label: {
                        Image(systemName: "xmark")
                            .foregroundStyle(Color.textTertiary)
                            .font(.system(size: 12))
                    }
                }
                .padding(12)
                .background(Color.dangerDim)
                .cornerRadius(10)
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
            }

            if bundles.isEmpty && !isRefreshing {
                emptyState
            } else {
                bundleList
            }
        }
        .background(Color.bgPrimary)
        .task {
            await loadBundles()
        }
        .sheet(isPresented: $showAddDialog) {
            addBundleSheet
        }
    }

    // MARK: - Bundle List

    @ViewBuilder
    private var bundleList: some View {
        List {
            ForEach(bundles) { item in
                bundleRow(item: item)
                    .listRowBackground(Color.bgCard)
                    .listRowSeparatorTint(Color.ssdidBorder)
                    .listRowInsets(EdgeInsets(top: 4, leading: 20, bottom: 4, trailing: 20))
            }
            .onDelete { indexSet in
                Task { await deleteBundle(at: indexSet) }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func bundleRow(item: BundleUiItem) -> some View {
        HStack(spacing: 12) {
            // Icon
            ZStack {
                Circle()
                    .fill(Color.ssdidAccent.opacity(0.12))
                    .frame(width: 40, height: 40)
                Image(systemName: "doc.badge.checkmark")
                    .foregroundStyle(Color.ssdidAccent)
                    .font(.system(size: 18))
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(item.displayName)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
                Text(item.fetchedAt)
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textTertiary)
            }

            Spacer()

            BundleFreshnessBadge(freshnessRatio: item.freshnessRatio)
        }
        .padding(.vertical, 4)
    }

    // MARK: - Empty State

    @ViewBuilder
    private var emptyState: some View {
        Spacer()
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.bgElevated)
                    .frame(width: 80, height: 80)
                Image(systemName: "internaldrive")
                    .font(.system(size: 36))
                    .foregroundStyle(Color.textTertiary)
            }
            Text("No Cached Bundles")
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)
            Text("Add issuer DIDs to cache their verification bundles for offline use.")
                .font(.system(size: 14))
                .foregroundStyle(Color.textTertiary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                showAddDialog = true
            } label: {
                Label("Add Bundle", systemImage: "plus")
            }
            .buttonStyle(.ssdidSecondary)
            .padding(.horizontal, 60)
            .padding(.top, 8)
        }
        Spacer()
    }

    // MARK: - Add Bundle Sheet

    @ViewBuilder
    private var addBundleSheet: some View {
        NavigationStack {
            VStack(spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Issuer DID")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Color.textSecondary)
                    TextField("did:ssdid:...", text: $didInput)
                        .font(.ssdidMono)
                        .foregroundStyle(Color.textPrimary)
                        .padding(12)
                        .background(Color.bgElevated)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.ssdidBorder, lineWidth: 1)
                        )
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }
                .padding(.horizontal, 20)

                Button {
                    Task {
                        await addBundle(did: didInput)
                        showAddDialog = false
                    }
                } label: {
                    Label("Fetch Bundle", systemImage: "arrow.down.circle")
                }
                .buttonStyle(.ssdidPrimary(enabled: !didInput.isEmpty))
                .disabled(didInput.isEmpty)
                .padding(.horizontal, 20)

                Spacer()
            }
            .padding(.top, 20)
            .background(Color.bgPrimary)
            .navigationTitle("Add Bundle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        didInput = ""
                        showAddDialog = false
                    }
                    .foregroundStyle(Color.ssdidAccent)
                }
            }
        }
        .presentationDetents([.medium])
        .presentationBackground(Color.bgPrimary)
    }

    // MARK: - Data Operations

    private func loadBundles() async {
        let store = services.bundleStore
        let ttl = services.ttlProvider
        do {
            let raw = try await store.listBundles()
            let items = raw.map { bundle in
                BundleUiItem(
                    issuerDid: bundle.issuerDid,
                    displayName: shortDid(bundle.issuerDid),
                    fetchedAt: formattedDate(bundle.fetchedAt),
                    freshnessRatio: ttl.freshnessRatio(fetchedAt: bundle.fetchedAt)
                )
            }
            await MainActor.run { bundles = items }
        } catch {
            await MainActor.run { self.error = error.localizedDescription }
        }
    }

    private func refresh() async {
        let syncManager = services.bundleSyncManager
        isRefreshing = true
        await syncManager.syncNow()
        await loadBundles()
        isRefreshing = false
    }

    private func addBundle(did: String) async {
        let trimmed = did.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let manager = services.bundleManager
        let result = await manager.prefetchBundle(issuerDid: trimmed)
        if case .failure(let err) = result {
            await MainActor.run {
                self.error = "Failed to fetch bundle for \(trimmed): \(err.localizedDescription)"
            }
        }
        await loadBundles()
        didInput = ""
    }

    private func deleteBundle(at offsets: IndexSet) async {
        let store = services.bundleStore
        let toDelete = offsets.map { bundles[$0].issuerDid }
        for did in toDelete {
            try? await store.deleteBundle(issuerDid: did)
        }
        await loadBundles()
    }

    // MARK: - Helpers

    private func shortDid(_ did: String) -> String {
        let parts = did.split(separator: ":")
        guard parts.count >= 3 else { return did }
        let id = String(parts[parts.count - 1])
        if id.count > 20 {
            return "\(id.prefix(8))...\(id.suffix(6))"
        }
        return id
    }

    private func formattedDate(_ iso: String) -> String {
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: iso) else { return iso }
        let display = DateFormatter()
        display.dateStyle = .medium
        display.timeStyle = .short
        return display.string(from: date)
    }
}
