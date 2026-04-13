import test from 'node:test'
import assert from 'node:assert/strict'
import { createPopupController } from '../../shared/src/popup-controller.js'
import { createInvalidExtensionAuthError } from '../../shared/src/auth-errors.js'
import {
  createFakeBanner,
  createFakeButton,
  createFakeCard,
  createFakeList,
  createFakeText
} from './test-helpers.mjs'

function createPopupElements() {
  return {
    attentionCount: createFakeText(),
    connectionCopy: createFakeText(),
    errorList: createFakeList(),
    healthyState: createFakeText(),
    metricDuplicates: createFakeText(),
    metricErrors: createFakeText(),
    metricFetched: createFakeText(),
    metricImported: createFakeText(),
    openAppButton: createFakeButton('Open InboxBridge'),
    openRemoteButton: createFakeButton('Open InboxBridge Go'),
    openSettingsButton: createFakeButton('Settings'),
    popupStatus: createFakeBanner(),
    refreshStatusButton: createFakeButton('Refresh'),
    runPollButton: createFakeButton('Run poll now'),
    summaryCard: createFakeCard('summary-card'),
    statusPill: createFakeText(),
    updatedAt: createFakeText()
  }
}

test('popup controller renders disconnected state when config is missing', async () => {
  const elements = createPopupElements()
  let popupMessageListener = null
  let openedSettings = 0
  const controller = createPopupController({
    deps: {
      clearStatusBanner(target) {
        target.hidden = true
      },
      deriveStatusView() {
        throw new Error('should not derive view without config')
      },
      disconnectedView(message) {
        return {
          attentionCount: '0',
          connectionCopy: message,
          errorSources: [],
          healthy: true,
          metrics: { imported: '-', fetched: '-', duplicates: '-', errors: '-' },
          runPollDisabled: true,
          statusLabel: 'Not connected',
          statusTone: 'warning',
          updatedText: 'Not configured'
        }
      },
      escapeHtml(value) {
        return value
      },
      fetchStatus() {
        throw new Error('should not fetch without config')
      },
      localizePopupPage() {},
      loadConfig: async () => null,
      onMessage(listener) {
        popupMessageListener = listener
      },
      openOptionsPage: async () => {
        openedSettings += 1
      },
      openTab: async () => {},
      resolveLanguagePreference: () => 'en',
      runPoll: async () => ({}),
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      showStatusBanner() {},
      targetDocument: {},
      translate: (_locale, key) => {
        if (key === 'popup.openSettingsToSignIn') return 'Open Settings to sign in and connect this browser extension to InboxBridge.'
        if (key === 'popup.signIn') return 'Sign in'
        return key
      }
    },
    elements
  })

  controller.initialize()
  await controller.refreshPopup()
  popupMessageListener?.({ type: 'noop' })

  assert.equal(elements.connectionCopy.textContent, 'Open Settings to sign in and connect this browser extension to InboxBridge.')
  assert.equal(elements.runPollButton.disabled, false)
  assert.equal(elements.runPollButton.textContent, 'Sign in')
  await elements.runPollButton.click()
  assert.equal(openedSettings, 1)
})

test('popup controller renders attention items and triggers configured tabs', async () => {
  const elements = createPopupElements()
  const openedTabs = []
  let popupMessageListener = null
  const controller = createPopupController({
    deps: {
      clearStatusBanner() {},
      deriveStatusView(serverUrl, status) {
        assert.equal(serverUrl, 'https://mail.example.com')
        return {
          attentionCount: '1 source',
          connectionCopy: `Connected to ${status.user.username}`,
          errorSources: [{ sourceId: 'acc-1', label: 'Primary', lastError: 'Needs attention' }],
          healthy: false,
          metrics: { imported: '4', fetched: '5', duplicates: '1', errors: '1' },
          runPollDisabled: false,
          statusLabel: 'Needs attention',
          statusTone: 'warning',
          updatedText: 'just now'
        }
      },
      disconnectedView() {
        throw new Error('should not disconnect')
      },
      escapeHtml(value) {
        return String(value).replace('Needs attention', 'Needs attention')
      },
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      localizePopupPage() {},
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', token: 'ibx_token' }),
      onMessage(listener) {
        popupMessageListener = listener
      },
      openOptionsPage: async () => {},
      openTab: async (url) => {
        openedTabs.push(url)
      },
      resolveLanguagePreference: () => 'en',
      runPoll: async () => ({ accepted: true, started: true, message: 'Started' }),
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      showStatusBanner() {},
      targetDocument: {},
      translate: (_locale, key) => key
    },
    elements
  })

  controller.initialize()
  await controller.refreshPopup()
  await elements.openAppButton.click()
  await elements.openRemoteButton.click()
  popupMessageListener({
    type: 'extension-status-updated',
    serverUrl: 'https://mail.example.com',
    status: { user: { username: 'alice' } }
  })

  assert.equal(elements.connectionCopy.textContent, 'Connected to alice')
  assert.equal(elements.errorList.hidden, false)
  assert.equal(elements.errorList.items.length, 1)
  assert.deepEqual(openedTabs, ['https://mail.example.com', 'https://mail.example.com/remote'])
})

test('popup controller shows explicit feedback while a manual refresh is running', async () => {
  const elements = createPopupElements()
  let resolveFetch
  let popupMessageListener = null
  const controller = createPopupController({
    deps: {
      clearStatusBanner(target) {
        target.hidden = true
        target.textContent = ''
      },
      deriveStatusView() {
        return {
          attentionCount: '',
          connectionCopy: 'Connected to alice',
          errorSources: [],
          healthy: true,
          metrics: { imported: '4', fetched: '5', duplicates: '1', errors: '0' },
          runPollDisabled: false,
          statusLabel: 'Healthy',
          statusTone: 'success',
          updatedText: 'just now'
        }
      },
      disconnectedView(message) {
        return {
          attentionCount: '',
          connectionCopy: message,
          errorSources: [],
          healthy: true,
          metrics: { imported: '-', fetched: '-', duplicates: '-', errors: '-' },
          runPollDisabled: true,
          statusLabel: 'Disconnected',
          statusTone: 'neutral',
          updatedText: 'No InboxBridge status available'
        }
      },
      escapeHtml(value) {
        return value
      },
      fetchStatus: () => new Promise((resolve) => {
        resolveFetch = resolve
      }),
      localizePopupPage() {},
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', token: 'ibx_token' }),
      onMessage(listener) {
        popupMessageListener = listener
      },
      openOptionsPage: async () => {},
      openTab: async () => {},
      resolveLanguagePreference: () => 'en',
      runPoll: async () => ({}),
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      showStatusBanner(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      targetDocument: {},
      translate: (_locale, key) => {
        const values = {
          'status.refreshing': 'Refreshing…',
          'popup.refreshSuccess': 'InboxBridge status refreshed.'
        }
        return values[key] || key
      }
    },
    elements
  })

  controller.initialize()
  const clickPromise = elements.refreshStatusButton.click()
  await new Promise((resolve) => setImmediate(resolve))

  assert.equal(elements.refreshStatusButton.disabled, true)
  assert.equal(elements.refreshStatusButton.textContent, 'Refreshing…')
  assert.equal(elements.statusPill.textContent, 'Refreshing…')
  assert.match(elements.summaryCard.className, /is-refreshing/)

  resolveFetch({ user: { username: 'alice' } })
  await clickPromise

  assert.equal(elements.refreshStatusButton.disabled, false)
  assert.equal(elements.refreshStatusButton.textContent, 'Refresh')
  assert.equal(elements.popupStatus.textContent, 'InboxBridge status refreshed.')
  assert.doesNotMatch(elements.summaryCard.className, /is-refreshing/)

  popupMessageListener({
    type: 'extension-status-updated',
    serverUrl: 'https://mail.example.com',
    status: { user: { username: 'alice' } }
  })
  assert.equal(elements.connectionCopy.textContent, 'Connected to alice')
})

test('popup controller marks extension-started manual polls for later browser notifications', async () => {
  const elements = createPopupElements()
  const messages = []
  let deriveCalls = 0
  let resolveRunPoll
  const controller = createPopupController({
    deps: {
      clearStatusBanner() {},
      deriveStatusView(_serverUrl, status) {
        deriveCalls += 1
        return {
          attentionCount: '',
          connectionCopy: 'Connected to alice',
          errorSources: [],
          healthy: true,
          metrics: { imported: '0', fetched: '0', duplicates: '0', errors: '0' },
          runPollDisabled: Boolean(status?.poll?.running),
          statusLabel: status?.poll?.running ? 'Polling' : 'Healthy',
          statusTone: 'success',
          updatedText: status?.poll?.running ? 'Polling now' : 'just now'
        }
      },
      disconnectedView(message) {
        return {
          attentionCount: '',
          connectionCopy: message,
          errorSources: [],
          healthy: true,
          metrics: { imported: '-', fetched: '-', duplicates: '-', errors: '-' },
          runPollDisabled: true,
          statusLabel: 'Disconnected',
          statusTone: 'neutral',
          updatedText: 'No InboxBridge status available'
        }
      },
      escapeHtml(value) {
        return value
      },
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      localizePopupPage() {},
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', token: 'ibx_token' }),
      onMessage() {},
      openOptionsPage: async () => {},
      openTab: async () => {},
      resolveLanguagePreference: () => 'en',
      runPoll: () => new Promise((resolve) => {
        resolveRunPoll = () => resolve({ accepted: true, started: true, message: 'Started' })
      }),
      saveUserPreferences: async () => {},
      sendMessage: async (message) => {
        messages.push(message)
      },
      showStatusBanner() {},
      targetDocument: {},
      translate: (_locale, key) => key
    },
    elements
  })

  controller.initialize()
  await controller.refreshPopup()
  const startPollPromise = controller.startPoll()
  await new Promise((resolve) => setImmediate(resolve))

  assert.deepEqual(messages, [
    { type: 'refresh-status' },
    { type: 'manual-poll-triggered', serverUrl: 'https://mail.example.com' }
  ])
  assert.equal(elements.statusPill.textContent, 'Polling')
  assert.equal(elements.updatedAt.textContent, 'Polling now')

  resolveRunPoll()
  await startPollPromise

  assert.equal(elements.statusPill.textContent, 'Polling')
  assert.equal(elements.updatedAt.textContent, 'Polling now')
  assert.equal(deriveCalls >= 2, true)
})

test('popup controller clears saved auth and shows sign-in state after a revoked token response', async () => {
  const elements = createPopupElements()
  let cleared = 0
  const messages = []
  const controller = createPopupController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {
        cleared += 1
      },
      clearStatusBanner() {},
      deriveStatusView() {
        return {
          attentionCount: '',
          connectionCopy: 'Connected',
          errorSources: [],
          healthy: true,
          metrics: { imported: '0', fetched: '0', duplicates: '0', errors: '0' },
          runPollDisabled: false,
          statusLabel: 'Healthy',
          statusTone: 'success',
          updatedText: 'just now'
        }
      },
      disconnectedView(message) {
        return {
          attentionCount: '',
          connectionCopy: message,
          errorSources: [],
          healthy: true,
          metrics: { imported: '-', fetched: '-', duplicates: '-', errors: '-' },
          runPollDisabled: true,
          statusLabel: 'Disconnected',
          statusTone: 'neutral',
          updatedText: 'No InboxBridge status available'
        }
      },
      escapeHtml(value) {
        return value
      },
      fetchStatus: async () => {
        throw createInvalidExtensionAuthError()
      },
      localizePopupPage() {},
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', token: 'ibx_token' }),
      onMessage() {},
      openOptionsPage: async () => {},
      openTab: async () => {},
      resolveLanguagePreference: () => 'en',
      runPoll: async () => ({ accepted: true, started: true }),
      saveUserPreferences: async () => {},
      sendMessage: async (message) => {
        messages.push(message)
      },
      showStatusBanner(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      targetDocument: {},
      translate: (_locale, key) => {
        if (key === 'popup.openSettingsToSignIn') return 'Open Settings to sign in and connect this browser extension to InboxBridge.'
        if (key === 'popup.signIn') return 'Sign in'
        return key
      }
    },
    elements
  })

  controller.initialize()
  await controller.refreshPopup()

  assert.equal(cleared, 1)
  assert.equal(elements.runPollButton.textContent, 'Sign in')
  assert.equal(elements.connectionCopy.textContent, 'Open Settings to sign in and connect this browser extension to InboxBridge.')
  assert.equal(elements.popupStatus.textContent, 'The saved InboxBridge sign-in is no longer valid.')
  assert.deepEqual(messages, [
    { type: 'refresh-status' },
    { type: 'refresh-context-menus' }
  ])
})
