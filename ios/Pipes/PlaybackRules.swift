import Foundation

extension LibraryRules {
    static func upcoming(_ selected: Track, in songs: [Track], shuffle: Bool) -> [Track] {
        let distinct = LibraryRules.unique(songs)
        let remaining: [Track]
        if shuffle { remaining = distinct.filter { $0.id != selected.id } }
        else if let index = distinct.firstIndex(where: { $0.id == selected.id }) { remaining = Array(distinct.dropFirst(index + 1)) }
        else { remaining = [] }
        return shuffle ? remaining.shuffled() : remaining
    }

    static func restoreOrder(_ queue: [Track], savedIDs: [String], forcedID: String?) -> [Track] {
        let ranks = Dictionary(savedIDs.enumerated().map { ($0.element, $0.offset) }, uniquingKeysWith: { min($0, $1) })
        let ordered = queue.enumerated().sorted {
            let a = ranks[$0.element.id] ?? (savedIDs.count + $0.offset)
            let b = ranks[$1.element.id] ?? (savedIDs.count + $1.offset)
            return a < b
        }.map(\.element)
        guard let forcedID, let next = ordered.first(where: { $0.id == forcedID }) else { return ordered }
        return playNext(next, queue: ordered)
    }

    private static func recording(_ track: Track) -> String {
        func normalized(_ value: String) -> String {
            value.lowercased()
                .replacingOccurrences(of: "\\b(official|audio|video|lyrics|lyric|topic|music)\\b", with: " ", options: .regularExpression)
                .replacingOccurrences(of: "[^\\p{L}\\p{N}]+", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return normalized(track.title) + "|" + normalized(track.artist)
    }

    static func recommendations(_ candidates: [Track], excluding history: [Track]) -> [Track] {
        var ids = Set(history.map(\.id))
        var recordings = Set(history.map(recording))
        return candidates.filter { track in
            guard track.localFilename == nil, let id = track.videoID, validVideoID(id), !ids.contains(track.id),
                  !recordings.contains(recording(track)) else { return false }
            ids.insert(track.id); recordings.insert(recording(track)); return true
        }
    }

    static func continuation(_ candidates: [Track], queued: [Track], recent: [Track]) -> [Track] {
        let fresh = recommendations(candidates, excluding: queued + recent)
        return Array((fresh.isEmpty ? recommendations(candidates, excluding: queued) : fresh).prefix(30))
    }

    static func discoverySeeds(current: Track?, recent: [Track], favorites: [Track], collections: [MusicCollection]) -> [Track] {
        let samples = collections.map { collection -> [Track] in
            let step = max(1, collection.tracks.count / 6)
            return Array(collection.tracks.enumerated().filter { $0.offset % step == 0 }.prefix(6).map(\.element))
        }
        let collectionSeeds = (0..<6).flatMap { index in samples.compactMap { index < $0.count ? $0[index] : nil } }
        return Array(unique([current].compactMap { $0 } + Array(recent.prefix(12)) + Array(favorites.reversed().prefix(12)) + collectionSeeds)
            .filter { $0.localFilename == nil && $0.videoID.map(validVideoID) == true }.prefix(48))
    }
}
