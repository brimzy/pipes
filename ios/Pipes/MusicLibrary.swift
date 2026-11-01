import Foundation
import Combine
import AVFoundation
import MediaPlayer
import ImageIO

@MainActor
final class MusicLibrary: ObservableObject {
    static let shared = MusicLibrary()
    @Published var tracks: [Track] = [] { didSet { persist(tracks, "localTracks") } }
    @Published var favorites: [Track] = [] { didSet { persist(favorites, "favorites") } }
    @Published var queue: [Track] = [] { didSet { persist(queue, "queue") } }
    @Published var collections: [MusicCollection] = [] { didSet { persist(collections, "collections") } }
    @Published var current: Track?
    @Published var isPlaying = false
    @Published var position: Double = 0
    @Published var duration: Double = 0
    @Published var error: String?
    @Published var notice: String?
    @Published var searching = false
    @Published var downloading = false
    @Published var radioLoading = false
    @Published var radioSeed: Track?
    @Published var shuffle = false
    @Published var repeatMode: RepeatMode = .off
    @Published var recentSearches: [String] = [] { didSet { persist(recentSearches, "recentSearches") } }
    @Published var region = UserDefaults.standard.string(forKey: "region") ?? "US" {
        didSet { UserDefaults.standard.set(region, forKey: "region") }
    }
    lazy var embeddedPlayer = EmbeddedPlayer(library: self)
    private let player = AVPlayer()
    private var history: [Track] = []
    private var cycle: [Track] = []
    private var forcedNextID: String?
    private var clock: Any?
    private var ended: NSObjectProtocol?
    private var interruption: NSObjectProtocol?
    private var routeChange: NSObjectProtocol?
    private var status: NSKeyValueObservation?
    private var searchTask: Task<Void, Never>?
    private var radioTask: Task<Void, Never>?
    private var radioSeen = Set<String>()
    private var resumeAfterInterruption = false
    private let defaults: UserDefaults
    let documents: URL

    init(defaults: UserDefaults = .standard, documents: URL? = nil, configurePlayback: Bool = true) {
        self.defaults = defaults
        self.documents = documents ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        tracks = restore("localTracks")
        favorites = restore("favorites")
        queue = restore("queue")
        collections = restore("collections")
        recentSearches = restore("recentSearches")
        guard configurePlayback else { return }
        do { try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default) }
        catch { self.error = error.localizedDescription }
        clock = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.25, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor in
                guard let self, self.current?.filename != nil else { return }
                self.position = time.seconds.isFinite ? max(0, time.seconds) : 0
                let length = self.player.currentItem?.duration.seconds ?? 0
                self.duration = length.isFinite ? max(0, length) : 0
                self.updateNowPlaying()
            }
        }
        ended = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] note in
            Task { @MainActor in
                guard let self, let item = note.object as? AVPlayerItem, item === self.player.currentItem else { return }
                self.advance(automatic: true, localOnly: true)
            }
        }
        interruption = NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            let type = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            let options = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            Task { @MainActor in
                guard let self else { return }
                if type == AVAudioSession.InterruptionType.began.rawValue {
                    self.resumeAfterInterruption = self.isPlaying
                    self.pause()
                } else if self.resumeAfterInterruption && AVAudioSession.InterruptionOptions(rawValue: options).contains(.shouldResume) {
                    self.resumeAfterInterruption = false
                    self.resume()
                }
            }
        }
        routeChange = NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] note in
            let reason = note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
            if reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue {
                Task { @MainActor in self?.pause() }
            }
        }
        let commands = MPRemoteCommandCenter.shared()
        commands.playCommand.addTarget { [weak self] _ in Task { @MainActor in self?.resume() }; return .success }
        commands.pauseCommand.addTarget { [weak self] _ in Task { @MainActor in self?.pause() }; return .success }
        commands.nextTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.advance(localOnly: true) }; return .success }
        commands.previousTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.previous(localOnly: true) }; return .success }
        commands.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seek(event.positionTime) }; return .success
        }
        updateNowPlaying()
    }

    private func restore<T: Decodable>(_ key: String) -> [T] {
        guard let data = defaults.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([T].self, from: data)) ?? []
    }
    private func persist<T: Encodable>(_ value: T, _ key: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        defaults.set(data, forKey: key)
    }
    func fileURL(_ filename: String) -> URL? {
        guard !filename.isEmpty, filename != ".", filename != "..", filename == (filename as NSString).lastPathComponent else { return nil }
        return documents.appendingPathComponent(filename)
    }
    func carPlayable(_ candidates: [Track]) -> [Track] {
        LibraryRules.unique(candidates.filter {
            guard let filename = $0.localFilename, let url = fileURL(filename) else { return false }
            return FileManager.default.fileExists(atPath: url.path)
        })
    }
    func isFavorite(_ track: Track) -> Bool { favorites.contains { $0.id == track.id } }
    func toggleFavorite(_ track: Track) {
        if isFavorite(track) { favorites.removeAll { $0.id == track.id } }
        else { favorites.append(track) }
    }
    func importFiles(_ urls: [URL]) {
        var imported: [Track] = []
        for url in urls {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            do {
                let name = UUID().uuidString + "." + url.pathExtension
                try FileManager.default.copyItem(at: url, to: documents.appendingPathComponent(name))
                imported.append(Track(id: name, title: url.deletingPathExtension().lastPathComponent, artist: "Local audio", filename: name))
            } catch { self.error = error.localizedDescription }
        }
        tracks += imported
        if let first = imported.first {
            queue = Array(imported.dropFirst())
            play(first)
        }
    }
    func play(_ track: Track, rememberHistory: Bool = true) {
        if rememberHistory, let current, current.id != track.id {
            history.append(current)
            if history.count > 100 { history.removeFirst() }
        }
        if !cycle.contains(where: { $0.id == track.id }) { cycle.append(track) }
        pause()
        status = nil
        current = track
        position = 0; duration = 0
        player.replaceCurrentItem(with: nil)
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        guard let filename = track.localFilename else {
            guard let id = track.videoID, LibraryRules.validVideoID(id) else { error = "This song has no playable source."; return }
            embeddedPlayer.load(id)
            return
        }
        guard !carPlayable([track]).isEmpty else {
            error = "This file is no longer available. Import it again from Files."
            return
        }
        let item = AVPlayerItem(url: documents.appendingPathComponent(filename))
        status = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self, item === self.player.currentItem else { return }
                if item.status == .failed {
                    self.error = item.error?.localizedDescription ?? "iOS could not play this audio format."
                    self.isPlaying = false
                }
            }
        }
        player.replaceCurrentItem(with: item)
        resume()
    }
    func play(_ track: Track, from songs: [Track]) {
        radioTask?.cancel(); radioSeed = nil; radioLoading = false
        cycle = []; forcedNextID = nil
        let index = songs.firstIndex { $0.id == track.id } ?? 0
        queue = LibraryRules.unique(Array(songs.dropFirst(index + 1))).filter { $0.id != track.id }
        play(track)
    }
    func resume() {
        guard current != nil else { return }
        do { try AVAudioSession.sharedInstance().setActive(true) }
        catch { self.error = error.localizedDescription; return }
        if current?.filename != nil { player.play(); isPlaying = true; updateNowPlaying() }
        else { embeddedPlayer.command("playVideo()") }
    }
    func pause() {
        player.pause()
        if current?.videoID != nil { embeddedPlayer.command("pauseVideo()") }
        isPlaying = false; updateNowPlaying()
    }
    func stop() {
        pause(); radioTask?.cancel(); radioSeed = nil; radioLoading = false
        current = nil; queue = []; history = []; cycle = []; forcedNextID = nil
        player.replaceCurrentItem(with: nil); embeddedPlayer.clear()
        position = 0; duration = 0; updateNowPlaying()
    }
    func addToQueue(_ songs: [Track]) { queue = LibraryRules.unique(queue + songs); notice = "Added to queue" }
    func playNext(_ track: Track) {
        guard current != nil else { play(track); return }
        queue = LibraryRules.playNext(track, queue: queue)
        forcedNextID = track.id
        notice = "Playing next"
    }
    func next() { advance() }
    func advance(automatic: Bool = false, localOnly: Bool = false) {
        if automatic && repeatMode == .one { seek(0); resume(); return }
        var candidates = localOnly ? carPlayable(queue) : queue
        if candidates.isEmpty && repeatMode == .all {
            queue = LibraryRules.unique(cycle)
            candidates = localOnly ? carPlayable(queue) : queue
        }
        let next = LibraryRules.nextTrack(in: candidates, shuffle: shuffle, forcedID: forcedNextID)
        forcedNextID = nil
        guard let next else {
            pause(); return
        }
        queue.removeAll { $0.id == next.id }
        play(next)
        if queue.count < 4, let seed = radioSeed { fillRadio(seed) }
    }
    func previous(localOnly: Bool = false) {
        if position > 3 { seek(0); return }
        while let track = history.popLast() {
            if localOnly && carPlayable([track]).isEmpty { continue }
            if let current { queue = LibraryRules.playNext(current, queue: queue) }
            play(track, rememberHistory: false); return
        }
        seek(0)
    }
    func seek(_ seconds: Double) {
        guard seconds.isFinite, duration > 0 else { return }
        let value = max(0, min(seconds, duration))
        position = value
        if current?.filename != nil { player.seek(to: CMTime(seconds: value, preferredTimescale: 600)); updateNowPlaying() }
        else { embeddedPlayer.command("seekTo(\(value),true)") }
    }
    private func updateNowPlaying() {
        let commands = MPRemoteCommandCenter.shared()
        let local = current?.filename != nil
        commands.playCommand.isEnabled = local
        commands.pauseCommand.isEnabled = local
        commands.nextTrackCommand.isEnabled = local && (!carPlayable(queue).isEmpty || repeatMode == .all)
        commands.previousTrackCommand.isEnabled = local
        commands.changePlaybackPositionCommand.isEnabled = local && duration > 0
        guard let current, local else { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil; return }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: current.title, MPMediaItemPropertyArtist: current.artist,
            MPMediaItemPropertyPlaybackDuration: duration, MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0
        ]
    }
    func search(_ query: String, completion: @escaping ([Track]) -> Void) {
        searchTask?.cancel()
        let query = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { completion([]); searching = false; return }
        recentSearches = Array(([query] + recentSearches.filter { $0 != query }).prefix(12))
        searching = true
        let region = region
        searchTask = Task {
            do {
                let results = try await MusicSource.search(query, region: region)
                guard !Task.isCancelled else { return }
                completion(results); searching = false
            } catch {
                guard !Task.isCancelled else { return }
                self.error = error.localizedDescription; searching = false
            }
        }
    }
    func startRadio(_ track: Track) {
        guard track.videoID != nil else { error = "Radio needs an online song."; return }
        radioTask?.cancel(); radioLoading = false
        radioSeed = track; radioSeen = [track.id]; queue = []; cycle = []; forcedNextID = nil
        if current?.id != track.id { play(track) }
        fillRadio(track)
    }
    private func fillRadio(_ seed: Track) {
        guard !radioLoading else { return }
        radioLoading = true
        let region = region
        radioTask = Task {
            do {
                let songs = try await MusicSource.radio(seed, region: region)
                try Task.checkCancellation()
                guard radioSeed?.id == seed.id else { return }
                let additions = songs.filter { !radioSeen.contains($0.id) && $0.id != current?.id }
                queue = LibraryRules.unique(queue + additions).prefix(30).map { $0 }
                radioSeen.formUnion(additions.map(\.id))
                if additions.isEmpty { notice = "No more related songs. Try another radio seed." }
            } catch {
                if !Task.isCancelled { self.error = "Radio: " + error.localizedDescription }
            }
            if !Task.isCancelled { radioLoading = false }
        }
    }
    @discardableResult
    func createCollection(_ name: String, kind: CollectionKind, songs: [Track] = []) -> String? {
        guard let name = LibraryRules.collectionName(name) else { error = "Give your collection a name."; return nil }
        var collection = MusicCollection(name: name, kind: kind)
        collection.add(songs); collections.append(collection)
        notice = "Saved to \(name)"
        return collection.id
    }
    func add(_ songs: [Track], to id: String) {
        guard let index = collections.firstIndex(where: { $0.id == id }) else { return }
        collections[index].add(songs); notice = "Saved to \(collections[index].name)"
    }
    func editCollection(_ id: String, name: String, kind: CollectionKind) {
        guard let name = LibraryRules.collectionName(name), let index = collections.firstIndex(where: { $0.id == id }) else { return }
        collections[index].name = name; collections[index].kind = kind
    }
    func remove(_ ids: Set<String>, from collectionID: String) {
        guard let index = collections.firstIndex(where: { $0.id == collectionID }) else { return }
        collections[index].tracks.removeAll { ids.contains($0.id) }
    }
    func deleteCollection(_ id: String) {
        if let collection = collections.first(where: { $0.id == id }), let name = collection.artworkFilename, let url = fileURL(name) { try? FileManager.default.removeItem(at: url) }
        collections.removeAll { $0.id == id }
    }
    func setArtwork(_ url: URL?, for id: String) {
        guard let index = collections.firstIndex(where: { $0.id == id }) else { return }
        let previous = collections[index].artworkFilename
        do {
            if let url {
                let access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                guard size > 0 && size <= 20 * 1024 * 1024 else { throw PipesError.message("Choose artwork smaller than 20 MB.") }
                guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                      let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
                      let width = props[kCGImagePropertyPixelWidth] as? Int,
                      let height = props[kCGImagePropertyPixelHeight] as? Int,
                      width > 0, height > 0, Double(width) * Double(height) <= 16_000_000 else {
                    throw PipesError.message("Choose a readable image up to 16 megapixels.")
                }
                let name = "cover-\(UUID().uuidString).\(url.pathExtension)"
                try FileManager.default.copyItem(at: url, to: documents.appendingPathComponent(name))
                collections[index].artworkFilename = name
            } else { collections[index].artworkFilename = nil }
            if let previous, let oldURL = fileURL(previous) { try? FileManager.default.removeItem(at: oldURL) }
        } catch { self.error = error.localizedDescription }
    }
    func download(_ input: String, title: String, category: String) async {
        guard !downloading else { return }
        guard let url = LibraryRules.directAudioURL(input) else {
            error = "Enter a direct HTTPS audio-file URL (MP3, M4A, AAC, FLAC, WAV or MP4). Search for YouTube and Spotify links instead."
            return
        }
        downloading = true
        defer { downloading = false }
        do {
            let (temporary, response) = try await URLSession.shared.download(for: URLRequest(url: url, timeoutInterval: 120))
            defer { try? FileManager.default.removeItem(at: temporary) }
            guard let response = response as? HTTPURLResponse, response.statusCode == 200,
                  let finalURL = response.url, LibraryRules.directAudioURL(finalURL.absoluteString) != nil,
                  !(response.mimeType ?? "").contains("text/") else { throw PipesError.message("The link did not return a usable audio file.") }
            let asset = AVURLAsset(url: temporary)
            guard try await asset.load(.isPlayable) else { throw PipesError.message("iOS cannot play the file returned by this link.") }
            let name = UUID().uuidString + "." + url.pathExtension
            try FileManager.default.moveItem(at: temporary, to: documents.appendingPathComponent(name))
            let track = Track(id: name, title: LibraryRules.collectionName(title) ?? url.deletingPathExtension().lastPathComponent,
                              artist: "Downloaded audio", filename: name)
            tracks.append(track)
            let category = LibraryRules.collectionName(category) ?? "Downloads"
            if let existing = collections.first(where: { $0.name == category }) { add([track], to: existing.id) }
            else { createCollection(category, kind: .playlist, songs: [track]) }
            notice = "Downloaded to \(category)"
        } catch { self.error = error.localizedDescription }
    }
}

enum PipesError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}
