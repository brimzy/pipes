import SwiftUI

enum PipesStyle {
    static let background = Color(red: 0.06, green: 0.06, blue: 0.06)
    static let surface = Color(red: 0.10, green: 0.10, blue: 0.10)
    static let accent = Color(red: 61.0 / 255, green: 219.0 / 255, blue: 217.0 / 255)
    static var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "5.1.60" }
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

private struct ShowPlayerKey: EnvironmentKey { static let defaultValue: () -> Void = {} }
private struct AddSongsKey: EnvironmentKey { static let defaultValue: ([Track]) -> Void = { _ in } }
extension EnvironmentValues {
    var showPlayer: () -> Void {
        get { self[ShowPlayerKey.self] }
        set { self[ShowPlayerKey.self] = newValue }
    }
    var addSongs: ([Track]) -> Void {
        get { self[AddSongsKey.self] }
        set { self[AddSongsKey.self] = newValue }
    }
}
