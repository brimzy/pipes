# Car media support

## Android Auto

Pipes exposes a Media3 MediaLibraryService with saved likes, playlists, completed downloads, and voice/text search within those saved recordings. Selection resolves a saved opaque ID rather than accepting a controller-supplied playback URI. Playlist selections retain their remaining order. Browsing does not start playback. Phone playback, notifications and car controls share one ExoPlayer.

Car browsing is limited to Android-trusted automotive controllers or the official Android Auto package with verified UID ownership and a signature matching Google Play services. Generic trusted notification controllers retain their previous limited playback controls. All Pipes-specific queue/radio custom commands still require the app's UID. Unknown IDs, external URIs and untrusted host identities are rejected.

The manifest advertises the platform MediaBrowserService and Media3 library interfaces and the Android Auto media descriptor. MediaLibraryService browser binding must follow the public browser-service manifest contract; authorization is enforced in the session callbacks. See https://developer.android.com/media/media3/session/serve-content and https://developer.android.com/training/cars/media/auto .

For a directly installed GitHub APK, Android Auto may require enabling unknown sources in its developer settings. Head-unit/vehicle testing remains necessary.

## Apple CarPlay

The iPhone app registers a CPTemplateApplicationScene backed by the same MusicLibrary instance as the phone. Tabs show local audio, local favorites, and the local queue; Now Playing uses the shared AVPlayer and remote commands. Online embedded videos are excluded.

The source declares the CarPlay audio entitlement. Real-device/vehicle use requires Apple approval and entitlement-bearing provisioning; the public unsigned IPA cannot enable that approval. See https://developer.apple.com/carplay/ .
