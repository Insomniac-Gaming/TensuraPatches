import unittest
from pathlib import Path
from unittest.mock import patch

from publish_curseforge import MARKER, RELATIONS, VERSION_NAMES, marker_state, metadata, publish


class CurseForgePublishTests(unittest.TestCase):
    def test_metadata_uses_release_notes_and_exact_relations(self):
        result = metadata("0.2.3", "# Release\n\n**Compatible client version:** 0.2.0\n")
        self.assertEqual(result["gameVersionNames"], list(VERSION_NAMES))
        self.assertEqual(result["changelogType"], "markdown")
        self.assertIn("Compatible client version", result["changelog"])
        self.assertEqual(result["releaseType"], "release")
        self.assertFalse(result["isMarkedForManualRelease"])
        projects = result["relations"]["projects"]
        self.assertEqual(len(projects), len(RELATIONS))
        self.assertTrue(all(isinstance(p["projectID"], int) for p in projects))
        self.assertEqual([p["type"] for p in projects].count("requiredDependency"), 2)
        self.assertEqual([p["type"] for p in projects].count("optionalDependency"), 3)

    def test_upload_marker_blocks_unknown_outcome_and_skips_known_file(self):
        self.assertIsNone(marker_state("Release notes", "123", "0.2.3"))
        pending = "<!-- curseforge-upload project=123 version=0.2.3 status=pending -->"
        completed = "<!-- curseforge-upload project=123 version=0.2.3 status=file:456 -->"
        self.assertEqual(marker_state(pending, "123", "0.2.3"), "pending")
        self.assertEqual(marker_state(completed, "123", "0.2.3"), "file:456")
        with self.assertRaisesRegex(ValueError, "different project"):
            marker_state(pending, "789", "0.2.3")
        with self.assertRaisesRegex(ValueError, "multiple"):
            marker_state(pending + completed, "123", "0.2.3")
        self.assertIsNotNone(MARKER.search(completed))

    def test_rerun_never_uploads_after_an_uncertain_attempt(self):
        pending = "<!-- curseforge-upload project=123 version=0.2.3 status=pending -->"
        with patch("publish_curseforge.settings", return_value=(
                "123", "token", Path("release.jar"), Path("release-notes.md"))), \
             patch("publish_curseforge.release_body", return_value=pending), \
             patch("publish_curseforge.upload") as send:
            with self.assertRaisesRegex(ValueError, "previous upload may have succeeded"):
                publish("0.2.3", "owner/repo")
            send.assert_not_called()


if __name__ == "__main__":
    unittest.main()
