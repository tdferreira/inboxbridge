import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const manifestPath = resolve('src/manifest.json')

test('firefox manifest declares gecko metadata and background scripts', async () => {
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'))

  assert.equal(manifest.manifest_version, 3)
  assert.deepEqual(manifest.background, {
    scripts: ['background.js'],
    type: 'module'
  })
  assert.deepEqual(manifest.permissions, ['storage', 'alarms', 'contextMenus', 'notifications'])
  assert.deepEqual(manifest.optional_permissions, ['tabs'])
  assert.deepEqual(manifest.optional_host_permissions, ['https://*/*'])
  assert.deepEqual(manifest.icons, {
    16: 'icon16.png',
    32: 'icon32.png',
    48: 'icon48.png',
    128: 'icon128.png'
  })
  assert.deepEqual(manifest.action.default_icon, {
    16: 'icon16.png',
    32: 'icon32.png',
    48: 'icon48.png',
    128: 'icon128.png'
  })
  assert.equal(manifest.browser_specific_settings?.gecko?.id, 'inboxbridge@extensions.inboxbridge.dev')
  assert.equal(manifest.browser_specific_settings?.gecko?.strict_min_version, '121.0')
})

test('firefox source icon artwork matches the /remote PWA icon', async () => {
  const [extensionIcon, remoteIcon] = await Promise.all([
    readFile(resolve('src/remote-icon.svg'), 'utf8'),
    readFile(resolve('../../admin-ui/public/remote-icon.svg'), 'utf8')
  ])

  assert.equal(extensionIcon, remoteIcon)
})
