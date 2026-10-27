import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'

// Reuse the frontend's declared DOM dependency without adding a site toolchain.
const require = createRequire(new URL('../admin-ui/package.json', import.meta.url))
const { JSDOM } = require('jsdom')
const siteHtml = readFileSync(new URL('./index.html', import.meta.url), 'utf8')

function mountSite(t) {
  const dom = new JSDOM(siteHtml, { url: 'https://example.test/' })
  // Browsers expose named form controls as properties; jsdom needs this shim.
  const form = dom.window.document.querySelector('[data-env-generator]')
  for (const control of form.elements) {
    for (const name of new Set([control.id, control.name].filter(Boolean))) {
      if (!(name in form)) Object.defineProperty(form, name, { value: control, configurable: true })
    }
  }
  const globals = {
    window: dom.window,
    document: dom.window.document,
    navigator: dom.window.navigator,
    Event: dom.window.Event,
    KeyboardEvent: dom.window.KeyboardEvent
  }
  const descriptors = new Map(Object.keys(globals).map((name) => [name, Object.getOwnPropertyDescriptor(globalThis, name)]))
  for (const [name, value] of Object.entries(globals)) {
    Object.defineProperty(globalThis, name, { value, configurable: true })
  }
  t.after(() => {
    dom.window.close()
    for (const [name, descriptor] of descriptors) {
      if (descriptor) Object.defineProperty(globalThis, name, descriptor)
      else delete globalThis[name]
    }
  })
  return dom.window.document
}

test('stored folder names remain literal in both public-site pillboxes', async (t) => {
  const document = mountSite(t)
  const folder = '<img src=x onerror="window.folderInjected=true">'
  const spamFolder = '<svg onload="window.folderInjected=true">'
  document.getElementById('sourceFolder').value = `INBOX,${folder}`
  document.getElementById('sourceSpamJunkSourceFolder').value = spamFolder

  await import('./app.js?pillbox-regression=stored')

  const values = document.querySelector('[data-pillbox-hidden-input="sourceFolder"] [data-pillbox-values]')
  const spamValues = document.querySelector('[data-pillbox-hidden-input="sourceSpamJunkSourceFolder"] [data-pillbox-values]')
  assert.equal(values.querySelector('img, svg, script'), null)
  assert.equal(spamValues.querySelector('img, svg, script'), null)
  assert.deepEqual([...values.querySelectorAll('.site-pillbox-pill > span')].map((node) => node.textContent), ['INBOX', folder])
  assert.equal(spamValues.querySelector('.site-pillbox-pill > span').textContent, spamFolder)
})

test('typed markup stays literal through rendering and removal', async (t) => {
  const document = mountSite(t)
  await import('./app.js?pillbox-regression=typed')
  const root = document.querySelector('[data-pillbox-hidden-input="sourceFolder"]')
  const input = root.querySelector('[data-pillbox-input]')
  const folder = '<img src=x onerror="window.folderInjected=true">'
  input.value = folder
  input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))

  const values = root.querySelector('[data-pillbox-values]')
  assert.equal(values.querySelector('img, svg, script'), null)
  const pill = [...values.querySelectorAll('.site-pillbox-pill')].find((node) => node.firstElementChild.textContent === folder)
  assert.ok(pill)
  assert.ok(document.getElementById('sourceFolder').value.includes(folder))
  pill.querySelector('button').click()
  assert.equal(document.getElementById('sourceFolder').value.includes(folder), false)
  assert.equal(values.querySelector('img, svg, script'), null)
})
