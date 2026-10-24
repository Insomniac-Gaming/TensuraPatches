import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from release_gate import require_successful_status, tested_commit_for_release


class ReleaseGateTests(unittest.TestCase):
    CONTEXT = "ci/woodpecker/push/tests"

    def test_accepts_successful_push_status(self):
        require_successful_status({"statuses": [
            {"context": "ci/woodpecker/pull_request/tests", "state": "failure"},
            {"context": self.CONTEXT, "state": "success"},
        ]}, self.CONTEXT)

    def test_rejects_missing_status(self):
        with self.assertRaisesRegex(ValueError, "missing"):
            require_successful_status({"statuses": []}, self.CONTEXT)

    def test_rejects_pending_status(self):
        with self.assertRaisesRegex(ValueError, "pending"):
            require_successful_status({"statuses": [
                {"context": self.CONTEXT, "state": "pending"},
            ]}, self.CONTEXT)

    def test_rejects_failed_status(self):
        with self.assertRaisesRegex(ValueError, "failure"):
            require_successful_status({"statuses": [
                {"context": self.CONTEXT, "state": "failure"},
            ]}, self.CONTEXT)

    def test_generated_log_commit_uses_tested_parent_only_for_expected_diff(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, text=True).strip()
            git("init", "-q")
            git("config", "user.name", "Test")
            git("config", "user.email", "test@example.invalid")
            (root / "CHANGELOG.md").write_text("# Changelog\n")
            (root / "changes").mkdir()
            (root / "changes/01-Added.md").write_text("- New\n")
            git("add", ".")
            git("commit", "-qm", "Tested version bump")
            parent = git("rev-parse", "HEAD")
            (root / "CHANGELOG.md").write_text("# Changelog\n\n## [0.3.0]\n- New\n")
            (root / "changes/01-Added.md").unlink()
            git("add", "-A")
            git("commit", "-qm", "Compile changelog for v0.3.0 [skip ci]")
            generated = git("rev-parse", "HEAD")
            previous = Path.cwd()
            try:
                os.chdir(root)
                self.assertEqual(tested_commit_for_release(generated, "0.3.0"), parent)
                self.assertEqual(tested_commit_for_release(generated, "0.4.0"), generated)
                (root / "src.txt").write_text("unexpected\n")
                git("add", ".")
                git("commit", "-qm", "Compile changelog for v0.3.0 [skip ci]")
                with self.assertRaisesRegex(ValueError, "unexpected files"):
                    tested_commit_for_release(git("rev-parse", "HEAD"), "0.3.0")
            finally:
                os.chdir(previous)


if __name__ == "__main__":
    unittest.main()
