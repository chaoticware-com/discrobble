import SwiftUI

@main
struct DiscrobbleApp: App {
    @StateObject private var appModel = AppModel()
    @StateObject private var shazamRecognitionCoordinator = ShazamRecognitionCoordinator()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appModel)
                .environmentObject(shazamRecognitionCoordinator)
                .onOpenURL { url in
                    appModel.handleIncomingURL(url)
                }
        }
    }
}
