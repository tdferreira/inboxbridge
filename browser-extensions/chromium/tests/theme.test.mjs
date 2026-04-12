import test from 'node:test'
import assert from 'node:assert/strict'

import {
  applyThemePreference,
  normalizeThemePreference,
  resolveThemePreference
} from '../../shared/src/theme.js'

test('normalizeThemePreference keeps the explicit extension theme variants and maps legacy values', () => {
  assert.equal(normalizeThemePreference('light-green'), 'light-green')
  assert.equal(normalizeThemePreference('light-blue'), 'light-blue')
  assert.equal(normalizeThemePreference('dark-green'), 'dark-green')
  assert.equal(normalizeThemePreference('dark-blue'), 'dark-blue')
  assert.equal(normalizeThemePreference('light'), 'light-green')
  assert.equal(normalizeThemePreference('dark'), 'dark-blue')
})

test('resolveThemePreference follows InboxBridge user theme variants when configured to follow the user', () => {
  assert.deepEqual(resolveThemePreference('user', 'LIGHT_GREEN'), { mode: 'light', variant: 'green' })
  assert.deepEqual(resolveThemePreference('user', 'LIGHT_BLUE'), { mode: 'light', variant: 'blue' })
  assert.deepEqual(resolveThemePreference('user', 'DARK_GREEN'), { mode: 'dark', variant: 'green' })
  assert.deepEqual(resolveThemePreference('user', 'DARK_BLUE'), { mode: 'dark', variant: 'blue' })
})

test('applyThemePreference writes both the mode and variant datasets', () => {
  const targetDocument = { documentElement: { dataset: {} } }

  applyThemePreference(targetDocument, 'dark-green')
  assert.equal(targetDocument.documentElement.dataset.theme, 'dark')
  assert.equal(targetDocument.documentElement.dataset.themeVariant, 'green')

  applyThemePreference(targetDocument, 'user', 'LIGHT_BLUE')
  assert.equal(targetDocument.documentElement.dataset.theme, 'light')
  assert.equal(targetDocument.documentElement.dataset.themeVariant, 'blue')
})
