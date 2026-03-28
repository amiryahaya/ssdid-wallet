import Foundation

public protocol StatusListFetcher: Sendable {
    func fetch(url: String) async throws -> StatusListCredential
}

public actor RevocationManager {
    private let fetcher: StatusListFetcher
    private var cache: [String: StatusListCredential] = [:]

    public     init(fetcher: StatusListFetcher) {
        self.fetcher = fetcher
    }

    public     func checkRevocation(_ credential: VerifiableCredential) async -> RevocationStatus {
        guard let status = credential.credentialStatus else {
            return .valid
        }

        let listUrl = status.statusListCredential
        guard let index = Int(status.statusListIndex) else {
            return .unknown
        }

        let statusListCred: StatusListCredential
        if let cached = cache[listUrl] {
            statusListCred = cached
        } else {
            do {
                let fetched = try await fetcher.fetch(url: listUrl)
                cache[listUrl] = fetched
                statusListCred = fetched
            } catch {
                return .unknown
            }
        }

        do {
            if try BitstringParser.isRevoked(encodedList: statusListCred.credentialSubject.encodedList, index: index) {
                return .revoked
            } else {
                return .valid
            }
        } catch {
            return .unknown
        }
    }

    public     func invalidateCache() {
        cache.removeAll()
    }
}
