import test from 'node:test'
import assert from 'node:assert/strict'

import { deriveStatusView, disconnectedView, escapeHtml, formatRelative } from '../../shared/src/popup-view.js'

function translate(key, params = {}) {
  const messages = {
    'popup.disconnected': 'Disconnected',
    'popup.noStatus': 'No InboxBridge status available',
    'popup.hasErrors': 'Has errors',
    'popup.connectedTo': `Connected to ${params.userLabel} at ${params.host}`,
    'popup.sourceCount': `${params.count} source`,
    'popup.lastCompleted': `Last completed ${params.value}`,
    'popup.minutesAgo': `${params.count} min ago`,
    'popup.justNow': 'just now'
  }
  return messages[key] || key
}

test('disconnectedView disables polling and shows fallback text', () => {
  const view = disconnectedView('Connect first', translate)
  assert.equal(view.statusLabel, 'Disconnected')
  assert.equal(view.runPollDisabled, true)
  assert.equal(view.connectionCopy, 'Connect first')
})

test('deriveStatusView summarizes attention state and metrics', () => {
  const view = deriveStatusView('https://mail.example.com', {
    user: { username: 'alice', displayName: 'Alice' },
    poll: { running: false, canRun: true },
    summary: {
      errorSourceCount: 1,
      lastCompletedRunAt: '2026-04-12T15:00:00Z',
      lastCompletedRun: { imported: 4, fetched: 9, duplicates: 5, errors: 1 }
    },
    sources: [
      { sourceId: 'alpha', label: 'alpha', lastError: 'Auth failed', needsAttention: true }
    ]
  }, { now: Date.parse('2026-04-12T15:30:00Z'), translate })

  assert.equal(view.statusLabel, 'Has errors')
  assert.equal(view.statusTone, 'error')
  assert.equal(view.metrics.imported, '4')
  assert.equal(view.attentionCount, '1 source')
  assert.equal(view.errorSources.length, 1)
  assert.match(view.updatedText, /30 min ago/)
})

test('formatRelative handles near-now timestamps', () => {
  assert.equal(formatRelative('2026-04-12T15:00:00Z', Date.parse('2026-04-12T15:00:10Z'), translate), 'just now')
})

test('escapeHtml protects rendered source labels', () => {
  assert.equal(escapeHtml('<alpha & beta>'), '&lt;alpha &amp; beta&gt;')
})
