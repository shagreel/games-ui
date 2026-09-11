import SwiftUI

struct AdminView: View {
    @ObservedObject var viewModel: GameListViewModel

    var body: some View {
        Group {
            if viewModel.borrowedGames.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "checkmark.circle")
                        .font(.largeTitle)
                        .foregroundStyle(.secondary)
                    Text("No games are currently borrowed.")
                        .foregroundStyle(.secondary)
                }
            } else {
                List(viewModel.borrowedGames) { game in
                    if let borrowed = game.borrowed {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(game.name)
                                .font(.headline)
                            HStack {
                                Text(borrowed.name)
                                Spacer()
                                Text(borrowed.date)
                                    .font(.system(.footnote, design: .monospaced))
                                    .foregroundStyle(.secondary)
                            }
                            if let mailURL = URL(string: "mailto:\(borrowed.email)") {
                                Link(borrowed.email, destination: mailURL)
                                    .font(.footnote)
                            } else {
                                Text(borrowed.email)
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Borrowed Games")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { Tracker.trackPageView("Borrowed List") }
    }
}
