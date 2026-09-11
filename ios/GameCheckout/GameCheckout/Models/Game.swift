import Foundation

struct Game: Identifiable, Codable, Equatable {
    let id: String
    let name: String
    let cover: String
    var borrowed: BorrowedInfo?
}

struct BorrowedInfo: Codable, Equatable {
    let name: String
    let email: String
    let date: String
}

/// Shape of each entry returned by `GET /games/borrowed`.
struct BorrowedEntry: Codable {
    let id: String
    let borrowed: BorrowedInfo?
}
