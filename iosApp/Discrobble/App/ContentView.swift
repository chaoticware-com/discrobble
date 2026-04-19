import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(spacing: 16) {
            Text("Discrobble")
                .font(.largeTitle)
                .bold()

            Text("iPhone shell scaffold")
                .font(.headline)

            Text("Deep-link auth handling and secure token storage land in the next backlog slice.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .padding(24)
    }
}

#Preview {
    ContentView()
}
