import XCTest
@testable import Pipes

final class LibraryRulesTests: XCTestCase {
    private let a = Track(id: "abcdefghijk", title: "First", artist: "Artist", videoID: "abcdefghijk")
    private let b = Track(id: "lmnopqrstuv", title: "Second", artist: "Artist", videoID: "lmnopqrstuv")

    func testPlayNextMovesExistingSongWithoutDuplicatingOrReorderingOthers() {
        let c = Track(id: "third", title: "Third", artist: "Artist")
        XCTAssertEqual(LibraryRules.playNext(b, queue: [a, b, c, b]).map(\.id), [b.id, a.id, c.id])
    }
    func testCollectionCopiesAreIndependentAndArtworkSurvivesEdits() {
        var original = MusicCollection(name: "Saved", tracks: [a], artworkFilename: "cover.gif")
        var copy = original
        copy.add([a, b, b])
        original.name = "Renamed"; original.kind = .album
        XCTAssertEqual(original.tracks, [a])
        XCTAssertEqual(copy.tracks, [a, b])
        XCTAssertEqual(original.artworkFilename, "cover.gif")
        XCTAssertEqual(copy.name, "Saved")
    }
    func testLegacy50TrackDataDecodesWithoutLosingSources() throws {
        let data = Data("[{\"id\":\"local\",\"title\":\"My audio\",\"artist\":\"Local audio\",\"filename\":\"original.flac\"}]".utf8)
        let tracks = try JSONDecoder().decode([Track].self, from: data)
        XCTAssertEqual(tracks.first?.localFilename, "original.flac")
        XCTAssertNil(tracks.first?.videoID)
        XCTAssertEqual(try JSONDecoder().decode([Track].self, from: JSONEncoder().encode(tracks)), tracks)
    }
    func testCollectionRoundTripPreservesOrderKindAndCustomCover() throws {
        let collection = MusicCollection(name: "Night", kind: .album, tracks: [b, a], artworkFilename: "cover.gif")
        XCTAssertEqual(try JSONDecoder().decode(MusicCollection.self, from: JSONEncoder().encode(collection)), collection)
    }
    func testFileNamesCannotEscapeAppDocuments() {
        for filename in ["../secret.mp3", "/tmp/music.mp3", ".", "..", ""] {
            XCTAssertNil(Track(id: "x", title: "x", artist: "x", filename: filename).localFilename)
        }
        XCTAssertEqual(Track(id: "x", title: "x", artist: "x", filename: "safe.wav").localFilename, "safe.wav")
    }
    func testSourceLinksRejectLookalikesAndInvalidIDs() {
        XCTAssertEqual(LibraryRules.videoID(from: "https://youtu.be/abcdefghijk?t=2"), a.id)
        XCTAssertEqual(LibraryRules.videoID(from: "music.youtube.com/watch?v=abcdefghijk"), a.id)
        XCTAssertNil(LibraryRules.videoID(from: "https://youtube.com.evil.test/watch?v=abcdefghijk"))
        XCTAssertNil(LibraryRules.videoID(from: "https://user@youtube.com/watch?v=abcdefghijk"))
        XCTAssertNil(LibraryRules.videoID(from: "https://youtube.com/watch?v=bad<script>"))
    }
    func testDirectDownloadsRequireHTTPSAudioAndPublicHostShape() {
        XCTAssertNotNil(LibraryRules.directAudioURL("https://example.com/audio.flac?token=public"))
        for value in ["http://example.com/a.mp3", "https://127.0.0.1/a.mp3", "https://localhost/a.mp3", "https://host.local/a.mp3", "https://example.com:444/a.mp3", "https://user:pass@example.com/a.mp3", "https://example.com/page.html"] {
            XCTAssertNil(LibraryRules.directAudioURL(value), value)
        }
    }
    func testRendererParsingDeduplicatesAndRejectsInvalidVideoIDs() {
        func row(_ id: String) -> [String: Any] {
            ["playlistPanelVideoRenderer": ["videoId": id, "title": ["runs": [["text": "Song"]]], "longBylineText": ["runs": [["text": "Artist"]]]]]
        }
        let songs = MusicSource.parse([row(a.id), row(a.id), row("<script>"), row(b.id)])
        XCTAssertEqual(songs.map(\.id), [a.id, b.id])
        XCTAssertEqual(songs.first?.artist, "Artist")
    }
    func testRepeatCyclesAndBlankCollectionNames() {
        XCTAssertEqual(RepeatMode.off.next.next.next, .off)
        XCTAssertNil(LibraryRules.collectionName(" \n "))
        XCTAssertEqual(LibraryRules.collectionName("  My album \n"), "My album")
    }
    @MainActor
    func testUpgradeAndRelaunchRetainLegacyDataQueueAndCollectionEdits() async throws {
        let suite = "pipes-tests-" + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(suite)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let local = Track(id: "legacy", title: "Legacy song", artist: "Local audio", filename: "old.flac")
        try Data([1, 2, 3]).write(to: directory.appendingPathComponent("old.flac"))
        defaults.set(try JSONEncoder().encode([local]), forKey: "localTracks")
        defaults.set(try JSONEncoder().encode([a]), forKey: "favorites")
        let upgraded = MusicLibrary(defaults: defaults, documents: directory, configurePlayback: false)
        XCTAssertEqual(upgraded.carPlayable(upgraded.tracks), [local])
        XCTAssertEqual(upgraded.favorites, [a])
        let id = try XCTUnwrap(upgraded.createCollection("Commute", kind: .playlist, songs: [a, b]))
        upgraded.editCollection(id, name: "Night drive", kind: .album)
        upgraded.remove([a.id], from: id)
        upgraded.addToQueue([a, b, a])
        let relaunched = MusicLibrary(defaults: defaults, documents: directory, configurePlayback: false)
        XCTAssertEqual(relaunched.tracks, [local])
        XCTAssertEqual(relaunched.favorites, [a])
        XCTAssertEqual(relaunched.queue, [a, b])
        XCTAssertEqual(relaunched.collections.first?.name, "Night drive")
        XCTAssertEqual(relaunched.collections.first?.kind, .album)
        XCTAssertEqual(relaunched.collections.first?.tracks, [b])
        relaunched.deleteCollection(id)
        XCTAssertEqual(relaunched.carPlayable(relaunched.tracks), [local])
    }

}
