import os
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from smoke import extract_world, prepare_server_permissions, slimethrone_installed, stage_client, stage_server


class SmokeInputTests(unittest.TestCase):
    def test_slimethrone_mod_detection(self):
        with tempfile.TemporaryDirectory() as temporary:
            mods = Path(temporary) / "mods"
            mods.mkdir()
            self.assertFalse(slimethrone_installed(mods))
            (mods / "SlimeThroneExtras-neoforge-2.1.2.1.jar").write_bytes(b"mod")
            self.assertTrue(slimethrone_installed(mods))

    def test_world_zip_flattens_world_root(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "world.zip"
            with ZipFile(archive, "w") as z:
                z.writestr("backup/world/level.dat", b"level")
                z.writestr("backup/world/region/r.0.0.mca", b"region")
            extract_world(archive, root / "staged")
            self.assertEqual((root / "staged/level.dat").read_bytes(), b"level")
            self.assertEqual((root / "staged/region/r.0.0.mca").read_bytes(), b"region")

    def test_world_zip_rejects_traversal(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "world.zip"
            with ZipFile(archive, "w") as z:
                z.writestr("world/level.dat", b"level")
                z.writestr("world/../../outside", b"bad")
            with self.assertRaisesRegex(ValueError, "unsafe"):
                extract_world(archive, root / "staged")
            self.assertFalse((root / "outside").exists())

    def test_server_snapshot_requires_matching_horizons(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "snapshot/mods").mkdir(parents=True)
            jar = root / "InsomniacServerPatches-1.0.0.jar"
            jar.write_bytes(b"addon")
            with self.assertRaisesRegex(ValueError, "missing Tensura"):
                stage_server(root / "snapshot", jar, root / "stage", None)
            (root / "snapshot/mods/Tensura_Horizons-0.31.jar").write_bytes(b"horizons")
            stage_server(root / "snapshot", jar, root / "stage", None)
            self.assertEqual((root / "stage/mods" / jar.name).read_bytes(), b"addon")

    def test_server_staging_is_writable_without_changing_snapshot(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            mods = root / "snapshot/mods"
            mods.mkdir(parents=True)
            horizons = mods / "Tensura_Horizons-0.31.jar"
            horizons.write_bytes(b"horizons")
            horizons.chmod(0o400)
            mods.chmod(0o500)
            jar = root / "InsomniacServerPatches-1.0.0.jar"
            jar.write_bytes(b"addon")
            stage = root / "stage"
            stage_server(root / "snapshot", jar, stage, None)
            prepare_server_permissions(stage, os.getuid(), os.getgid())
            self.assertEqual((stage / "mods").stat().st_mode & 0o700, 0o700)
            self.assertEqual((stage / "mods" / horizons.name).stat().st_mode & 0o600, 0o600)
            self.assertEqual(mods.stat().st_mode & 0o700, 0o500)
            self.assertEqual(horizons.stat().st_mode & 0o600, 0o400)

    def test_client_staging_removes_host_commands_and_saved_worlds(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            prism = root / "prism"
            instance = prism / "instances" / "Test Pack"
            mods = instance / "minecraft" / "mods"
            mods.mkdir(parents=True)
            (instance / "minecraft/saves").mkdir()
            (instance / "minecraft/saves/world.dat").write_bytes(b"private")
            (instance / "instance.cfg").write_text(
                "JavaPath=/home/person/java\nPreLaunchCommand=/home/person/private-script\n"
            )
            (prism / "accounts.json").write_text("{}")
            (prism / "prismlauncher.cfg").write_text("launcher=true\n")
            release = root / "InsomniacServerPatches-1.0.0.jar"
            probe = root / "InsomniacTitleProbe-1.0.0.jar"
            release.write_bytes(b"release")
            probe.write_bytes(b"probe")
            staged = stage_client(prism, "Test Pack", release, probe, root / "stage")
            config = (staged / "instance.cfg").read_text()
            self.assertIn("JavaPath=/usr/lib/jvm/java-21-openjdk-amd64/bin/java", config)
            self.assertIn("PreLaunchCommand=\n", config)
            launcher_config = (root / "stage/prismlauncher.cfg").read_text()
            self.assertIn("Language=en_US\n", launcher_config)
            self.assertIn("IgnoreJavaWizard=true\n", launcher_config)
            self.assertIn("ApplicationTheme=system\n", launcher_config)
            self.assertIn("IconTheme=pe_colored\n", launcher_config)
            self.assertEqual(
                (staged / "minecraft/options.txt").read_text(),
                "onboardAccessibility:false\n",
            )
            self.assertFalse((staged / "minecraft/saves").exists())
            self.assertEqual((staged / "minecraft/mods" / probe.name).read_bytes(), b"probe")


if __name__ == "__main__":
    unittest.main()
