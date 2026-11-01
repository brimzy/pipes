import SwiftUI

private struct Lesson {
    let title: String
    let icon: String
    let tips: [(String, String)]
    let action: String
    let destination: Int
}

private let lessons = [
    Lesson(title: "Find your next song", icon: "magnifyingglass", tips: [
        ("Four ways in", "Home follows the playing song or your liked artists. Search finds recordings. Explore offers moods and genres. Library keeps your likes, collections and offline audio."),
        ("Search links", "Paste a YouTube video or public playlist, or a Spotify song link. Spotify provides metadata; playable matches come from YouTube Music. Spotify playlist import is still Android-only."),
        ("Bring your own music", "The folder button imports audio from Files. Pipes copies the files so moving the original doesn't remove them.")
    ], action: "Try Search", destination: 1),
    Lesson(title: "Get to know the player", icon: "play.circle", tips: [
        ("Preview, then seek", "Drag the waveform to preview a time. Release to seek. The waveform becomes available when the duration is known. The practice control below is silent."),
        ("Minimize or stop", "The down arrow keeps your music and queue. The close button stops playback and clears the queue. Pause keeps your position. Swipe the mini-player title left for next or right for previous."),
        ("Online and offline", "Online songs use the visible YouTube player and need Pipes open. Imported and downloaded audio supports background playback and lock-screen controls. Shuffle and repeat work with your queue.")
    ], action: "Try the player", destination: 5),
    Lesson(title: "Decide what plays next", icon: "text.line.first.and.arrowtriangle.forward", tips: [
        ("Choose the order", "Play next moves a song to the front of the upcoming queue. Add to queue appends it. Open Queue in the player to reorder or remove upcoming songs."),
        ("Swipe a song", "Swipe right to queue it. Swipe left to remove an upcoming entry. This gesture never removes songs from a saved collection."),
        ("Start radio", "Choose Start radio on an online song for related recordings. Starting radio on the current song preserves its position. Source availability can affect suggestions.")
    ], action: "Try the queue", destination: 6),
    Lesson(title: "Make a collection yours", icon: "square.stack", tips: [
        ("Keep your favorites", "Tap the heart to save a liked song. A saved online entry still needs a connection; liking is separate from downloading."),
        ("Choose where songs go", "Use + in the player or Add to playlist or album in a song menu. Choose a collection or create one. Duplicate songs are skipped."),
        ("Save several", "Open a song list and tap Select, or hold a song to begin selecting. Add selected songs together. A collection's menu lets you rename it or switch between Playlist and Album.")
    ], action: "Try Library", destination: 3),
    Lesson(title: "Give it a cover", icon: "photo", tips: [
        ("Pick an image or GIF", "Open a playlist or custom album, then choose artwork from Files. Choose a readable image up to 20 MB and 16 megapixels. Animated GIFs stay animated; Reduce Motion shows a still cover."),
        ("Your own copy", "Pipes stores a copy of the cover. It appears in Library, the collection and the Add to… picker, even after moving the original."),
        ("Change your mind", "Change artwork replaces the cover. Use default artwork restores the first song's cover. Renaming and editing songs keep your custom artwork.")
    ], action: "Choose a collection", destination: 3),
    Lesson(title: "Take music offline", icon: "arrow.down.circle", tips: [
        ("Import from Files", "Use the folder button to import your audio. Local formats stay in their original format; iOS decides which codecs it can play."),
        ("Download a direct link", "Library → Download link accepts direct HTTPS audio files. Choose a title and category, and keep Pipes open until the transfer finishes. Completed files appear in Offline audio and the category playlist."),
        ("Know what's saved", "Collections save song entries. Imported or downloaded copies have an offline indicator. YouTube audio extraction and Instagram downloads remain Android-only.")
    ], action: "See offline audio", destination: 3),
    Lesson(title: "Settle in", icon: "gearshape", tips: [
        ("Find Settings", "The gear button is available on Home, Search, Explore and Library. Minimize the full player first."),
        ("Tune recommendations", "Choose a recommendation region in Settings. Home also follows your current online song or your liked artists."),
        ("Keep learning", "Find newer iPhone releases through Settings. Install them using the same signing identity to preserve your library. Learn Pipes resumes this guide or replays it after finishing.")
    ], action: "Open Settings", destination: 4)
]

struct TutorialView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("tutorialStarted51") private var started = false
    @AppStorage("tutorialLesson51") private var step = 0
    @AppStorage("tutorialCompleted51") private var completed = false
    @AppStorage("tutorialPracticing51") private var practicing = false
    let onTry: (Int) -> Void
    @State private var practicePosition = 45.0
    @State private var practiceTab = "Home"
    @State private var practiceStatus = "This practice player is silent."
    private var index: Int { min(max(0, step), lessons.count - 1) }
    var body: some View {
        NavigationStack {
            if !started {
                VStack(alignment: .leading, spacing: 24) {
                    Image(systemName: "waveform").font(.system(size: 56)).foregroundStyle(PipesStyle.accent)
                    Text("Learn Pipes").font(.largeTitle.bold())
                    Text("Find music, learn the player, and make your library yours. Seven short lessons include silent practice and shortcuts into the app.").foregroundStyle(.secondary)
                    Button("Start tutorial") { started = true; step = 0 }.buttonStyle(.borderedProminent)
                    Button("Later") { practicing = false; dismiss() }
                    Spacer()
                }.padding(24).navigationTitle("Welcome")
            } else {
                VStack(spacing: 0) {
                    ProgressView(value: Double(index + 1), total: Double(lessons.count)).padding(.horizontal, 20)
                    ScrollView {
                        VStack(alignment: .leading, spacing: 24) {
                            Text("LESSON \(index + 1) / \(lessons.count)").font(.caption.bold()).tracking(2).foregroundStyle(PipesStyle.accent)
                            Image(systemName: lessons[index].icon).font(.largeTitle).foregroundStyle(PipesStyle.accent)
                            Text(lessons[index].title).font(.largeTitle.bold()).accessibilityAddTraits(.isHeader)
                            if index == 0 {
                                Picker("Practice navigation", selection: $practiceTab) {
                                    ForEach(["Home", "Search", "Explore", "Library"], id: \.self) { Text($0) }
                                }.pickerStyle(.segmented)
                                Text("\(practiceTab) selected. This practice doesn't leave the guide.").font(.caption).foregroundStyle(.secondary)
                            }
                            if index == 1 {
                                WaveformSeek(position: practicePosition, duration: 180) { practicePosition = $0; practiceStatus = "Position saved. Real playback seeks here on release." }
                                HStack {
                                    Button("Minimize") { practiceStatus = "Minimized: music continues while you browse." }
                                    Spacer()
                                    Button("Pause") { practiceStatus = "Paused: your position and queue are kept." }
                                    Spacer()
                                    Button("Stop") { practicePosition = 0; practiceStatus = "Stopped: playback ends and the queue is cleared." }
                                }.font(.caption)
                                Text(practiceStatus).font(.caption).foregroundStyle(.secondary)
                            }
                            ForEach(lessons[index].tips.indices, id: \.self) { tip in
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(lessons[index].tips[tip].0).font(.headline).accessibilityAddTraits(.isHeader)
                                    Text(lessons[index].tips[tip].1).foregroundStyle(.secondary)
                                }
                            }
                            Button(lessons[index].action) { practicing = true; onTry(lessons[index].destination) }.buttonStyle(.borderedProminent)
                            Text("Try it in the app, then use Continue tutorial to return.").font(.caption).foregroundStyle(.secondary)
                        }.padding(24)
                    }.id(index)
                    HStack {
                        Button("Back") { step = max(0, index - 1) }.disabled(index == 0)
                        Spacer()
                        Button(index == lessons.count - 1 ? "Finish" : "Next lesson") {
                            if index == lessons.count - 1 { completed = true; practicing = false; dismiss() }
                            else { step = index + 1 }
                        }.buttonStyle(.borderedProminent)
                    }.padding(20)
                }.navigationTitle("Learn Pipes").navigationBarTitleDisplayMode(.inline)
                    .toolbar { Button("Save & close") { practicing = false; dismiss() } }
            }
        }
        .onAppear { if completed { step = 0; completed = false; started = true } }
    }
}

struct SettingsView: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    let onLearn: () -> Void
    @AppStorage("tutorialCompleted51") private var completed = false
    var body: some View {
        NavigationStack {
            Form {
                Section("Pipes for iPhone & iPad") {
                    LabeledContent("Version", value: PipesStyle.version)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Pipes version \(PipesStyle.version)")
                        .accessibilityIdentifier("pipes-version")
                    Button(completed ? "Replay Learn Pipes" : "Learn Pipes", action: onLearn)
                    Link("Check for iPhone updates", destination: URL(string: "https://github.com/brimzy/pipes/releases?q=ios-v5.1")!)
                }
                Section("Recommendations") {
                    Picker("Region", selection: $library.region) {
                        ForEach(["US", "PH", "GB", "JP", "KR", "IN", "BR", "DE", "CA", "AU"], id: \.self) { code in
                            Text(Locale.current.localizedString(forRegionCode: code) ?? code).tag(code)
                        }
                    }
                    Text("Home follows your current online song or liked artists. Explore caches suggestions until you refresh.").font(.caption).foregroundStyle(.secondary)
                }
                Section("Playback on iPhone") {
                    Text("Online playback uses the visible YouTube player. Keep Pipes open; some recordings prohibit embedding. Imported and downloaded audio supports background playback, seeking, and lock-screen controls.")
                    Text("CarPlay browses local audio only and requires an Apple-approved audio entitlement when signing.")
                }
                Section("Your data") {
                    Text("Likes, collections, covers, queue, search history and tutorial progress stay on this device. Importing copies audio into Pipes. Keep the same app identity when updating to preserve data.")
                    Text("Search, discovery and online playback contact YouTube/Google; Spotify song links contact Spotify. Direct downloads contact your chosen host; update links open GitHub. Those services receive your IP address and request details.")
                }
                Section("Platform differences") {
                    Text("Spotify playlist imports, artist discographies, YouTube audio extraction, Instagram downloads, the live audio spectrum and the Kirby save animation remain Android-only. iPhone has waveform seeking, song radio, direct audio downloads and editable collections.")
                }
            }.navigationTitle("Settings").toolbar { Button("Done") { dismiss() } }
        }
    }
}
