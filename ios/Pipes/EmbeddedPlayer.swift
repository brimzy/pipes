import SwiftUI
import WebKit

@MainActor
final class EmbeddedPlayer: NSObject, WKScriptMessageHandler {
    private weak var library: MusicLibrary?
    private(set) var webView: WKWebView!
    private var videoID: String?
    private var generation = UUID().uuidString
    private var endedHandled = false
    init(library: MusicLibrary) {
        self.library = library
        super.init()
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.userContentController.add(WeakMessageHandler(self), name: "pipes")
        webView = WKWebView(frame: .zero, configuration: configuration)
        webView.isOpaque = false; webView.backgroundColor = .black
        webView.scrollView.isScrollEnabled = false
    }
    func load(_ id: String) {
        guard LibraryRules.validVideoID(id) else { return }
        videoID = id
        generation = UUID().uuidString
        endedHandled = false
        let token = generation
        let html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body,#player{margin:0;width:100%;height:100%;background:#111}</style></head><body>
        <div id="player"></div><script src="https://www.youtube.com/iframe_api"></script><script>
        var player; function send(data){data.id='\(id)';data.token='\(token)';window.webkit.messageHandlers.pipes.postMessage(data);}
        function onYouTubeIframeAPIReady(){player=new YT.Player('player',{videoId:'\(id)',
        playerVars:{playsinline:1,autoplay:0,rel:0},events:{onReady:function(){send({ready:true});},
        onStateChange:function(e){send({state:e.data});},onError:function(e){send({error:e.data});}}});}
        setInterval(function(){if(player&&player.getDuration){send({position:player.getCurrentTime(),duration:player.getDuration()});}},500);
        </script></body></html>
        """
        webView.loadHTMLString(html, baseURL: URL(string: "https://com.nazcarbon.music.ios"))
    }
    func command(_ javascript: String) {
        webView.evaluateJavaScript("if(window.player && player.getPlayerState){player.\(javascript);}", completionHandler: nil)
    }
    func clear() { videoID = nil; webView.loadHTMLString("", baseURL: nil) }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let data = message.body as? [String: Any], let id = data["id"] as? String,
              id == videoID, data["token"] as? String == generation,
              library?.current?.videoID == id, let library else { return }
        if data["ready"] as? Bool == true {
            command(library.playbackRequested && library.isForeground ? "playVideo()" : "pauseVideo()")
        }
        if let state = data["state"] as? Int {
            if state == 1 && (!library.playbackRequested || !library.isForeground) {
                command("pauseVideo()"); library.isPlaying = false; return
            }
            library.isPlaying = state == 1
            if state == 1 { endedHandled = false; library.recordListening() }
            if state == 0 && !endedHandled {
                endedHandled = true
                if library.playbackRequested { library.advance(automatic: true) }
            }
        }
        if let position = data["position"] as? Double, position.isFinite { library.position = max(0, position) }
        if let duration = data["duration"] as? Double, duration.isFinite { library.duration = max(0, duration) }
        if data["error"] != nil {
            library.isPlaying = false
            library.notice = "YouTube couldn't play this recording. Trying the next song."
            if library.playbackRequested { library.advance() }
        }
    }
}

@MainActor
private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var target: EmbeddedPlayer?
    init(_ target: EmbeddedPlayer) { self.target = target }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(userContentController, didReceive: message)
    }
}

struct YouTubePlayer: UIViewRepresentable {
    let player: EmbeddedPlayer
    func makeUIView(context: Context) -> WKWebView { player.webView }
    func updateUIView(_ view: WKWebView, context: Context) {}
}
