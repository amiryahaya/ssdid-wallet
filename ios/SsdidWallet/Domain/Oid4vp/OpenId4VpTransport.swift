import Foundation

class OpenId4VpTransport {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func fetchRequestObject(requestUri: String) throws -> AuthorizationRequest {
        guard let url = URL(string: requestUri) else {
            throw OpenId4VpError.invalidRequestUri(requestUri)
        }

        var result: Result<AuthorizationRequest, Error>!
        let semaphore = DispatchSemaphore(value: 0)

        let task = session.dataTask(with: url) { data, response, error in
            if let error = error {
                result = .failure(error)
                semaphore.signal()
                return
            }
            guard let httpResponse = response as? HTTPURLResponse,
                  (200...299).contains(httpResponse.statusCode) else {
                result = .failure(OpenId4VpError.httpError((response as? HTTPURLResponse)?.statusCode ?? 0))
                semaphore.signal()
                return
            }
            guard let data = data, let json = String(data: data, encoding: .utf8) else {
                result = .failure(OpenId4VpError.emptyResponse)
                semaphore.signal()
                return
            }
            result = Self.parseJsonRequest(json)
            semaphore.signal()
        }
        task.resume()
        semaphore.wait()
        return try result.get()
    }

    func postVpResponse(
        responseUri: String,
        vpToken: String,
        presentationSubmission: PresentationSubmission?,
        state: String?
    ) throws {
        var body = "vp_token=\(vpToken.urlEncoded)"
        if let submission = presentationSubmission {
            let json = try submission.toJson()
            body += "&presentation_submission=\(json.urlEncoded)"
        }
        if let state = state {
            body += "&state=\(state.urlEncoded)"
        }
        try post(to: responseUri, body: body)
    }

    func postError(responseUri: String, error: String, state: String?) throws {
        var body = "error=\(error.urlEncoded)"
        if let state = state {
            body += "&state=\(state.urlEncoded)"
        }
        try post(to: responseUri, body: body)
    }

    private func post(to urlString: String, body: String) throws {
        guard let url = URL(string: urlString) else {
            throw OpenId4VpError.invalidResponseUri(urlString)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = body.data(using: .utf8)

        var result: Result<Void, Error>!
        let semaphore = DispatchSemaphore(value: 0)

        let task = session.dataTask(with: request) { _, response, error in
            if let error = error {
                result = .failure(error)
            } else if let httpResponse = response as? HTTPURLResponse,
                      !(200...299).contains(httpResponse.statusCode) {
                result = .failure(OpenId4VpError.httpError(httpResponse.statusCode))
            } else {
                result = .success(())
            }
            semaphore.signal()
        }
        task.resume()
        semaphore.wait()
        try result.get()
    }

    static func parseJsonRequest(_ json: String) -> Result<AuthorizationRequest, Error> {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return .failure(OpenId4VpError.invalidRequestObject)
        }

        guard let clientId = obj["client_id"] as? String else {
            return .failure(OpenId4VpError.missingClientId)
        }

        var pdString: String? = nil
        if let pd = obj["presentation_definition"] {
            if let pdData = try? JSONSerialization.data(withJSONObject: pd) {
                pdString = String(data: pdData, encoding: .utf8)
            }
        }

        var dcqlString: String? = nil
        if let dcql = obj["dcql_query"] {
            if let dcqlData = try? JSONSerialization.data(withJSONObject: dcql) {
                dcqlString = String(data: dcqlData, encoding: .utf8)
            }
        }

        return .success(AuthorizationRequest(
            clientId: clientId,
            responseType: obj["response_type"] as? String,
            responseMode: obj["response_mode"] as? String,
            responseUri: obj["response_uri"] as? String,
            nonce: obj["nonce"] as? String,
            state: obj["state"] as? String,
            presentationDefinition: pdString,
            dcqlQuery: dcqlString,
            requestUri: nil
        ))
    }
}

private extension String {
    var urlEncoded: String {
        addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? self
    }
}
