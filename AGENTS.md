# Repository guidance

## Release notes and client compatibility

- Put player-visible changes in the appropriate `changes/NN-Category.md` fragment. The release script compiles the fragments into `CHANGELOG.md` and GitHub release notes. Do not edit a pending version's changelog section by hand or repeat compatibility text in a fragment.
- Keep `mod_version` and `compatible_client_version` in `gradle.properties`. The latter is the **oldest client release explicitly supported by this server release**. The release script inserts `Compatible client version: X.Y.Z` into both outputs.
- Review `compatible_client_version` for every release. Keep it when changes are server-authoritative and use existing Minecraft chat, commands, or packets. Raise it to the first client release containing a required client mixin, UI, asset, registration, or network protocol change. Do not change it merely because `mod_version` changed.
- Do not claim a client version is verified solely because NeoForge accepts the version mismatch or the server reaches RCON. Before publishing a compatibility claim, test a login with that client version and exercise the affected interaction. Record any untested compatibility as pending in work notes, not as a confirmed result.
- Preview with `python3 ci/release_notes.py --preview --output /tmp/insomniac-release-notes.md` and run the release-note tests before tagging. A local build or commit does not authorize a push, tag, release, CurseForge upload, or live deployment.
- If a CurseForge description is prepared manually, copy the compatibility line from the generated release notes so it agrees with the changelog and GitHub release.
