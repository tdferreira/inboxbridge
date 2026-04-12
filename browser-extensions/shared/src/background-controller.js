/**
 * Registers background listeners with injected browser/runtime dependencies so
 * the badge refresh behavior can be covered without a real extension runtime.
 */
export function registerBackgroundController({
  alarmName = 'refresh-status',
  deps
}) {
  const MENU_OPEN_SETTINGS = 'inboxbridge-open-settings'
  const MENU_RUN_POLL = 'inboxbridge-run-poll'
  const MAX_PENDING_MANUAL_POLL_CHECK_ATTEMPTS = 24
  const {
    applyBadge,
    applyIcon = async () => {},
    createNotification = async () => {},
    clearBadge,
    createContextMenu = async () => {},
    createAlarm,
    fetchStatus,
    loadCachedStatus,
    loadConfig,
    mergeLivePollIntoStatus,
    onAlarm,
    onContextMenuClicked,
    onInstalled,
    onMessage,
    openOptionsPage = async () => {},
    removeAllContextMenus = async () => {},
    runPoll = async () => ({ accepted: false, started: false }),
    saveCachedStatus,
    saveUserPreferences = async () => {},
    clearTimeoutFn = globalThis.clearTimeout,
    sendMessage,
    resolveLanguagePreference = () => 'en',
    setTimeoutFn = globalThis.setTimeout,
    shouldOverlayLiveStatus,
    shouldRefreshStatusFromLiveEvent,
    subscribeExtensionEvents,
    translate = (_locale, key, params = {}) => interpolateFallback(key, params)
  } = deps
  let liveSubscription = null
  let liveSubscriptionKey = null
  let liveReconnectTimer = null
  let lastStatus = null
  let lastErrorNotificationKey = ''
  let menuConfigPromise = null
  let menuSignature = ''
  let pendingManualPollCheckAttempts = 0
  let pendingManualPollCheckTimer = null
  let statusPrimed = false
  let pendingManualPollNotification = null

  function clearPendingReconnect() {
    if (liveReconnectTimer) {
      clearTimeoutFn(liveReconnectTimer)
      liveReconnectTimer = null
    }
  }

  function clearPendingManualPollCheck() {
    if (pendingManualPollCheckTimer) {
      clearTimeoutFn(pendingManualPollCheckTimer)
      pendingManualPollCheckTimer = null
    }
    pendingManualPollCheckAttempts = 0
  }

  async function primePendingManualPollOverlay(serverUrl = '') {
    const cached = await loadCachedStatus().catch(() => null)
    const baseStatus = lastStatus || cached?.status || null
    const optimistic = applyPendingManualPollOverlay(baseStatus)
    if (!optimistic) {
      return
    }
    await saveCachedStatus(optimistic)
    await applyToolbarState(optimistic)
    await broadcastStatus(optimistic, null, serverUrl)
  }

  function expirePendingManualPollOverlay() {
    pendingManualPollNotification = null
    clearPendingManualPollCheck()
    void refreshBadgeFromServer().catch(() => {})
  }

  function schedulePendingManualPollCheck() {
    if (!pendingManualPollNotification) {
      clearPendingManualPollCheck()
      return
    }
    if (pendingManualPollCheckAttempts >= MAX_PENDING_MANUAL_POLL_CHECK_ATTEMPTS) {
      expirePendingManualPollOverlay()
      return
    }
    if (pendingManualPollCheckTimer) {
      return
    }
    pendingManualPollCheckAttempts += 1
    pendingManualPollCheckTimer = setTimeoutFn(async () => {
      pendingManualPollCheckTimer = null
      try {
        await refreshBadgeFromServer()
      } catch {
        // keep retrying until the poll finishes or the retry budget is exhausted
      } finally {
        if (pendingManualPollNotification) {
          schedulePendingManualPollCheck()
        } else {
          clearPendingManualPollCheck()
        }
      }
    }, 5_000)
  }

  function closeLiveSubscription() {
    clearPendingReconnect()
    liveSubscription?.close?.()
    liveSubscription = null
    liveSubscriptionKey = null
  }

  async function applyToolbarState(status, errorState = null) {
    await applyBadge(status, errorState?.message ?? null)
    await applyIcon(status, errorState)
  }

  async function broadcastStatus(status, errorMessage = null, serverUrl = '') {
    try {
      await sendMessage?.({
        type: 'extension-status-updated',
        errorMessage,
        serverUrl,
        status
      })
    } catch {
      // the popup is often closed, which should not fail background updates
    }
  }

  async function refreshBadgeFromServer() {
    try {
      const config = await loadConfig()
      if (!config?.serverUrl || !config?.token) {
        closeLiveSubscription()
        lastStatus = null
        lastErrorNotificationKey = ''
        clearPendingManualPollCheck()
        pendingManualPollNotification = null
        statusPrimed = false
        await clearBadge('InboxBridge is not configured')
        await applyIcon(null, {
          kind: 'signed-out',
          message: 'Open Settings to sign in and connect this browser extension to InboxBridge.'
        })
        await broadcastStatus(null, 'Open Settings to sign in and connect this browser extension to InboxBridge.', '')
        return null
      }
      let status
      try {
        status = await fetchStatus(config.serverUrl, config.token)
      } catch (error) {
        await applyToolbarState(null, {
          kind: 'transport',
          message: error.message
        })
        await broadcastStatus(null, error.message, config.serverUrl)
        throw error
      }
      status = applyPendingManualPollOverlay(status)
      await saveCachedStatus(status)
      await saveUserPreferences?.(status.user)
      await applyToolbarState(status)
      await maybeNotifyForStatusUpdate(status, config)
      lastStatus = status
      statusPrimed = true
      await broadcastStatus(status, null, config.serverUrl)
      await configureContextMenus()
      try {
        await ensureLiveSubscription(config)
      } catch {
        // keep the last known good badge/status even if live updates need to retry
      }
      return status
    } catch (error) {
      throw error
    }
  }

  async function refreshBadgeFromCache() {
    const cached = await loadCachedStatus()
    await applyToolbarState(cached?.status || null)
  }

  async function configureContextMenus() {
    if (menuConfigPromise) {
      return menuConfigPromise
    }
    menuConfigPromise = (async () => {
      const config = await loadConfig().catch(() => null)
      const locale = resolveLanguagePreference?.(config || {}) || 'en'
      const openSettingsTitle = translate(locale, 'settings.openPrompt')
      const runPollTitle = translate(locale, 'popup.runPoll')
      const nextSignature = `${locale}|${openSettingsTitle}|${runPollTitle}`
      if (nextSignature === menuSignature) {
        return
      }
      await removeAllContextMenus()
      await createContextMenu({
        contexts: ['action'],
        id: MENU_OPEN_SETTINGS,
        title: openSettingsTitle
      })
      await createContextMenu({
        contexts: ['action'],
        id: MENU_RUN_POLL,
        title: runPollTitle
      })
      menuSignature = nextSignature
    })()
    try {
      await menuConfigPromise
    } finally {
      menuConfigPromise = null
    }
  }

  async function triggerManualPollFromBackground() {
    const config = await loadConfig()
    if (!config?.serverUrl || !config?.token) {
      await openOptionsPage()
      return { openedOptions: true }
    }
    pendingManualPollNotification = {
      lastCompletedRunAt: lastStatus?.summary?.lastCompletedRunAt || null,
      requestedAt: Date.now()
    }
    await primePendingManualPollOverlay(config.serverUrl)
    schedulePendingManualPollCheck()
    const result = await runPoll(config.serverUrl, config.token)
    if (result?.accepted && result?.started) {
      await refreshBadgeFromServer()
    } else {
      expirePendingManualPollOverlay()
    }
    return result
  }

  onInstalled(() => {
    createAlarm(alarmName, 0.1, 5)
    void configureContextMenus()
    void refreshBadgeFromServer()
  })

  onContextMenuClicked?.((info) => {
    if (info?.menuItemId === MENU_OPEN_SETTINGS) {
      void openOptionsPage()
      return
    }
    if (info?.menuItemId === MENU_RUN_POLL) {
      void triggerManualPollFromBackground()
    }
  })

  onAlarm(async (alarm) => {
    if (alarm.name !== alarmName) {
      return
    }
    await refreshBadgeFromServer()
  })

  onMessage((message, _sender, sendResponse) => {
    if (message?.type === 'refresh-status') {
      refreshBadgeFromServer()
        .then((payload) => sendResponse({ ok: true, payload }))
        .catch((error) => sendResponse({ ok: false, error: error.message }))
      return true
    }
    if (message?.type === 'badge-from-cache') {
      refreshBadgeFromCache()
        .then(() => sendResponse({ ok: true }))
        .catch((error) => sendResponse({ ok: false, error: error.message }))
      return true
    }
    if (message?.type === 'manual-poll-triggered') {
      pendingManualPollNotification = {
        lastCompletedRunAt: lastStatus?.summary?.lastCompletedRunAt || null,
        requestedAt: Date.now()
      }
      void primePendingManualPollOverlay(message.serverUrl || '')
      schedulePendingManualPollCheck()
      sendResponse?.({ ok: true })
      return false
    }
    if (message?.type === 'refresh-context-menus') {
      configureContextMenus()
        .then(() => sendResponse({ ok: true }))
        .catch((error) => sendResponse({ ok: false, error: error.message }))
      return true
    }
    return false
  })

  async function ensureLiveSubscription(preloadedConfig = null, { forceReconnect = false } = {}) {
    const config = preloadedConfig || await loadConfig()
    if (!config?.serverUrl || !config?.token) {
      closeLiveSubscription()
      return
    }
    const key = `${config.serverUrl}|${config.token}`
    if (!forceReconnect && liveSubscription && liveSubscriptionKey === key) {
      return
    }
    closeLiveSubscription()
    liveSubscriptionKey = key
    liveSubscription = subscribeExtensionEvents(config.serverUrl, config.token, {
      onDisconnect(error) {
        liveSubscription = null
        if (!error) {
          return
        }
        clearPendingReconnect()
        liveReconnectTimer = setTimeoutFn(() => {
          liveReconnectTimer = null
          void ensureLiveSubscription(null, { forceReconnect: true })
        }, 2_000)
      },
      onEvent(event) {
        void handleLiveEvent(event, config)
      }
    })
  }

  async function handleLiveEvent(event, config) {
    if (!event || event.type === 'keepalive') {
      return
    }
    if (event.type === 'session-revoked') {
      closeLiveSubscription()
    }
    if (shouldRefreshStatusFromLiveEvent?.(event)) {
      await refreshBadgeFromServer()
      return
    }
    if (shouldOverlayLiveStatus?.(event) && event.poll) {
      const cached = await loadCachedStatus()
      const merged = mergeLivePollIntoStatus?.(cached?.status || null, event.poll)
      if (merged) {
        await saveCachedStatus(merged)
        await applyToolbarState(merged)
        await broadcastStatus(merged, null, config?.serverUrl || '')
        return
      }
    }
    if (config?.serverUrl && config?.token) {
      await refreshBadgeFromServer()
    }
  }

  async function maybeNotifyForStatusUpdate(status, config) {
    const locale = resolveLanguagePreference?.(config) || 'en'
    const nextErrorKey = buildErrorNotificationKey(status)
    if (!statusPrimed) {
      lastErrorNotificationKey = nextErrorKey
      return
    }

    if (config?.notifyErrors) {
      if (nextErrorKey && nextErrorKey !== lastErrorNotificationKey) {
        await createNotification(`inboxbridge-errors-${Date.now()}`, {
          iconUrl: config.iconUrl,
          message: translate(locale, 'notifications.errorBody', {
            count: Number(status.summary?.errorSourceCount || 0),
            sources: formatErrorSources(status, locale, translate)
          }),
          title: translate(locale, 'notifications.errorTitle'),
          type: 'basic'
        })
      }
    }
    lastErrorNotificationKey = nextErrorKey

    const completedAt = status.summary?.lastCompletedRunAt || null
    if (pendingManualPollNotification && !status.poll?.running && completedAt && completedAt !== pendingManualPollNotification.lastCompletedRunAt) {
      const shouldNotifyManualPollSuccess = Boolean(config?.notifyManualPollSuccess)
      if (shouldNotifyManualPollSuccess) {
        const run = status.summary?.lastCompletedRun || {}
        await createNotification(`inboxbridge-manual-poll-${Date.now()}`, {
          iconUrl: config.iconUrl,
          message: translate(locale, 'notifications.manualPollBody', {
            errors: Number(run.errors || 0),
            fetched: Number(run.fetched || 0),
            imported: Number(run.imported || 0)
          }),
          title: translate(locale, 'notifications.manualPollTitle'),
          type: 'basic'
        })
      }
      pendingManualPollNotification = null
      clearPendingManualPollCheck()
    }
  }

  function applyPendingManualPollOverlay(status) {
    if (!pendingManualPollNotification || !status) {
      return status
    }
    if (status.poll?.running) {
      return status
    }
    const completedAt = status.summary?.lastCompletedRunAt || null
    if (completedAt && completedAt !== pendingManualPollNotification.lastCompletedRunAt) {
      return status
    }
    return {
      ...status,
      poll: {
        ...(status.poll || {}),
        canRun: false,
        running: true,
        state: 'RUNNING',
        updatedAt: status.poll?.updatedAt || new Date(pendingManualPollNotification.requestedAt).toISOString()
      }
    }
  }

  void ensureLiveSubscription()
  void configureContextMenus()

  return {
    closeLiveSubscription,
    configureContextMenus,
    ensureLiveSubscription,
    refreshBadgeFromCache,
    refreshBadgeFromServer,
    triggerManualPollFromBackground
  }
}

function buildErrorNotificationKey(status) {
  const attentionSources = (status?.sources || [])
    .filter((source) => source?.needsAttention)
    .map((source) => source.sourceId)
    .sort()
  if (!attentionSources.length) {
    return ''
  }
  return `${Number(status?.summary?.errorSourceCount || 0)}|${attentionSources.join(',')}`
}

function formatErrorSources(status, locale, translate) {
  const labels = (status?.sources || [])
    .filter((source) => source?.needsAttention)
    .map((source) => source.label || source.sourceId)
  const visible = labels.slice(0, 3)
  const remaining = Math.max(labels.length - visible.length, 0)
  if (remaining > 0) {
    visible.push(translate(locale, 'notifications.moreSources', { count: remaining }))
  }
  return visible.join(', ')
}

function interpolateFallback(key, params) {
  return Object.entries(params || {}).reduce((value, [name, token]) => value.replace(`{${name}}`, token), key)
}
