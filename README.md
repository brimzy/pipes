# Pipes Music

Android music player APK releases are available on the [Releases page](https://github.com/brimzy/pipes/releases).

Install the APK directly on Android. The release supports ARM64 devices, including Snapdragon phones.


The player includes a seek bar, shuffle/repeat, a live queue, and an artwork backdrop. Starting radio on the playing song preserves its position and paused/playing state.

Audio uses the highest-bitrate URL exposed by the source, without additional encoding. YouTube streams are compressed, not lossless. Use the folder button in the header to open local audio, including FLAC and WAV; files play in their original format through Android's audio pipeline.

The library can download direct HTTPS audio-file links that you own or are licensed to download. Choose a category when adding a download, then play it offline from the Downloads section. Categories can be created or removed; removing one moves its downloads to Unsorted. FLAC/WAV files preserve their source audio; YouTube Music links are compressed streams and are not downloadable audio files in Pipes.

Paste a Spotify song or public playlist link into Search or the link button. Playlist import requires no Spotify sign-in. Pipes starts an anonymous web-player session using Spotify’s publicly served client configuration and reads every playlist page, preserving song order, repeated recordings, artwork, and duration. The supplied Favorites test playlist returns all 1,464 items: 1,462 music tracks and two restricted non-music items, which are reported as skipped. Pages load with at most three concurrent requests; incomplete or inconsistent pages fail without replacing the saved playlist. Temporary guest sessions and complete imports are cached in memory. The guest endpoint is undocumented and may require updates when Spotify changes its web player. Song links and imported playlist entries match playable YouTube Music recordings by title, artist, and duration, with a bounded match cache.

Radio uses YouTube Music's song radio for the current recording, filters out non-music videos, long mixes and duplicate recordings, then samples a varied order with artist diversity. The playback service prepares playlist and radio songs in small parallel batches and continues loading in the background. Playlist queues transfer through a small request ID within the app’s process, avoiding Android’s Binder message-size limit for large playlists. Starting radio retains the current position and paused/playing state. Loading screens use the supplied animated GIF.

Pipes does not play or download Spotify streams; its offline downloader accepts direct HTTPS audio-file links only.

Validation: `gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Set `PIPES_LIVE_SPOTIFY_TEST=1` to test the supplied public playlist without signing in. Set `PIPES_LIVE_SOURCE_TEST=1` to include the optional network smoke test. Search regression tests cover debouncing, stale results, clearing a pending search, duplicate results, and retry after failure.

Device checks: seek midway through a song and start radio while playing and paused; confirm the position does not reset. Switch songs while radio loads and check the queue. Check a small screen, large text, the search keyboard, and gesture navigation. Open a local FLAC/WAV file and verify play, pause, and seeking.

## Pipes 3.0

A Quickshell-inspired black and charcoal theme with restrained blue focus, selection, and progress accents. Existing playback, radio, discography, playlists, and offline download features remain available.

Liked songs now retain their metadata across restarts and updates. Existing playlist, like, artist, download, and category preference keys are preserved. Older likes saved with only an ID remain visible as “Saved song”; playing or finding them in search refreshes their metadata. Expiring stream URLs are not saved.

The Search tab shows up to 30 recent searches. Tap a query to reuse it, use its × button to delete it, or choose Clear all. Deletions persist across restarts. Queries are recorded when the debounced search starts.

Install updates over the existing app, using APKs signed with the same release key. Keep the package ID and signing secrets unchanged; do not uninstall or clear app storage to update. Debug APKs cannot replace a release signed with a different key. Uninstalling or clearing Android app storage still removes local data. Release version codes use a 30000 base plus the workflow run number.

3.0 device verification: like a song, create a playlist/category, and finish a download on the previous release; install the same-key 3.0 APK over it and confirm all remain. Restart the app and check saved songs and recent searches, then delete one search and clear all and restart again. Inspect Home, Search, Library, artist releases, and the player on small screens and with large text.

Playback reliability: the service keeps CPU/network wake locks during playback, refreshes failed or stalled streams without losing the current position or pause state, and retries temporary queue failures. A repeatedly unavailable recording is skipped when another queued song is available. Artwork requests normalize CDN URLs, retry transient failures twice, and display a fallback when unavailable. Release publication now requires unit tests, a debug build, and lint to pass.

Home now offers a continuously expanding discovery feed, seeded by the current online song and liked artists. Scrolling near the end loads another varied batch of related music; duplicate source URLs and normalized recordings are excluded across pages. Discovered songs and the scroll position survive navigation to the player and back during the app session. Network failures offer a retry, and exhausted recommendations offer another wave.

The player uses a handle-free decorative waveform seek bar: cyan illumination stays on the waveform while hovering, pressing, and dragging. A borderless charcoal play/pause button uses shade and a soft shadow for separation. Artwork fills its frame with centered scaling instead of leaving empty bands. The waveform is a visual treatment, not measured audio amplitude; native slider accessibility and seeking remain available.

The Up next screen includes a saved region preference for newly fetched recommendations. Auto keeps radio tied to the playing recording, uses a language hint from title/artist scripts, and does not force a country; country cannot reliably be inferred from song metadata. Choose a country manually to provide the source with a market hint. Existing queued songs remain in order. Song menus include Add to queue without interrupting playback, and repeated songs are selected by queue position. Radio results loaded for an earlier playing song are discarded after a transition. The waveform now illuminates cyan to pale white around the seek position during touch/drag, without increasing its height.

App updates: Pipes checks the latest stable GitHub release when opened or resumed, at most once per six hours after a successful check. The header update icon checks immediately. Newer v3.0 build numbers offer a Download button; Android DownloadManager continues the APK transfer across app restarts. Pipes verifies the GitHub SHA-256 digest, APK size, package, version, and installed signing certificate before opening Android’s installer. Allow Pipes to install apps when Android requests it, then confirm the update. Installation keeps app data. Checks and downloads do not silently install updates. Install this updater-enabled release once manually; subsequent releases can be installed through the app. Debug installations cannot accept production-signed updates.
