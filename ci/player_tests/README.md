# Player tests

This suite runs against disposable copies of the server and Prism modpack. It
never writes to the protected CI snapshot or the live server. The release mod
does not contain either test mod.

## Layers

- `ci/player-test-mod` is a NeoForge 1.21.1 GameTest mod. Add annotated Java
  methods to `PlayerStateGameTests` and structure templates under
  `data/insomniac_player_tests/structure/`. The first test uses the connected
  disposable `ServerPlayer` session, seeds its Void Spirit contract, invokes
  the real full reset method, and checks the persistent result. `make_empty_template.py`
  reproduces the shared one-block template.
  NeoForge disables automatic GameTest registration in production jars, so the
  disposable test mod registers its annotated tests, `/test` command, and test
  ticker there.
  `rebirthLocksReportedSkills` seeds a disposable player at rebirth 3 with
  Gluttony, Starved, and Raphael owned, then checks ReRebirth's lock methods,
  the two-slot limit, unlocking, and the stored locks. It then uses Tensura's
  skill reset path and checks that locked skills remain owned while an unlocked
  skill is removed. It uses no copied player data.
- `scenarios/*.py` are real-client cases. Each declares `NAME` and `FIXTURE`
  and implements `run(session)`. The shared `PlayerSession` provides MC Pilot
  actions, server and player commands, screen/widget observations, bounded
  waits, log assertions, and screenshots.
- `fixtures/<name>.json` selects setup commands and can name a synthetic saved
  `<name>.dat` file in the same directory. Saved data is installed in the
  disposable world's `playerdata/` before server start. Pass `--player-name`
  when using saved data so the correct offline UUID is chosen. The `fresh`
  fixture needs no saved data. Setup commands may contain `{player}` and run
  only after that player joins.

The CI-only title probe writes `insomniac-ci-screen.json` with the current
screen class and visible vanilla widget tree. A test asserting a control is
absent fails if the probe cannot see any widgets. Screens that draw custom
controls outside that tree need a dedicated observer or an explicit screenshot
baseline; an empty widget list is never proof of absence.

## Run locally

Build the three mods and the client image:

```bash
./gradlew clean build :title-probe:build :player-test-mod:build --no-daemon
docker build -f ci/Dockerfile.client -t insomniac-client-smoke:11.1.1 .
python3 -m unittest discover -s ci -p 'test_*.py'
```

Then run the GameTest alone, or run it followed by the real-client scenario in
separate disposable servers, each staged from the same clean source:

```bash
python3 ci/player_tests/run.py all \
  --snapshot /path/to/private/server-copy \
  --prism-root /path/to/private/prism-copy \
  --instance-id 'Tensura by Insomniac Gaming' \
  --profile 'CI profile name' \
  --jar build/libs/InsomniacServerPatches-0.2.1.jar \
  --test-mod ci/player-test-mod/build/libs/InsomniacPlayerTests-1.0.0.jar \
  --probe ci/title-probe/build/libs/InsomniacTitleProbe-1.0.0.jar
```

Use `gametest` in place of `all` to run only the Java test. It still launches a
real test client because this pack rejects custom payloads sent to Minecraft's
embedded mock player. Use `--world` for a disposable copy of a test world.
For the ReRebirth case alone, run `rebirth-gametest` with `--snapshot`, `--jar`,
and `--test-mod`; it does not require a Prism client. The real-client command
case is `client --scenario rebirth_locks` with the full arguments above.
The runner downloads exactly the
MC Pilot v0.20.2 NeoForge 1.21.1 client JAR and checks its pinned SHA-256;
`--pilot-jar` uses an already downloaded copy with the same original hash.
After verification the *disposable copy* is patched to retain only the seven
client mixins required for WebSocket command dispatch, input and GUI controls.
The upstream JAR is not modified. All gameplay checks, real-client connection,
and screenshot requirements remain strict. This compatibility filter was derived
from isolated NeoForge 1.21.1 real-client tests: full upstream MC Pilot fails to
join with a Moonlight/registry configuration-phase error, while the no-mixin,
tick-only and seven-GUI-mixin variants join. Do not remove the filter or change
MC Pilot versions without rerunning the full real-client tests. It is added
only to the staged client. The player-test mod is added only to the staged
server. MC Pilot's control socket remains on loopback inside the client
container; the outer runner reaches it through `docker exec`.
For a failure-path check, run `gametest --game-test-negative-control`: it
deliberately expects the Void Spirit contract to survive the reset and must
exit with a gameplay assertion failure.

Cases return exit 0 on pass, 1 on a gameplay assertion, and 2 on missing
inputs or a broken test environment. Failure screenshots and the tail of both
logs go to `ci-artifacts/player-tests/`. `screenshot(name, baseline=...)`
optionally compares a captured PNG against a baseline with a per-channel
`tolerance`; by default it only saves the image.

The first client case closes ReReroll's first-login screen on the disposable
account, uses Tensura's default `B` binding, checks the prestige
tab on the main screen and the skill-lock control on the ability screen, then invokes a
test-only server command to open the retained Imitator menu. The command calls
SlimeThrone's actual `ImitatorSkill.onPressed` menu path without granting a
permanent skill to the disposable account. It requires the private modpack's
screen implementations to match the pinned test version. Run locally against
that pack before treating a Woodpecker result as verified.
