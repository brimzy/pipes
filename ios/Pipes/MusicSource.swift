import Foundation

enum MusicSource {
    static func search(_ query: String) async throws -> [Track] {
        var request = URLRequest(url: URL(string: "https://music.youtube.com/youtubei/v1/search")!)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "context": ["client": ["clientName": "WEB_REMIX", "clientVersion": "1.20261012.01.00", "hl": "en", "gl": "US"]],
            "query": query,
            "params": "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        ])
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        let root = try JSONSerialization.jsonObject(with: data)
        var results: [Track] = []
        var seen = Set<String>()
        func visit(_ value: Any) {
            if let object = value as? [String: Any] {
                if let renderer = object["musicResponsiveListItemRenderer"] as? [String: Any],
                   let columns = renderer["flexColumns"] as? [[String: Any]],
                   let first = columns.first?["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any],
                   let text = first["text"] as? [String: Any],
                   let runs = text["runs"] as? [[String: Any]],
                   let title = runs.first?["text"] as? String {
                    let playlist = renderer["playlistItemData"] as? [String: Any]
                    let endpoint = runs.first?["navigationEndpoint"] as? [String: Any]
                    let watch = endpoint?["watchEndpoint"] as? [String: Any]
                    if let id = (playlist?["videoId"] ?? watch?["videoId"]) as? String,
                       id.range(of: "^[A-Za-z0-9_-]{11}$", options: .regularExpression) != nil,
                       seen.insert(id).inserted {
                        let second = columns.dropFirst().first?["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any]
                        let subtitle = second?["text"] as? [String: Any]
                        let artists = subtitle?["runs"] as? [[String: Any]]
                        let artist = artists?.first?["text"] as? String ?? "YouTube Music"
                        results.append(Track(id: id, title: title, artist: artist,
                                             artwork: URL(string: "https://i.ytimg.com/vi/\(id)/hqdefault.jpg"), videoID: id))
                    }
                }
                for child in object.values { visit(child) }
            } else if let array = value as? [Any] { for child in array { visit(child) } }
        }
        visit(root)
        return results
    }
}
