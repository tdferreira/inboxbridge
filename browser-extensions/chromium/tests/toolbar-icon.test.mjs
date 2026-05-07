import test from 'node:test'
import assert from 'node:assert/strict'

import { setToolbarIconForState, toolbarIconStateForStatus } from '../../shared/src/toolbar-icon.js'

test('toolbarIconStateForStatus distinguishes default, polling, error, and signed-out states', () => {
  assert.equal(toolbarIconStateForStatus({ poll: { running: false }, summary: { errorSourceCount: 0 } }), 'default')
  assert.equal(toolbarIconStateForStatus({ poll: { running: true }, summary: { errorSourceCount: 0 } }), 'polling')
  assert.equal(toolbarIconStateForStatus({ poll: { running: false }, summary: { errorSourceCount: 2 } }), 'error')
  assert.equal(toolbarIconStateForStatus(null, { kind: 'signed-out', message: 'Open Settings to sign in.' }), 'signed-out')
  assert.equal(toolbarIconStateForStatus(null, { kind: 'transport', message: 'InboxBridge did not respond.' }), 'error')
})

test('setToolbarIconForState ignores stale icon renders that finish after a newer state', async () => {
  const applied = []
  let releasePollingFetch
  let fetchCalls = 0
  const pollingFetchGate = new Promise((resolve) => {
    releasePollingFetch = resolve
  })
  const deps = {
    OffscreenCanvasImpl: FakeOffscreenCanvas,
    createImageBitmapImpl: async () => ({}),
    fetchImpl: async (url) => {
      fetchCalls += 1
      if (url.includes('icon16.png') && fetchCalls === 1) {
        await pollingFetchGate
      }
      return { blob: async () => ({}) }
    },
    runtime: { getURL: (path) => `extension://${path}` },
    setIcon: async (imageData) => {
      applied.push(imageData[16].state)
    }
  }

  const pollingUpdate = setToolbarIconForState('polling', deps)
  await new Promise((resolve) => setImmediate(resolve))
  const defaultUpdate = setToolbarIconForState('default', deps)
  releasePollingFetch()

  assert.equal(await defaultUpdate, true)
  assert.equal(await pollingUpdate, false)
  assert.deepEqual(applied, ['default'])
})

class FakeOffscreenCanvas {
  constructor(size) {
    this.size = size
  }

  getContext() {
    const context = {
      overlay: false,
      arc() {
        this.overlay = true
      },
      getImageData: () => ({ state: context.overlay ? 'polling' : 'default' }),
      beginPath() {},
      clearRect() {},
      closePath() {},
      drawImage() {},
      fill() {},
      fillText() {},
      lineTo() {},
      moveTo() {},
      restore() {},
      save() {},
      stroke() {},
      set fillStyle(_value) {},
      set font(_value) {},
      set lineCap(_value) {},
      set lineWidth(_value) {},
      set strokeStyle(_value) {},
      set textAlign(_value) {},
      set textBaseline(_value) {}
    }
    return context
  }
}
