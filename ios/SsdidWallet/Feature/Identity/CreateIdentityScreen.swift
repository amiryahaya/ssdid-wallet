import SwiftUI

struct CreateIdentityScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let acceptedAlgorithms: String?

    @State private var name = ""
    @State private var selectedAlgorithm: Algorithm = .KAZ_SIGN_192
    @State private var isCreating = false
    @State private var errorMessage: String?

    init(acceptedAlgorithms: String? = nil) {
        self.acceptedAlgorithms = acceptedAlgorithms
    }

    private var availableAlgorithms: [Algorithm] {
        guard let accepted = acceptedAlgorithms, !accepted.isEmpty else {
            return Algorithm.allCases
        }
        // Parse JSON array of algorithm names
        guard let data = accepted.data(using: .utf8),
              let names = try? JSONDecoder().decode([String].self, from: data),
              !names.isEmpty else {
            return Algorithm.allCases
        }
        return Algorithm.allCases.filter { names.contains($0.rawValue) }
    }

    private struct AlgorithmGroup: Identifiable {
        let id: String
        let name: String
        let algorithms: [Algorithm]
    }

    private var algorithmGroups: [AlgorithmGroup] {
        let algos = availableAlgorithms
        var groups: [AlgorithmGroup] = []

        let classical = algos.filter { !$0.isPostQuantum }
        if !classical.isEmpty {
            groups.append(AlgorithmGroup(id: "classical", name: "CLASSICAL", algorithms: classical))
        }

        let kazSign = algos.filter { $0.isKazSign }
        if !kazSign.isEmpty {
            groups.append(AlgorithmGroup(id: "kaz", name: "KAZ-SIGN (PQC)", algorithms: kazSign))
        }

        let mlDsa = algos.filter { $0.isMlDsa }
        if !mlDsa.isEmpty {
            groups.append(AlgorithmGroup(id: "mldsa", name: "ML-DSA (PQC)", algorithms: mlDsa))
        }

        let slhDsa = algos.filter { $0.isSlhDsa }
        if !slhDsa.isEmpty {
            groups.append(AlgorithmGroup(id: "slhdsa", name: "SLH-DSA (PQC)", algorithms: slhDsa))
        }

        return groups
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

                Text("Create Identity")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    // Identity name
                    Text("IDENTITY NAME")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)

                    TextField("", text: $name, prompt: Text("e.g. Personal, Work").foregroundStyle(Color.textTertiary))
                        .textFieldStyle(.plain)
                        .font(.ssdidBody)
                        .foregroundStyle(Color.textPrimary)
                        .padding(14)
                        .background(Color.bgCard)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.ssdidBorder, lineWidth: 1)
                        )

                    Spacer().frame(height: 4)

                    Text("SIGNATURE ALGORITHM")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)

                    // Algorithm groups
                    ForEach(algorithmGroups) { group in
                        Text(group.name)
                            .font(.ssdidCaption)
                            .foregroundStyle(group.id == "classical" ? Color.classical : Color.pqc)
                            .padding(.top, 4)

                        ForEach(group.algorithms, id: \.rawValue) { algo in
                            algorithmRow(algo)
                        }
                    }
                }
                .padding(.horizontal, 20)
            }

            // Error
            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.danger)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 4)
            }

            // Create button
            Button {
                createIdentity()
            } label: {
                if isCreating {
                    HStack(spacing: 10) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        Text("Creating...")
                    }
                } else {
                    Text("Create Identity")
                }
            }
            .buttonStyle(.ssdidPrimary(enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty && !isCreating))
            .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || isCreating)
            .padding(20)
        }
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func algorithmRow(_ algo: Algorithm) -> some View {
        let isSelected = selectedAlgorithm == algo
        Button {
            selectedAlgorithm = algo
        } label: {
            HStack(spacing: 8) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textTertiary)
                    .font(.system(size: 20))

                VStack(alignment: .leading, spacing: 2) {
                    Text(algo.rawValue.replacingOccurrences(of: "_", with: " "))
                        .font(.ssdidBody.weight(.medium))
                        .foregroundStyle(Color.textPrimary)
                    Text(algo.w3cType)
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textTertiary)
                }

                Spacer()

                AlgorithmBadge(
                    name: algo.isPostQuantum ? "PQC" : "Classical",
                    isPostQuantum: algo.isPostQuantum
                )
            }
            .padding(14)
            .background(isSelected ? Color.accentDim : Color.bgCard)
            .cornerRadius(12)
        }
    }

    private func createIdentity() {
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        guard !trimmedName.isEmpty else { return }
        isCreating = true
        errorMessage = nil

        Task {
            do {
                _ = try await services.ssdidClient.initIdentity(
                    name: trimmedName,
                    algorithm: selectedAlgorithm
                )
                await MainActor.run {
                    isCreating = false
                    router.push(.biometricSetup)
                }
            } catch {
                await MainActor.run {
                    isCreating = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}
