#!/usr/bin/env python3
"""Run disposable Minecraft server and client boot checks through Docker."""

import argparse
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time
from zipfile import ZipFile

SERVER_DIRS = ("mods", "config", "defaultconfigs", "kubejs", "scripts", "datapacks")
CLIENT_ROOT_DIRS = ("assets", "libraries", "meta", "java", "icons")
CLIENT_INSTANCE_EXCLUDE = {"saves", "logs", "screenshots", "backups", "crash-reports"}
READY_MARKER = "INSOMNIAC_CI_TITLE_READY"


def run(*args, check=True, capture=False):
    result = subprocess.run(args, check=False, text=True,
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.STDOUT if capture else None)
    if check and result.returncode:
        # subprocess.CalledProcessError includes all arguments. Docker create
        # arguments can contain the private Prism profile name.
        raise RuntimeError(f"{args[0]} {args[1]} failed (exit {result.returncode})")
    return result


def safe_copy(src: Path, dst: Path, ignore=None):
    if src.is_symlink():
        raise ValueError(f"symlink input is not allowed: {src}")
    if src.is_file():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        return
    if not src.is_dir():
        raise ValueError(f"not a regular file or directory: {src}")
    dst.mkdir(parents=True, exist_ok=True)
    for child in src.iterdir():
        if ignore and child.name in ignore:
            continue
        safe_copy(child, dst / child.name, ignore)


def extract_world(zip_path: Path, destination: Path):
    if not zip_path.is_file():
        raise ValueError(f"world ZIP does not exist: {zip_path}")
    with ZipFile(zip_path) as archive:
        files = [item for item in archive.infolist() if not item.is_dir()]
        if len(files) > 200_000 or sum(item.file_size for item in files) > 20 * 1024**3:
            raise ValueError("world ZIP exceeds the CI safety limit")
        parts = []
        for item in files:
            path = PurePosixPath(item.filename)
            if path.is_absolute() or ".." in path.parts or "\\" in item.filename:
                raise ValueError(f"unsafe world ZIP entry: {item.filename}")
            if ((item.external_attr >> 16) & 0o170000) == 0o120000:
                raise ValueError(f"symlink world ZIP entry: {item.filename}")
            parts.append(path.parts)
        roots = [p[:-1] for p in parts if p[-1:] == ("level.dat",)]
        if len(roots) != 1:
            raise ValueError("world ZIP must contain exactly one level.dat")
        prefix = roots[0]
        destination.mkdir(parents=True, exist_ok=True)
        for item, path in zip(files, parts):
            if path[:len(prefix)] != prefix:
                continue
            relative = path[len(prefix):]
            if not relative:
                continue
            target = destination.joinpath(*relative)
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(item) as source, target.open("wb") as output:
                shutil.copyfileobj(source, output)


def stage_server(snapshot: Path, jar: Path, destination: Path, world: Path | None):
    if not (snapshot / "mods").is_dir():
        raise ValueError("server snapshot must have a mods/ directory")
    if not any((snapshot / "mods").glob("Tensura_Horizons-0.31.jar")):
        raise ValueError("server snapshot is missing Tensura_Horizons-0.31.jar")
    for name in SERVER_DIRS:
        source = snapshot / name
        if source.exists():
            safe_copy(source, destination / name)
    for previous in (destination / "mods").glob("InsomniacServerPatches-*.jar"):
        previous.unlink()
    release = destination / "mods" / jar.name
    safe_copy(jar, release)
    if world:
        extract_world(world, destination / "world")
    return release


def prepare_server_permissions(destination: Path, uid=1000, gid=1000):
    """Give the disposable server's user ownership of its staged files."""
    for path in (destination, *destination.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"symlink input is not allowed: {path}")
        mode = path.stat().st_mode
        path.chmod(mode | (0o700 if path.is_dir() else 0o600))
        if (path.stat().st_uid, path.stat().st_gid) != (uid, gid):
            os.chown(path, uid, gid)


def set_config_value(path: Path, key: str, value: str):
    lines = path.read_text().splitlines()
    pattern = re.compile(r"^" + re.escape(key) + r"=")
    updated = False
    for index, line in enumerate(lines):
        if pattern.match(line):
            lines[index] = f"{key}={value}"
            updated = True
    if not updated:
        lines.append(f"{key}={value}")
    path.write_text("\n".join(lines) + "\n")


def stage_client(prism_root: Path, instance_id: str, jar: Path, probe: Path, destination: Path):
    instance = prism_root / "instances" / instance_id
    if not (instance / "minecraft" / "mods").is_dir():
        raise ValueError("Prism root must contain the selected instance and its mods/")
    for name in CLIENT_ROOT_DIRS:
        source = prism_root / name
        if source.exists():
            safe_copy(source, destination / name)
    for name in ("accounts.json", "prismlauncher.cfg"):
        source = prism_root / name
        if not source.is_file():
            raise ValueError(f"Prism root is missing {name}")
        safe_copy(source, destination / name)
    staged_instance = destination / "instances" / instance_id
    safe_copy(instance, staged_instance, CLIENT_INSTANCE_EXCLUDE)
    mods = staged_instance / "minecraft" / "mods"
    for previous in list(mods.glob("InsomniacServerPatches-*.jar")) + list(mods.glob("InsomniacTitleProbe-*.jar")):
        previous.unlink()
    safe_copy(jar, mods / jar.name)
    safe_copy(probe, mods / probe.name)
    config = staged_instance / "instance.cfg"
    for key, value in {
        "OverrideJavaLocation": "true",
        "JavaPath": "/usr/lib/jvm/java-21-openjdk-amd64/bin/java",
        "OverrideMemory": "true",
        "MaxMemAlloc": "8192",
        "OverrideCommands": "true",
        "PreLaunchCommand": "",
        "PostExitCommand": "",
        "WrapperCommand": "",
        "JoinServerOnLaunch": "false",
    }.items():
        set_config_value(config, key, value)
    return staged_instance


def docker_logs(name: str, tail=120):
    result = run("docker", "logs", "--tail", str(tail), name, check=False, capture=True)
    return result.stdout or ""


def stop_container(name: str):
    run("docker", "stop", "--time", "30", name, check=False, capture=True)
    run("docker", "rm", "-fv", name, check=False, capture=True)


def server_smoke(args):
    name = f"insomniac-server-smoke-{secrets.token_hex(5)}"
    with tempfile.TemporaryDirectory(prefix="insomniac-server-") as temporary:
        data = Path(temporary) / "data"
        stage_server(args.snapshot, args.jar, data, args.world)
        prepare_server_permissions(data)
        password = secrets.token_urlsafe(18)
        command = ["docker", "create", "--name", name, "--memory", args.container_memory,
                   "--cpus", "4", "-e", "EULA=TRUE", "-e", "TYPE=NEOFORGE",
                   "-e", "VERSION=1.21.1", "-e", f"NEOFORGE_VERSION={args.neoforge}",
                   "-e", f"MEMORY={args.java_memory}", "-e", "ENABLE_RCON=true",
                   "-e", f"RCON_PASSWORD={password}", "-e", "ONLINE_MODE=false",
                   "-e", "OVERRIDE_SERVER_PROPERTIES=true", "-e", "LEVEL=world",
                   args.image]
        try:
            run(*command)
            run("docker", "cp", "-a", str(data) + "/.", name + ":/data/")
            run("docker", "start", name)
            deadline = time.monotonic() + args.timeout
            while time.monotonic() < deadline:
                state = run("docker", "inspect", "-f", "{{.State.Status}}", name,
                            capture=True).stdout.strip()
                if state != "running":
                    raise RuntimeError(f"server container stopped with status {state}")
                probe = run("docker", "exec", name, "rcon-cli", "list", check=False, capture=True)
                if probe.returncode == 0:
                    logs = docker_logs(name, "all")
                    if "InsomniacServerPatches loaded" not in logs:
                        raise RuntimeError("server reached RCON, but patch mod load marker was absent")
                    print("PASS: Minecraft server reached RCON with InsomniacServerPatches loaded")
                    return
                time.sleep(5)
            raise TimeoutError(f"server did not reach RCON within {args.timeout}s")
        except Exception:
            print(docker_logs(name), file=sys.stderr)
            raise
        finally:
            stop_container(name)


def client_smoke(args):
    name = f"insomniac-client-smoke-{secrets.token_hex(5)}"
    with tempfile.TemporaryDirectory(prefix="insomniac-client-") as temporary:
        prism = Path(temporary) / "prism"
        stage_client(args.prism_root, args.instance_id, args.jar, args.probe, prism)
        command = ["docker", "create", "--name", name, "--memory", args.container_memory,
                   "--cpus", "4", "--shm-size", "2g",
                   "-e", f"PRISM_INSTANCE_ID={args.instance_id}",
                   "-e", f"PRISM_PROFILE={args.profile}", args.image]
        try:
            run(*command)
            run("docker", "cp", str(prism) + "/.", name + ":/prism/")
            run("docker", "start", name)
            deadline = time.monotonic() + args.timeout
            log_path = f"/prism/instances/{args.instance_id}/minecraft/logs/latest.log"
            while time.monotonic() < deadline:
                state = run("docker", "inspect", "-f", "{{.State.Status}}", name,
                            capture=True).stdout.strip()
                if state != "running":
                    raise RuntimeError(f"client container stopped with status {state}")
                found = run("docker", "exec", name, "grep", "-Fq", READY_MARKER, log_path,
                            check=False, capture=True)
                if found.returncode == 0:
                    print("PASS: Minecraft client reached the title screen")
                    return
                time.sleep(5)
            raise TimeoutError(f"client did not reach the title screen within {args.timeout}s")
        except Exception:
            # Prism output can contain account details. Minecraft's game log is
            # enough to diagnose most launch failures without exposing tokens.
            log = run("docker", "exec", name, "tail", "-n", "100",
                      f"/prism/instances/{args.instance_id}/minecraft/logs/latest.log",
                      check=False, capture=True)
            if log.returncode == 0:
                print(log.stdout, file=sys.stderr)
            else:
                print("Client failed before Minecraft wrote latest.log.", file=sys.stderr)
            raise
        finally:
            stop_container(name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="mode", required=True)
    server = subparsers.add_parser("server")
    server.add_argument("--snapshot", type=Path, required=True)
    server.add_argument("--world", type=Path)
    server.add_argument("--jar", type=Path, required=True)
    server.add_argument("--image", default="itzg/minecraft-server:java21")
    server.add_argument("--neoforge", default="21.1.262")
    server.add_argument("--java-memory", default="6G")
    server.add_argument("--container-memory", default="10g")
    server.add_argument("--timeout", type=int, default=1200)
    client = subparsers.add_parser("client")
    client.add_argument("--prism-root", type=Path, required=True)
    client.add_argument("--instance-id", required=True)
    client.add_argument("--profile", required=True)
    client.add_argument("--jar", type=Path, required=True)
    client.add_argument("--probe", type=Path, required=True)
    client.add_argument("--image", default="insomniac-client-smoke:11.1.1")
    client.add_argument("--container-memory", default="12g")
    client.add_argument("--timeout", type=int, default=1200)
    args = parser.parse_args()
    if args.mode == "server":
        server_smoke(args)
    else:
        client_smoke(args)


if __name__ == "__main__":
    main()
