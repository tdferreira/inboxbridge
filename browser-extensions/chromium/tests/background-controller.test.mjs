import test from 'node:test'
import assert from 'node:assert/strict'
import { registerBackgroundController } from '../../shared/src/background-controller.js'

test('background controller registers listeners and refreshes badge from server', async () => {
  let installedListener
  let alarmListener
  let messageListener
  let alarmCreated = null
  let badgeApplied = null
  let liveStreamRequest = null
  let iconApplied = []

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async (status, errorMessage = null) => {
        badgeApplied = { status, errorMessage }
      },
      applyIcon: async (status, errorState = null) => {
        iconApplied.push({ status, errorState })
      },
      clearBadge: async () => {},
      createNotification: async () => {},
      createAlarm(name, delayInMinutes, periodInMinutes) {
        alarmCreated = { name, delayInMinutes, periodInMinutes }
      },
      fetchStatus: async () => ({ summary: { healthySources: 2 } }),
      loadCachedStatus: async () => ({ status: { summary: { healthySources: 1 } } }),
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', token: 'ibx_token' }),
      onAlarm(listener) {
        alarmListener = listener
      },
      onInstalled(listener) {
        installedListener = listener
      },
      onMessage(listener) {
        messageListener = listener
      },
      saveCachedStatus: async () => {},
      sendMessage: async () => {},
      resolveLanguagePreference: () => 'en',
      mergeLivePollIntoStatus: (status, poll) => ({ ...status, poll }),
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents(serverUrl, token, handlers) {
        liveStreamRequest = { serverUrl, token, handlers }
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key, params = {}) => {
        const values = {
          'settings.openPrompt': 'Open settings',
          'popup.runPoll': 'Run poll now'
        }
        return values[key] || key.replace('{count}', params.count ?? '')
      }
    }
  })

  await new Promise((resolve) => setImmediate(resolve))
  installedListener()
  assert.deepEqual(alarmCreated, { name: 'refresh-status', delayInMinutes: 0.1, periodInMinutes: 5 })
  assert.equal(liveStreamRequest.serverUrl, 'https://mail.example.com')
  assert.equal(liveStreamRequest.token, 'ibx_token')

  await alarmListener({ name: 'refresh-status' })
  assert.deepEqual(badgeApplied, {
    status: { summary: { healthySources: 2 } },
    errorMessage: null
  })
  assert.deepEqual(iconApplied.at(-1), {
    status: { summary: { healthySources: 2 } },
    errorState: null
  })

  let responsePayload = null
  const keepChannelOpen = messageListener({ type: 'badge-from-cache' }, null, (payload) => {
    responsePayload = payload
  })
  assert.equal(keepChannelOpen, true)
  await new Promise((resolve) => setImmediate(resolve))
  assert.deepEqual(responsePayload, { ok: true })

  await controller.refreshBadgeFromServer()
})

test('background controller overlays live polling updates into the cached badge state', async () => {
  let messageListener
  let appliedStatuses = []
  let savedStatuses = []
  let liveHandlers = null
  let broadcasts = []
  let iconStates = []

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async (status) => {
        appliedStatuses.push(status)
      },
      applyIcon: async (status, errorState = null) => {
        iconStates.push({ status, errorState })
      },
      clearBadge: async () => {},
      createNotification: async () => {},
      createAlarm() {},
      fetchStatus: async () => ({ poll: { running: false }, sources: [{ sourceId: 'beta', status: 'SUCCESS' }] }),
      loadCachedStatus: async () => ({ status: { poll: { running: false }, sources: [{ sourceId: 'beta', status: 'SUCCESS' }] } }),
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', token: 'ibx_token' }),
      mergeLivePollIntoStatus: (status, poll) => ({
        ...status,
        poll,
        sources: status.sources.map((source) => source.sourceId === 'beta' ? { ...source, status: 'RUNNING' } : source)
      }),
      onAlarm() {},
      onInstalled() {},
      onMessage(listener) {
        messageListener = listener
      },
      saveCachedStatus: async (status) => {
        savedStatuses.push(status)
      },
      sendMessage: async (message) => {
        broadcasts.push(message)
      },
      resolveLanguagePreference: () => 'en',
      shouldOverlayLiveStatus: (event) => event.type === 'poll-source-started',
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents(_serverUrl, _token, handlers) {
        liveHandlers = handlers
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key, params = {}) => {
        const values = {
          'settings.openPrompt': 'Open settings',
          'popup.runPoll': 'Run poll now'
        }
        return values[key] || key.replace('{count}', params.count ?? '')
      }
    }
  })

  await new Promise((resolve) => setImmediate(resolve))
  liveHandlers.onEvent({
    type: 'poll-source-started',
    poll: {
      activeSourceId: 'beta',
      running: true,
      state: 'RUNNING',
      updatedAt: '2026-04-12T18:10:00Z',
      sources: [{ sourceId: 'beta', state: 'RUNNING' }]
    }
  })
  await new Promise((resolve) => setImmediate(resolve))

  assert.equal(savedStatuses.at(-1).poll.running, true)
  assert.equal(savedStatuses.at(-1).sources[0].status, 'RUNNING')
  assert.equal(appliedStatuses.at(-1).poll.running, true)
  assert.equal(iconStates.at(-1).status.poll.running, true)
  assert.equal(broadcasts.at(-1).type, 'extension-status-updated')

  let responsePayload = null
  messageListener({ type: 'refresh-status' }, null, (payload) => {
    responsePayload = payload
  })
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(responsePayload.ok, true)
})

test('background controller keeps the last good badge when popup broadcasting fails', async () => {
  let appliedStatuses = []
  let iconStates = []

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async (status, errorMessage = null) => {
        appliedStatuses.push({ status, errorMessage })
      },
      applyIcon: async (status, errorState = null) => {
        iconStates.push({ status, errorState })
      },
      clearBadge: async () => {},
      createNotification: async () => {},
      createAlarm() {},
      fetchStatus: async () => ({ summary: { healthySources: 3 } }),
      loadCachedStatus: async () => ({ status: null }),
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', token: 'ibx_token' }),
      mergeLivePollIntoStatus: (status) => status,
      onAlarm() {},
      onInstalled() {},
      onMessage() {},
      saveCachedStatus: async () => {},
      sendMessage: async () => {
        throw new Error('popup disconnected')
      },
      resolveLanguagePreference: () => 'en',
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents() {
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key, params = {}) => {
        const values = {
          'settings.openPrompt': 'Open settings',
          'popup.runPoll': 'Run poll now'
        }
        return values[key] || key.replace('{count}', params.count ?? '')
      }
    }
  })

  await new Promise((resolve) => setImmediate(resolve))
  const payload = await controller.refreshBadgeFromServer()

  assert.deepEqual(payload, { summary: { healthySources: 3 } })
  assert.deepEqual(appliedStatuses.at(-1), {
    status: { summary: { healthySources: 3 } },
    errorMessage: null
  })
  assert.deepEqual(iconStates.at(-1), {
    status: { summary: { healthySources: 3 } },
    errorState: null
  })
  assert.equal(appliedStatuses.some((entry) => entry.errorMessage === 'popup disconnected'), false)
})

test('background controller marks signed-out toolbar state when extension auth is missing', async () => {
  let iconState = null
  let clearedTitle = null

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async () => {},
      applyIcon: async (_status, errorState = null) => {
        iconState = errorState
      },
      clearBadge: async (title) => {
        clearedTitle = title
      },
      createNotification: async () => {},
      createAlarm() {},
      fetchStatus: async () => {
        throw new Error('should not fetch')
      },
      loadCachedStatus: async () => null,
      loadConfig: async () => ({ serverUrl: '', token: '' }),
      mergeLivePollIntoStatus: (status) => status,
      onAlarm() {},
      onInstalled() {},
      onMessage() {},
      saveCachedStatus: async () => {},
      sendMessage: async () => {},
      resolveLanguagePreference: () => 'en',
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents() {
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key, params = {}) => {
        const values = {
          'settings.openPrompt': 'Open settings',
          'popup.runPoll': 'Run poll now'
        }
        return values[key] || key.replace('{count}', params.count ?? '')
      }
    }
  })

  await controller.refreshBadgeFromServer()

  assert.equal(clearedTitle, 'InboxBridge is not configured')
  assert.deepEqual(iconState, {
    kind: 'signed-out',
    message: 'Open Settings to sign in and connect this browser extension to InboxBridge.'
  })
})

test('background controller registers browser-action context menu entries and can trigger polling', async () => {
  let contextMenuListener
  let messageListener
  const createdMenus = []
  const pollRequests = []
  let optionsOpened = 0
  let currentLanguage = 'en'
  const scheduled = []

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async () => {},
      applyIcon: async () => {},
      clearTimeoutFn() {},
      clearBadge: async () => {},
      createAlarm() {},
      createContextMenu: async (options) => {
        createdMenus.push(options)
      },
      createNotification: async () => {},
      fetchStatus: async () => ({
        poll: { running: false },
        summary: { lastCompletedRunAt: '2026-04-12T18:30:00Z' },
        sources: []
      }),
      loadCachedStatus: async () => ({ status: null }),
      loadConfig: async () => ({
        notifyManualPollSuccess: true,
        serverUrl: 'https://mail.example.com',
        token: 'ibx_token'
      }),
      mergeLivePollIntoStatus: (status) => status,
      onAlarm() {},
      onContextMenuClicked(listener) {
        contextMenuListener = listener
      },
      onInstalled() {},
      onMessage(listener) {
        messageListener = listener
      },
      openOptionsPage: async () => {
        optionsOpened += 1
      },
      removeAllContextMenus: async () => {
        createdMenus.length = 0
      },
      runPoll: async (serverUrl, token) => {
        pollRequests.push({ serverUrl, token })
        return { accepted: true, started: true }
      },
      saveCachedStatus: async () => {},
      saveUserPreferences: async (user) => {
        currentLanguage = user?.language || currentLanguage
      },
      sendMessage: async () => {},
      setTimeoutFn(fn) {
        scheduled.push(fn)
        return scheduled.length
      },
      resolveLanguagePreference: (config) => config?.language || config?.userLanguage || currentLanguage || 'en',
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents() {
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key, params = {}) => {
        const values = {
          'settings.openPrompt': 'Open settings',
          'popup.runPoll': 'Run poll now'
        }
        const frenchValues = {
          'settings.openPrompt': 'Ouvrir les paramètres',
          'popup.runPoll': 'Lancer un polling'
        }
        if (_locale === 'fr') {
          return frenchValues[key] || key.replace('{count}', params.count ?? '')
        }
        return values[key] || key.replace('{count}', params.count ?? '')
      }
    }
  })

  await new Promise((resolve) => setImmediate(resolve))
  assert.deepEqual(createdMenus, [
    { contexts: ['action'], id: 'inboxbridge-open-settings', title: 'Open settings' },
    { contexts: ['action'], id: 'inboxbridge-run-poll', title: 'Run poll now' }
  ])

  await contextMenuListener({ menuItemId: 'inboxbridge-open-settings' })
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(optionsOpened, 1)

  await contextMenuListener({ menuItemId: 'inboxbridge-run-poll' })
  await new Promise((resolve) => setImmediate(resolve))
  assert.deepEqual(pollRequests, [{ serverUrl: 'https://mail.example.com', token: 'ibx_token' }])

  currentLanguage = 'fr'
  let refreshMenusResponse = null
  const keepChannelOpen = messageListener({ type: 'refresh-context-menus' }, null, (payload) => {
    refreshMenusResponse = payload
  })
  assert.equal(keepChannelOpen, true)
  await new Promise((resolve) => setImmediate(resolve))
  assert.deepEqual(refreshMenusResponse, { ok: true })
  assert.deepEqual(createdMenus, [
    { contexts: ['action'], id: 'inboxbridge-open-settings', title: 'Ouvrir les paramètres' },
    { contexts: ['action'], id: 'inboxbridge-run-poll', title: 'Lancer un polling' }
  ])

  await controller.triggerManualPollFromBackground()
  assert.equal(pollRequests.length, 2)
})

test('background controller applies an optimistic running state as soon as the popup reports a manual poll trigger', async () => {
  let messageListener
  const appliedStatuses = []
  const broadcasts = []

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async (status) => {
        appliedStatuses.push(status)
      },
      applyIcon: async () => {},
      clearBadge: async () => {},
      createAlarm() {},
      createContextMenu: async () => {},
      createNotification: async () => {},
      fetchStatus: async () => ({
        poll: { running: false, canRun: true, state: 'IDLE' },
        summary: { errorSourceCount: 0, lastCompletedRunAt: '2026-04-12T18:30:00Z' },
        sources: []
      }),
      loadCachedStatus: async () => ({
        status: {
          poll: { running: false, canRun: true, state: 'IDLE' },
          summary: { errorSourceCount: 0, lastCompletedRunAt: '2026-04-12T18:30:00Z' },
          sources: []
        }
      }),
      loadConfig: async () => ({
        serverUrl: 'https://mail.example.com',
        token: 'ibx_token'
      }),
      mergeLivePollIntoStatus: (status) => status,
      onAlarm() {},
      onContextMenuClicked() {},
      onInstalled() {},
      onMessage(listener) {
        messageListener = listener
      },
      openOptionsPage: async () => {},
      removeAllContextMenus: async () => {},
      runPoll: async () => ({ accepted: true, started: true }),
      saveCachedStatus: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async (message) => {
        broadcasts.push(message)
      },
      setTimeoutFn() {
        return 1
      },
      resolveLanguagePreference: () => 'en',
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents() {
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key) => key
    }
  })

  await new Promise((resolve) => setImmediate(resolve))
  await controller.refreshBadgeFromServer()
  let response = null
  messageListener({ type: 'manual-poll-triggered', serverUrl: 'https://mail.example.com' }, null, (payload) => {
    response = payload
  })
  await new Promise((resolve) => setImmediate(resolve))
  await new Promise((resolve) => setImmediate(resolve))

  assert.deepEqual(response, { ok: true })
  assert.equal(appliedStatuses.some((status) => status?.poll?.running === true), true)
  assert.equal(broadcasts.some((message) => message?.type === 'extension-status-updated' && message?.status?.poll?.running === true), true)
})

test('background controller avoids recreating duplicate context menu ids when refreshes overlap', async () => {
  const createdMenus = []
  let releaseCreate
  const createGate = new Promise((resolve) => {
    releaseCreate = resolve
  })

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async () => {},
      applyIcon: async () => {},
      clearBadge: async () => {},
      createAlarm() {},
      createContextMenu: async (options) => {
        createdMenus.push(options)
        await createGate
      },
      createNotification: async () => {},
      fetchStatus: async () => ({
        poll: { running: false },
        summary: { lastCompletedRunAt: '2026-04-12T18:30:00Z' },
        sources: []
      }),
      loadCachedStatus: async () => ({ status: null }),
      loadConfig: async () => ({
        language: 'en',
        serverUrl: 'https://mail.example.com',
        token: 'ibx_token'
      }),
      mergeLivePollIntoStatus: (status) => status,
      onAlarm() {},
      onContextMenuClicked() {},
      onInstalled() {},
      onMessage() {},
      openOptionsPage: async () => {},
      removeAllContextMenus: async () => {},
      runPoll: async () => ({ accepted: true, started: true }),
      saveCachedStatus: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      resolveLanguagePreference: (config) => config.language || 'en',
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents() {
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key) => {
        const values = {
          'settings.openPrompt': 'Open settings',
          'popup.runPoll': 'Run poll now'
        }
        return values[key] || key
      }
    }
  })

  const first = controller.configureContextMenus()
  const second = controller.configureContextMenus()
  releaseCreate()
  await Promise.all([first, second])

  assert.deepEqual(createdMenus, [
    { contexts: ['action'], id: 'inboxbridge-open-settings', title: 'Open settings' },
    { contexts: ['action'], id: 'inboxbridge-run-poll', title: 'Run poll now' }
  ])

  await controller.configureContextMenus()
  assert.equal(createdMenus.length, 2)
})

test('background controller groups error notifications after the initial status load', async () => {
  const notifications = []
  let liveHandlers = null
  let refreshCount = 0

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async () => {},
      applyIcon: async () => {},
      clearBadge: async () => {},
      createNotification: async (_id, options) => {
        notifications.push(options)
      },
      createAlarm() {},
      fetchStatus: async () => {
        refreshCount += 1
        return {
          poll: { running: false },
          summary: { errorSourceCount: refreshCount === 1 ? 1 : 2 },
          sources: refreshCount === 1
            ? [{ sourceId: 'alpha', label: 'Primary inbox', needsAttention: true }]
            : [
                { sourceId: 'alpha', label: 'Primary inbox', needsAttention: true },
                { sourceId: 'beta', label: 'Archive mailbox', needsAttention: true }
              ]
        }
      },
      loadCachedStatus: async () => ({ status: { poll: { running: true }, sources: [] } }),
      loadConfig: async () => ({
        notifyErrors: true,
        serverUrl: 'https://mail.example.com',
        token: 'ibx_token'
      }),
      mergeLivePollIntoStatus: (status) => status,
      onAlarm() {},
      onInstalled() {},
      onMessage() {},
      saveCachedStatus: async () => {},
      sendMessage: async () => {},
      resolveLanguagePreference: () => 'en',
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: (event) => event.type === 'poll-run-finished',
      subscribeExtensionEvents(_serverUrl, _token, handlers) {
        liveHandlers = handlers
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key, params = {}) => {
        const values = {
          'notifications.errorTitle': 'InboxBridge needs attention',
          'notifications.errorBody': `${params.count} source errors: ${params.sources}`,
          'notifications.moreSources': `+${params.count} more`
        }
        return values[key] || key
      }
    }
  })

  await controller.refreshBadgeFromServer()
  assert.equal(notifications.length, 0)

  liveHandlers.onEvent({ type: 'poll-run-finished' })
  await new Promise((resolve) => setImmediate(resolve))

  assert.equal(notifications.length, 1)
  assert.equal(notifications[0].title, 'InboxBridge needs attention')
  assert.match(notifications[0].message, /Primary inbox/)
})

test('background controller shows a completion notification for manual polls started from the extension', async () => {
  let messageListener
  const notifications = []
  let refreshCount = 0
  const scheduled = []

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async () => {},
      applyIcon: async () => {},
      clearTimeoutFn() {},
      clearBadge: async () => {},
      createNotification: async (_id, options) => {
        notifications.push(options)
      },
      createAlarm() {},
      fetchStatus: async () => {
        refreshCount += 1
        return {
          poll: { running: false },
          summary: {
            errorSourceCount: 0,
            lastCompletedRunAt: refreshCount === 1 ? '2026-04-12T18:30:00Z' : '2026-04-12T18:40:00Z',
            lastCompletedRun: {
              fetched: 7,
              imported: 5,
              errors: 0
            }
          }
          ,
          sources: []
        }
      },
      loadCachedStatus: async () => ({ status: null }),
      loadConfig: async () => ({
        notifyManualPollSuccess: true,
        serverUrl: 'https://mail.example.com',
        token: 'ibx_token'
      }),
      mergeLivePollIntoStatus: (status) => status,
      onAlarm() {},
      onInstalled() {},
      onMessage(listener) {
        messageListener = listener
      },
      saveCachedStatus: async () => {},
      sendMessage: async () => {},
      setTimeoutFn(fn) {
        scheduled.push(fn)
        return scheduled.length
      },
      resolveLanguagePreference: () => 'en',
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents() {
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key, params = {}) => {
        const values = {
          'notifications.manualPollTitle': 'InboxBridge manual poll finished',
          'notifications.manualPollBody': `Imported ${params.imported} of ${params.fetched} fetched messages. Sources with errors: ${params.errors}.`
        }
        return values[key] || key
      }
    }
  })

  await controller.refreshBadgeFromServer()
  messageListener({ type: 'manual-poll-triggered' }, null, () => {})
  await controller.refreshBadgeFromServer()

  assert.equal(notifications.length, 1)
  assert.equal(notifications[0].title, 'InboxBridge manual poll finished')
  assert.match(notifications[0].message, /Imported 5 of 7/)
  assert.equal(scheduled.length > 0, true)
})

test('background controller polls status after a manual poll starts when no live completion event arrives', async () => {
  let refreshCount = 0
  const notifications = []
  const originalSetTimeout = globalThis.setTimeout
  const originalClearTimeout = globalThis.clearTimeout
  const scheduled = []
  globalThis.setTimeout = (fn, _delay) => {
    scheduled.push(fn)
    return scheduled.length
  }
  globalThis.clearTimeout = () => {}

  try {
    const controller = registerBackgroundController({
      deps: {
        applyBadge: async () => {},
        applyIcon: async () => {},
        clearTimeoutFn() {},
        clearBadge: async () => {},
        createAlarm() {},
        createContextMenu: async () => {},
        createNotification: async (_id, options) => {
          notifications.push(options)
        },
        fetchStatus: async () => {
          refreshCount += 1
          return refreshCount === 1
            ? {
                poll: { running: false },
                summary: {
                  errorSourceCount: 0,
                  lastCompletedRunAt: '2026-04-12T18:30:00Z',
                  lastCompletedRun: { fetched: 2, imported: 2, errors: 0 }
                },
                sources: []
              }
            : {
                poll: { running: false },
                summary: {
                  errorSourceCount: 0,
                  lastCompletedRunAt: '2026-04-12T18:35:00Z',
                  lastCompletedRun: { fetched: 4, imported: 4, errors: 0 }
                },
                sources: []
              }
        },
        loadCachedStatus: async () => ({ status: null }),
        loadConfig: async () => ({
          notifyManualPollSuccess: true,
          serverUrl: 'https://mail.example.com',
          token: 'ibx_token'
        }),
        mergeLivePollIntoStatus: (status) => status,
        onAlarm() {},
        onContextMenuClicked() {},
        onInstalled() {},
        onMessage() {},
        openOptionsPage: async () => {},
        removeAllContextMenus: async () => {},
        runPoll: async () => ({ accepted: true, started: true }),
        saveCachedStatus: async () => {},
        saveUserPreferences: async () => {},
        sendMessage: async () => {},
        setTimeoutFn(fn) {
          scheduled.push(fn)
          return scheduled.length
        },
        resolveLanguagePreference: () => 'en',
        shouldOverlayLiveStatus: () => false,
        shouldRefreshStatusFromLiveEvent: () => false,
        subscribeExtensionEvents() {
          return { close() {}, completed: Promise.resolve() }
        },
        translate: (_locale, key, params = {}) => {
          const values = {
            'notifications.manualPollTitle': 'InboxBridge manual poll finished',
            'notifications.manualPollBody': `Imported ${params.imported} of ${params.fetched} fetched messages. Sources with errors: ${params.errors}.`,
            'settings.openPrompt': 'Open settings',
            'popup.runPoll': 'Run poll now'
          }
          return values[key] || key
        }
      }
    })

    await controller.refreshBadgeFromServer()
    await controller.triggerManualPollFromBackground()
    assert.equal(scheduled.length > 0, true)
    await scheduled.shift()()

    assert.equal(notifications.length, 1)
    assert.equal(notifications[0].title, 'InboxBridge manual poll finished')
    assert.match(notifications[0].message, /Imported 4 of 4/)
  } finally {
    globalThis.setTimeout = originalSetTimeout
    globalThis.clearTimeout = originalClearTimeout
  }
})

test('background controller keeps the syncing icon active until the backend reports a newer completed run', async () => {
  const appliedStatuses = []
  let refreshCount = 0

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async (status) => {
        appliedStatuses.push(status)
      },
      applyIcon: async () => {},
      clearTimeoutFn() {},
      clearBadge: async () => {},
      createAlarm() {},
      createContextMenu: async () => {},
      createNotification: async () => {},
      fetchStatus: async () => {
        refreshCount += 1
        if (refreshCount === 1) {
          return {
            poll: { running: false, canRun: true, state: 'IDLE' },
            summary: {
              errorSourceCount: 0,
              lastCompletedRunAt: '2026-04-12T18:30:00Z'
            },
            sources: []
          }
        }
        if (refreshCount === 2) {
          return {
            poll: { running: false, canRun: true, state: 'IDLE' },
            summary: {
              errorSourceCount: 0,
              lastCompletedRunAt: '2026-04-12T18:30:00Z'
            },
            sources: []
          }
        }
        return {
          poll: { running: false, canRun: true, state: 'IDLE' },
          summary: {
            errorSourceCount: 0,
            lastCompletedRunAt: '2026-04-12T18:40:00Z'
          },
          sources: []
        }
      },
      loadCachedStatus: async () => ({ status: null }),
      loadConfig: async () => ({
        notifyManualPollSuccess: false,
        serverUrl: 'https://mail.example.com',
        token: 'ibx_token'
      }),
      mergeLivePollIntoStatus: (status) => status,
      onAlarm() {},
      onContextMenuClicked() {},
      onInstalled() {},
      onMessage() {},
      openOptionsPage: async () => {},
      removeAllContextMenus: async () => {},
      runPoll: async () => ({ accepted: true, started: true }),
      saveCachedStatus: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      setTimeoutFn() {
        return 1
      },
      resolveLanguagePreference: () => 'en',
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents() {
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key) => key
    }
  })

  await controller.refreshBadgeFromServer()
  await controller.triggerManualPollFromBackground()
  await controller.refreshBadgeFromServer()

  assert.equal(appliedStatuses[0].poll.running, false)
  assert.equal(appliedStatuses.some((status) => status?.poll?.running === true && status?.poll?.canRun === false), true)
  assert.equal(appliedStatuses.at(-1).poll.running, false)
})

test('background controller clears a stale pending manual poll overlay after the retry budget is exhausted', async () => {
  const appliedStatuses = []
  const scheduled = []
  let refreshCount = 0

  const controller = registerBackgroundController({
    deps: {
      applyBadge: async (status) => {
        appliedStatuses.push(status)
      },
      applyIcon: async () => {},
      clearTimeoutFn() {},
      clearBadge: async () => {},
      createAlarm() {},
      createContextMenu: async () => {},
      createNotification: async () => {},
      fetchStatus: async () => {
        refreshCount += 1
        return {
          poll: { running: false, canRun: true, state: 'IDLE' },
          summary: {
            errorSourceCount: 0,
            lastCompletedRunAt: '2026-04-12T18:30:00Z'
          },
          sources: []
        }
      },
      loadCachedStatus: async () => ({ status: null }),
      loadConfig: async () => ({
        notifyManualPollSuccess: false,
        serverUrl: 'https://mail.example.com',
        token: 'ibx_token'
      }),
      mergeLivePollIntoStatus: (status) => status,
      onAlarm() {},
      onContextMenuClicked() {},
      onInstalled() {},
      onMessage() {},
      openOptionsPage: async () => {},
      removeAllContextMenus: async () => {},
      runPoll: async () => ({ accepted: true, started: true }),
      saveCachedStatus: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      setTimeoutFn(fn) {
        scheduled.push(fn)
        return scheduled.length
      },
      resolveLanguagePreference: () => 'en',
      shouldOverlayLiveStatus: () => false,
      shouldRefreshStatusFromLiveEvent: () => false,
      subscribeExtensionEvents() {
        return { close() {}, completed: Promise.resolve() }
      },
      translate: (_locale, key) => key
    }
  })

  await controller.refreshBadgeFromServer()
  await controller.triggerManualPollFromBackground()

  while (scheduled.length > 0) {
    const next = scheduled.shift()
    await next()
    await new Promise((resolve) => setImmediate(resolve))
    if (refreshCount > 30) {
      break
    }
  }

  assert.equal(appliedStatuses[0].poll.running, false)
  assert.equal(appliedStatuses[1].poll.running, true)
  assert.equal(appliedStatuses.at(-1).poll.running, false)
  assert.equal(appliedStatuses.at(-1).poll.canRun, true)
})
