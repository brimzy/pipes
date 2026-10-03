# Pipes Music

Android music player APK releases are available on the [Releases page](https://github.com/brimzy/pipes/releases).

Install the APK directly on Android. The release supports ARM64 devices, including Snapdragon phones.


The player includes a seek bar, shuffle/repeat, a live queue, and an artwork backdrop. Starting radio on the playing song preserves its position and paused/playing state.

Audio uses the highest-bitrate URL exposed by the source, without additional encoding. YouTube streams are compressed, not lossless. Use the folder button in the header to open local audio, including FLAC and WAV; files play in their original format through Android's audio pipeline.

The library can download direct HTTPS audio-file links that you own or are licensed to download. Choose a category when adding a download, then play it offline from the Downloads section. Categories can be created or removed; removing one moves its downloads to Unsorted. FLAC/WAV files preserve their source audio; YouTube Music links are compressed streams and are not downloadable audio files in Pipes.

Search results can be opened in Spotify through Spotify's official app or website. Pipes does not play or download Spotify streams; its offline downloader accepts direct HTTPS audio-file links only.

Validation: `gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Set `PIPES_LIVE_SOURCE_TEST=1` to include the optional network smoke test. Search regression tests cover debouncing, stale results, clearing a pending search, duplicate results, and retry after failure.

Device checks: seek midway through a song and start radio while playing and paused; confirm the position does not reset. Switch songs while radio loads and check the queue. Check a small screen, large text, the search keyboard, and gesture navigation. Open a local FLAC/WAV file and verify play, pause, and seeking.
