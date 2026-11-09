import XCTest
@testable import Pipes

final class PlaybackRulesTests: XCTestCase {
    private func song(_ number: Int) -> Track {
        let id = String(format: "%011d", number)
        return Track(id: id, title: "Song \(number)", artist: "Artist \(number)", videoID: id)
    }
    @MainActor
    private func library(provider: @escaping (Track, String) async throws -> [Track] = { _, _ in [] }) throws -> (MusicLibrary, UserDefaults, String) {
        let suite = "playback-tests-" + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        return (MusicLibrary(defaults: defaults, configurePlayback: false, radioProvider: provider), defaults, suite)
    }
    @MainActor
    private func until(_ predicate: () -> Bool) async throws {
        for _ in 0..<200 {
            if predicate() { return }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTFail("Timed out waiting for the queue operation")
    }

    func testShuffleIncludesWholeLargePlaylistFromSelectedSong() {
        let songs = (0..<1000).map(song)
        let queue = LibraryRules.upcoming(songs[990], in: songs, shuffle: true)
        XCTAssertEqual(queue.count, 999)
        XCTAssertEqual(Set(queue.map(\.id)), Set(songs.filter { $0.id != songs[990].id }.map(\.id)))
        XCTAssertEqual(LibraryRules.upcoming(songs[990], in: songs, shuffle: false), Array(songs.suffix(9)))
    }

    @MainActor
    func testVisibleQueueIsShufflePlaybackOrderAndTogglingNeverDropsSongs() throws {
        let (library, defaults, suite) = try library()
        defer { library.stop(); defaults.removePersistentDomain(forName: suite) }
        let songs = (0..<1000).map(song)
        library.shuffle = true
        library.play(songs[990], from: songs)
        XCTAssertEqual(library.queue.count, 999)
        for _ in 0..<20 {
            let expected = library.queue.first
            library.next()
            XCTAssertEqual(library.current, expected)
        }
        let remaining = Set(library.queue.map(\.id))
        let requested = try XCTUnwrap(library.queue.last)
        library.playNext(requested)
        library.shuffle = false
        XCTAssertEqual(Set(library.queue.map(\.id)), remaining)
        XCTAssertEqual(library.queue.first, requested)
        library.next()
        XCTAssertEqual(library.current, requested)
        XCTAssertFalse(library.queue.contains(requested))
    }

    @MainActor
    func testRepeatAllRestartsFullCollectionRatherThanRepeatingLastSong() throws {
        let (library, defaults, suite) = try library()
        defer { library.stop(); defaults.removePersistentDomain(forName: suite) }
        let songs = (0..<3).map(song)
        library.repeatMode = .all
        library.play(songs[0], from: songs)
        library.advance(automatic: true)
        library.advance(automatic: true)
        XCTAssertEqual(library.current, songs[2])
        library.advance(automatic: true)
        XCTAssertEqual(library.current, songs[0])
        XCTAssertEqual(library.queue, Array(songs.dropFirst()))
    }

    @MainActor
    func testLateAutoplayResultsResumeAnEndedQueue() async throws {
        var response: CheckedContinuation<[Track], Never>?
        let (library, defaults, suite) = try library { _, _ in await withCheckedContinuation { response = $0 } }
        defer { library.stop(); defaults.removePersistentDomain(forName: suite) }
        let a = song(1), b = song(2), c = song(3)
        library.play(a, from: [a])
        try await until { response != nil }
        library.advance(automatic: true)
        XCTAssertFalse(library.isPlaying)
        response?.resume(returning: [a, b, c])
        try await until { library.current == b }
        XCTAssertTrue(library.isPlaying)
        XCTAssertEqual(library.queue, [c])
    }

    @MainActor
    func testPauseAndStopCannotBeOverriddenByLateAutoplay() async throws {
        var response: CheckedContinuation<[Track], Never>?
        let (library, defaults, suite) = try library { _, _ in await withCheckedContinuation { response = $0 } }
        defer { library.stop(); defaults.removePersistentDomain(forName: suite) }
        let a = song(1), b = song(2)
        library.play(a, from: [a])
        try await until { response != nil }
        library.advance(automatic: true)
        library.pause()
        response?.resume(returning: [b])
        try await until { !library.radioLoading }
        XCTAssertEqual(library.current, a)
        XCTAssertFalse(library.isPlaying)
        XCTAssertFalse(library.playbackRequested)
        XCTAssertEqual(library.queue, [b])

        response = nil
        library.play(a, from: [a])
        try await until { response != nil }
        library.stop()
        response?.resume(returning: [b])
        await Task.yield()
        XCTAssertNil(library.current)
        XCTAssertTrue(library.queue.isEmpty)
        XCTAssertFalse(library.radioLoading)
    }

    @MainActor
    func testAutoplayNeverTruncatesSavedQueueAndQueueSelectionPreservesRemainingEntries() async throws {
        let suggestions = (200..<230).map(song)
        let (library, defaults, suite) = try library { _, _ in suggestions }
        defer { library.stop(); defaults.removePersistentDomain(forName: suite) }
        let songs = (0..<12).map(song)
        library.play(songs[0], from: songs)
        XCTAssertEqual(library.queue, Array(songs.dropFirst()))
        library.next()
        try await until { !library.radioLoading }
        XCTAssertEqual(library.queue.count, 40)
        XCTAssertEqual(Array(library.queue.prefix(10)), Array(songs.dropFirst(2)))
        library.playQueued(songs[5])
        XCTAssertEqual(library.current, songs[5])
        XCTAssertEqual(library.queue.first, songs[6])
        XCTAssertEqual(Array(library.queue.suffix(30)), suggestions)
    }

    func testExhaustedRadioCanReuseOlderMusicWithoutRepeatingQueuedSongs() {
        let a = song(1), b = song(2), c = song(3)
        XCTAssertEqual(LibraryRules.continuation([a, b, c], queued: [a, b], recent: []), [c])
        XCTAssertEqual(LibraryRules.continuation([a, b], queued: [a], recent: [b]), [b])
        XCTAssertTrue(LibraryRules.continuation([a, b], queued: [a, b], recent: []).isEmpty)
    }

    @MainActor
    func testRecentListeningPersistsAndHomeIncludesEveryCollection() throws {
        let (library, defaults, suite) = try library()
        defer { library.stop(); defaults.removePersistentDomain(forName: suite) }
        library.repeatMode = .one
        let played = song(1), liked = song(2), saved = song(3), other = song(4)
        library.favorites = [liked]
        library.collections = [MusicCollection(name: "Big", tracks: [saved]), MusicCollection(name: "Small", tracks: [other])]
        library.play(played, from: [played])
        let restored = MusicLibrary(defaults: defaults, configurePlayback: false)
        XCTAssertEqual(restored.recentlyPlayed.first, played)
        XCTAssertEqual(restored.discoverySeeds, [played, liked, saved, other])
        XCTAssertEqual(restored.collections, library.collections)
    }

    @MainActor
    func testBackgroundingPreservesLocalAudioAndPausesEmbeddedPlayback() throws {
        let (library, defaults, suite) = try library()
        defer { library.stop(); defaults.removePersistentDomain(forName: suite) }
        let local = Track(id: "local", title: "Local", artist: "Artist", filename: "audio.wav")
        library.play(local, from: [local])
        library.setForeground(false)
        XCTAssertTrue(library.isPlaying)
        XCTAssertTrue(library.playbackRequested)
        library.setForeground(true)
        library.repeatMode = .one
        library.play(song(1), from: [song(1)])
        library.setForeground(false)
        XCTAssertFalse(library.isPlaying)
        XCTAssertFalse(library.playbackRequested)
    }

    func testRadioContinuationAndRecordingDeduplication() {
        XCTAssertEqual(MusicSource.continuation(["continuations": [["nextContinuationData": ["continuation": "next-page"]]]]), "next-page")
        XCTAssertEqual(MusicSource.continuation(["continuationEndpoint": ["continuationCommand": ["token": "more"]]]), "more")
        let original = song(1)
        var duplicate = song(2)
        duplicate.title = original.title + " (Official Audio)"
        duplicate.artist = original.artist + " - Topic"
        XCTAssertTrue(LibraryRules.recommendations([duplicate], excluding: [original]).isEmpty)
    }
}
