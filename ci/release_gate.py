#!/usr/bin/env python3
"""Require a successful Woodpecker push status on a release commit."""

import json
import os
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


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: release_gate.py OWNER/REPO COMMIT_SHA STATUS_CONTEXT", file=sys.stderr)
        return 2
    repo, commit, context = sys.argv[1:]
    token = os.environ.get("GH_TOKEN")
    if not token:
        print("GH_TOKEN is required", file=sys.stderr)
        return 2
    api = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    request = Request(
        f"{api}/repos/{repo}/commits/{commit}/status",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "User-Agent": "insomniac-release-gate",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urlopen(request, timeout=20) as response:
            payload = json.load(response)
        require_successful_status(payload, context)
    except Exception as error:
        print(f"Release blocked: {error}", file=sys.stderr)
        return 1
    print(f"Release gate passed: {context} succeeded for {commit}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
