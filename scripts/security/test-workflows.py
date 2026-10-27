#!/usr/bin/env python3
"""Exercise release input handling without publishing or accessing credentials."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def steps(path):
    return path.read_text().split("      - name: ")[1:]


class WorkflowSecurityTests(unittest.TestCase):
    def test_maintainer_approval_is_a_protected_job_for_every_pr_author(self):
        workflow = (ROOT / '.github/workflows/maintainer-approval.yml').read_text()
        self.assertIn('  pull_request:\n    branches: [main]', workflow)
        self.assertIn('types: [opened, reopened, synchronize, ready_for_review, edited]', workflow)
        self.assertIn('permissions: {}', workflow)
        self.assertIn('environment: maintainer-approval', workflow)
        self.assertIn('group: maintainer-approval-${{ github.event.pull_request.number }}', workflow)
        self.assertIn('cancel-in-progress: true', workflow)
        self.assertIn('if: ${{ !github.event.pull_request.draft }}', workflow)
        self.assertNotIn('pull_request.author', workflow)
        self.assertNotIn('pull_request.user', workflow)
        self.assertNotIn('github.actor', workflow)
        self.assertNotIn('pull_request_target:', workflow)
        self.assertNotIn('workflow_dispatch:', workflow)
        self.assertNotRegex(workflow, r'(?m)^\s+uses:')
        self.assertNotRegex(workflow, r'(?m)^\s+\w[\w-]*:\s*write\b')
        # Exercise the exact step: it must have no checkout, API call or secret input.
        gate = steps(ROOT / '.github/workflows/maintainer-approval.yml')
        self.assertEqual(len(gate), 1)
        script = gate[0].split('        run: ', 1)[1].strip()
        self.assertEqual(script, "echo 'The designated maintainer approved this candidate.'")
        result = subprocess.run(['sh', '-c', script], env={}, capture_output=True, text=True, timeout=5)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, 'The designated maintainer approved this candidate.\n')

    def test_actions_have_immutable_commit_references(self):
        for path in (ROOT / ".github/workflows").glob("*.yml"):
            for action in re.findall(r"uses:\s*([^\s]+)", path.read_text()):
                self.assertRegex(action, r"^[^@]+@[a-f0-9]{40}$", str(path))

    def test_context_values_never_become_shell_source(self):
        for path in (ROOT / ".github/workflows").glob("*.yml"):
            for step in steps(path):
                script = step.partition("        run:")[2]
                self.assertNotIn("${{", script, step.splitlines()[0])

    def test_release_tags_are_validated_as_data(self):
        metadata = next(step for step in steps(ROOT / ".github/workflows/release.yml")
                        if step.startswith("Resolve release metadata\n"))
        script = metadata.split("        run: |\n", 1)[1]
        script = "\n".join(line[10:] for line in script.splitlines())
        for event in ("push", "workflow_dispatch"):
            for tag, valid in (("v1.2.3", True), ("v1.2.3-rc.1", True),
                               ("v1.2.3$(touch injected)", False),
                               ('v1.2.3"; touch injected; #', False),
                               ("v1.2.3\nextra_output=attacker", False), ("../main", False)):
                with self.subTest(event=event, tag=tag), tempfile.TemporaryDirectory() as directory:
                    output = Path(directory) / "output"
                    env = {**os.environ, "RELEASE_EVENT_NAME": event,
                           "INPUT_TAG_NAME": tag, "REF_NAME": tag,
                           "REPOSITORY": "example/inboxbridge", "GITHUB_OUTPUT": str(output)}
                    result = subprocess.run(["bash", "-c", script], cwd=directory, env=env,
                                            capture_output=True, text=True, timeout=10)
                    self.assertEqual(result.returncode == 0, valid, result.stderr)
                    self.assertFalse((Path(directory) / "injected").exists())
                    if valid:
                        self.assertEqual(output.read_text(), f"tag_name={tag}\n"
                                         f"docs_base_url=https://github.com/example/inboxbridge/blob/{tag}\n")
                    else:
                        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
