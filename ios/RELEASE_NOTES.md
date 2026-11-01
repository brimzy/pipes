# Pipes for iPhone & iPad 5.1.55

Updated from the original 5.0 preview to the current 5.1 app experience. Requires iOS 17 or later.

- Four destinations: personalized Home, Search, mood/genre Explore with session caching and refresh, and Library.
- Charcoal player with artwork atmosphere, waveform preview/release-to-seek, minimize versus stop, mini-player next/previous swipes, shuffle and repeat.
- Persistent likes and upcoming queue, Play next without duplicates, queue editing and song radio that preserves the playing song's position.
- Editable playlists and custom albums, bulk selection, independent collection copies, and custom image/animated GIF covers copied into app storage.
- Seven resumable Learn Pipes lessons, silent navigation/player practice, and shortcuts into the actual app.
- YouTube video/public-playlist search links, Spotify song metadata matching, recent searches and recommendation regions.
- Files imports and direct HTTPS audio downloads into named category playlists. Local audio supports background playback, seeking, lock-screen controls and the existing local-only CarPlay scene.
- Existing iOS 5.0 likes and imported file records are retained when installed with the same app identity.

Download **Pipes-iOS.ipa** and sign/install it using your sideloading tool, or build with Xcode. The IPA is **unsigned** and cannot install directly from Safari. Your signing account/tool determines device limits and expiration.

Online songs use the visible YouTube embedded player and require Pipes to remain open. Embedding/source availability can change. This is an iOS adaptation, with platform differences: Spotify playlist import, artist discographies, YouTube audio extraction, Instagram downloads, the decoded audio spectrum and Kirby save animation remain Android-only. Saving an online song in a collection does not download its audio.

Real-car CarPlay requires Apple's audio entitlement and a matching provisioning profile; ordinary sideload signing does not grant that entitlement.

The release workflow builds simulator and device targets, runs domain regressions plus a simulator tutorial/collection/persistence smoke test, and publishes an IPA with its SHA-256 checksum. Device/vehicle playback testing remains separate.
