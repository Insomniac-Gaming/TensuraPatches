"""Removed SlimeThrone controls and retained Imitator menu on a real client."""

import time

from player_tests.harness import PlayerSession, TestFailure

NAME = "slimethrone_ui"
FIXTURE = "fresh"


def run(session: PlayerSession):
    session.assert_log_contains("client", "INSOMNIAC_CI_TITLE_READY")
    # The first-login ReReroll screen can arrive after the player entity appears.
    # Close it once observed, then retry the Tensura menu binding.
    deadline = time.monotonic() + 25
    while True:
        if (session.screen().get("screen") or "").endswith("GachaStartScreen"):
            session.close_screen()
            session.wait_no_screen()
        session.key("b")  # Tensura's default main-screen binding in the test pack.
        try:
            main = session.wait_screen("MainScreen", timeout=4)
            break
        except TestFailure:
            if time.monotonic() >= deadline:
                raise
            if not (session.screen().get("screen") or "").endswith("GachaStartScreen"):
                raise
    session.assert_widgets_absent(main, ("prestige",))
    session.screenshot("main_screen")

    for attempt in range(3):
        session.click_widget_any(("skill", "abilit"))
        try:
            abilities = session.wait_screen("AbilityCategoriesScreen", timeout=6)
            break
        except TestFailure:
            if attempt == 2 or not (session.screen().get("screen") or "").endswith("MainScreen"):
                raise
    session.assert_widgets_absent(abilities, ("skill lock",))
    session.screenshot("abilities_screen")

    session.close_screen()
    session.wait_no_screen()
    player = session.connected_player()
    result = session.server_command("insomniac_testkit open_imitator")
    if f"INSOMNIAC_TESTKIT_IMITATOR_OPEN {player}" not in result:
        raise TestFailure(f"Imitator test command did not confirm execution: {result[-300:]}")
    session.wait_screen("ImitatorScreen")
    session.screenshot("imitator_screen")
