# Pipes Music

Android music player APK releases are available on the [Releases page](https://github.com/brimzy/pipes/releases).

Install the APK directly on Android. The release supports ARM64 devices, including Snapdragon phones.


The player includes a seek bar, shuffle/repeat, a live queue, and an artwork backdrop. Starting radio on the playing song preserves its position and paused/playing state.

Audio uses the highest-bitrate URL exposed by the source, without additional encoding. YouTube streams are compressed, not lossless. Use the folder button in the header to open local audio, including FLAC and WAV; files play in their original format through Android's audio pipeline.

The library can download direct HTTPS audio-file links that you own or are licensed to download. Choose a category when adding a download, then play it offline from the Downloads section. Categories can be created or removed; removing one moves its downloads to Unsorted. FLAC/WAV files preserve their source audio; YouTube Music links are compressed streams and are not downloadable audio files in Pipes.

Paste a Spotify song or playlist link into Search or the link button. Song links find playable matches by title and artist. Playlist imports ask you to sign in to Spotify, then save the full playlist with tracks matched to Pipes as they are played. Spotify only provides the complete playlist to an authorized account that owns or collaborates on it. Search results can also be opened in Spotify through Spotify's official app or website. Pipes does not play or download Spotify streams; its offline downloader accepts direct HTTPS audio-file links only.

Validation: `gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Set `PIPES_LIVE_SOURCE_TEST=1` to include the optional network smoke test. Search regression tests cover debouncing, stale results, clearing a pending search, duplicate results, and retry after failure.

Device checks: seek midway through a song and start radio while playing and paused; confirm the position does not reset. Switch songs while radio loads and check the queue. Check a small screen, large text, the search keyboard, and gesture navigation. Open a local FLAC/WAV file and verify play, pause, and seeking.

## Pipes 3.0

A Quickshell-inspired black and charcoal theme with restrained blue focus, selection, and progress accents. Existing playback, radio, discography, playlists, and offline download features remain available.

Liked songs now retain their metadata across restarts and updates. Existing playlist, like, artist, download, and category preference keys are preserved. Older likes saved with only an ID remain visible as “Saved song”; playing or finding them in search refreshes their metadata. Expiring stream URLs are not saved.

The Search tab shows up to 30 recent searches. Tap a query to reuse it, use its × button to delete it, or choose Clear all. Deletions persist across restarts. Queries are recorded when the debounced search starts.

Install updates over the existing app, using APKs signed with the same release key. Keep the package ID and signing secrets unchanged; do not uninstall or clear app storage to update. Debug APKs cannot replace a release signed with a different key. Uninstalling or clearing Android app storage still removes local data. Release version codes use a 30000 base plus the workflow run number.

3.0 device verification: like a song, create a playlist/category, and finish a download on the previous release; install the same-key 3.0 APK over it and confirm all remain. Restart the app and check saved songs and recent searches, then delete one search and clear all and restart again. Inspect Home, Search, Library, artist releases, and the player on small screens and with large text.
