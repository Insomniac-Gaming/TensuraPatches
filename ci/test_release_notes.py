import tempfile
import unittest
from pathlib import Path

from release_notes import extract_version_notes, validate_project_version


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

    def test_checks_gradle_and_mod_metadata(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root / "src/main/resources/META-INF/neoforge.mods.toml"
            manifest.parent.mkdir(parents=True)
            (root / "build.gradle").write_text("version = '0.2.0'\n")
            manifest.write_text('modId="example"\nversion="0.2.0"\n')
            validate_project_version(root, "0.2.0")
            with self.assertRaisesRegex(ValueError, "build.gradle"):
                validate_project_version(root, "0.3.0")
            (root / "build.gradle").write_text("version = '0.3.0'\n")
            with self.assertRaisesRegex(ValueError, "neoforge.mods.toml"):
                validate_project_version(root, "0.3.0")


if __name__ == "__main__":
    unittest.main()
