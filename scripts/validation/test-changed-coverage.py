#!/usr/bin/env python3
"""Regression checks for the opt-in changed-code coverage gate."""
import contextlib
import importlib.util
import io
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('changed', Path(__file__).with_name('changed-coverage.py'))
coverage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(coverage)

class CoverageGateTest(unittest.TestCase):
    def run_gate(self, unit, integration, frontend):
        with patch('sys.argv', ['changed-coverage.py', '--check']), \
                patch.object(coverage, 'changed_lines', return_value={}), \
                patch.object(coverage, 'java_coverage', return_value={}), \
                patch.object(coverage, 'frontend_coverage', return_value={}), \
                patch.object(coverage, 'summarize', side_effect=[{'total': unit}, {'total': integration}, {'total': frontend}]), \
                contextlib.redirect_stdout(io.StringIO()):
            coverage.main()

    def test_integration_does_not_replace_missing_unit_coverage(self):
        with self.assertRaisesRegex(SystemExit, 'backend unit'):
            self.run_gate([1, 2, 2, 2], [2, 2, 2, 2], [2, 2, 2, 2])

    def test_frontend_branch_gap_fails_the_gate(self):
        with self.assertRaisesRegex(SystemExit, 'frontend'):
            self.run_gate([2, 2, 2, 2], [0, 2, 0, 2], [2, 2, 1, 2])

    def test_separate_integration_coverage_is_not_a_unit_threshold(self):
        self.run_gate([2, 2, 2, 2], [0, 2, 0, 2], [2, 2, 2, 2])

if __name__ == '__main__':
    unittest.main()
