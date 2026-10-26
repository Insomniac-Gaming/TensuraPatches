import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from player_tests.harness import InfrastructureFailure, PlayerSession, TestFailure
from player_tests.run import load_fixture, offline_uuid, stage_fixture, wait_join


class PlayerTestFrameworkTests(unittest.TestCase):
    def test_offline_uuid_matches_minecraft_name_uuid(self):
        self.assertEqual(offline_uuid("Notch"), "b50ad385-829d-3141-a216-7e7d7539ba7f")

    def test_fixture_rejects_missing_saved_state_before_server_launch(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "test.json").write_text(json.dumps({
                "setup_commands": [], "player_data": "missing.dat"}))
            with self.assertRaisesRegex(InfrastructureFailure, "Missing fixture player data"):
                load_fixture(root, "test")

    def test_fixture_stages_copy_for_disposable_offline_account(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "player.dat"
            source.write_bytes(b"synthetic-player")
            stage_fixture({"player_data_path": source}, root / "stage", "TestPlayer")
            target = root / "stage/world/playerdata" / f"{offline_uuid('TestPlayer')}.dat"
            self.assertEqual(target.read_bytes(), b"synthetic-player")
            self.assertEqual(source.read_bytes(), b"synthetic-player")

    def test_absence_check_fails_closed_when_probe_cannot_see_controls(self):
        with self.assertRaisesRegex(TestFailure, "no widgets"):
            PlayerSession.assert_widgets_absent({"widgets": []}, ("prestige",))
        with self.assertRaisesRegex(TestFailure, "Removed control"):
            PlayerSession.assert_widgets_absent({"widgets": [
                {"visible": True, "class": "Button", "text": "Prestige"}]}, ("prestige",))
        with self.assertRaisesRegex(TestFailure, "Removed control"):
            PlayerSession.assert_widgets_absent({"widgets": [
                {"visible": True, "class": "SimpleButton", "text": "",
                 "tooltip": "Skill Lock", "texture": "stextras:textures/gui/skill/skill_lock_button.png"}]},
                ("skill lock",))
        with self.assertRaisesRegex(TestFailure, "Removed control"):
            PlayerSession.assert_widgets_absent({"widgets": [
                {"visible": True, "class": "SimpleButton", "text": "",
                 "tooltip": "", "texture": "stextras:textures/gui/skill/skill_lock_button.png"}]},
                ("skill lock",))
        PlayerSession.assert_widgets_absent({"widgets": [
            {"visible": True, "class": "Button", "text": "Abilities"}]}, ("prestige",))

    def test_rpc_error_is_infrastructure_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            session = PlayerSession("server", "client", "/minecraft", Path(temporary))
            with patch("player_tests.harness.command") as fake:
                fake.return_value.returncode = 2
                fake.return_value.stdout = "INVALID_ACTION"
                with self.assertRaisesRegex(InfrastructureFailure, "INVALID_ACTION"):
                    session.rpc("screen.size")

    def test_server_command_passes_words_to_rcon_cli(self):
        with tempfile.TemporaryDirectory() as temporary:
            session = PlayerSession("server", "client", "/minecraft", Path(temporary))
            with patch("player_tests.harness.command") as docker:
                docker.return_value.returncode = 0
                docker.return_value.stdout = "INSOMNIAC_TESTKIT_IMITATOR_OPEN TestPlayer"
                session.server_command("insomniac_testkit open_imitator TestPlayer")
                docker.assert_called_once_with("docker", "exec", "server", "rcon-cli",
                                               "insomniac_testkit", "open_imitator", "TestPlayer",
                                               check=False)

    def test_named_widget_click_fails_if_observer_cannot_identify_target(self):
        with tempfile.TemporaryDirectory() as temporary:
            session = PlayerSession("server", "client", "/minecraft", Path(temporary))
            with patch.object(session, "screen", return_value={"widgets": [
                    {"visible": True, "text": "Settings", "x": 0, "y": 0,
                     "width": 20, "height": 20}]}):
                with self.assertRaisesRegex(TestFailure, "Expected one visible widget"):
                    session.click_widget_any(("skill", "abilit"))

    def test_join_fails_fast_on_disconnect_screen(self):
        with tempfile.TemporaryDirectory() as temporary:
            session = PlayerSession("server", "client", "/minecraft", Path(temporary))
            disconnected = {
                "playerPresent": False,
                "screen": "net.minecraft.client.gui.screens.DisconnectedScreen",
                "widgets": [{"text": "Network Protocol Error"}],
            }
            with patch.object(session, "screen", return_value=disconnected), \
                    patch("player_tests.run.time.sleep") as sleep:
                with self.assertRaisesRegex(
                        InfrastructureFailure, "Network Protocol Error"):
                    wait_join(session, 1200)
                sleep.assert_not_called()

    def test_join_waits_past_loading_terrain(self):
        with tempfile.TemporaryDirectory() as temporary:
            session = PlayerSession("server", "client", "/minecraft", Path(temporary))
            loading = {"playerPresent": True,
                       "screen": "net.minecraft.client.gui.screens.ReceivingLevelScreen"}
            ready = {"playerPresent": True, "screen": "com.tensurareroll.client.GachaStartScreen"}
            with patch.object(session, "screen", side_effect=[loading, ready]) as screen, \
                    patch.object(session, "connected_player", return_value="TestPlayer"), \
                    patch("player_tests.run.time.sleep"), \
                    patch("player_tests.run.command") as docker:
                docker.return_value.stdout = "running"
                self.assertEqual(wait_join(session, 10), "TestPlayer")
                self.assertEqual(screen.call_count, 2)


if __name__ == "__main__":
    unittest.main()
