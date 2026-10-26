# CI and releases

Woodpecker runs the tests in [`.woodpecker/tests.yml`](../.woodpecker/tests.yml)
for `main` pushes, approved pull requests targeting `main`, and manual runs on
`main`. It builds the release mod and CI-only test mods, runs the Python
input and release-gate tests, starts a disposable Minecraft server, and
launches a disposable Prism client under Xvfb. The server must reach RCON and
log the patch load marker; the client must log `INSOMNIAC_CI_TITLE_READY`.
When SlimeThroneExtras is present, both checks additionally require a log
marker confirming that the compatibility mixin filter actually activated.
The server check also confirms Lootr processor injection and inspects the
`/stextras` help tree for removed command branches and retained tools.
The additional player-test step runs a GameTest and joins a real client to a
disposable server for UI and menu assertions. Containers are removed after
each check and no Minecraft port is published. The startup steps remain
startup checks; no check uses the production server.

[The GitHub Actions release workflow](../.github/workflows/build.yml) runs
only for `v*` tags. Editing `mod_version` or adding change fragments does not
compile the changelog. The tag must point to the current `main` commit, and
that commit must have a successful Woodpecker **push** status with context
`ci/woodpecker/push/tests`. A missing, pending, or failed status blocks the
release. The workflow builds the JAR, compiles pending `changes/*.md` into
`CHANGELOG.md`, deletes those fragments, and pushes a generated
`Compile changelog for vX.Y.Z [skip ci]` commit to `main`. It then publishes
the GitHub Release using the compiled notes. A rerun can reuse that generated
commit. Repository rules must allow `github-actions[bot]` to push it. No
live-server installation or CurseForge upload is configured.

The release compiler reads `compatible_client_version` from
[`gradle.properties`](../gradle.properties) and prints **Compatible client
version: X.Y.Z** in both the changelog entry and GitHub release notes. Review
this value for every release. Keep it for server-only behavior that existing
clients can use; raise it when a client mixin, screen, asset, registration, or
network change requires a new client build. A server startup check does not
prove that an older client can log in and use the changed behavior. See
[`AGENTS.md`](../AGENTS.md) for the maintenance rule. A manual CurseForge
description should copy the same compatibility line from the generated notes.

## Configure Woodpecker (repository owner)

These settings and inputs are not created by the repository changes:

1. Activate `Insomniac-Gaming/TensuraPatches` in the GitHub-connected
   Woodpecker instance at `https://woodpecker.riftwalkerdev.com`. Set its
   pipeline path to `.woodpecker/` if the default path discovery does not find
   `tests.yml`. Make sure its GitHub integration reports commit statuses back
   to **this** repository. The release workflow expects the default status
   context `ci/woodpecker/push/tests`; if the Woodpecker server uses a custom
   status context, update the context passed to `ci/release_gate.py` in the
   GitHub workflow before publishing a tag.
2. Set the Woodpecker project's visibility to **Private** and restrict
   repository write/admin access to people trusted with the CI host inputs.
   Enable pull-request runs and set **Require approval for** to **all pull
   requests** (`pull_requests`), rather than the default fork-only approval.
   Before approving a run, inspect its exact PR diff. Decline it if any
   `.woodpecker/` pipeline file changed. Review all executable code in the PR
   as well: approved smoke runs give that code access to Docker and a copied
   Prism account. This is a reviewer rule, not an automatic security check;
   a PR can change its own pipeline configuration.
3. Have a Woodpecker administrator enable trusted host volumes for this
   repository. Its smoke steps mount `/var/run/docker.sock` and
   the Docker volume `insomniac-ci-input` read-only at `/ci-input`. Create that
   named volume on the agent host as shown below. The Docker socket still
   grants broad control of the agent host, so dedicate an agent to trusted
   repositories if other untrusted projects use the instance. Create the
   volume on every eligible agent host, or schedule this workflow only on the
   agent that has it.
4. Add a Woodpecker repository secret named `prism_profile`, set to the
   profile name displayed for the CI account in Prism Launcher. Make it
   available to `push`, `pull_request`, and `manual` events so the client step
   can run in each case. The name is not an access token; `accounts.json` in
   the host input directory contains private account data. Do not place that
   file or the server snapshot in Git.
5. Prepare the host input directory and named volume below, then trigger a manual run on
   `main`. Confirm all five steps pass. A manual status does **not** replace
   the `main` push status required for a release; push a commit and verify
   GitHub shows `ci/woodpecker/push/tests` as successful for its SHA.

## Protected host inputs

On the host running the trusted Woodpecker Docker agent, prepare an existing
private directory with this structure. Its actual path stays outside the
repository:

```text
<private host directory>/
  server/
    mods/                    required; includes Tensura_Horizons-0.31.jar
    config/                  recommended
    defaultconfigs/          optional
    kubejs/                  optional
    scripts/                 optional
    datapacks/               optional
  prism/
    accounts.json            private; dedicated CI launcher account
    prismlauncher.cfg
    assets/ libraries/ meta/
    instances/Tensura by Insomniac Gaming/
  world.zip                  optional; one world with one level.dat
```

After the directory exists, create a Docker named volume on that host:

```bash
docker volume create --driver local \
  --opt type=none --opt o=bind \
  --opt device=/absolute/path/to/private/host/directory \
  insomniac-ci-input
docker volume inspect insomniac-ci-input
```

Replace the `--opt device` value locally; do not commit or paste the real path
into CI logs. The workflow contains only `insomniac-ci-input:/ci-input:ro`.
Docker administrators can still see the underlying path with `docker volume
inspect`, so this hides it from the public repository rather than treating it
as a cryptographic secret. If the volume name already exists, inspect its
driver options before using it; `docker volume create` does not replace an
existing volume's mapping.

Copy the server `mods/` and configuration folders from a known-good snapshot.
Leave out `bluemap/`, `config/bluemap/`, `config/bluemap3d*.toml`, the BlueMap
mod and addon JARs, and `DiscordIntegration-*.jar`; the disposable smoke server
does not need their web services. Leave out temporary profiler files under
`config/spark/tmp-client/`.
Do not copy the live server's entire `/data`, backups, logs, or secrets. The
world ZIP can contain `level.dat` at its root or inside one enclosing folder;
the runner rejects traversal, symlinks, and archives above 20 GiB
uncompressed. It does not write to these inputs.
The server smoke runner copies the snapshot into a disposable container and
gives the Minecraft user ownership of that copy, so mods can write their
configuration and generated files without changing the protected snapshot.

Use a dedicated CI copy of Prism data, not a mount of a live desktop profile.
Keep `accounts.json` readable only to the CI operator and agent. The staged
client excludes saves, logs, screenshots, and launcher commands. The runner
replaces an old InsomniacServerPatches JAR with the newly built JAR and adds
the CI-only title probe. The probe is not published in the release. The host
needs enough RAM for the 10 GiB server and 12 GiB client container limits,
plus disk for snapshot copies and the Prism image.

## Local checks

The reusable in-engine player suite is documented in
[`player_tests/README.md`](player_tests/README.md). It adds a CI-only NeoForge
GameTest mod, named player fixtures, Python real-client scenarios, and a
hash-pinned MC Pilot client mod. Its Woodpecker step is an additional gate;
it uses disposable server/client copies and writes failure evidence under
`ci-artifacts/player-tests/`. Run it against the private CI modpack locally
before relying on its first Woodpecker result.

```bash
./gradlew clean build :title-probe:build :player-test-mod:build --no-daemon
python3 -m unittest discover -s ci -p 'test_*.py'
version="$(sed -n 's/^mod_version=//p' gradle.properties)"
python3 ci/smoke.py server --snapshot /path/to/server-copy \
  --jar "build/libs/InsomniacServerPatches-${version}.jar" \
  --world /path/to/world.zip
docker build -f ci/Dockerfile.client -t insomniac-client-smoke:11.1.1 .
python3 ci/smoke.py client --prism-root /path/to/prism-copy \
  --instance-id 'Tensura by Insomniac Gaming' --profile 'CI profile name' \
  --jar "build/libs/InsomniacServerPatches-${version}.jar" \
  --probe ci/title-probe/build/libs/InsomniacTitleProbe-1.0.0.jar
```

Omit `--world` to generate a fresh test world. The server runner defaults to
NeoForge `21.1.262`, Minecraft `1.21.1`, and `itzg/minecraft-server:java21`.
Use `--neoforge`, `--java-memory`, `--container-memory`, or `--timeout` if the
isolated test requires adjustment.

## First release checklist

1. Confirm the `main` push pipeline passed on the exact commit to tag. The
   changelog remains pending until this tag triggers the release workflow.
2. Confirm `mod_version` in `gradle.properties` matches the planned tag.
3. Tag that tested `main` commit and push the tag. GitHub Actions compiles the
   changelog, pushes the no-CI commit, and publishes the release.
4. Check the GitHub Release contains only the release mod JAR. If repository
   or organization Actions settings prevent `GITHUB_TOKEN` from writing a
   release, allow this workflow's declared `contents: write` permission.

## Release notes

Write player-facing changes as Markdown fragments directly in `changes/`,
using files named `01-Section title.md`, `02-Section title.md`, and so on.
The number controls section order; the rest of the filename becomes a heading.
Set `mod_version` in `gradle.properties` for the planned release. Gradle uses
that same version for the JAR and NeoForge metadata. The tag must match it.
The tag-triggered release workflow compiles and consumes the fragments; a
version edit alone leaves them untouched. Release notes are not made from
commit messages.

To preview pending changes locally without deleting them:

```bash
python3 ci/release_notes.py --preview --output /tmp/insomniac-release-notes.md
cat /tmp/insomniac-release-notes.md
```

The tag workflow runs `--release X.Y.Z --output ...`, which writes the
changelog, removes the fragments, and prepares the GitHub Release notes.
