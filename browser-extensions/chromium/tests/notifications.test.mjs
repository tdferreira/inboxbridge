import test from 'node:test'
import assert from 'node:assert/strict'
import { withBrowserNotificationDefaults } from '../../shared/src/notifications.js'

test('withBrowserNotificationDefaults keeps required defaults when caller passes undefined values', () => {
  const options = withBrowserNotificationDefaults({
    iconUrl: undefined,
    message: 'Imported 3 messages.',
    title: 'InboxBridge manual poll finished',
    type: 'basic'
  }, {
    iconUrl: 'chrome-extension://abc/icon128.png'
  })

  assert.deepEqual(options, {
    iconUrl: 'chrome-extension://abc/icon128.png',
    message: 'Imported 3 messages.',
    title: 'InboxBridge manual poll finished',
    type: 'basic'
  })
})
