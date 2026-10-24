#!/usr/bin/env python3
"""Require a successful Woodpecker push status on a release commit."""

import json
import os
import re
import subprocess
import sys
from urllib.request import Request, urlopen


def require_successful_status(payload: dict, context: str) -> None:
    for status in payload.get("statuses", []):
        if status.get("context") == context:
            state = status.get("state")
            if state == "success":
                return
            raise ValueError(f"Woodpecker status {context} is {state!r}, not success")
    raise ValueError(f"Woodpecker status {context} is missing")


def tested_commit_for_release(commit: str, version: str) -> str:
    """A generated changelog commit inherits only its tested parent's status."""
    def git(*args: str) -> str:
        return subprocess.check_output(["git", *args], text=True).strip()

    message = git("show", "-s", "--format=%B", commit)
    if message != f"Compile changelog for v{version} [skip ci]":
        return commit
    ancestry = git("rev-list", "--parents", "-n", "1", commit).split()
    if len(ancestry) != 2:
        raise ValueError("Generated changelog commit must have exactly one parent")
    parent = ancestry[1]
    changes = [line.split("\t", 1) for line in
               git("diff", "--name-status", parent, commit).splitlines()]
    if (not changes or
            not any(status == "M" and path == "CHANGELOG.md" for status, path in changes) or
            not any(status == "D" and re.fullmatch(r"changes/[^/]+\.md", path)
                    for status, path in changes) or
            any(not ((status == "M" and path == "CHANGELOG.md") or
                     (status == "D" and re.fullmatch(r"changes/[^/]+\.md", path)))
                for status, path in changes)):
        raise ValueError("Generated changelog commit changed unexpected files")
    return parent


def main() -> int:
    if len(sys.argv) != 5:
        print("usage: release_gate.py OWNER/REPO COMMIT_SHA STATUS_CONTEXT VERSION", file=sys.stderr)
        return 2
    repo, commit, context, version = sys.argv[1:]
    token = os.environ.get("GH_TOKEN")
    if not token:
        print("GH_TOKEN is required", file=sys.stderr)
        return 2
    api = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    try:
        tested_commit = tested_commit_for_release(commit, version)
        if tested_commit != commit:
            print(f"Generated changelog commit {commit}; checking tested parent {tested_commit}")
        request = Request(
            f"{api}/repos/{repo}/commits/{tested_commit}/status",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "User-Agent": "insomniac-release-gate",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        with urlopen(request, timeout=20) as response:
            payload = json.load(response)
        require_successful_status(payload, context)
    except Exception as error:
        print(f"Release blocked: {error}", file=sys.stderr)
        return 1
    print(f"Release gate passed: {context} succeeded for {tested_commit}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
