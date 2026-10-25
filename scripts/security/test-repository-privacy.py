#!/usr/bin/env python3
"""Check tracked names, ignore rules and image metadata without opening local secrets."""
from pathlib import Path, PurePosixPath
import struct
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
PRIVATE_SUFFIXES = {".pem", ".key", ".p12", ".pfx", ".jks", ".keystore",
                    ".crt", ".cer", ".der", ".db", ".sqlite", ".sqlite3", ".dump", ".backup"}


def tracked_paths():
    output = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [name for name in output.decode().split("\0") if name]


class RepositoryPrivacyTests(unittest.TestCase):
    def test_local_secret_and_backup_files_are_not_tracked(self):
        for name in tracked_paths():
            path = PurePosixPath(name)
            self.assertFalse(path.name == ".env" or
                             (path.name.startswith(".env.") and path.name != ".env.example"), name)
            self.assertNotIn(path.suffix.lower(), PRIVATE_SUFFIXES, name)
            self.assertFalse({"backups", ".backups"}.intersection(path.parts), name)
            if "certs" in path.parts:
                self.assertEqual(path.name, ".gitkeep", name)

    def test_local_artifacts_are_ignored_but_the_example_is_preserved(self):
        private = [".env", ".env.local", "admin-ui/.env.production", "private.key",
                   "private.pem", "identity.p12", "identity.pfx", "truststore.jks",
                   "operator.sqlite3", "database.dump", "backups/deployment.tar.gz"]
        result = subprocess.run(["git", "check-ignore", "--no-index", "--stdin"], cwd=ROOT,
                                input="\n".join(private) + "\n", capture_output=True, text=True, check=True)
        self.assertEqual(set(result.stdout.splitlines()), set(private))
        result = subprocess.run(["git", "check-ignore", "--no-index", ".env.example", "certs/.gitkeep"],
                                cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stdout, "")

    def test_png_assets_have_no_optional_personal_metadata(self):
        for name in tracked_paths():
            if not name.endswith(".png"):
                continue
            data = (ROOT / name).read_bytes()
            self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n", name)
            position = 8
            while position + 12 <= len(data):
                size = struct.unpack(">I", data[position:position + 4])[0]
                tag = data[position + 4:position + 8]
                self.assertNotIn(tag, (b"eXIf", b"tEXt", b"zTXt", b"iTXt"), name)
                position += size + 12
            self.assertEqual(position, len(data), name)


if __name__ == "__main__":
    unittest.main()
