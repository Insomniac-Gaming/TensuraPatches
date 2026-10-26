"""Exercise ReRebirth locks with the reported rebirth level and owned skills."""

import time

from player_tests.harness import PlayerSession, TestFailure

NAME = "rebirth_locks"
FIXTURE = "fresh"


def run(session: PlayerSession):
    if session.screen().get("screen") is not None:
        session.close_screen()
        session.wait_no_screen()

    ready = session.server_command("insomniac_testkit rebirth_fixture")
    if "INSOMNIAC_TESTKIT_REBIRTH_READY" not in ready:
        raise TestFailure(f"Rebirth fixture did not initialize: {ready[-300:]}")
    assert_locks(session, ())

    # These are the IDs actually owned in the reporter's saved player data.
    lock(session, "tensura:gluttony", ("tensura:gluttony",))
    lock(session, "foxablazeultimate:raphael",
         ("tensura:gluttony", "foxablazeultimate:raphael"))

    # Rebirth 3 has two slots. A third lock must fail because the slots are full.
    session.player_command("rebirth lock tensura:starved")
    assert_locks(session, ("tensura:gluttony", "foxablazeultimate:raphael"))

    session.player_command("rebirth unlock foxablazeultimate:raphael")
    assert_locks(session, ("tensura:gluttony",))
    lock(session, "tensura:starved", ("tensura:gluttony", "tensura:starved"))


def lock(session: PlayerSession, skill: str, expected: tuple[str, ...]):
    session.player_command(f"rebirth lock {skill}")
    assert_locks(session, expected)


def assert_locks(session: PlayerSession, expected: tuple[str, ...]):
    wanted = "INSOMNIAC_TESTKIT_REBIRTH level=3 slots=2 locks=" + ",".join(expected)
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        actual = session.server_command("insomniac_testkit rebirth_state").strip()
        if actual == wanted:
            return
        time.sleep(0.25)
    raise TestFailure(f"Expected {wanted!r}; got {actual!r}")
