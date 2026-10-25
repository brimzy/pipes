# Typography and colour sources

The Android app bundles unmodified Libron v0.32, by Nico Verbruggen, with regular, bold, italic and bold italic faces. All Material typography roles use Libron; normal and bold weights establish hierarchy. Font binaries are bundled locally and require no font service or network request.

Source: https://github.com/nicoverbruggen/libron/releases/tag/v0.32

SIL Open Font License and copyright notices ship in `app/src/main/assets/licenses/`.

Oxocarbon reference: https://github.com/nyoom-engineering/oxocarbon.nvim
Style guidance: https://github.com/nyoom-engineering/oxocarbon/blob/main/docs/style-guide.md

Charcoal surfaces establish depth. Blue marks primary and selected controls, cyan marks playback progress and queue addition, and violet marks saved state and queue removal. Artwork supplies the broader colour field. Decorative signal strips and dot/dash navigation are removed.

The neutral colours match the reference theme's HSLuv blends: background `#161616`, surface `#262626`, raised `#393939`, edge `#525252`, secondary text `#d0d0d0`, and primary text `#f2f2f2`. Shadows use matching silhouettes and shared 2/4/8dp elevations; the floating player lifts to 12dp during a drag. Playback controls live in a common transport surface, with a recessed play/pause housing and a pressed spring response. Search expands over 280ms and receives focus after expansion; page transitions use a short fade and restrained translation. Compose animation primitives follow the system animation duration scale.
