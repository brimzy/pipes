package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class ResolvedAudioDownloadTest {
    @Test fun acceptsSignedProgressivePlaybackCdnAudioWithoutAFileExtension() {
        val audio = ResolvedAudioDownload("https://rr1---sn-a5mekn.googlevideo.com/videoplayback?expire=123&sig=abc", "audio/webm", "webm")
        assertEquals(audio, audio.validated())
    }

    @Test fun rejectsUntrustedHostsCredentialsPortsAndNonAudioFormats() {
        listOf(
            "http://rr1.googlevideo.com/audio", "https://evil.com/audio", "https://googlevideo.com.evil.com/audio",
            "https://user:secret@rr1.googlevideo.com/audio", "https://rr1.googlevideo.com:8443/audio", "https://rr1.googlevideo.com/audio#fragment",
        ).forEach { url -> assertTrue(url, runCatching { ResolvedAudioDownload(url, "audio/mp4", "m4a").validated() }.isFailure) }
        assertTrue(runCatching { ResolvedAudioDownload("https://rr1.googlevideo.com/audio", "text/html", "m4a").validated() }.isFailure)
        assertTrue(runCatching { ResolvedAudioDownload("https://rr1.googlevideo.com/audio", "audio/mp4", "../file").validated() }.isFailure)
    }
}
