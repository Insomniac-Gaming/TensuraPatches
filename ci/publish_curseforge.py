#!/usr/bin/env python3
"""Preflight and publish the tagged release JAR to CurseForge."""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from urllib.request import Request, urlopen

API = "https://minecraft.curseforge.com/api"
VERSION_NAMES = ("1.21.1", "NeoForge", "Java 21", "Client", "Server")
RELATIONS = (
    ("tensura-reincarnated", 643695, "requiredDependency"),
    ("tensura-horizons", 1610035, "requiredDependency"),
    ("tensura-slimethrone-extras", 1249067, "optionalDependency"),
    ("tensura-mysticism", 1148149, "optionalDependency"),
    ("foxablaze-ultimate", 1540951, "optionalDependency"),
)
MARKER = re.compile(r"<!-- curseforge-upload project=(\d+) version=([0-9.]+) status=(pending|file:\d+) -->")


def settings(version: str) -> tuple[str, str, Path, Path]:
    project = os.environ.get("CURSEFORGE_PROJECT_ID", "").strip()
    token = os.environ.get("CURSEFORGE_TOKEN", "").strip()
    if not project.isdecimal() or not token:
        raise ValueError("CURSEFORGE_PROJECT_ID (numeric) and CURSEFORGE_TOKEN are required")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("release version must be X.Y.Z")
    jar = Path(f"build/libs/InsomniacServerPatches-{version}.jar")
    notes = Path("release-notes.md")
    if not jar.is_file() or not notes.is_file():
        raise ValueError("release JAR and release-notes.md must exist")
    return project, token, jar, notes


def check_versions(token: str) -> None:
    request = Request(f"{API}/game/versions", headers={"X-Api-Token": token})
    with urlopen(request, timeout=30) as response:
        versions = json.load(response)
    names = {entry["name"] for entry in versions}
    missing = set(VERSION_NAMES) - names
    if missing:
        raise ValueError(f"CurseForge does not list required game version names: {', '.join(sorted(missing))}")


def metadata(version: str, notes: str) -> dict:
    return {
        "changelog": notes,
        "changelogType": "markdown",
        "displayName": f"InsomniacServerPatches v{version}",
        "gameVersionNames": list(VERSION_NAMES),
        "releaseType": "release",
        "isMarkedForManualRelease": False,
        "relations": {"projects": [
            {"slug": slug, "projectID": project_id, "type": kind}
            for slug, project_id, kind in RELATIONS
        ]},
    }


def release_body(tag: str, repo: str) -> str:
    result = subprocess.run(
        ["gh", "release", "view", tag, "--repo", repo, "--json", "body", "--jq", ".body"],
        check=True, capture_output=True, text=True,
    )
    return result.stdout.rstrip("\n")


def marker_state(body: str, project: str, version: str) -> str | None:
    markers = MARKER.findall(body)
    if len(markers) > 1:
        raise ValueError("GitHub Release has multiple CurseForge upload markers")
    if not markers:
        return None
    marked_project, marked_version, state = markers[0]
    if marked_project != project or marked_version != version:
        raise ValueError("GitHub Release upload marker refers to a different project or version")
    return state


def edit_release(tag: str, repo: str, body: str) -> None:
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".md") as file:
        file.write(body + "\n")
        file.flush()
        subprocess.run(["gh", "release", "edit", tag, "--repo", repo,
                        "--notes-file", file.name], check=True)


def upload(project: str, token: str, jar: Path, payload: dict) -> int:
    result = subprocess.run(
        ["curl", "--fail-with-body", "--silent", "--show-error", "--max-time", "180",
         "--write-out", "\n%{http_code}",
         "--header", f"X-Api-Token: {token}",
         "--form-string", f"metadata={json.dumps(payload, separators=(',', ':'))}",
         "--form", f"file=@{jar}", f"{API}/projects/{project}/upload-file"],
        capture_output=True, text=True,
    )
    body, separator, status = result.stdout.rpartition("\n")
    if result.returncode:
        detail = body.strip().replace(token, "[redacted]")[:500]
        raise RuntimeError(f"CurseForge upload failed (HTTP {status if separator else 'unknown'}): {detail}")
    response = json.loads(body)
    file_id = response.get("id")
    if not isinstance(file_id, int) or file_id <= 0:
        raise RuntimeError("CurseForge response did not contain a valid file ID; inspect the project")
    return file_id


def publish(version: str, repo: str) -> None:
    project, token, jar, notes_path = settings(version)
    tag = f"v{version}"
    body = release_body(tag, repo)
    state = marker_state(body, project, version)
    if state == "pending":
        raise ValueError("A previous upload may have succeeded. Inspect CurseForge before retrying")
    if state is not None:
        print(f"CurseForge upload already recorded: {state}")
        return
    pending = f"<!-- curseforge-upload project={project} version={version} status=pending -->"
    edit_release(tag, repo, body.rstrip() + "\n\n" + pending)
    file_id = upload(project, token, jar, metadata(version, notes_path.read_text(encoding="utf-8")))
    completed = pending.replace("status=pending", f"status=file:{file_id}")
    edit_release(tag, repo, body.rstrip() + "\n\n" + completed)
    print(f"CurseForge accepted file {file_id}; publication is pending moderation")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("preflight", "upload"))
    parser.add_argument("version")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", ""))
    args = parser.parse_args()
    try:
        project, token, _, _ = settings(args.version)
        check_versions(token)
        if args.mode == "preflight":
            print("CurseForge preflight passed")
        else:
            if not args.repo:
                raise ValueError("--repo or GITHUB_REPOSITORY is required")
            publish(args.version, args.repo)
    except Exception as error:
        print(f"CurseForge publication blocked: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
