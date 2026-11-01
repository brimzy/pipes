import SwiftUI
import UniformTypeIdentifiers

struct LibraryView: View {
    @EnvironmentObject var library: MusicLibrary
    let onImport: () -> Void
    let onDownload: () -> Void
    @State private var creating = false
    var body: some View {
        List {
            Section {
                NavigationLink { SongListView(title: "Liked songs", songs: library.favorites) } label: {
                    Label("Liked songs · \(library.favorites.count)", systemImage: "heart")
                }
                NavigationLink { SongListView(title: "Offline audio", songs: library.tracks) } label: {
                    Label("Offline audio · \(library.tracks.count)", systemImage: "arrow.down.circle")
                }
                Button(action: onImport) { Label("Import audio files", systemImage: "folder.badge.plus") }
                Button(action: onDownload) { Label("Download link", systemImage: "link") }
            }.listRowBackground(PipesStyle.surface)
            Section {
                Button { creating = true } label: { Label("New playlist or album", systemImage: "plus") }
            }.listRowBackground(PipesStyle.surface)
            ForEach(CollectionKind.allCases, id: \.self) { kind in
                Section(kind == .playlist ? "Playlists" : "Custom albums") {
                    let collections = library.collections.filter { $0.kind == kind }
                    if collections.isEmpty { Text(kind == .playlist ? "Your playlists will appear here." : "Make an album from your favorite songs.").foregroundStyle(.secondary) }
                    ForEach(collections) { collection in
                        NavigationLink {
                            CollectionDetailView(id: collection.id)
                        } label: {
                            HStack(spacing: 12) {
                                CoverArt(url: collection.artworkFilename.flatMap(library.fileURL) ?? collection.tracks.first?.artwork).frame(width: 60, height: 60)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(collection.name).font(.headline)
                                    Text("\(collection.tracks.count) songs · \(collection.kind.rawValue)").font(.caption).foregroundStyle(.secondary)
                                }
                            }.padding(.vertical, 4)
                        }.listRowBackground(PipesStyle.surface)
                    }
                }
            }
        }.scrollContentBackground(.hidden)
            .sheet(isPresented: $creating) { CollectionEditor() }
    }
}

struct SongListView: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.addSongs) private var addSongs
    @Environment(\.showPlayer) private var showPlayer
    let title: String
    let songs: [Track]
    var collectionID: String? = nil
    @State private var selecting = false
    @State private var selection = Set<String>()
    var body: some View {
        List {
            if songs.isEmpty {
                ContentUnavailableView("No songs yet", systemImage: "music.note.list", description: Text("Find music in Search, or import audio from Files."))
            } else {
                Section {
                    HStack {
                        Button("Play all", systemImage: "play.fill") { if let first = songs.first { library.play(first, from: songs); showPlayer() } }
                        Spacer()
                        Button("Add all to…") { addSongs(songs) }
                    }
                }.listRowBackground(PipesStyle.surface)
                if selecting {
                    Section {
                        HStack {
                            Button("Select all") { selection = Set(songs.map(\.id)) }
                            Spacer()
                            Text("\(selection.count) selected").foregroundStyle(.secondary)
                        }
                        Button("Add selected to…") { addSongs(songs.filter { selection.contains($0.id) }) }.disabled(selection.isEmpty)
                        if let collectionID {
                            Button("Remove selected", role: .destructive) {
                                library.remove(selection, from: collectionID); selection = []; selecting = false
                            }.disabled(selection.isEmpty)
                        }
                    }.listRowBackground(PipesStyle.surface)
                }
                ForEach(songs) { track in
                    TrackRow(track: track, songs: songs,
                             selected: selecting ? selection.contains(track.id) : nil,
                             onSelect: selecting ? { toggle(track) } : nil,
                             onRemove: collectionID.map { id in { library.remove([track.id], from: id) } })
                    .onLongPressGesture { selecting = true; selection.insert(track.id) }
                }
            }
        }.scrollContentBackground(.hidden).background(PipesStyle.background)
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(selecting ? "Cancel" : "Select") { selecting.toggle(); selection = [] }.disabled(songs.isEmpty)
                }
            }
    }
    private func toggle(_ track: Track) {
        if selection.contains(track.id) { selection.remove(track.id) } else { selection.insert(track.id) }
    }
}

struct CollectionDetailView: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    let id: String
    @State private var artworkPicker = false
    @State private var editing = false
    @State private var deleting = false
    private var collection: MusicCollection? { library.collections.first { $0.id == id } }
    var body: some View {
        Group {
            if let collection {
                SongListView(title: collection.name, songs: collection.tracks, collectionID: id)
                    .safeAreaInset(edge: .top) {
                        HStack(spacing: 16) {
                            CoverArt(url: collection.artworkFilename.flatMap(library.fileURL) ?? collection.tracks.first?.artwork).frame(width: 80, height: 80)
                            VStack(alignment: .leading, spacing: 8) {
                                Text("\(collection.kind.rawValue) · \(collection.tracks.count) songs").font(.caption).foregroundStyle(.secondary)
                                Button(collection.artworkFilename == nil ? "Choose artwork" : "Change artwork") { artworkPicker = true }.font(.subheadline)
                                if collection.artworkFilename != nil { Button("Use default artwork") { library.setArtwork(nil, for: id) }.font(.caption) }
                            }
                            Spacer()
                        }.padding(16).background(PipesStyle.background)
                    }
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Menu {
                                Button("Rename or change type", systemImage: "pencil") { editing = true }
                                Button("Delete collection", systemImage: "trash", role: .destructive) { deleting = true }
                            } label: { Image(systemName: "ellipsis.circle") }.accessibilityLabel("Collection actions")
                        }
                    }
            } else { ContentUnavailableView("Collection removed", systemImage: "square.stack") }
        }
        .sheet(isPresented: $editing) { if let collection { CollectionEditor(collection: collection) } }
        .fileImporter(isPresented: $artworkPicker, allowedContentTypes: [.image]) { result in
            switch result {
            case .success(let url): library.setArtwork(url, for: id)
            case .failure(let error): library.error = error.localizedDescription
            }
        }
        .confirmationDialog("Delete this collection? Your audio files and other collections stay saved.", isPresented: $deleting, titleVisibility: .visible) {
            Button("Delete collection", role: .destructive) { library.deleteCollection(id); dismiss() }
        }
    }
}

struct CollectionEditor: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    var collection: MusicCollection?
    var songs: [Track] = []
    var onSaved: (() -> Void)? = nil
    @State private var name = ""
    @State private var kind: CollectionKind = .playlist
    var body: some View {
        NavigationStack {
            Form {
                TextField("Collection name", text: $name).submitLabel(.done)
                Picker("Type", selection: $kind) { ForEach(CollectionKind.allCases, id: \.self) { Text($0.rawValue).tag($0) } }
                if !songs.isEmpty { Text("\(songs.count) songs will be added. Duplicates are skipped.").foregroundStyle(.secondary) }
            }
            .navigationTitle(collection == nil ? "New collection" : "Edit collection")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if let collection { library.editCollection(collection.id, name: name, kind: kind) }
                        else { library.createCollection(name, kind: kind, songs: songs) }
                        dismiss(); onSaved?()
                    }.disabled(LibraryRules.collectionName(name) == nil)
                }
            }.onAppear { if let collection { name = collection.name; kind = collection.kind } }
        }
    }
}

struct AddToCollectionView: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    let songs: [Track]
    @State private var creating = false
    var body: some View {
        NavigationStack {
            List {
                Section("\(LibraryRules.unique(songs).count) songs") {
                    Button("New playlist or album", systemImage: "plus") { creating = true }
                }
                ForEach(library.collections) { collection in
                    Button {
                        library.add(songs, to: collection.id); dismiss()
                    } label: {
                        HStack {
                            CoverArt(url: collection.artworkFilename.flatMap(library.fileURL) ?? collection.tracks.first?.artwork).frame(width: 48, height: 48)
                            VStack(alignment: .leading) {
                                Text(collection.name)
                                Text("\(collection.kind.rawValue) · \(collection.tracks.count) songs").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }.navigationTitle("Add to…").toolbar { Button("Done") { dismiss() } }
                .sheet(isPresented: $creating, onDismiss: { if saved { dismiss() } }) {
                    CollectionEditor(songs: songs, onSaved: { saved = true })
                }
        }
    }
    @State private var saved = false
}

struct DownloadView: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    @State private var url = ""
    @State private var title = ""
    @State private var category = "Downloads"
    var body: some View {
        NavigationStack {
            Form {
                Section("Direct audio file") {
                    TextField("https://example.com/song.mp3", text: $url).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                    TextField("Title (optional)", text: $title)
                    TextField("Category", text: $category)
                }
                Section {
                    Text("Save a direct HTTPS MP3, M4A, AAC, FLAC, WAV or MP4 file for offline listening. Your category appears as a playlist in Library. Keep Pipes open until the transfer finishes.")
                    Text("Online song extraction and Instagram downloads remain Android-only. Spotify links provide metadata, not downloadable audio.")
                }.foregroundStyle(.secondary)
                Section {
                    if library.downloading { ProgressView("Downloading audio…") }
                    else {
                        Button("Download", systemImage: "arrow.down.circle") {
                            Task {
                                await library.download(url, title: title, category: category)
                                if library.error == nil { dismiss() }
                            }
                        }.disabled(LibraryRules.directAudioURL(url) == nil)
                    }
                }
            }.navigationTitle("Download link").toolbar { Button("Done") { dismiss() } }
        }.interactiveDismissDisabled(library.downloading)
    }
}
