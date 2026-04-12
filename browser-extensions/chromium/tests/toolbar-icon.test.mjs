import test from 'node:test'
import assert from 'node:assert/strict'

import { toolbarIconStateForStatus } from '../../shared/src/toolbar-icon.js'

test('toolbarIconStateForStatus distinguishes default, polling, error, and signed-out states', () => {
  assert.equal(toolbarIconStateForStatus({ poll: { running: false }, summary: { errorSourceCount: 0 } }), 'default')
  assert.equal(toolbarIconStateForStatus({ poll: { running: true }, summary: { errorSourceCount: 0 } }), 'polling')
  assert.equal(toolbarIconStateForStatus({ poll: { running: false }, summary: { errorSourceCount: 2 } }), 'error')
  assert.equal(toolbarIconStateForStatus(null, { kind: 'signed-out', message: 'Open Settings to sign in.' }), 'signed-out')
  assert.equal(toolbarIconStateForStatus(null, { kind: 'transport', message: 'InboxBridge did not respond.' }), 'error')
})
