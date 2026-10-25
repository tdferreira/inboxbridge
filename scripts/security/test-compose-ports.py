#!/usr/bin/env python3
"""Verify port compatibility without reading the deployment's environment file."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def frontend_port(overrides=None):
    env = {key: value for key, value in os.environ.items()
           if key not in ("PUBLIC_BIND_ADDRESS", "PUBLIC_PORT", "COMPOSE_ENV_FILES")}
    env.update(overrides or {})
    with tempfile.TemporaryDirectory() as directory:
        output = subprocess.check_output(
            ["docker", "compose", "--project-directory", directory,
             "--env-file", "/dev/null", "-f", str(ROOT / "docker-compose.yml"),
             "config", "--no-env-resolution", "--format", "json"],
            env=env, text=True, timeout=30)
    return json.loads(output)["services"]["inboxbridge-admin"]["ports"][0]


class ComposePortTests(unittest.TestCase):
    def test_existing_installations_keep_lan_and_tailnet_access(self):
        port = frontend_port()
        self.assertEqual(port["host_ip"], "0.0.0.0")
        self.assertEqual(str(port["published"]), "3000")
        self.assertEqual(port["target"], 8443)

    def test_fresh_example_explicitly_selects_local_bootstrap(self):
        setting = re.search(r"^PUBLIC_BIND_ADDRESS=(.+)$", (ROOT / ".env.example").read_text(), re.M)
        self.assertIsNotNone(setting)
        self.assertEqual(frontend_port({"PUBLIC_BIND_ADDRESS": setting[1]})["host_ip"], "127.0.0.1")

    def test_operator_bind_address_and_port_are_preserved(self):
        port = frontend_port({"PUBLIC_BIND_ADDRESS": "100.64.0.10", "PUBLIC_PORT": "9443"})
        self.assertEqual(port["host_ip"], "100.64.0.10")
        self.assertEqual(str(port["published"]), "9443")


if __name__ == "__main__":
    unittest.main()
