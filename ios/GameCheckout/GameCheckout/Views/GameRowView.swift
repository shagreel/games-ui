import SwiftUI

struct GameRowView: View {
    let game: Game

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            AsyncImage(url: URL(string: game.cover)) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().aspectRatio(contentMode: .fit)
                case .failure:
                    Image(systemName: "photo")
                        .foregroundStyle(.secondary)
                default:
                    ProgressView()
                }
            }
            .frame(width: 80, height: 80)
            .clipShape(RoundedRectangle(cornerRadius: 6))

            VStack(alignment: .leading, spacing: 4) {
                Text(game.name)
                    .font(.title3)
                    .strikethrough(game.borrowed != nil, color: .blue)
                    .foregroundStyle(game.borrowed != nil ? .blue : .primary)

                if let borrowed = game.borrowed {
                    Text("\(borrowed.name)  \(borrowed.email)")
                        .font(.system(.footnote, design: .monospaced))
                        .foregroundStyle(.blue)
                    Text(Self.formattedDate(borrowed.date))
                        .font(.system(.footnote, design: .monospaced))
                        .foregroundStyle(.blue)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }

    private static func formattedDate(_ raw: String) -> String {
        let input = DateFormatter()
        input.dateFormat = "yyyy-MM-dd"
        input.timeZone = TimeZone(identifier: "UTC")
        guard let date = input.date(from: raw) else { return raw }

        let output = DateFormatter()
        output.dateFormat = "MMM d"
        return output.string(from: date)
    }
}
