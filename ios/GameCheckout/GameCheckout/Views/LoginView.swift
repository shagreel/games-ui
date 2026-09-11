import SwiftUI

struct LoginView: View {
    @ObservedObject var authManager: AuthManager
    @State private var password = ""

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            VStack(spacing: 8) {
                // The app icon, presentation-sized. The artwork is full-bleed, so
                // it is clipped to the shape iOS masks launcher icons to rather
                // than shown as a bare square.
                Image(.loginMark)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 110, height: 110)
                    .clipShape(
                        RoundedRectangle(cornerRadius: 110 * 0.2237, style: .continuous)
                    )
                Text("Game Checkout")
                    .font(.largeTitle.bold())
            }

            VStack(alignment: .leading, spacing: 8) {
                SecureField("Password", text: $password)
                    .textContentType(.password)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding()
                    .background(.quaternary, in: RoundedRectangle(cornerRadius: 10))
                    .submitLabel(.go)
                    .disabled(authManager.isValidating)
                    .onSubmit(submit)

                if let error = authManager.loginError {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Text("Forgot the password? Check the **Game Checkout Procedure and Policies** canvas in the **#lehi-board-games** Slack channel.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 32)

            Button(action: submit) {
                if authManager.isValidating {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                } else {
                    Text("Continue")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding(.horizontal, 32)
            .disabled(password.isEmpty || authManager.isValidating)

            Spacer()
            Spacer()
        }
    }

    private func submit() {
        guard !password.isEmpty, !authManager.isValidating else { return }
        let candidate = password
        password = ""
        Task {
            await authManager.login(password: candidate)
        }
    }
}
