import Foundation

final class HttpStatusListFetcher: StatusListFetcher {
    private let session: URLSession
    private let decoder = JSONDecoder()

    init(session: URLSession = .shared) {
        self.session = session
    }

    func fetch(url: String) async throws -> StatusListCredential {
        guard !url.isEmpty else {
            throw RevocationError.fetchFailed("Status list URL must not be empty")
        }
        guard let parsed = URL(string: url), parsed.scheme == "https" else {
            throw RevocationError.fetchFailed("Status list URL must use HTTPS: \(url)")
        }

        var request = URLRequest(url: parsed)
        request.timeoutInterval = 10

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw RevocationError.fetchFailed("HTTP \(code)")
        }

        return try decoder.decode(StatusListCredential.self, from: data)
    }
}
