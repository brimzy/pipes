import Foundation
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
                self.next()
            }
        }
        MPRemoteCommandCenter.shared().playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resume() }; return .success
        }
        MPRemoteCommandCenter.shared().pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }; return .success
        }
        MPRemoteCommandCenter.shared().nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.next() }; return .success
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
    func play(_ track: Track) {
        pause()
        current = track
        position = 0
        duration = 0
        player.replaceCurrentItem(with: nil)
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        guard let filename = track.filename else { return }
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
    func seek(_ seconds: Double) { player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600)) }
    private func updateNowPlaying() {
        guard let current, current.filename != nil else { return }
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
