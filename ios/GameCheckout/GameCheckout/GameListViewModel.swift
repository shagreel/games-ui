import Foundation

@MainActor
final class GameListViewModel: ObservableObject {
    @Published var filteredGames: [Game] = []
    @Published var searchText: String = "" {
        didSet { applyFilter() }
    }
    @Published var isLoading = false
    @Published var errorMessage: String?

    private var allGames: [Game] = []
    private let authManager: AuthManager

    init(authManager: AuthManager) {
        self.authManager = authManager
    }

    /// Currently borrowed games, oldest borrow date first — same ordering as the web app's admin table.
    var borrowedGames: [Game] {
        allGames
            .filter { $0.borrowed != nil }
            .sorted { $0.borrowed!.date > $1.borrowed!.date }
    }

    func load() async {
        guard let authHeader = authManager.authHeaderValue else { return }

        isLoading = true
        errorMessage = nil

        async let catalogTask = APIClient.shared.fetchCatalog()
        async let borrowedTask = Self.fetchBorrowedResult(authHeader: authHeader)

        do {
            var games = try await catalogTask
            switch await borrowedTask {
            case .success(let entries):
                let borrowedById = Dictionary(
                    uniqueKeysWithValues: entries.compactMap { entry -> (String, BorrowedInfo)? in
                        guard let info = entry.borrowed else { return nil }
                        return (entry.id, info)
                    }
                )
                for index in games.indices {
                    games[index].borrowed = borrowedById[games[index].id]
                }
            case .failure(APIError.unauthorized):
                authManager.handleUnauthorized()
                isLoading = false
                return
            case .failure:
                break // degrade gracefully: show the catalog without borrowed status
            }

            allGames = games
            applyFilter()
        } catch {
            errorMessage = "Couldn't load games. Pull to refresh to try again."
        }
        isLoading = false
    }

    func borrow(game: Game, name: String, email: String) async throws {
        guard let authHeader = authManager.authHeaderValue else { throw APIError.unauthorized }
        do {
            let info = try await APIClient.shared.borrowGame(id: game.id, name: name, email: email, authHeader: authHeader)
            updateLocal(id: game.id) { $0.borrowed = info }
            BorrowerProfileStore.save(name: name, email: email)
            if info?.name == name {
                Tracker.trackBorrowed(game: game.name, name: name, email: email)
            }
        } catch APIError.unauthorized {
            authManager.handleUnauthorized()
            throw APIError.unauthorized
        }
    }

    func returnGame(_ game: Game) async throws {
        guard let authHeader = authManager.authHeaderValue else { throw APIError.unauthorized }
        do {
            try await APIClient.shared.returnGame(id: game.id, authHeader: authHeader)
            if let borrowed = game.borrowed {
                Tracker.trackReturned(game: game.name, name: borrowed.name, email: borrowed.email)
            }
            updateLocal(id: game.id) { $0.borrowed = nil }
        } catch APIError.unauthorized {
            authManager.handleUnauthorized()
            throw APIError.unauthorized
        }
    }

    private static func fetchBorrowedResult(authHeader: String) async -> Result<[BorrowedEntry], Error> {
        do {
            return .success(try await APIClient.shared.fetchBorrowed(authHeader: authHeader))
        } catch {
            return .failure(error)
        }
    }

    private func applyFilter() {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else {
            filteredGames = allGames
            return
        }
        filteredGames = allGames.filter { game in
            game.name.localizedCaseInsensitiveContains(query)
                || (game.borrowed?.name.localizedCaseInsensitiveContains(query) ?? false)
                || (game.borrowed?.email.localizedCaseInsensitiveContains(query) ?? false)
        }
    }

    private func updateLocal(id: String, mutate: (inout Game) -> Void) {
        if let index = allGames.firstIndex(where: { $0.id == id }) {
            mutate(&allGames[index])
        }
        applyFilter()
    }
}
