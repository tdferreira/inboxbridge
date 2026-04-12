import test from 'node:test'
import assert from 'node:assert/strict'

import { badgeStateForStatus } from '../../shared/src/badge-state.js'

test('badgeStateForStatus marks failures with an alert badge', () => {
  const badge = badgeStateForStatus({
    poll: { running: false },
    summary: { errorSourceCount: 2 }
  })
  assert.equal(badge.text, '!')
  assert.match(badge.title, /2 sources need attention/)
})

test('badgeStateForStatus marks active polling distinctly', () => {
  const badge = badgeStateForStatus({
    poll: { running: true },
    summary: { errorSourceCount: 0 }
  })
  assert.equal(badge.clear, true)
  assert.match(badge.title, /polling running/)
})

test('badgeStateForStatus clears for healthy state and surfaces transport errors', () => {
  assert.equal(badgeStateForStatus({ poll: { running: false }, summary: { errorSourceCount: 0 } }).clear, true)
  assert.equal(badgeStateForStatus(null, 'offline').text, '?')
})
