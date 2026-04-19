import SwiftUI

@main
struct DiscrobbleApp: App {
    @StateObject private var appModel = AppModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appModel)
                .onOpenURL { url in
                    appModel.handleIncomingURL(url)
                }
        }
    }
}
