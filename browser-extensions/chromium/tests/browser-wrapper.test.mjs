import test from 'node:test'
import assert from 'node:assert/strict'

test('browser wrapper prefers the browser namespace when available', async () => {
  const marker = { runtime: { marker: 'browser-runtime' } }
  globalThis.browser = marker
  globalThis.chrome = { runtime: { marker: 'chrome-runtime' } }

  const module = await import(`../../shared/src/browser.js?browser-wrapper=${Date.now()}`)

  assert.equal(module.browserRuntime(), marker.runtime)

  delete globalThis.browser
  delete globalThis.chrome
})

test('browser wrapper includes the failing operation in runtime.lastError messages', async () => {
  let shouldFail = false
  globalThis.chrome = {
    runtime: {
      get lastError() {
        return shouldFail ? { message: 'Cannot create item with duplicate id inboxbridge-open-settings' } : null
      }
    },
    contextMenus: {
      create(_options, done) {
        done()
      }
    }
  }

  const module = await import(`../../shared/src/browser.js?browser-wrapper-error=${Date.now()}`)
  shouldFail = true

  await assert.rejects(
    module.createContextMenu({ id: 'inboxbridge-open-settings', title: 'Open settings', contexts: ['action'] }),
    /contextMenus\.create failed: Cannot create item with duplicate id inboxbridge-open-settings/
  )

  delete globalThis.chrome
})
