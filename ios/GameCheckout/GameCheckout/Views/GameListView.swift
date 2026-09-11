import SwiftUI

struct GameListView: View {
    @StateObject private var viewModel: GameListViewModel
    @ObservedObject private var authManager: AuthManager
    @State private var selectedGame: Game?

    init(authManager: AuthManager) {
        self.authManager = authManager
        _viewModel = StateObject(wrappedValue: GameListViewModel(authManager: authManager))
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading && viewModel.filteredGames.isEmpty {
                    ProgressView("Loading games…")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let errorMessage = viewModel.errorMessage, viewModel.filteredGames.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "wifi.slash")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text(errorMessage)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(.secondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(viewModel.filteredGames) { game in
                        GameRowView(game: game)
                            .contentShape(Rectangle())
                            .onTapGesture { selectedGame = game }
                    }
                    .listStyle(.plain)
                    .refreshable { await viewModel.load() }
                }
            }
            .navigationTitle("Game Checkout")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $viewModel.searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    NavigationLink {
                        AdminView(viewModel: viewModel)
                    } label: {
                        Label("Borrowed Games", systemImage: "list.bullet.clipboard")
                    }
                }
                ToolbarItem(placement: .principal) {
                    Text("Game Checkout")
                        .font(.headline)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .destructive) { authManager.signOut() } label: {
                        Label("Sign Out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
        }
        .task { await viewModel.load() }
        .onAppear { Tracker.trackPageView("Game List") }
        .sheet(item: $selectedGame) { game in
            BorrowReturnSheet(game: game, viewModel: viewModel)
        }
    }
}
