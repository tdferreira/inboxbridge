import test from 'node:test'
import assert from 'node:assert/strict'

import { registerBackgroundController } from '../../shared/src/background-controller.js'

test('firefox background controller subscribes to extension live events after config loads', async () => {
  let liveRequest = null
  let iconStates = []

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async () => {},
      applyIcon: async (status, errorState = null) => {
        iconStates.push({ status, errorState })
      },
      clearBadge: async () => {},
      createAlarm() {},
      fetchStatus: async () => ({ summary: { errorSourceCount: 0 } }),
      loadCachedStatus: async () => null,
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', token: 'ibx_token' }),
      mergeLivePollIntoStatus: (status, poll) => ({ ...status, poll }),
      onAlarm() {},
      onInstalled() {},
      onMessage() {},
      saveCachedStatus: async () => {},
      sendMessage: async () => {},
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents(serverUrl, token) {
        liveRequest = { serverUrl, token }
        return { close() {}, completed: Promise.resolve() }
      }
    }
  })

  await new Promise((resolve) => setImmediate(resolve))
  await controller.refreshBadgeFromServer()

  assert.deepEqual(liveRequest, {
    serverUrl: 'https://mail.example.com',
    token: 'ibx_token'
  })
  assert.deepEqual(iconStates.at(-1), {
    status: { summary: { errorSourceCount: 0 } },
    errorState: null
  })
})
