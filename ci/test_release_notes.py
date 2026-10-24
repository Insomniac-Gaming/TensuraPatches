import tempfile
import unittest
from pathlib import Path

from release_notes import (compile_fragments, consume_changes,
                           extract_version_notes, merge_changelog, preview_changes,
                           validate_project_version)


class ReleaseNotesTests(unittest.TestCase):
    CHANGELOG = """# Changelog

## [0.2.0] - 2026-10-24

### Fixed
- Old skill locks no longer override ReRebirth.

## [0.1.0] - 2026-10-23

### Added
- Initial patch.
"""

    def test_extracts_only_target_version(self):
        result = extract_version_notes(self.CHANGELOG, "0.2.0")
        self.assertIn("Old skill locks", result)
        self.assertIn("# InsomniacServerPatches v0.2.0", result)
        self.assertNotIn("Initial patch", result)

    def test_older_release_is_extractable(self):
        result = extract_version_notes(self.CHANGELOG, "0.1.0")
        self.assertIn("Initial patch", result)
        self.assertNotIn("Old skill locks", result)

    def test_missing_section_fails(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            extract_version_notes(self.CHANGELOG, "0.3.0")

    def test_duplicate_section_fails(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            extract_version_notes(self.CHANGELOG + "\n## [0.2.0]\n- duplicate\n", "0.2.0")

    def test_empty_section_fails(self):
        with self.assertRaisesRegex(ValueError, "empty"):
            extract_version_notes("## [0.2.0]\n\n## [0.1.0]\n- details", "0.2.0")

    def test_rejects_malformed_version(self):
        with self.assertRaisesRegex(ValueError, "Invalid release version"):
            extract_version_notes(self.CHANGELOG, "v0.2.0")

    def test_checks_single_project_version(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "gradle.properties").write_text("mod_version=0.2.0\n")
            validate_project_version(root, "0.2.0")
            with self.assertRaisesRegex(ValueError, "gradle.properties"):
                validate_project_version(root, "0.3.0")
            (root / "gradle.properties").write_text("mod_version=bad\n")
            with self.assertRaisesRegex(ValueError, "one valid mod_version"):
                validate_project_version(root, "0.3.0")

    def test_compiles_ordered_fragments_and_replaces_pending_section(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            folder = root / "changes"
            folder.mkdir(parents=True)
            (folder / "02-Kept.md").write_text("- Lootr remains.\n")
            (folder / "01-Removed.md").write_text("- Old quests stop.\n")
            section = compile_fragments(root, "0.2.0")
            self.assertLess(section.index("### Removed"), section.index("### Kept"))
            original = "# Changelog\n\n## [0.2.0]\n\n- Old text.\n\n## [0.1.0]\n\n- First.\n"
            # The current release is regenerated, while earlier history stays intact.
            merged = merge_changelog(original, "0.2.0", section)
            self.assertIn("- Old quests stop.", merged)
            self.assertNotIn("- Old text.", merged)
            self.assertIn("## [0.1.0]\n\n- First.", merged)
            self.assertEqual(merged, merge_changelog(merged, "0.2.0", section))

    def test_missing_or_empty_fragments_block_release(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with self.assertRaisesRegex(ValueError, "No change fragments"):
                compile_fragments(root, "0.2.0")
            folder = root / "changes"
            folder.mkdir(parents=True)
            (folder / "01-Changes.md").write_text("\n")
            with self.assertRaisesRegex(ValueError, "Empty change fragment"):
                compile_fragments(root, "0.2.0")

    def test_version_increment_consumes_changes_once(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "gradle.properties").write_text("mod_version=0.3.0\n")
            (root / "CHANGELOG.md").write_text("# Changelog\n\n## [0.2.0]\n\n- Earlier.\n")
            folder = root / "changes"
            folder.mkdir()
            (folder / "01-Added.md").write_text("- A new feature.\n")
            self.assertTrue(consume_changes(root, "0.2.0"))
            changelog = (root / "CHANGELOG.md").read_text()
            self.assertLess(changelog.index("## [0.3.0]"), changelog.index("## [0.2.0]"))
            self.assertIn("- A new feature.", changelog)
            self.assertEqual(list(folder.glob("*.md")), [])
            self.assertFalse(consume_changes(root, "0.3.0"))
            self.assertEqual(changelog, (root / "CHANGELOG.md").read_text())

    def test_version_decrease_and_duplicate_log_block_consumption(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "gradle.properties").write_text("mod_version=0.2.0\n")
            (root / "CHANGELOG.md").write_text("## [0.2.0]\n\n- Existing.\n")
            with self.assertRaisesRegex(ValueError, "decreased"):
                consume_changes(root, "0.3.0")
            with self.assertRaisesRegex(ValueError, "already contains"):
                consume_changes(root, "0.1.0")

    def test_initial_migration_compiles_pending_version_without_increment(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "gradle.properties").write_text("mod_version=0.2.0\n")
            (root / "CHANGELOG.md").write_text("# Changelog\n")
            folder = root / "changes"
            folder.mkdir()
            fragment = folder / "01-Fixed.md"
            fragment.write_text("- Restored skill locks.\n")
            self.assertIn("Restored skill locks", preview_changes(root))
            self.assertTrue(fragment.exists())
            self.assertFalse(consume_changes(root, "0.2.0"))
            self.assertTrue(consume_changes(root, "0.2.0", allow_same_version=True))
            self.assertFalse(fragment.exists())
            self.assertIn("## [0.2.0]", (root / "CHANGELOG.md").read_text())


if __name__ == "__main__":
    unittest.main()
