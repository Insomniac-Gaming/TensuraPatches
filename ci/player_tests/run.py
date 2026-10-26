#!/usr/bin/env python3
"""Run GameTests or a Python real-player scenario in disposable containers."""

import argparse
import hashlib
import importlib
import json
from pathlib import Path
import re
import secrets
import shutil
import sys
import tempfile
import time
import urllib.request
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from smoke import prepare_server_permissions, stage_client, stage_server, stop_container  # noqa: E402
from player_tests.harness import InfrastructureFailure, PlayerSession, TestFailure, command  # noqa: E402

PILOT_URL = "https://github.com/kzheart/mc-pilot/releases/download/v0.20.2/mct-client-mod-neoforge-1.21.1.jar"
PILOT_SHA256 = "cb41cad450a9ed9e77eee0b3da40c039ec2a4ad168243679c54921a38e743cbd"
GAME_TEST_MARKERS = (
    "INSOMNIAC_GAMETEST_PASS full_reset_clears_void_spirit_contract",
    "INSOMNIAC_GAMETEST_PASS rebirth_locks_reported_skills",
)
FIXTURE_NAME = re.compile(r"^[a-z][a-z0-9_]*$")
PLAYER_NAME = re.compile(r"^[A-Za-z0-9_]{1,16}$")


def fetch_pilot(destination: Path, supplied: Path | None):
    if supplied is not None:
        shutil.copyfile(supplied, destination)
    else:
        with urllib.request.urlopen(urllib.request.Request(PILOT_URL,
                headers={"User-Agent": "InsomniacPlayerTests/1"}), timeout=60) as source:
            with destination.open("wb") as output:
                shutil.copyfileobj(source, output)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    if digest != PILOT_SHA256:
        raise InfrastructureFailure("MC Pilot NeoForge 1.21.1 JAR hash mismatch")
    return destination


def offline_uuid(name: str):
    if not PLAYER_NAME.fullmatch(name):
        raise InfrastructureFailure("Player name must be a Minecraft account name")
    value = hashlib.md5(("OfflinePlayer:" + name).encode()).digest()
    return str(uuid.UUID(bytes=value, version=3))


def load_fixture(root: Path, name: str):
    if not FIXTURE_NAME.fullmatch(name):
        raise InfrastructureFailure("Invalid fixture name")
    path = root / f"{name}.json"
    if path.is_symlink() or not path.is_file():
        raise InfrastructureFailure(f"Missing regular fixture file: {path}")
    data = json.loads(path.read_text())
    if not isinstance(data.get("setup_commands"), list) or not all(
            isinstance(item, str) for item in data["setup_commands"]):
        raise InfrastructureFailure("Fixture setup_commands must be a list of commands")
    if "player_data" in data:
        filename = data["player_data"]
        if not isinstance(filename, str) or Path(filename).name != filename or not filename.endswith(".dat"):
            raise InfrastructureFailure("Fixture player_data must name one .dat file in the fixture directory")
        player_data = root / filename
        if player_data.is_symlink() or not player_data.is_file():
            raise InfrastructureFailure(f"Missing fixture player data: {player_data}")
        data["player_data_path"] = player_data
    return data


def stage_fixture(data: dict, staged_server: Path, player_name: str | None):
    player_data = data.get("player_data_path")
    if player_data is None:
        return
    if not player_name:
        raise InfrastructureFailure("A fixture with saved player data requires --player-name")
    destination = staged_server / "world" / "playerdata" / f"{offline_uuid(player_name)}.dat"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(player_data, destination)


def wait_server(name: str, timeout: int):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        status = command("docker", "inspect", "-f", "{{.State.Status}}", name).stdout.strip()
        if status != "running":
            raise InfrastructureFailure(f"Minecraft server stopped: {status}")
        if command("docker", "exec", name, "rcon-cli", "list", check=False).returncode == 0:
            return
        time.sleep(5)
    raise InfrastructureFailure("Minecraft server did not reach RCON")


def wait_client(session: PlayerSession, timeout: int):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        status = command("docker", "inspect", "-f", "{{.State.Status}}", session.client).stdout.strip()
        if status != "running":
            raise InfrastructureFailure(f"Minecraft client stopped: {status}")
        try:
            session.rpc("screen.size", timeout=8)
            if session.screen().get("screen", "").endswith("TitleScreen"):
                return
        except InfrastructureFailure:
            pass
        time.sleep(5)
    raise InfrastructureFailure("Minecraft client and MC Pilot did not reach the title screen")


def wait_join(session: PlayerSession, timeout: int):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            snapshot = session.screen()
            if (snapshot.get("playerPresent") and
                    (snapshot.get("screen") is None or
                     snapshot["screen"].endswith("GachaStartScreen"))):
                return session.connected_player()
        except InfrastructureFailure:
            pass
        time.sleep(2)
    raise InfrastructureFailure("Real Minecraft client did not join the disposable server")


def execute(args):
    token = secrets.token_hex(5)
    network = f"insomniac-player-tests-{token}"
    server = f"insomniac-player-server-{token}"
    client = f"insomniac-player-client-{token}"
    artifacts = args.artifacts.resolve()
    artifacts.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="insomniac-player-tests-") as temporary:
        temporary = Path(temporary)
        staged_server = temporary / "server"
        stage_server(args.snapshot, args.jar, staged_server, args.world)
        fixture = None
        if args.mode in ("client", "all"):
            scenario = importlib.import_module(f"player_tests.scenarios.{args.scenario}")
            fixture = load_fixture(args.fixture_root, scenario.FIXTURE)
            stage_fixture(fixture, staged_server, args.player_name)
        shutil.copyfile(args.test_mod, staged_server / "mods" / args.test_mod.name)
        prepare_server_permissions(staged_server)
        session = None
        try:
            command("docker", "network", "create", network)
            command("docker", "create", "--name", server, "--network", network,
                    "--network-alias", "test-server", "--memory", "10g", "--cpus", "4",
                    "-e", "EULA=TRUE", "-e", "TYPE=NEOFORGE", "-e", "VERSION=1.21.1",
                    "-e", "NEOFORGE_VERSION=21.1.262", "-e", "MEMORY=6G",
                    "-e", "ENABLE_RCON=true", "-e", "ONLINE_MODE=false",
                    "-e", "RCON_PASSWORD=" + secrets.token_urlsafe(18),
                    "-e", "OVERRIDE_SERVER_PROPERTIES=true", "-e", "LEVEL=world",
                    "-e", "JVM_DD_OPTS=" + ("insomniac.playerTests.expectVoidContractAfterReset=true"
                                            if args.game_test_negative_control else ""),
                    args.server_image)
            command("docker", "cp", "-a", str(staged_server) + "/.", server + ":/data/")
            command("docker", "start", server)
            wait_server(server, args.timeout)

            if args.mode == "rebirth-gametest":
                result = command("docker", "exec", server, "rcon-cli", "test", "run",
                                 "rebirthlocksreportedskills", check=False)
                if result.returncode or "No such test" in result.stdout:
                    raise InfrastructureFailure(f"Rebirth GameTest was unavailable: {result.stdout[-1000:]}")
                deadline = time.monotonic() + 90
                while time.monotonic() < deadline:
                    logs = command("docker", "logs", server, check=False).stdout
                    if GAME_TEST_MARKERS[1] in logs:
                        print("PASS: in-engine ReRebirth lock GameTest")
                        return
                    if "rebirthlocksreportedskills failed" in logs.lower():
                        raise TestFailure("Rebirth lock GameTest failed; see server log")
                    time.sleep(2)
                raise TestFailure("Rebirth lock GameTest did not report success")

            pilot = fetch_pilot(temporary / "mct-client-mod-neoforge-1.21.1.jar", args.pilot_jar)
            staged_client_root = temporary / "prism"
            instance = stage_client(args.prism_root, args.instance_id, args.jar, args.probe, staged_client_root)
            shutil.copyfile(pilot, instance / "minecraft" / "mods" / pilot.name)
            command("docker", "create", "--name", client, "--network", network,
                    "--memory", "12g", "--cpus", "4", "--shm-size", "2g",
                    "-e", "PRISM_INSTANCE_ID=" + args.instance_id,
                    "-e", "PRISM_PROFILE=" + args.profile,
                    "-e", "MCT_CLIENT_WS_PORT=25560", args.client_image)
            command("docker", "cp", str(staged_client_root) + "/.", client + ":/prism/")
            command("docker", "start", client)
            minecraft_dir = f"/prism/instances/{args.instance_id}/minecraft"
            session = PlayerSession(server, client, minecraft_dir, artifacts / args.scenario)
            wait_client(session, args.timeout)
            session.rpc("client.reconnect", {"address": "test-server:25565"})
            player = wait_join(session, args.timeout)
            if args.player_name and player != args.player_name:
                raise InfrastructureFailure("Joined player does not match fixture --player-name")
            if fixture is not None:
                for setup in fixture["setup_commands"]:
                    session.server_command(setup.replace("{player}", player))

            if args.mode == "gametest":
                result = command("docker", "exec", server, "rcon-cli", "test", "runall", check=False)
                if result.returncode or "Unknown or incomplete command" in result.stdout:
                    raise InfrastructureFailure(f"GameTest command was unavailable: {result.stdout[-1000:]}")
                deadline = time.monotonic() + 90
                while time.monotonic() < deadline:
                    logs = command("docker", "logs", server, check=False).stdout
                    if all(marker in logs for marker in GAME_TEST_MARKERS):
                        print("PASS: in-engine player GameTests")
                        break
                    if any(name in logs.lower() for name in (
                            "fullresetclearsvoidspiritcontract failed",
                            "rebirthlocksreportedskills failed")):
                        raise TestFailure("Player GameTest failed; see server log")
                    time.sleep(2)
                else:
                    if "INSOMNIAC_GAMETEST_START full_reset_clears_void_spirit_contract" not in logs:
                        raise InfrastructureFailure("GameTest was registered but its template/test method did not start")
                    raise TestFailure("Player GameTests did not all report success")
            else:
                scenario.run(session)
                print(f"PASS: real-client scenario {args.scenario}")
        except Exception:
            if session is not None:
                session.collect_failure()
            else:
                (artifacts / "server.log").write_text(
                    command("docker", "logs", "--tail", "250", server, check=False).stdout)
            raise
        finally:
            stop_container(client)
            stop_container(server)
            command("docker", "network", "rm", network, check=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("gametest", "client", "all", "rebirth-gametest"))
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--world", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--test-mod", type=Path, required=True)
    parser.add_argument("--prism-root", type=Path)
    parser.add_argument("--instance-id")
    parser.add_argument("--profile")
    parser.add_argument("--probe", type=Path)
    parser.add_argument("--pilot-jar", type=Path)
    parser.add_argument("--scenario", default="slimethrone_ui")
    parser.add_argument("--fixture-root", type=Path,
                        default=Path(__file__).parent / "fixtures")
    parser.add_argument("--player-name")
    parser.add_argument("--artifacts", type=Path, default=Path("ci-artifacts/player-tests"))
    parser.add_argument("--server-image", default="itzg/minecraft-server:java21")
    parser.add_argument("--client-image", default="insomniac-client-smoke:11.1.1")
    parser.add_argument("--timeout", type=int, default=1200)
    parser.add_argument("--game-test-negative-control", action="store_true",
                        help="Invert the Void Spirit assertion to verify GameTest failure reporting")
    args = parser.parse_args()
    if args.mode != "rebirth-gametest" and not all((args.prism_root, args.instance_id, args.profile, args.probe)):
        parser.error("all modes require --prism-root, --instance-id, --profile and --probe")
    try:
        if args.mode == "all":
            # Each layer gets a fresh copy of the fixture/world; GameTest's
            # structure and mock player never carry into the real-client case.
            for mode in ("gametest", "client"):
                execute(argparse.Namespace(**{**vars(args), "mode": mode}))
        else:
            execute(args)
    except TestFailure as exception:
        print(f"FAIL: {exception}; artifacts: {args.artifacts}", file=sys.stderr)
        sys.exit(1)
    except Exception as exception:
        print(f"INFRASTRUCTURE ERROR: {exception}; artifacts: {args.artifacts}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
