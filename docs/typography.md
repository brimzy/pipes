# Typography and colour sources

The Android app bundles unmodified Libron v0.32, by Nico Verbruggen, with regular, bold, italic and bold italic faces. All Material typography roles use Libron; normal and bold weights establish hierarchy. Font binaries are bundled locally and require no font service or network request.

Source: https://github.com/nicoverbruggen/libron/releases/tag/v0.32

SIL Open Font License and copyright notices ship in `app/src/main/assets/licenses/`.

Canonical colour source: https://github.com/nyoom-engineering/base16-oxocarbon/blob/23497e43ad4553dad4ad1a4d3fb7eb2e5bbbb313/base16-oxocarbon-dark.yaml

The unmodified YAML is vendored at `design/base16-oxocarbon-dark.yaml`. Run `python3 scripts/sync_oxocarbon.py` to generate both Compose tokens and Android colour resources, or add `--check` to detect drift. Every Material colour role is explicitly assigned. Surface tint is transparent and dynamic wallpaper colours are disabled.

The app canvas is deliberately true black (`#000000`), including the launch window and system-bar background. This is an app-specific override, not a change to Oxocarbon Base00. The floating navigation dock uses Base00 (`#161616`), cards use Base01 (`#262626`), and raised surfaces use Base02 (`#393939`). Base04 is `#dde1e6` and Base05 is `#f2f4f8`, exactly as the Base16 source specifies.

Headings, song titles, wordmark and secondary labels use neutral text. Base08 cyan is reserved for active controls and playback; other palette hues are not decorative accents. Artwork retains its original colours.

The navigation dock uses a shared 24-unit icon grid, 1.6-unit strokes, a 64dp minimum touch-row height and 24dp outer margins. The selected destination expands to show its label, and a single cyan marker follows its center with a damped spring. Inactive destinations retain accessible names and tab semantics; keyboard focus has a visible outline. The dock reserves layout space rather than covering the bottom of scroll content. Floating-player and snackbar clearance share a 96dp token.

Artwork uses an opt-in two-physical-pixel rim in Base0F at 20% opacity. This stays the same pixel width across screen densities. General surfaces and the navigation dock have no decorative outline; keyboard focus retains its functional outline. Home and Explore share their artwork card component and the 24dp page / 16dp column / 8dp artwork-caption / 4dp metadata spacing tokens. The Library header contains counts and actions, with no duplicate song showcase.

Shadows use matching silhouettes and shared 2/4/8dp elevations; the floating player lifts to 12dp during a drag. Playback controls live in a common transport surface, with a recessed play/pause housing and a pressed spring response. Search takes focus after its expansion settles. Compose animation primitives follow the system animation duration scale.
