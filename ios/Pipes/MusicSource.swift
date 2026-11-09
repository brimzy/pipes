import Foundation

enum MusicSource {
    static func search(_ input: String, region: String = "US") async throws -> [Track] {
        let query = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let possibleURL = URL(string: query.contains("://") ? query : "https://" + query)
        if let url = possibleURL, let host = url.host?.lowercased(),
           ["youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be"].contains(host), url.user == nil {
            if let list = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "list" })?.value,
               list.range(of: "^[A-Za-z0-9_-]{2,200}$", options: .regularExpression) != nil {
                let root: Any
                if list.hasPrefix("RD"), let id = LibraryRules.videoID(from: query) {
                    root = try await request("next", body: ["videoId": id, "playlistId": list], region: region)
                } else { root = try await request("browse", body: ["browseId": "VL" + list], region: region) }
                let songs = parse(root)
                guard !songs.isEmpty else { throw PipesError.message("This YouTube playlist is unavailable. Try a public playlist or search for its songs.") }
                return songs
            }
            if let id = LibraryRules.videoID(from: query) {
                var parts = URLComponents(string: "https://www.youtube.com/oembed")!
                parts.queryItems = [URLQueryItem(name: "url", value: "https://www.youtube.com/watch?v=" + id), URLQueryItem(name: "format", value: "json")]
                let metadata = try await getJSON(parts.url!) as? [String: Any]
                return [Track(id: id, title: metadata?["title"] as? String ?? "YouTube recording",
                              artist: metadata?["author_name"] as? String ?? "YouTube",
                              artwork: URL(string: "https://i.ytimg.com/vi/\(id)/hqdefault.jpg"), videoID: id)]
            }
        }
        if let url = possibleURL, url.host?.lowercased() == "open.spotify.com", url.user == nil {
            guard url.pathComponents.contains("track") else {
                throw PipesError.message("This iPhone version supports Spotify song links. Public Spotify playlist import is still Android-only.")
            }
            var parts = URLComponents(string: "https://open.spotify.com/oembed")!
            parts.queryItems = [URLQueryItem(name: "url", value: url.absoluteString)]
            let metadata = try await getJSON(parts.url!) as? [String: Any]
            guard let title = metadata?["title"] as? String, !title.isEmpty else { throw PipesError.message("Couldn't read that Spotify song.") }
            return try await searchSongs(title, region: region)
        }
        return try await searchSongs(query, region: region)
    }

    private static func searchSongs(_ query: String, region: String) async throws -> [Track] {
        parse(try await request("search", body: ["query": query, "params": "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"], region: region))
    }
    static func radio(_ seed: Track, region: String) async throws -> [Track] {
        guard let id = seed.videoID else { return [] }
        var response = try await request("next", body: ["videoId": id, "playlistId": "RDAMVM" + id], region: region)
        var songs = parse(response)
        var visited = Set<String>()
        for _ in 0..<3 {
            guard songs.count < 60, let token = continuation(response), visited.insert(token).inserted else { break }
            do {
                response = try await request("next", body: ["continuation": token], region: region)
                try Task.checkCancellation()
                songs = LibraryRules.unique(songs + parse(response))
            } catch {
                if Task.isCancelled { throw CancellationError() }
                break
            }
        }
        if songs.isEmpty { return try await searchSongs(seed.artist + " " + seed.title + " radio", region: region) }
        return songs
    }
    static func discovery(_ seeds: [Track], region: String, refresh: Bool = false) async throws -> [Track] {
        var songs: [Track] = []
        for seed in seeds.prefix(3) {
            do {
                let related = try await radio(seed, region: region)
                try Task.checkCancellation()
                songs += LibraryRules.recommendations(related, excluding: seeds + songs).prefix(10)
            } catch {
                if Task.isCancelled { throw CancellationError() }
            }
        }
        if songs.isEmpty {
            let query = seeds.first.map { $0.artist + " songs" } ?? "indie pop soul new music"
            songs = try await explore(query, region: region, refresh: refresh)
        }
        return Array(LibraryRules.recommendations(songs, excluding: seeds).prefix(30))
    }
    static func continuation(_ root: Any) -> String? {
        if let object = root as? [String: Any] {
            if let next = object["nextContinuationData"] as? [String: Any], let token = next["continuation"] as? String { return token }
            if let endpoint = object["continuationEndpoint"] as? [String: Any],
               let command = endpoint["continuationCommand"] as? [String: Any], let token = command["token"] as? String { return token }
            for key in object.keys.sorted() {
                if let value = object[key], let token = continuation(value) { return token }
            }
        } else if let array = root as? [Any] {
            for value in array { if let token = continuation(value) { return token } }
        }
        return nil
    }
    static func explore(_ query: String, region: String, refresh: Bool = false) async throws -> [Track] {
        let key = region + ":" + query
        if !refresh, let cached = await FeedCache.shared.get(key) { return cached }
        let songs = Array(try await searchSongs(query, region: region).prefix(18))
        if !songs.isEmpty { await FeedCache.shared.set(key, songs) }
        return songs
    }
    private static func request(_ endpoint: String, body: [String: Any], region: String) async throws -> Any {
        var request = URLRequest(url: URL(string: "https://music.youtube.com/youtubei/v1/" + endpoint)!)
        request.httpMethod = "POST"; request.timeoutInterval = 25
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var body = body
        body["context"] = ["client": ["clientName": "WEB_REMIX", "clientVersion": "1.20261012.01.00", "hl": "en", "gl": region]]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw PipesError.message("YouTube Music is unavailable. Please retry.") }
        return try JSONSerialization.jsonObject(with: data)
    }
    private static func getJSON(_ url: URL) async throws -> Any {
        let (data, response) = try await URLSession.shared.data(for: URLRequest(url: url, timeoutInterval: 20))
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw PipesError.message("This source link could not be opened.") }
        return try JSONSerialization.jsonObject(with: data)
    }
    static func parse(_ root: Any) -> [Track] {
        var results: [Track] = []
        func text(_ value: Any?) -> String? {
            guard let object = value as? [String: Any] else { return nil }
            return object["simpleText"] as? String ?? (object["runs"] as? [[String: Any]])?.compactMap { $0["text"] as? String }.joined()
        }
        func visit(_ value: Any) {
            if let object = value as? [String: Any] {
                if let item = object["musicResponsiveListItemRenderer"] as? [String: Any],
                   let columns = item["flexColumns"] as? [[String: Any]],
                   let first = columns.first?["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any],
                   let title = text(first["text"]) {
                    let playlist = item["playlistItemData"] as? [String: Any]
                    let titleText = first["text"] as? [String: Any]
                    let runs = titleText?["runs"] as? [[String: Any]]
                    let endpoint = runs?.first?["navigationEndpoint"] as? [String: Any]
                    let watch = endpoint?["watchEndpoint"] as? [String: Any]
                    if let id = (playlist?["videoId"] ?? watch?["videoId"]) as? String, LibraryRules.validVideoID(id) {
                        let second = columns.dropFirst().first?["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any]
                        let artist = text(second?["text"])?.components(separatedBy: " • ").first ?? "YouTube Music"
                        results.append(Track(id: id, title: title, artist: artist,
                                             artwork: URL(string: "https://i.ytimg.com/vi/\(id)/hqdefault.jpg"), videoID: id))
                    }
                }
                if let item = object["playlistPanelVideoRenderer"] as? [String: Any],
                   let id = item["videoId"] as? String, LibraryRules.validVideoID(id) {
                    results.append(Track(id: id, title: text(item["title"]) ?? "Recording", artist: text(item["longBylineText"]) ?? "YouTube Music",
                                         artwork: URL(string: "https://i.ytimg.com/vi/\(id)/hqdefault.jpg"), videoID: id))
                }
                for key in object.keys.sorted() { if let child = object[key] { visit(child) } }
            } else if let array = value as? [Any] { for child in array { visit(child) } }
        }
        visit(root)
        return LibraryRules.unique(results)
    }
}

private actor FeedCache {
    static let shared = FeedCache()
    private var entries: [String: [Track]] = [:]
    private var order: [String] = []
    func get(_ key: String) -> [Track]? { entries[key] }
    func set(_ key: String, _ tracks: [Track]) {
        order.removeAll { $0 == key }; order.append(key); entries[key] = tracks
        while order.count > 8 { entries.removeValue(forKey: order.removeFirst()) }
    }
}
