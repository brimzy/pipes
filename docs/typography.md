# Typography and colour sources

The Android app bundles unmodified Libron v0.32, by Nico Verbruggen, with regular, bold, italic and bold italic faces. All Material typography roles use Libron; normal and bold weights establish hierarchy. Font binaries are bundled locally and require no font service or network request.

Source: https://github.com/nicoverbruggen/libron/releases/tag/v0.32

SIL Open Font License and copyright notices ship in `app/src/main/assets/licenses/`.

Canonical colour source: https://github.com/nyoom-engineering/base16-oxocarbon/blob/23497e43ad4553dad4ad1a4d3fb7eb2e5bbbb313/base16-oxocarbon-dark.yaml

The unmodified YAML is vendored at `design/base16-oxocarbon-dark.yaml`. Run `python3 scripts/sync_oxocarbon.py` to generate both Compose tokens and Android colour resources, or add `--check` to detect drift. Every Material colour role is explicitly assigned, including error, inverse, fixed and container roles; Android's window/background and accent use the same generated resources. Surface tint is transparent to preserve the actual neutral surfaces.

Base04 is `#dde1e6` and Base05 is `#f2f4f8`, exactly as the Base16 source specifies. These differ from the newer Neovim implementation's dynamically blended neutrals. Primary actions and playback use Base08 cyan; secondary controls use Base09 blue, saved hearts use Base0C pink, and queue removal uses Base0E violet. No dynamic wallpaper palette is used. Opacity remains reserved for shadows, scrims and interaction feedback.

Shadows use matching silhouettes and shared 2/4/8dp elevations; the floating player lifts to 12dp during a drag. Playback controls live in a common transport surface, with a recessed play/pause housing and a pressed spring response. Search takes focus after its expansion settles. Compose animation primitives follow the system animation duration scale.

Screenshot review of build 36 showed that correct palette tokens alone left Home visually monochrome: the large heading, brand, featured song and actions still used neutral text. The visible hierarchy now uses Base0E violet for heading emphasis, Base09 blue for song titles, Base0F ice blue for the wordmark and Base08 cyan for actions and section labels. Body and artist metadata retain the neutral foreground. The selected navigation surface uses opaque Base02 instead of a cyan wash. This same hierarchy extends to Library, search, artist pages, downloads and the player. Artwork keeps its original colours.
