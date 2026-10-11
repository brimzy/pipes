import SwiftUI
import UniformTypeIdentifiers
import WebKit

struct ContentView: View {
    @EnvironmentObject var library: MusicLibrary
    @State private var query = ""
    @State private var results: [Track] = []
    @State private var importing = false
    @State private var showingPlayer = false
    var body: some View {
        TabView {
            NavigationStack {
                List {
                    Section {
                        Text("Your music, in motion.").font(.title2.bold())
                        Text("Search for a song, or bring your own audio.").foregroundStyle(.secondary)
                    }
                    if library.searching { ProgressView("Finding music…") }
                    if !query.isEmpty && !library.searching && results.isEmpty {
                        Text("No songs found. Try another search.").foregroundStyle(.secondary)
                    }
                    ForEach(results) { row($0) }
                }
                .navigationTitle("Pipes")
                .searchable(text: $query, prompt: "Songs and artists")
                .onSubmit(of: .search) { library.search(query) { results = $0 } }
                .toolbar { Button { importing = true } label: { Image(systemName: "folder.badge.plus") } }
            }.tabItem { Label("Search", systemImage: "magnifyingglass") }
            NavigationStack {
                List {
                    Section("Liked songs") {
                        if library.favorites.isEmpty { Text("Like a song to save it here.").foregroundStyle(.secondary) }
                        ForEach(library.favorites) { row($0) }
                    }
                    Section("On this iPhone") {
                        Button("Import audio files") { importing = true }
                        ForEach(library.tracks) { row($0) }
                    }
                }.navigationTitle("Library")
            }.tabItem { Label("Library", systemImage: "music.note.list") }
            NavigationStack {
                List {
                    ForEach(Array(library.queue.enumerated()), id: \.offset) { _, track in row(track) }
                        .onDelete { library.queue.remove(atOffsets: $0) }
                        .onMove { library.queue.move(fromOffsets: $0, toOffset: $1) }
                    if library.queue.isEmpty { Text("Add songs using the ⋯ menu.").foregroundStyle(.secondary) }
                }.navigationTitle("Queue").toolbar { EditButton() }
            }.tabItem { Label("Queue", systemImage: "text.line.first.and.arrowtriangle.forward") }
            NavigationStack {
                Form {
                    Section("Pipes for iPhone") {
                        Text("Version 3.0")
                        Link("GitHub releases", destination: URL(string: "https://github.com/brimzy/pipes/releases")!)
                    }
                    Section("Playback") {
                        Text("Imported audio supports background playback and lock-screen controls. Online songs use the YouTube player and require the app to stay open.")
                        Text("Online search depends on YouTube Music's unofficial endpoint. Some recordings cannot be embedded.")
                    }
                }.navigationTitle("Settings")
            }.tabItem { Label("Settings", systemImage: "gearshape") }
        }
        .safeAreaInset(edge: .bottom) {
            if let track = library.current {
                Button { showingPlayer = true } label: {
                    HStack {
                        Image(systemName: "waveform").foregroundStyle(.cyan)
                        VStack(alignment: .leading) {
                            Text(track.title).font(.headline).lineLimit(1)
                            Text(track.artist).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.up")
                    }.padding().background(.ultraThinMaterial)
                }.buttonStyle(.plain)
            }
        }
        .sheet(isPresented: $showingPlayer) { PlayerView() }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.audio], allowsMultipleSelection: true) { result in
            switch result {
            case .success(let urls): library.importFiles(urls)
            case .failure(let error): library.error = error.localizedDescription
            }
        }
        .alert("Pipes", isPresented: Binding(get: { library.error != nil }, set: { if !$0 { library.error = nil } })) {
            Button("OK") { library.error = nil }
        } message: { Text(library.error ?? "") }
    }
    private func row(_ track: Track) -> some View {
        HStack {
            Button {
                library.play(track); showingPlayer = true
            } label: {
                HStack {
                    AsyncImage(url: track.artwork) { image in image.resizable().scaledToFill() } placeholder: {
                        Image(systemName: "music.note").frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.white.opacity(0.08))
                    }.frame(width: 48, height: 48).clipped().cornerRadius(8)
                    VStack(alignment: .leading) {
                        Text(track.title).lineLimit(2)
                        Text(track.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer()
                }
            }.buttonStyle(.plain)
            Menu {
                Button("Add to queue", systemImage: "text.badge.plus") { library.queue.append(track) }
                Button(library.favorites.contains(track) ? "Unlike" : "Like", systemImage: "heart") { library.toggleFavorite(track) }
            } label: { Image(systemName: "ellipsis").padding(8) }
        }
    }
}

struct PlayerView: View {
    @EnvironmentObject var library: MusicLibrary
    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                if let track = library.current {
                    if let id = track.videoID {
                        YouTubePlayer(videoID: id).id(id).frame(height: 240)
                        Text("Tap play in the video. Keep Pipes open for online playback.")
                            .font(.caption).foregroundStyle(.secondary)
                        Link("Open in YouTube", destination: URL(string: "https://www.youtube.com/watch?v=\(id)")!)
                    } else {
                        Image(systemName: "waveform").font(.system(size: 100)).foregroundStyle(.cyan).frame(height: 200)
                    }
                    Text(track.title).font(.title2.bold()).multilineTextAlignment(.center)
                    Text(track.artist).foregroundStyle(.secondary)
                    if track.filename != nil {
                        Slider(value: Binding(get: { min(library.position, max(library.duration, 1)) }, set: { library.seek($0) }), in: 0...max(library.duration, 1))
                        HStack {
                            Text(time(library.position)); Spacer(); Text(time(library.duration))
                        }.font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        Button {
                            if library.isPlaying { library.pause() } else { library.resume() }
                        } label: {
                            Image(systemName: library.isPlaying ? "pause.circle.fill" : "play.circle.fill").font(.system(size: 64))
                        }
                    }
                    HStack(spacing: 40) {
                        Button { library.toggleFavorite(track) } label: {
                            Image(systemName: library.favorites.contains(track) ? "heart.fill" : "heart")
                        }
                        Button { library.next() } label: { Image(systemName: "forward.end.fill") }
                            .disabled(library.queue.isEmpty)
                    }.font(.title2)
                }
                Spacer()
            }.padding(24).navigationTitle("Now playing").navigationBarTitleDisplayMode(.inline)
        }
    }
    private func time(_ value: Double) -> String {
        let seconds = Int(max(0, value)); return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}

struct YouTubePlayer: UIViewRepresentable {
    let videoID: String
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.isOpaque = false
        view.backgroundColor = .black
        return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {
        // The base URL identifies this app to the embedded player.
        let html = """
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>body{margin:0;background:#000}iframe{width:100%;height:100%;border:0;position:absolute}</style>
        <iframe src="https://www.youtube.com/embed/\(videoID)?playsinline=1" allow="autoplay; encrypted-media; fullscreen" allowfullscreen></iframe>
        """
        view.loadHTMLString(html, baseURL: URL(string: "https://com.nazcarbon.music.ios"))
    }
}
