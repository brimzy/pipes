import Foundation

struct Track: Identifiable, Codable, Equatable, Hashable {
    var id: String
    var title: String
    var artist: String
    var artwork: URL?
    var videoID: String?
    var filename: String?

    var localFilename: String? {
        guard let filename, !filename.isEmpty, filename != ".", filename != "..",
              filename == (filename as NSString).lastPathComponent else { return nil }
        return filename
    }
}

enum CollectionKind: String, Codable, CaseIterable {
    case playlist = "Playlist", album = "Album"
}

struct MusicCollection: Identifiable, Codable, Equatable {
    var id = UUID().uuidString
    var name: String
    var kind: CollectionKind = .playlist
    var tracks: [Track] = []
    var artworkFilename: String?

    mutating func add(_ songs: [Track]) {
        tracks = LibraryRules.unique(tracks + songs)
    }
}

enum RepeatMode: String, Codable, CaseIterable {
    case off, all, one
    var next: RepeatMode { self == .off ? .all : self == .all ? .one : .off }
}

enum LibraryRules {
    static func unique(_ tracks: [Track]) -> [Track] {
        var seen = Set<String>()
        return tracks.filter { seen.insert($0.id).inserted }
    }
    static func playNext(_ track: Track, queue: [Track]) -> [Track] {
        [track] + queue.filter { $0.id != track.id }
    }
    static func collectionName(_ value: String) -> String? {
        let name = String(value.trimmingCharacters(in: .whitespacesAndNewlines).prefix(100))
        return name.isEmpty ? nil : name
    }
    static func videoID(from input: String) -> String? {
        guard let url = URL(string: input.contains("://") ? input : "https://" + input),
              ["https", "http"].contains(url.scheme?.lowercased() ?? ""), url.user == nil,
              let host = url.host?.lowercased(),
              ["youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be"].contains(host) else { return nil }
        let parts = url.pathComponents.filter { $0 != "/" }
        let candidate: String?
        if host.hasSuffix("youtu.be") { candidate = parts.first }
        else if ["embed", "shorts", "live"].contains(parts.first ?? "") { candidate = parts.dropFirst().first }
        else { candidate = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "v" }?.value }
        guard let candidate, validVideoID(candidate) else { return nil }
        return candidate
    }
    static func validVideoID(_ id: String) -> Bool {
        id.range(of: "^[A-Za-z0-9_-]{11}$", options: .regularExpression) != nil
    }
    static func directAudioURL(_ input: String) -> URL? {
        guard let url = URL(string: input.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.scheme == "https", url.user == nil, url.password == nil, url.port == nil,
              let host = url.host?.lowercased(), host.contains("."),
              host.range(of: "^[0-9.]+$", options: .regularExpression) == nil,
              !host.hasSuffix(".local"), !host.hasSuffix(".localhost"), !host.contains(":"),
              ["mp3", "m4a", "aac", "wav", "flac", "aif", "aiff", "mp4"].contains(url.pathExtension.lowercased()) else { return nil }
        return url
    }
}
