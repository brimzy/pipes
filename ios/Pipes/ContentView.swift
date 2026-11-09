import SwiftUI
import UniformTypeIdentifiers

private enum AppSheet: Identifiable {
    case settings, queue, tutorial, download, add([Track])
    var id: String {
        switch self {
        case .settings: return "settings"
        case .queue: return "queue"
        case .tutorial: return "tutorial"
        case .download: return "download"
        case .add: return "add"
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab = 0
    @State private var importing = false
    @State private var showingPlayer = false
    @State private var sheet: AppSheet?
    @State private var pendingSheet: AppSheet?
    @AppStorage("tutorialOffered51") private var tutorialOffered = false
    @AppStorage("tutorialPracticing51") private var practicing = false
    var body: some View {
        ZStack {
            PipesStyle.background.ignoresSafeArea()
            TabView(selection: $tab) {
                page("Pipes") { DiscoveryView(query: homeQuery, title: "Your music, in motion.", isHome: true) }
                    .tabItem { Label("Home", systemImage: "house") }.tag(0)
                page("Search") { SearchView() }
                    .tabItem { Label("Search", systemImage: "magnifyingglass") }.tag(1)
                page("Explore") { ExploreView() }
                    .tabItem { Label("Explore", systemImage: "safari") }.tag(2)
                page("Library") {
                    LibraryView(onImport: { importing = true }, onDownload: { sheet = .download })
                }.tabItem { Label("Library", systemImage: "square.stack") }.tag(3)
            }
            .opacity(showingPlayer ? 0 : 1)
            .accessibilityHidden(showingPlayer)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if !showingPlayer {
                    VStack(spacing: 0) {
                        if practicing {
                            HStack {
                                Button("Continue tutorial") { sheet = .tutorial }
                                Spacer()
                                Button { practicing = false } label: { Image(systemName: "xmark") }.accessibilityLabel("Pause tutorial")
                            }.font(.caption).padding(12).background(PipesStyle.surface)
                        }
                        if library.current != nil { MiniPlayer(showingPlayer: $showingPlayer) }
                    }
                }
            }
            if showingPlayer, library.current != nil {
                PlayerView(onMinimize: { showingPlayer = false }, onQueue: { sheet = .queue })
                    .transition(.move(edge: .bottom))
            }
        }
        .sheet(item: $sheet, onDismiss: {
            if let pendingSheet { self.pendingSheet = nil; sheet = pendingSheet }
        }) { item in
            switch item {
            case .settings:
                SettingsView(onLearn: { pendingSheet = .tutorial; sheet = nil })
            case .queue: QueueView()
            case .tutorial:
                TutorialView { destination in
                    sheet = nil
                    if destination == 4 { pendingSheet = .settings }
                    else if destination == 5 {
                        if library.current != nil { showingPlayer = true } else { tab = 1; library.notice = "Choose a song to try the player." }
                    } else if destination == 6 { pendingSheet = .queue }
                    else { showingPlayer = false; tab = destination }
                }
            case .download: DownloadView()
            case .add(let songs): AddToCollectionView(songs: songs)
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.audio], allowsMultipleSelection: true) { result in
            switch result {
            case .success(let urls): library.importFiles(urls); if library.current != nil { showingPlayer = true }
            case .failure(let error): library.error = error.localizedDescription
            }
        }
        .alert("Pipes", isPresented: Binding(get: { library.error != nil }, set: { if !$0 { library.error = nil } })) {
            Button("OK") { library.error = nil }
        } message: { Text(library.error ?? "") }
        .overlay(alignment: .top) {
            if let notice = library.notice {
                Text(notice).font(.callout).padding(14).background(.regularMaterial, in: Capsule()).padding(.top, 8)
                    .accessibilityAddTraits(.updatesFrequently)
                    .task(id: notice) {
                        try? await Task.sleep(for: .seconds(2))
                        if !Task.isCancelled, library.notice == notice { library.notice = nil }
                    }
                    .allowsHitTesting(false)
            }
        }
        .onChange(of: scenePhase) { _, phase in library.setForeground(phase == .active) }
        .onChange(of: library.current?.id) { _, id in if id == nil { showingPlayer = false } }
        .task { if !tutorialOffered { tutorialOffered = true; sheet = .tutorial } }
        .environment(\.showPlayer, { sheet = nil; showingPlayer = true })
        .environment(\.addSongs, { sheet = .add($0) })
    }
    private var homeQuery: String {
        if let artist = library.current?.artist, library.current?.videoID != nil { return artist + " songs" }
        if let artist = library.favorites.last(where: { $0.videoID != nil })?.artist { return artist + " songs" }
        return "new music chill indie soul"
    }
    private func page<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        NavigationStack {
            content().navigationTitle(title).toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { importing = true } label: { Image(systemName: "folder.badge.plus") }.accessibilityLabel("Import audio")
                    Button { sheet = .settings } label: { Image(systemName: "gearshape") }.accessibilityLabel("Settings")
                }
            }.background(PipesStyle.background)
        }
    }
}

struct TrackRow: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.showPlayer) private var showPlayer
    @Environment(\.addSongs) private var addSongs
    let track: Track
    var songs: [Track] = []
    var selected: Bool? = nil
    var onSelect: (() -> Void)? = nil
    var onRemove: (() -> Void)? = nil
    var body: some View {
        HStack(spacing: 12) {
            Button {
                if let onSelect { onSelect() }
                else { library.play(track, from: songs.isEmpty ? [track] : songs); showPlayer() }
            } label: {
                HStack(spacing: 12) {
                    if let selected { Image(systemName: selected ? "checkmark.circle.fill" : "circle").foregroundStyle(PipesStyle.accent) }
                    CoverArt(url: track.artwork).frame(width: 52, height: 52)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(track.title).font(.body.weight(.medium)).lineLimit(2).foregroundStyle(library.current?.id == track.id ? PipesStyle.accent : .primary)
                        Text(track.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    if track.filename != nil { Image(systemName: "arrow.down.circle").font(.caption).foregroundStyle(.secondary).accessibilityLabel("Available offline") }
                }.contentShape(Rectangle())
            }.buttonStyle(.plain)
            Menu {
                Button("Play next", systemImage: "text.line.first.and.arrowtriangle.forward") { library.playNext(track) }
                Button("Add to queue", systemImage: "text.badge.plus") { library.addToQueue([track]) }
                Button(library.isFavorite(track) ? "Unlike" : "Like", systemImage: "heart") { library.toggleFavorite(track) }
                Button("Add to playlist or album", systemImage: "plus") { addSongs([track]) }
                if track.videoID != nil {
                    Button("Start radio", systemImage: "dot.radiowaves.left.and.right") { library.startRadio(track); showPlayer() }
                }
                if let onRemove { Button("Remove from collection", systemImage: "minus.circle", role: .destructive, action: onRemove) }
            } label: { Image(systemName: "ellipsis").frame(width: 36, height: 44).contentShape(Rectangle()) }
            .accessibilityLabel("Song actions for \(track.title)")
        }
        .listRowBackground(PipesStyle.surface)
        .swipeActions(edge: .leading) {
            Button("Queue", systemImage: "text.badge.plus") { library.addToQueue([track]) }.tint(PipesStyle.accent)
        }
        .swipeActions(edge: .trailing) {
            Button("Remove from queue", systemImage: "minus.circle", role: .destructive) { library.queue.removeAll { $0.id == track.id } }
        }
        .accessibilityAction(named: "Play next") { library.playNext(track) }
        .accessibilityAction(named: "Add to queue") { library.addToQueue([track]) }
    }
}

struct SearchView: View {
    @EnvironmentObject var library: MusicLibrary
    @State private var query = ""
    @State private var results: [Track] = []
    @State private var submitted = false
    var body: some View {
        List {
            if query.isEmpty {
                Section {
                    Text("Find your next song").font(.title2.bold())
                    Text("Search songs and artists, or paste a YouTube video, playlist, or Spotify song link.").foregroundStyle(.secondary)
                }
                if !library.recentSearches.isEmpty {
                    Section("Recent searches") {
                        ForEach(library.recentSearches, id: \.self) { value in Button(value) { query = value; search() } }
                        Button("Clear history", role: .destructive) { library.recentSearches = [] }
                    }
                }
            }
            if library.searching { ProgressView("Finding music…") }
            if submitted && !library.searching && results.isEmpty {
                ContentUnavailableView("No songs found", systemImage: "magnifyingglass", description: Text("Try another song or artist, or retry your search."))
                Button("Retry", action: search)
            }
            ForEach(results) { track in TrackRow(track: track, songs: results) }
            if !results.isEmpty {
                Section {
                    Button("Queue all results") { library.addToQueue(results) }
                    ResultsCollectionButton(songs: results)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .searchable(text: $query, prompt: "Songs, artists or source links")
        .onSubmit(of: .search, search)
        .task(id: query) {
            let value = query
            if value.isEmpty { library.search("") { results = $0 }; submitted = false; return }
            try? await Task.sleep(for: .milliseconds(450))
            guard !Task.isCancelled else { return }
            search()
        }
    }
    private func search() { submitted = true; results = []; library.search(query) { results = $0 } }
}

struct ResultsCollectionButton: View {
    @Environment(\.addSongs) private var addSongs
    let songs: [Track]
    var body: some View { Button("Save all to playlist or album", systemImage: "plus") { addSongs(songs) } }
}

struct DiscoveryView: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.showPlayer) private var showPlayer
    let query: String
    let title: String
    var isHome = false
    @State private var songs: [Track] = []
    @State private var loading = false
    @State private var failure: String?
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(title).font(.largeTitle.bold())
                Text(isHome ? "A little closer to your next favorite." : "A fresh starting point. Follow the sound.").foregroundStyle(.secondary)
                if isHome, let current = library.current {
                    Button { showPlayer() } label: {
                        HStack(spacing: 16) {
                            CoverArt(url: current.artwork).frame(width: 92, height: 92)
                            VStack(alignment: .leading, spacing: 6) {
                                Text("NOW PLAYING").font(.caption).foregroundStyle(PipesStyle.accent)
                                Text(current.title).font(.headline).lineLimit(2)
                                Text(current.artist).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.up")
                        }.padding(12).background(PipesStyle.surface, in: RoundedRectangle(cornerRadius: 20))
                    }.buttonStyle(.plain)
                }
                if loading { ProgressView("Finding your music…").frame(maxWidth: .infinity).padding(24) }
                if let failure {
                    ContentUnavailableView("Couldn't load music", systemImage: "wifi.exclamationmark", description: Text(failure))
                    Button("Retry") { Task { await load(refresh: true) } }
                } else if !loading && songs.isEmpty {
                    ContentUnavailableView("No suggestions yet", systemImage: "music.note", description: Text("Refresh to try again, or find a song in Search."))
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 145), spacing: 16)], spacing: 24) {
                    ForEach(songs) { track in
                        VStack(alignment: .leading, spacing: 8) {
                            Button { library.play(track, from: songs); showPlayer() } label: {
                                CoverArt(url: track.artwork).aspectRatio(1, contentMode: .fit)
                            }.buttonStyle(.plain).accessibilityLabel("Play \(track.title)")
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(track.title).font(.subheadline.weight(.semibold)).lineLimit(2)
                                    Text(track.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                }
                                Spacer(minLength: 0)
                                Menu {
                                    Button("Play next") { library.playNext(track) }
                                    Button("Add to queue") { library.addToQueue([track]) }
                                    CardAddButton(track: track)
                                    Button(library.isFavorite(track) ? "Unlike" : "Like") { library.toggleFavorite(track) }
                                    Button("Start radio") { library.startRadio(track); showPlayer() }
                                } label: { Image(systemName: "ellipsis").frame(width: 28, height: 32) }.accessibilityLabel("Song actions for \(track.title)")
                            }
                        }
                    }
                }
            }.padding(20)
        }
        .refreshable { await load(refresh: true) }
        .task(id: query + library.region + (isHome ? library.discoverySeeds.map(\.id).joined(separator: "|") : "")) { await load() }
    }
    private func load(refresh: Bool = false) async {
        loading = true; failure = nil
        defer { loading = false }
        do {
            let fetched = try await (isHome
                ? MusicSource.discovery(library.discoverySeeds, region: library.region, refresh: refresh)
                : MusicSource.explore(query, region: library.region, refresh: refresh))
            try Task.checkCancellation(); songs = fetched
        } catch { if !Task.isCancelled { failure = error.localizedDescription } }
    }
}

private struct CardAddButton: View {
    @Environment(\.addSongs) private var addSongs
    let track: Track
    var body: some View { Button("Add to playlist or album") { addSongs([track]) } }
}

struct ExploreView: View {
    @State private var scene = "Late night"
    private let scenes = ["Late night", "Focus", "Feel good", "Indie", "Jazz", "Soul", "Electronic", "Workout"]
    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(scenes, id: \.self) { value in
                        Button(value) { scene = value }
                            .buttonStyle(.bordered).tint(scene == value ? PipesStyle.accent : .gray)
                            .accessibilityAddTraits(scene == value ? .isSelected : [])
                    }
                }.padding(.horizontal, 20).padding(.vertical, 8)
            }
            DiscoveryView(query: scene + " music songs", title: scene)
        }
    }
}
