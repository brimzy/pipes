# Pipes working instructions

## Release completion

The user expects app updates to be released without having to ask again. For requested app changes, finish the implementation, run the required checks, commit the relevant files, push to `main`, and monitor `.github/workflows/release.yml` until the signed GitHub release is published. Verify that the release targets the intended commit and includes `app-release.apk`. Report the published release link in the final response.

This is standing authorization to publish requested app updates. Do not stop after a local edit or successful build, or ask for a separate release confirmation, unless the user explicitly requests a local-only change, a draft, or no release. If publication fails, investigate and fix it within the authorized scope; report any remaining blocker accurately.

Before publishing, run `python3 scripts/sync_oxocarbon.py --check` and the Android build, unit tests, and lint (`gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`) using the project's Android development environment. Update `docs/releases/5.1.md` when user-visible behavior changes. Keep unrelated local changes out of commits.
