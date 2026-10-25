# Pipes for iPhone

Native SwiftUI app for **iOS 17+**, including iPad. Black surfaces and cyan accents match the Android app.

## Install

Download **Pipes-iOS.ipa** from an `ios-v5.0.*` release on the [release page](https://github.com/brimzy/pipes/releases).

The published IPA is **unsigned** and must be signed with your own Apple account using a compatible sideloading tool, or built and installed using Xcode on a Mac. It cannot install directly from Safari. Your signing tool/account determines expiration and device limits. Signed ad hoc distribution requires an Apple distribution certificate and a provisioning profile containing the receiving device.

## Available

- Song/artist search using YouTube Music's unofficial search endpoint.
- Online playback through the visible YouTube embedded player; videos that prohibit embedding can be opened in YouTube.
- Import audio from Files; imported copies remain available across app restarts.
- Local audio background playback, seeking, and lock-screen controls.
- Persisted likes, an editable queue, and a link to GitHub releases.

Online playback requires the app to stay open. The queue advances automatically for local audio; use Next manually for embedded videos. Supported local formats depend on iOS AVFoundation; unsupported files report a playback error. YouTube source changes can affect search.

Android song radio, continuous discovery, Spotify imports, artist discographies, downloads, and automatic update checks are not yet implemented in this first iOS version.

## Build on a Mac

Install Xcode with the iOS SDK, and XcodeGen (`brew install xcodegen`).

```sh
cd ios
xcodegen generate
open Pipes.xcodeproj
```

Choose the Pipes target, select your development team under Signing & Capabilities, and run on your iPhone. Use your own bundle identifier if your team cannot register `com.nazcarbon.music.ios`.

The `Publish iPhone release` workflow builds both simulator and device targets before publishing an unsigned IPA and SHA-256 checksum. iOS releases do not replace the Android latest-release pointer.

## CarPlay

The CarPlay audio scene shares the phone's player and browses imported local files, local favorites and the local queue. Selecting a recording queues the remaining items from that list. The Now Playing screen supports play/pause, next, previous and seeking. Missing files and online embedded videos are excluded from the car browser. Import audio on the phone first.

CarPlay on a real vehicle requires Apple approval for the `com.apple.developer.carplay-audio` entitlement and a provisioning profile containing that entitlement. The public IPA is unsigned; routine sideload signing does not grant CarPlay access. The scene and entitlement are included for an approved developer team to sign. The CI checks simulator and device builds; vehicle testing and approved signing remain external requirements.

See https://developer.apple.com/carplay/ for Apple's entitlement process.
