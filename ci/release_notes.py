#!/usr/bin/env python3
"""Consume pending changes on a version bump; read the log for a tagged release."""

import argparse
import re
import subprocess
from pathlib import Path

VERSION = re.compile(r"\d+\.\d+\.\d+\Z")
HEADING = re.compile(r"^## \[(\d+\.\d+\.\d+)\](?: - [^\n]+)?[ \t]*$", re.MULTILINE)
FRAGMENT = re.compile(r"\d{2}-(.+)\.md\Z")
PROPERTY = re.compile(r"^mod_version=(\S+)$", re.MULTILINE)


def parse_version(properties: str) -> str:
    matches = PROPERTY.findall(properties)
    if len(matches) != 1 or not VERSION.fullmatch(matches[0]):
        raise ValueError("gradle.properties must contain one valid mod_version=X.Y.Z")
    return matches[0]


def read_version(root: Path) -> str:
    return parse_version((root / "gradle.properties").read_text(encoding="utf-8"))


def version_at_ref(root: Path, ref: str) -> tuple[str, bool]:
    def show(path: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(["git", "show", f"{ref}:{path}"], cwd=root,
                              text=True, capture_output=True, check=False)

    properties = show("gradle.properties")
    if properties.returncode == 0:
        if PROPERTY.search(properties.stdout):
            return parse_version(properties.stdout), False
    # The first push after adopting gradle.properties still has a static Gradle
    # version in its parent. This fallback is only for that migration commit.
    old_build = show("build.gradle")
    if old_build.returncode == 0:
        match = re.search(r"(?m)^version\s*=\s*['\"](\d+\.\d+\.\d+)['\"]", old_build.stdout)
        if match:
            return match.group(1), True
    raise ValueError(f"Cannot read the previous version at {ref}")


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
    return f"## [{version}]\n\n" + "\n\n".join(sections) + "\n"


def consume_changes(root: Path, previous_version: str, *, allow_same_version: bool = False) -> bool:
    current = read_version(root)
    if not VERSION.fullmatch(previous_version):
        raise ValueError(f"Invalid previous version {previous_version!r}")
    current_parts = tuple(map(int, current.split(".")))
    previous_parts = tuple(map(int, previous_version.split(".")))
    if current_parts < previous_parts:
        raise ValueError(f"Version decreased from {previous_version} to {current}")
    if current_parts == previous_parts and not allow_same_version:
        return False
    changelog_path = root / "CHANGELOG.md"
    existing = changelog_path.read_text(encoding="utf-8")
    if any(match.group(1) == current for match in HEADING.finditer(existing)):
        raise ValueError(f"CHANGELOG.md already contains version {current}")
    section = compile_fragments(root, current)
    changelog_path.write_text(merge_changelog(existing, current, section), encoding="utf-8")
    for path in (root / "changes").glob("*.md"):
        path.unlink()
    return True


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
    mode.add_argument("--version", help="SemVer from release tag, without leading v")
    mode.add_argument("--compile-after-ref", help="Git commit before the version-bump push")
    mode.add_argument("--preview", action="store_true", help="Preview pending fragments without consuming them")
    parser.add_argument("--output", type=Path, help="Generated GitHub Release notes file")
    args = parser.parse_args()

    root = Path(__file__).resolve().parent.parent
    if args.compile_after_ref:
        if not re.fullmatch(r"[0-9a-f]{40,64}", args.compile_after_ref):
            parser.error("--compile-after-ref must be a commit SHA")
        old_version, legacy_version = version_at_ref(root, args.compile_after_ref)
        if consume_changes(root, old_version, allow_same_version=legacy_version):
            print(f"Compiled {read_version(root)} changes into CHANGELOG.md")
        else:
            print("Version did not increase; changelog unchanged")
        return
    if not args.output:
        parser.error("--output is required with --version or --preview")
    if args.preview:
        args.output.write_text(preview_changes(root), encoding="utf-8")
        print(f"Previewed pending v{read_version(root)} changes")
        return
    validate_project_version(root, args.version)
    if list((root / "changes").glob("*.md")):
        raise ValueError("Pending change fragments remain; increment the version and compile first")
    changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")
    notes = extract_version_notes(changelog, args.version)
    args.output.write_text(notes, encoding="utf-8")
    print(f"Prepared release notes for v{args.version} from CHANGELOG.md")


if __name__ == "__main__":
    main()
