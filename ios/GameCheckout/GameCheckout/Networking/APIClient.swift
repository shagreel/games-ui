import Foundation

enum APIError: Error {
    case invalidResponse
    case unauthorized
}

/// This backend signals failures — including a bad `x-cfp` credential —
/// with HTTP 200 and this body shape instead of a 4xx status, so every
/// response needs an explicit content check rather than relying on status codes.
private struct APIErrorBody: Decodable {
    let id: String
    let name: String
}

final class APIClient {
    static let shared = APIClient()

    private let session: URLSession
    private let decoder = JSONDecoder()

    private init(session: URLSession = .shared) {
        self.session = session
    }

    func fetchCatalog() async throws -> [Game] {
        let (data, response) = try await session.data(from: Config.catalogURL)
        try Self.validate(response)
        return try decoder.decode([Game].self, from: data)
    }

    func fetchBorrowed(authHeader: String) async throws -> [BorrowedEntry] {
        let url = Config.apiBaseURL.appendingPathComponent("games/borrowed")
        var request = URLRequest(url: url)
        request.setValue(authHeader, forHTTPHeaderField: "x-cfp")

        let (data, response) = try await session.data(for: request)
        try Self.validate(response)
        try Self.throwIfErrorBody(data)
        return try decoder.decode([BorrowedEntry].self, from: data)
    }

    func borrowGame(id: String, name: String, email: String, authHeader: String) async throws -> BorrowedInfo? {
        let url = Config.apiBaseURL.appendingPathComponent("games/borrow")
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(authHeader, forHTTPHeaderField: "x-cfp")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "id": id,
            "borrowed": [
                "name": name,
                "email": email,
                "date": Self.todayDateString()
            ]
        ])

        let (data, response) = try await session.data(for: request)
        try Self.validate(response)
        try Self.throwIfErrorBody(data)
        return try decoder.decode(BorrowedEntry.self, from: data).borrowed
    }

    func returnGame(id: String, authHeader: String) async throws {
        let url = Config.apiBaseURL.appendingPathComponent("games/return")
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(authHeader, forHTTPHeaderField: "x-cfp")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["id": id])

        let (data, response) = try await session.data(for: request)
        try Self.validate(response)
        try Self.throwIfErrorBody(data)
    }

    private static func validate(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        if http.statusCode == 401 || http.statusCode == 403 {
            throw APIError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else {
            throw APIError.invalidResponse
        }
    }

    /// Detects this backend's `{"id":"error", ...}` failure shape (returned
    /// with HTTP 200) and surfaces it the same way a real 401/403 would be.
    private static func throwIfErrorBody(_ data: Data) throws {
        let decoder = JSONDecoder()
        if let errorBody = try? decoder.decode(APIErrorBody.self, from: data), errorBody.id == "error" {
            throw APIError.unauthorized
        }
    }

    private static func todayDateString() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.timeZone = TimeZone.current
        return formatter.string(from: Date())
    }
}
