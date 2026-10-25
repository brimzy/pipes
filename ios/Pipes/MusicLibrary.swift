import Foundation
import Combine
import AVFoundation
import MediaPlayer

struct Track: Identifiable, Codable, Equatable {
    var id: String
    var title: String
    var artist: String
    var artwork: URL?
    var videoID: String?
    var filename: String?
}

@MainActor
final class MusicLibrary: ObservableObject {
    static let shared = MusicLibrary()
    private var history: [Track] = []
    @Published var tracks: [Track] = []
    @Published var favorites: [Track] = []
    @Published var queue: [Track] = []
    @Published var current: Track?
    @Published var isPlaying = false
    @Published var position: Double = 0
    @Published var duration: Double = 0
    @Published var error: String?
    @Published var searching = false
    private let player = AVPlayer()
    private var clock: Any?
    private var ended: NSObjectProtocol?
    private var status: NSKeyValueObservation?
    private var searchTask: Task<Void, Never>?
    private let defaults = UserDefaults.standard
    private var documents: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0] }

    init() {
        tracks = Self.restore("localTracks")
        favorites = Self.restore("favorites")
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch { self.error = error.localizedDescription }
        clock = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor in
                guard let self else { return }
                self.position = time.seconds.isFinite ? time.seconds : 0
                let length = self.player.currentItem?.duration.seconds ?? 0
                self.duration = length.isFinite ? length : 0
                self.updateNowPlaying()
            }
        }
        ended = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] note in
            Task { @MainActor in
                guard let self, let item = note.object as? AVPlayerItem, item === self.player.currentItem else { return }
                self.nextLocal()
            }
        }
        MPRemoteCommandCenter.shared().playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resume() }; return .success
        }
        MPRemoteCommandCenter.shared().pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }; return .success
        }
        MPRemoteCommandCenter.shared().nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.nextLocal() }; return .success
        }
        MPRemoteCommandCenter.shared().previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.previous() }; return .success
        }
        MPRemoteCommandCenter.shared().changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seek(event.positionTime) }; return .success
        }
    }

    func carPlayable(_ candidates: [Track]) -> [Track] {
        var seen = Set<String>()
        return candidates.filter { track in
            guard let filename = track.filename, !filename.isEmpty,
                  filename == (filename as NSString).lastPathComponent,
                  FileManager.default.fileExists(atPath: documents.appendingPathComponent(filename).path) else { return false }
            return seen.insert(track.id).inserted
        }
    }

    private static func restore(_ key: String) -> [Track] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([Track].self, from: data)) ?? []
    }
    private func save() {
        defaults.set(try? JSONEncoder().encode(tracks), forKey: "localTracks")
        defaults.set(try? JSONEncoder().encode(favorites), forKey: "favorites")
    }
    func toggleFavorite(_ track: Track) {
        if favorites.contains(where: { $0.id == track.id }) { favorites.removeAll { $0.id == track.id } }
        else { favorites.append(track) }
        save()
    }
    func importFiles(_ urls: [URL]) {
        for url in urls {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            do {
                let name = UUID().uuidString + "." + url.pathExtension
                try FileManager.default.copyItem(at: url, to: documents.appendingPathComponent(name))
                tracks.append(Track(id: name, title: url.deletingPathExtension().lastPathComponent, artist: "Local audio", filename: name))
            } catch { self.error = error.localizedDescription }
        }
        save()
    }
    func play(_ track: Track, rememberHistory: Bool = true) {
        if rememberHistory, let current, current.filename != nil, current.id != track.id {
            history.append(current)
            if history.count > 100 { history.removeFirst(history.count - 100) }
        }
        pause()
        current = track
        position = 0
        duration = 0
        player.replaceCurrentItem(with: nil)
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        guard let filename = track.filename else { return }
        guard !carPlayable([track]).isEmpty else {
            error = "This local audio file is no longer available. Import it again on your iPhone."
            isPlaying = false
            return
        }
        let item = AVPlayerItem(url: documents.appendingPathComponent(filename))
        status = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                if item.status == .failed {
                    self?.error = item.error?.localizedDescription ?? "This audio format could not be played."
                    self?.isPlaying = false
                }
            }
        }
        player.replaceCurrentItem(with: item)
        resume()
    }
    func resume() {
        guard current?.filename != nil else { return }
        player.play(); isPlaying = true; updateNowPlaying()
    }
    func pause() { player.pause(); isPlaying = false; updateNowPlaying() }
    func next() {
        guard !queue.isEmpty else { pause(); return }
        play(queue.removeFirst())
    }
    private func nextLocal() {
        guard let index = queue.firstIndex(where: { !carPlayable([$0]).isEmpty }) else { pause(); return }
        play(queue.remove(at: index))
    }
    func previous() {
        if position > 3 { seek(0); return }
        guard let track = history.popLast() else { seek(0); return }
        if let current, current.filename != nil { queue.insert(current, at: 0) }
        play(track, rememberHistory: false)
    }
    func seek(_ seconds: Double) { player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600)) }
    private func updateNowPlaying() {
        let commands = MPRemoteCommandCenter.shared()
        let local = current?.filename != nil
        commands.playCommand.isEnabled = local
        commands.pauseCommand.isEnabled = local
        commands.nextTrackCommand.isEnabled = local && queue.contains { $0.filename != nil }
        commands.previousTrackCommand.isEnabled = local
        commands.changePlaybackPositionCommand.isEnabled = local && duration > 0
        guard let current, local else { return }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: current.title,
            MPMediaItemPropertyArtist: current.artist,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0
        ]
    }
    func search(_ query: String, completion: @escaping ([Track]) -> Void) {
        searchTask?.cancel()
        let query = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { completion([]); searching = false; return }
        searching = true
        searchTask = Task {
            do {
                let results = try await MusicSource.search(query)
                guard !Task.isCancelled else { return }
                completion(results); searching = false
            } catch {
                guard !Task.isCancelled else { return }
                self.error = error.localizedDescription; searching = false
            }
        }
    }
}
