"""Assertions and actions shared by Python player scenarios."""

import json
import re
import subprocess
import time
from pathlib import Path


class TestFailure(AssertionError):
    """The game ran, but an expected behavior was absent."""


class InfrastructureFailure(RuntimeError):
    """A test input, process, or test bridge was unavailable."""


def command(*arguments, check=True):
    result = subprocess.run(arguments, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, check=False)
    if check and result.returncode:
        raise InfrastructureFailure(f"{arguments[0]} {arguments[1]} failed: {result.stdout[-1500:]}")
    return result


class PlayerSession:
    def __init__(self, server: str, client: str, minecraft_dir: str, artifacts: Path):
        self.server = server
        self.client = client
        self.minecraft_dir = minecraft_dir
        self.artifacts = artifacts
        artifacts.mkdir(parents=True, exist_ok=True)
        for screenshot in artifacts.glob("*.png"):
            screenshot.unlink()
        for name in ("client.log", "server.log"):
            (artifacts / name).unlink(missing_ok=True)
        crash_reports = artifacts / "crash-reports"
        if crash_reports.is_dir():
            import shutil
            shutil.rmtree(crash_reports)

    def rpc(self, action: str, params=None, timeout=45):
        result = command("docker", "exec", self.client, "/opt/testkit-venv/bin/python",
                         "/opt/testkit/rpc.py", action, json.dumps(params or {}), str(timeout), check=False)
        if result.returncode:
            raise InfrastructureFailure(f"MC Pilot {action}: {result.stdout[-1500:]}")
        try:
            return json.loads(result.stdout)
        except json.JSONDecodeError as exception:
            raise InfrastructureFailure(f"MC Pilot {action} returned invalid JSON") from exception

    def server_command(self, text: str):
        # rcon-cli joins its argv with spaces; passing the whole line as one
        # argument makes it quote that argument and Brigadier rejects it.
        result = command("docker", "exec", self.server, "rcon-cli", *text.split(), check=False)
        if result.returncode or "<--[HERE]" in result.stdout:
            raise TestFailure(f"Server command failed: {text}: {result.stdout[-1000:]}")
        return result.stdout

    def player_command(self, text: str):
        return self.rpc("chat.command", {"command": text.lstrip("/")})

    def connected_player(self):
        listing = self.server_command("list").strip()
        names = listing.rsplit(":", 1)[-1].strip().split(",")
        names = [name.strip() for name in names if name.strip()]
        if len(names) != 1:
            raise InfrastructureFailure(f"Expected one joined test player; got {len(names)}")
        return names[0]

    def key(self, name: str):
        return self.rpc("input.key-press", {"key": name})

    def close_screen(self):
        return self.rpc("gui.close")

    def screen(self):
        path = f"{self.minecraft_dir}/insomniac-ci-screen.json"
        result = command("docker", "exec", self.client, "cat", path, check=False)
        if result.returncode:
            raise InfrastructureFailure("CI screen probe has not written its snapshot")
        try:
            return json.loads(result.stdout)
        except json.JSONDecodeError as exception:
            raise InfrastructureFailure("CI screen probe wrote invalid JSON") from exception

    def wait_screen(self, class_suffix: str, timeout=15):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            data = self.screen()
            if (data.get("screen") or "").endswith(class_suffix):
                return data
            time.sleep(0.25)
        raise TestFailure(f"Screen {class_suffix} did not open within {timeout}s; last={data.get('screen')}")

    def wait_no_screen(self, timeout=10):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.screen().get("screen") is None:
                return
            time.sleep(0.25)
        raise TestFailure("Client screen did not close")

    @staticmethod
    def assert_widgets_absent(screen: dict, forbidden: tuple[str, ...]):
        widgets = [widget for widget in screen.get("widgets", []) if widget.get("visible")]
        if not widgets:
            raise TestFailure("Screen probe found no widgets; absence cannot be established")
        for widget in widgets:
            if widget.get("inspectionError"):
                raise InfrastructureFailure(f"Screen probe could not inspect widget: {widget['inspectionError']}")
            description = " ".join(str(widget.get(key, "")) for key in
                                   ("class", "text", "tooltip", "texture")).lower()
            description = re.sub(r"[_-]+", " ", description)
            for needle in forbidden:
                if re.sub(r"[_-]+", " ", needle.lower()) in description:
                    raise TestFailure(f"Removed control is visible: {needle}")

    def click_widget(self, text: str):
        widgets = [widget for widget in self.screen().get("widgets", [])
                   if widget.get("visible") and text.lower() in widget.get("text", "").lower()]
        if len(widgets) != 1:
            raise TestFailure(f"Expected one visible widget containing {text!r}; found {len(widgets)}")
        widget = widgets[0]
        x = widget["x"] + widget["width"] // 2
        y = widget["y"] + widget["height"] // 2
        return self.rpc("input.click", {"x": x, "y": y, "button": "left", "modifiers": []})

    def click_widget_any(self, labels: tuple[str, ...]):
        widgets = [widget for widget in self.screen().get("widgets", [])
                   if widget.get("visible") and any(
                       label.lower() in (widget.get("text", "") + " " +
                                         widget.get("tooltip", "")).lower() for label in labels)]
        if len(widgets) != 1:
            raise TestFailure(f"Expected one visible widget matching {labels!r}; found {len(widgets)}")
        widget = widgets[0]
        return self.rpc("input.click", {"x": widget["x"] + widget["width"] // 2,
                                        "y": widget["y"] + widget["height"] // 2,
                                        "button": "left", "modifiers": []})

    def assert_log_contains(self, service: str, marker: str, timeout=10):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if service == "server":
                logs = command("docker", "logs", self.server, check=False).stdout
            elif service == "client":
                logs = command("docker", "exec", self.client, "cat",
                               f"{self.minecraft_dir}/logs/latest.log", check=False).stdout
            else:
                raise ValueError("service must be server or client")
            if marker in logs:
                return
            time.sleep(0.5)
        raise TestFailure(f"{service} log did not contain {marker!r}")

    def screenshot(self, name: str, baseline: Path | None = None, tolerance=0):
        if not name.isidentifier():
            raise ValueError("Screenshot name must be a Python identifier")
        remote = f"{self.minecraft_dir}/screenshots/insomniac-{name}.png"
        self.rpc("capture.screenshot", {"output": remote, "gui": True}, timeout=60)
        local = self.artifacts / f"{name}.png"
        command("docker", "cp", f"{self.client}:{remote}", str(local))
        if baseline is not None:
            from PIL import Image, ImageChops
            with Image.open(local) as actual, Image.open(baseline) as expected:
                if actual.size != expected.size:
                    raise TestFailure(f"Screenshot dimensions differ from {baseline}")
                difference = ImageChops.difference(actual.convert("RGB"), expected.convert("RGB"))
                pixels = difference.getdata()
                changed = sum(any(channel > tolerance for channel in pixel) for pixel in pixels)
                if changed:
                    raise TestFailure(f"Screenshot differs from {baseline}: {changed} pixels exceed tolerance {tolerance}")
        return local

    def collect_failure(self):
        failure_image = self.artifacts / "failure.png"
        failure_image.unlink(missing_ok=True)
        try:
            self.screenshot("failure")
        except Exception:
            pass
        for name, container in (("server", self.server), ("client", self.client)):
            if name == "server":
                logs = command("docker", "logs", "--tail", "250", container, check=False).stdout
            else:
                logs = command("docker", "exec", container, "tail", "-n", "800",
                               f"{self.minecraft_dir}/logs/latest.log", check=False).stdout
            (self.artifacts / f"{name}.log").write_text(logs)
        crash_reports = self.artifacts / "crash-reports"
        if crash_reports.exists():
            import shutil
            shutil.rmtree(crash_reports)
        command("docker", "cp", f"{self.client}:{self.minecraft_dir}/crash-reports",
                str(crash_reports), check=False)
