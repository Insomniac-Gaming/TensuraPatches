#!/usr/bin/env python3
"""Generate people-friendly GitHub Release notes from CHANGELOG.md.

Run on a version tag. Refuse to release when the changelog, Gradle version,
and NeoForge metadata disagree.
"""

import argparse
import re
from pathlib import Path

VERSION = re.compile(r"\d+\.\d+\.\d+\Z")
HEADING = re.compile(r"^## \[(\d+\.\d+\.\d+)\](?: - [^\n]+)?\s*$", re.MULTILINE)


def extract_version_notes(changelog: str, version: str) -> str:
    if not VERSION.fullmatch(version):
        raise ValueError(f"Invalid release version {version!r}; expected MAJOR.MINOR.PATCH")
    matches = list(HEADING.finditer(changelog))
    matching = [(i, match) for i, match in enumerate(matches) if match.group(1) == version]
    if len(matching) != 1:
        raise ValueError(f"Expected exactly one ## [{version}] section in CHANGELOG.md, got {len(matching)}")
    index, match = matching[0]
    end = matches[index + 1].start() if index + 1 < len(matches) else len(changelog)
    body = changelog[match.end():end].strip()
    if not body:
        raise ValueError(f"Changelog section for {version} is empty")
    return f"# InsomniacServerPatches v{version}\n\n{body}\n"


def validate_project_version(root: Path, version: str) -> None:
    for relative, regex in (
        ("build.gradle", r"(?m)^version\s*=\s*['\"]([^'\"]+)['\"]"),
        ("src/main/resources/META-INF/neoforge.mods.toml", r"(?m)^version\s*=\s*['\"]([^'\"]+)['\"]"),
    ):
        path = root / relative
        found = re.search(regex, path.read_text(encoding="utf-8"))
        if not found or found.group(1) != version:
            raise ValueError(f"{relative} version does not match release tag v{version}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="SemVer from tag, without leading v")
    parser.add_argument("--output", type=Path, required=True, help="Generated GitHub Release notes file")
    args = parser.parse_args()

    root = Path(__file__).resolve().parent.parent
    validate_project_version(root, args.version)
    notes = extract_version_notes((root / "CHANGELOG.md").read_text(encoding="utf-8"), args.version)
    args.output.write_text(notes, encoding="utf-8")
    print(f"Prepared release notes for v{args.version} from CHANGELOG.md")


if __name__ == "__main__":
    main()
