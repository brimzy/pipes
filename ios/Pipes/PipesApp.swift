import SwiftUI

@main
struct PipesApp: App {
    @StateObject private var library = MusicLibrary()
    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(library)
                .preferredColorScheme(.dark).tint(Color(red: 0.02, green: 0.82, blue: 0.98))
        }
    }
}
