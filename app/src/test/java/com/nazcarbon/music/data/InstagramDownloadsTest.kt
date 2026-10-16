package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class InstagramDownloadsTest {
    @Test fun `canonical links discard tracking parameters`() {
        assertEquals("https://www.instagram.com/reel/Ab_12-/",
            InstagramDownloads.canonicalUrl("https://instagram.com/reel/Ab_12-/?igsh=tracking#fragment"))
    }

    @Test fun `reject deceptive hosts credentials and unsupported paths`() {
        for (url in listOf("https://instagram.com.evil.test/reel/id/", "https://instagram.com@evil.test/reel/id/",
            "http://instagram.com/reel/id/", "https://user@instagram.com/reel/id/",
            "https://instagram.com:444/reel/id/", "https://instagram.com/stories/user/1/",
            "https://instagram.com/reel/id/../../other")) {
            assertThrows(url, IllegalArgumentException::class.java) { InstagramDownloads.canonicalUrl(url) }
        }
    }

    @Test fun `extract public metadata regardless of attribute order`() {
        assertEquals("https://video.cdninstagram.com/a.mp4?x=1&y=2", InstagramDownloads.mediaUrl(
            "<meta content='https://video.cdninstagram.com/a.mp4?x=1&amp;y=2' property='og:video' />"))
        assertEquals("https://video.fbcdn.net/a.mp4", InstagramDownloads.mediaUrl(
            """{"video_url":"https:\/\/video.fbcdn.net\/a.mp4"}"""))
    }

    @Test fun `reject untrusted extracted addresses and login pages`() {
        for (url in listOf("https://cdninstagram.com.evil.test/a.mp4", "https://127.0.0.1/a.mp4",
            "http://video.cdninstagram.com/a.mp4", "https://user@video.fbcdn.net/a.mp4",
            "https://video.fbcdn.net:444/a.mp4", "file:///private/data")) {
            assertThrows(url, IllegalArgumentException::class.java) { InstagramDownloads.validateMediaUrl(url) }
        }
        assertThrows(IllegalArgumentException::class.java) { InstagramDownloads.mediaUrl("<html>Login required</html>") }
    }
}
