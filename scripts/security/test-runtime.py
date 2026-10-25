#!/usr/bin/env python3
"""Anonymous checks of the deployed boundary; never submits valid credentials."""
import argparse
import json
from pathlib import Path
import ssl
import subprocess
import sys
import unittest
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(add_help=False)
parser.add_argument("--base-url", default="https://localhost:3000")
parser.add_argument("--ca-file", default=str(ROOT / "certs/ca.crt"))
parser.add_argument("--project")
options, test_arguments = parser.parse_known_args()
BASE_URL = options.base_url.rstrip("/")
CONTEXT = ssl.create_default_context(cafile=options.ca_file)


def request(path, body=None):
    req = urllib.request.Request(BASE_URL + path, data=body,
                                 headers={"Content-Type": "application/json"})
    try:
        response = urllib.request.urlopen(req, context=CONTEXT, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, response.headers, response.read()


def docker(*args):
    if options.project:
        args = list(args)
        mapping = {"inboxbridge": "inboxbridge", "inboxbridge-admin": "inboxbridge-admin", "inboxbridge-postgres": "postgres"}
        for index, value in enumerate(args):
            if value in mapping:
                args[index] = subprocess.check_output(["docker", "compose", "--env-file", "/dev/null", "-p", options.project,
                    "-f", str(ROOT / "dev/compose.yaml"), "ps", "-q", mapping[value]], text=True, timeout=30).strip()
    return subprocess.check_output(["docker", *args], text=True, timeout=30).strip()


class RuntimeSecurityTests(unittest.TestCase):
    def test_browser_headers_and_public_health(self):
        status, headers, _ = request("/")
        self.assertEqual(status, 200)
        for header in ("Content-Security-Policy", "Strict-Transport-Security",
                       "X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy"):
            self.assertTrue(headers.get(header), header)
        status, _, body = request("/api/health/summary")
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body), {"status": "UP"})

    def test_anonymous_access_and_oauth_state_enforcement(self):
        for path in ("/api/auth/me", "/api/account/sessions", "/api/admin/users"):
            self.assertIn(request(path)[0], (401, 403), path)
        for provider in ("google", "microsoft"):
            self.assertEqual(request(f"/api/{provider}-oauth/exchange", b'{"code":"invalid"}')[0], 400)

    def test_oversized_api_request_is_rejected(self):
        self.assertEqual(request("/api/google-oauth/exchange", b"x" * (256 * 1024 + 1))[0], 413)

    def test_login_post_is_reachable_without_testing_an_account_password(self):
        # Reject malformed JSON before authentication; do not consume login attempts.
        self.assertEqual(request("/api/auth/login", b"{")[0], 400)

    def test_applications_are_unprivileged_and_cannot_read_the_ca_key(self):
        for name in ("inboxbridge", "inboxbridge-admin"):
            self.assertNotEqual(docker("exec", name, "id", "-u"), "0")
            files = docker("exec", name, "ls", "/etc/inboxbridge/certs").split()
            leaf = "backend" if name == "inboxbridge" else "frontend"
            self.assertEqual(set(files), {"ca.crt", leaf + ".crt", leaf + ".key"})
            docker("exec", name, "sh", "-c", f"test -r /etc/inboxbridge/certs/{leaf}.key")

    def test_backend_and_database_have_no_host_ports(self):
        for name in ("inboxbridge", "inboxbridge-postgres"):
            ports = json.loads(docker("inspect", "--format", "{{json .HostConfig.PortBindings}}", name))
            self.assertFalse(ports, name)


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0], *test_arguments])
