import unittest

from release_gate import require_successful_status


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


if __name__ == "__main__":
    unittest.main()
