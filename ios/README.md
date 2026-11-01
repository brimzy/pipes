# Pipes for iPhone & iPad

Native SwiftUI adaptation of **Pipes 5.1.55** for **iOS 17+**. Home, Search, Explore and Library share the current app's charcoal surfaces, artwork atmosphere and cyan active controls.

## Install

Download **Pipes-iOS.ipa** from the newest `ios-v5.1.*` entry on the [release page](https://github.com/brimzy/pipes/releases?q=ios-v5.1).

The IPA is **unsigned**. Sign/install it with your Apple account using a compatible sideloading tool, or build and install with Xcode on a Mac. It cannot install directly from Safari. Your signing account/tool determines expiration and device limits. Use the same bundle identity and signing setup when updating to preserve your library. Signed ad hoc distribution requires a certificate and a provisioning profile containing the receiving device.

## What's current

- Home recommendations based on the playing online song or your liked artists; Explore moods/genres with a bounded session cache, refresh and retry.
- Search songs/artists, YouTube videos/public playlists and Spotify song links. Recent searches stay on your device; Settings lets you choose a recommendation region. Upstream endpoints are unofficial and may change.
- Persistent likes, queue, Play next, queue reordering/removal, shuffle, repeat, previous/next, and related-song radio. Starting radio on the current song preserves its position.
- Named playlists and custom albums: create, rename, change type, bulk-select songs, add all, remove entries or delete the collection. Duplicates are skipped. Collection copies are independent; deleting a collection leaves audio files intact.
- Custom image and animated GIF covers (up to 20 MB / 16 megapixels), copied into app storage. Library, the collection and the Add to… picker share the cover. Reduce Motion displays a still image. Change or restore the cover without changing songs.
- Full player, waveform preview with release-to-seek, a compact player, and mini-player title swipes (left: next; right: previous). Minimize continues; Stop clears the queue. The waveform is a seek control; the Android decoded audio spectrum is not ported.
- Seven resumable Learn Pipes lessons, silent controls and Try… shortcuts. Offered once on upgrading, including from the 5.0 preview; resume or replay from Settings.
- Files imports and direct HTTPS audio-file downloads into category playlists. Completed files appear in Offline audio. Imports play immediately; downloaded files can be played from Library. Keep Pipes open during downloads; failed transfers can be retried from the form.
- Local audio background playback, seeking, lock-screen commands, interruption/headphone handling and local-only CarPlay.

Existing 5.0 local file and favorite records use the same storage keys, and the bundle identifier remains `com.nazcarbon.music.ios`.

## Playback differences from Android

Online playback uses the **visible YouTube embedded player**. Keep Pipes open. Tap Play inside the video if autoplay is blocked; recordings that prohibit embedding can be opened in YouTube. The embedded player stays alive when minimized, reports position/state, and advances the queue when a video ends. Online background playback is not supported.

Supported local codecs depend on iOS AVFoundation. Local files retain their original format; a download does not become lossless. Direct audio links must use HTTPS, a public hostname without embedded credentials or a custom port, and a supported audio-file extension. This input validation is not a network firewall; use hosts you trust. No analytics or crash-reporting SDK is included. App files are not encrypted by Pipes and may be included in iOS backups. Online providers and chosen download hosts receive your network requests.

Spotify playlist import, artist discographies, YouTube audio extraction, Instagram downloads, Android's automatic APK updater, the audio spectrum and Kirby save animation remain Android-only. Spotify song links supply metadata for YouTube Music matching; they do not play or download Spotify streams. iPhone updates are linked from Settings and require installation through your signing setup.

## Build and test on a Mac

Install Xcode with the iOS SDK and XcodeGen (`brew install xcodegen`).

```sh
cd ios
xcodegen generate
open Pipes.xcodeproj
```

Choose your development team under Signing & Capabilities. Use your own bundle identifier if your team cannot register the default. To test, select an available iPhone simulator and run Product → Test. The Pipes scheme includes domain tests and a tutorial/collection/persistence UI smoke test. To run from the command line, substitute an installed simulator name:

```sh
xcodebuild -project Pipes.xcodeproj -scheme Pipes \
  -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO test
```

The `Publish iPhone release` workflow verifies simulator/device builds and tests before publishing an unsigned IPA with a checksum. Dispatching it on a branch validates without publishing. iOS releases preserve the Android latest-release pointer.

## CarPlay

The car scene shares the phone's local audio player and browses imported/downloaded audio, local favorites and the local queue. Selecting a recording queues the rest of that list. The Now Playing screen supports transport and seeking. Missing files and online videos are excluded.

Real-car use requires Apple approval for `com.apple.developer.carplay-audio` and a matching provisioning profile. Routine sideload signing does not grant this capability. CI does not test vehicles or provide approved signing.

See [Apple's CarPlay entitlement guide](https://developer.apple.com/documentation/carplay/requesting-carplay-entitlements).
