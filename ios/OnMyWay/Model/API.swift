import Foundation

/// Client for the Worker in `api/src/index.ts`.
enum API {
    /// Worker URL. `wrangler deploy` prints the real address; paste it here.
    /// Set the `API_URL` environment variable (e.g. `http://localhost:8787`) to use a local Worker.
    static let baseURL = ProcessInfo.processInfo.environment["API_URL"].flatMap(URL.init(string:))
        ?? URL(string: "https://onmyway-api.onmyway-wyl.workers.dev")!

    /// Sends a request and decodes the JSON response. Throws `APIError` with the server's message.
    static func send<Response: Decodable & Sendable>(
        _ method: String,
        _ path: String,
        body: [String: any Sendable]? = nil,
        query: [String: String] = [:],
        token: String?
    ) async throws -> Response {
        var components = URLComponents(url: baseURL.appending(path: path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty {
            components.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        var request = URLRequest(url: components.url!)
        request.httpMethod = method
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw APIError(message: "Could not reach the server. Check your connection and try again.")
        }

        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let body = try? decoder.decode(ErrorBody.self, from: data)
            throw APIError(message: body?.error ?? "Something went wrong. Try again.", code: body?.code, status: status)
        }
        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            throw APIError(message: "Something went wrong. Try again.", status: status)
        }
    }

    /// Decodes the Worker's ISO 8601 timestamps, with or without fractional seconds.
    static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let value = try decoder.singleValueContainer().decode(String.self)
            if let date = try? Date(value, strategy: .iso8601.year().month().day().time(includingFractionalSeconds: true)) {
                return date
            }
            if let date = try? Date(value, strategy: .iso8601) { return date }
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Bad date \(value)"))
        }
        return decoder
    }()

    static func iso(_ date: Date) -> String {
        date.formatted(.iso8601)
    }
}

// MARK: Account

extension API {
    struct Account: Decodable, Sendable {
        let id: String
        let username: String
        let email: String
        let firstName: String
        let lastName: String
        let token: String
    }

    static func register(
        email: String,
        firstName: String,
        lastName: String,
        username: String,
        password: String
    ) async throws -> Account {
        try await send("POST", "register", body: [
            "email": email,
            "firstName": firstName,
            "lastName": lastName,
            "username": username,
            "password": password,
        ], token: nil)
    }

    static func login(username: String, password: String) async throws -> Account {
        try await send("POST", "login", body: ["username": username, "password": password], token: nil)
    }

    static func currentAccount(token: String) async throws -> Account {
        try await send("GET", "me", token: token)
    }
}

struct APIError: LocalizedError {
    let message: String
    var code: String?
    var status = 0
    var errorDescription: String? { message }
}

/// For endpoints whose response body the app doesn't use.
struct Empty: Decodable, Sendable {}

private struct ErrorBody: Decodable {
    let error: String
    let code: String?
}
