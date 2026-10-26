#!/usr/bin/env python3
"""Compile pending changes during a tagged release."""

import argparse
import re
from pathlib import Path

VERSION = re.compile(r"\d+\.\d+\.\d+\Z")
HEADING = re.compile(r"^## \[(\d+\.\d+\.\d+)\](?: - [^\n]+)?[ \t]*$", re.MULTILINE)
FRAGMENT = re.compile(r"\d{2}-(.+)\.md\Z")
PROPERTY = re.compile(r"^mod_version=(\S+)$", re.MULTILINE)
CLIENT_PROPERTY = re.compile(r"^compatible_client_version=(\S+)$", re.MULTILINE)


def parse_version(properties: str) -> str:
    matches = PROPERTY.findall(properties)
    if len(matches) != 1 or not VERSION.fullmatch(matches[0]):
        raise ValueError("gradle.properties must contain one valid mod_version=X.Y.Z")
    return matches[0]


def read_version(root: Path) -> str:
    return parse_version((root / "gradle.properties").read_text(encoding="utf-8"))


def read_client_version(root: Path) -> str:
    properties = (root / "gradle.properties").read_text(encoding="utf-8")
    matches = CLIENT_PROPERTY.findall(properties)
    if len(matches) != 1 or not VERSION.fullmatch(matches[0]):
        raise ValueError("gradle.properties must contain one valid compatible_client_version=X.Y.Z")
    return matches[0]


def compile_fragments(root: Path, version: str) -> str:
    if not VERSION.fullmatch(version):
        raise ValueError(f"Invalid release version {version!r}; expected MAJOR.MINOR.PATCH")
    folder = root / "changes"
    files = sorted(folder.glob("*.md"))
    if not files:
        raise ValueError(f"No change fragments found in {folder}")
    sections = []
    for path in files:
        match = FRAGMENT.fullmatch(path.name)
        if not match:
            raise ValueError(f"Invalid change fragment filename: {path.name}")
        body = path.read_text(encoding="utf-8").strip()
        if not body:
            raise ValueError(f"Empty change fragment: {path.name}")
        sections.append(f"### {match.group(1)}\n\n{body}")
    client_version = read_client_version(root)
    return (f"## [{version}]\n\n**Compatible client version:** {client_version}\n\n"
            + "\n\n".join(sections) + "\n")


def compile_release(root: Path, version: str) -> str:
    validate_project_version(root, version)
    changelog_path = root / "CHANGELOG.md"
    existing = changelog_path.read_text(encoding="utf-8")
    if any(match.group(1) == version for match in HEADING.finditer(existing)):
        raise ValueError(f"CHANGELOG.md already contains version {version}")
    section = compile_fragments(root, version)
    compiled = merge_changelog(existing, version, section)
    notes = extract_version_notes(compiled, version)
    changelog_path.write_text(compiled, encoding="utf-8")
    for path in (root / "changes").glob("*.md"):
        path.unlink()
    return notes


def preview_changes(root: Path) -> str:
    version = read_version(root)
    changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")
    compiled = merge_changelog(changelog, version, compile_fragments(root, version))
    return extract_version_notes(compiled, version)


def merge_changelog(changelog: str, version: str, section: str) -> str:
    matches = list(HEADING.finditer(changelog))
    matching = [(i, match) for i, match in enumerate(matches) if match.group(1) == version]
    if len(matching) > 1:
        raise ValueError(f"Duplicate ## [{version}] sections in CHANGELOG.md")
    if matching:
        index, match = matching[0]
        end = matches[index + 1].start() if index + 1 < len(matches) else len(changelog)
        before = changelog[:match.start()].rstrip()
        after = changelog[end:].lstrip()
        return before + "\n\n" + section.rstrip() + ("\n\n" + after if after else "\n")
    if matches:
        before = changelog[:matches[0].start()].rstrip()
        after = changelog[matches[0].start():].lstrip()
        return before + "\n\n" + section.rstrip() + "\n\n" + after
    return changelog.rstrip() + "\n\n" + section


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
    if read_version(root) != version:
        raise ValueError(f"gradle.properties version does not match release tag v{version}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--release", help="SemVer from release tag, without leading v")
    mode.add_argument("--preview", action="store_true", help="Preview pending fragments without consuming them")
    parser.add_argument("--output", type=Path, help="Generated GitHub Release notes file")
    args = parser.parse_args()

    root = Path(__file__).resolve().parent.parent
    if not args.output:
        parser.error("--output is required")
    if args.preview:
        args.output.write_text(preview_changes(root), encoding="utf-8")
        print(f"Previewed pending v{read_version(root)} changes")
        return
    notes = compile_release(root, args.release)
    args.output.write_text(notes, encoding="utf-8")
    print(f"Compiled changelog and release notes for v{args.release}")


if __name__ == "__main__":
    main()
