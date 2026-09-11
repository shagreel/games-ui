import SwiftUI

struct BorrowReturnSheet: View {
    let game: Game
    @ObservedObject var viewModel: GameListViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var email: String
    @State private var isSubmitting = false
    @State private var errorMessage: String?

    private var isBorrowed: Bool { game.borrowed != nil }

    init(game: Game, viewModel: GameListViewModel) {
        self.game = game
        self.viewModel = viewModel
        _name = State(initialValue: BorrowerProfileStore.name)
        _email = State(initialValue: BorrowerProfileStore.email)
    }

    var body: some View {
        NavigationStack {
            Form {
                if let borrowed = game.borrowed {
                    Section {
                        LabeledContent("Borrowed by", value: borrowed.name)
                        LabeledContent("Email", value: borrowed.email)
                        LabeledContent("Date", value: borrowed.date)
                    }
                } else {
                    Section {
                        TextField("Full Name", text: $name)
                            .textContentType(.name)
                        TextField("Email", text: $email)
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("\(isBorrowed ? "Return" : "Borrow") \(game.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSubmitting {
                        ProgressView()
                    } else {
                        Button(isBorrowed ? "Return" : "Borrow") { submit() }
                            .disabled(!isBorrowed && (name.isEmpty || email.isEmpty))
                            .keyboardShortcut(.defaultAction)
                    }
                }
            }
            .disabled(isSubmitting)
            .onSubmit(submit)
        }
        .presentationDetents([.medium])
        .onAppear {
            if !isBorrowed {
                Tracker.trackViewed(game: game.name)
            }
        }
    }

    private func submit() {
        isSubmitting = true
        errorMessage = nil
        Task {
            do {
                if isBorrowed {
                    try await viewModel.returnGame(game)
                } else {
                    try await viewModel.borrow(game: game, name: name, email: email)
                }
                dismiss()
            } catch {
                errorMessage = "Something went wrong. Please try again."
                isSubmitting = false
            }
        }
    }
}
