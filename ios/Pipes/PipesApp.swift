import SwiftUI

enum PipesStyle {
    static let accent = Color(red: 61.0 / 255, green: 219.0 / 255, blue: 217.0 / 255)
}

@main
@MainActor
struct PipesApp: App {
    @StateObject private var library = MusicLibrary.shared
    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(library)
                .preferredColorScheme(.dark).tint(PipesStyle.accent)
        }
    }
}
