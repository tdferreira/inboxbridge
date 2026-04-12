import test from 'node:test'
import assert from 'node:assert/strict'

import {
  mergeLivePollIntoStatus,
  shouldOverlayLiveStatus,
  shouldRefreshStatusFromLiveEvent
} from '../../shared/src/live-status.js'

test('mergeLivePollIntoStatus overlays active running sources onto cached status', () => {
  const merged = mergeLivePollIntoStatus({
    poll: { running: false, canRun: true, state: 'IDLE' },
    sources: [
      { sourceId: 'alpha', status: 'ERROR' },
      { sourceId: 'beta', status: 'SUCCESS' }
    ]
  }, {
    activeSourceId: 'beta',
    running: true,
    startedAt: '2026-04-12T18:00:00Z',
    state: 'RUNNING',
    updatedAt: '2026-04-12T18:01:00Z',
    sources: [
      { sourceId: 'beta', state: 'RUNNING' }
    ]
  })

  assert.equal(merged.poll.running, true)
  assert.equal(merged.poll.canRun, false)
  assert.equal(merged.poll.activeSourceId, 'beta')
  assert.equal(merged.sources[1].status, 'RUNNING')
  assert.equal(merged.sources[0].status, 'ERROR')
})

test('live-status helpers distinguish overlay events from full-refresh events', () => {
  assert.equal(shouldOverlayLiveStatus({ type: 'poll-source-progress' }), true)
  assert.equal(shouldRefreshStatusFromLiveEvent({ type: 'poll-source-finished' }), true)
  assert.equal(shouldOverlayLiveStatus({ type: 'keepalive' }), false)
})
