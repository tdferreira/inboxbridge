#!/usr/bin/env python3
"""Prove privacy rules detect synthetic canaries and do not exempt changed keys."""
import json
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class SecretPolicyTests(unittest.TestCase):
    def test_private_identifiers_and_changed_fixture_keys_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            snapshot = Path(directory)
            fixture = Path("src/test/java/dev/inboxbridge/testsupport/LocalSecretStorageTestResource.java")
            source = (ROOT / fixture).read_text()
            key = re.search(r'ACTIVE_KEY\s*=\s*"([^"]+)"', source)[1]
            target = snapshot / fixture
            target.parent.mkdir(parents=True)
            target.write_text(source + '\nString api_key = "' + key[:-1] + 'Q";\n')
            # Construct synthetic identifiers at runtime so the repository itself
            # never contains a concrete personal path, tailnet host or contact.
            canaries = ["/Users/" + "synthetic-user/project", "node." + "synthetic-tailnet." + "ts.net",
                        "synthetic-person" + "@private-domain.local", "person@example.com"]
            (snapshot / "canaries.txt").write_text("\n".join(canaries))
            command = ["docker", "run", "--rm", "-v", str(snapshot) + ":/scan",
                       "-v", str(ROOT / ".gitleaks.toml") + ":/gitleaks.toml:ro",
                       "ghcr.io/gitleaks/gitleaks:v8.30.1", "dir", "/scan",
                       "--config", "/gitleaks.toml", "--redact=100", "--no-banner",
                       "--report-format", "json", "--report-path", "/scan/report.json"]
            result = subprocess.run(command, capture_output=True, text=True, timeout=120)
            self.assertEqual(result.returncode, 1, result.stderr)
            findings = json.loads((snapshot / "report.json").read_text())
            self.assertEqual({entry["RuleID"] for entry in findings}, {
                "private-home-directory", "private-tailnet-hostname", "personal-email-address", "generic-api-key"})
            self.assertEqual(len(findings), 4)


if __name__ == "__main__":
    unittest.main()
