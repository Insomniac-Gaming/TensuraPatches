# CI smoke tests

The public repository uses GitHub Actions:

- `.github/workflows/build.yml` compiles the release mod and CI-only title
  probe, runs input validation tests, and uploads the release JAR as a workflow
  artifact. It runs on `main` pushes, pull requests, manual dispatch, and
  version tags. A successful `v*` tag build publishes a GitHub Release with
  that JAR. It needs no configured secrets, host mounts, or Docker socket;
  the release job uses GitHub's temporary `GITHUB_TOKEN`.

The separate `ci/private-smoke.woodpecker.yml` is a template for a **private**
CI repository. It fetches reviewed `main` from the public source repository,
builds both artifacts, starts a disposable server, waits for RCON and the patch
load marker, then launches a disposable Prism client under Xvfb and waits for
the title probe's `INSOMNIAC_CI_TITLE_READY` marker. The private repository
may run on manual, push, or configured cron events. Containers are named
uniquely and removed after each check. No Minecraft port is published. This
tests startup with copied files; it does not test player joins, gameplay, or
CFSM's production bootstrap hooks.

## Private CI host inputs

On the host running the trusted Woodpecker Docker agent, create:

```text
/srv/insomniac-server-patches-ci/
  server/
    mods/                    required; must include Tensura_Horizons-0.31.jar
    config/                  recommended
    defaultconfigs/          optional
    kubejs/                  optional
    scripts/                 optional
    datapacks/               optional
  prism/
    accounts.json            private; launcher account entry used by CI
    prismlauncher.cfg
    assets/ libraries/ meta/ java/
    instances/Tensura by Insomniac Gaming/
  world.zip                  optional; one world with one level.dat
```

Copy the *server* `mods/` and config folders from a known-good server
snapshot. Do not put the live server's entire `/data`, backups, logs, or
secrets in this directory. `world.zip` may contain `level.dat` at its root or
inside one enclosing folder. The runner rejects traversal, symlinks, and
archives above 20 GiB uncompressed. It never writes into these inputs.

The Prism root should be a dedicated CI copy of the launcher data, not a bind
mount of a live desktop profile. Keep `accounts.json` readable only to the CI
operator and agent. The copied instance can be based on your current client
pack; its `saves/`, logs, screenshots, and launch commands are excluded from
the disposable client. The runner replaces any old InsomniacServerPatches JAR
with the just-built JAR, and adds the CI-only title probe. It does not copy
the probe into the release artifact.

Set the **private CI repository's** Woodpecker secret `prism_profile` to the
profile name shown for the account in Prism Launcher. Restrict this secret to
`push`, `manual`, and `cron` events; never enable it for pull requests. The name
itself is not a token, but the client snapshot includes private account data.
Keep the private CI repository restricted to maintainers, disable its pull
request runs, and enable trusted host mounts only there. The public source
repository receives no private secrets or host mounts. The host needs enough
RAM for the 10 GiB server and 12 GiB client container limits, plus disk for
snapshot copies and the Prism image.

## Local checks

```bash
./gradlew clean build :title-probe:build --no-daemon
python -m unittest discover -s ci -p 'test_*.py'
python ci/smoke.py server --snapshot /path/to/server-copy \
  --jar build/libs/InsomniacServerPatches-1.0.0.jar \
  --world /path/to/world.zip
docker build -f ci/Dockerfile.client -t insomniac-client-smoke:11.1.1 .
python ci/smoke.py client --prism-root /path/to/prism-copy \
  --instance-id 'Tensura by Insomniac Gaming' --profile 'CI profile name' \
  --jar build/libs/InsomniacServerPatches-1.0.0.jar \
  --probe ci/title-probe/build/libs/InsomniacTitleProbe-1.0.0.jar
```

Omit `--world` to generate a fresh test world. The server runner defaults to
NeoForge `21.1.262`, Minecraft `1.21.1`, and `itzg/minecraft-server:java21`.
Use `--neoforge`, `--java-memory`, `--container-memory`, or `--timeout` to adjust
the isolated test if the server snapshot requires it.

## GitHub Actions and private Woodpecker setup

GitHub Actions runs the public build workflow from this repository. Check the
repository's Actions tab after a push and enable Actions in repository settings
if the organization has disabled them. The downloadable workflow artifact is
the release mod JAR; the CI-only title probe is not uploaded.

After the private smoke check passes, tag the **same commit** that its
`fetch-public-source` step printed. For version 1.0.0, use `v1.0.0`. Push the
tag to start the release workflow. The tag version must match the JAR version
in `build.gradle`; the release job fails if they differ. Do not tag an untested
commit. The release job uploads only the release mod JAR and generates notes
from GitHub. No CurseForge upload is configured yet.

Create a separate private GitHub repository for the smoke runner. Copy
`ci/private-smoke.woodpecker.yml` into that repository as `.woodpecker/smoke.yml`
and enable it in `woodpecker.riftwalkerdev.com`. Disable pull request builds and restrict write
access, then let an administrator enable trusted volumes for it. Set its
`prism_profile` secret and populate the private host directory above. The bind
mount path must exist on whichever Woodpecker agent runs this private job. If
agents run on several hosts, dedicate one agent or provide the same protected
directory on each eligible host. Run a manual smoke pipeline first. A private
Woodpecker cron can then check the latest reviewed public `main` automatically;
private-repository pushes also run the smoke workflow.
