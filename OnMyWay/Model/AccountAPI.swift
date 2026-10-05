import Foundation

enum AccountAPI {
    /// Worker URL. `wrangler deploy` prints the real address; paste it here.
    static let baseURL = URL(string: "https://onmyway-api.onmyway-wyl.workers.dev")!

    struct Account: Decodable {
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
        try await post("register", body: [
            "email": email,
            "firstName": firstName,
            "lastName": lastName,
            "username": username,
            "password": password,
        ])
    }

    static func login(username: String, password: String) async throws -> Account {
        try await post("login", body: [
            "username": username,
            "password": password,
        ])
    }

    static func currentAccount(token: String) async throws -> Account {
        var request = URLRequest(url: baseURL.appending(path: "me"))
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        return try await send(request)
    }

    private static func post(_ path: String, body: [String: String]) async throws -> Account {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await send(request)
    }

    private static func send(_ request: URLRequest) async throws -> Account {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw AccountAPIError(message: "Could not reach the server. Check your connection and try again.")
        }

        if let account = try? JSONDecoder().decode(Account.self, from: data),
           let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) {
            return account
        }

        let message = (try? JSONDecoder().decode(ErrorBody.self, from: data))?.error
            ?? "Could not sign in. Try again."
        throw AccountAPIError(message: message)
    }
}

struct AccountAPIError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

private struct ErrorBody: Decodable {
    let error: String
}
