import { isInvalidExtensionAuthError } from './auth-errors.js'

/**
 * Wires the popup UI to the shared extension API without depending directly on
 * browser globals, which keeps the entrypoint small and the behavior easy to
 * cover in tests.
 */
export function createPopupController({
  deps,
  elements
}) {
  const {
    applyThemePreference,
    clearConfig = async () => {},
    clearStatusBanner,
    deriveStatusView,
    disconnectedView,
    escapeHtml,
    fetchStatus,
    localizePopupPage,
    loadConfig,
    onMessage,
    openOptionsPage,
    openTab,
    runPoll,
    saveUserPreferences,
    sendMessage,
    resolveLanguagePreference,
    showStatusBanner,
    targetDocument,
    translate
  } = deps

  const {
    attentionCount,
    connectionCopy,
    errorList,
    healthyState,
    metricDuplicates,
    metricErrors,
    metricFetched,
    metricImported,
    openAppButton,
    openRemoteButton,
    openSettingsButton,
    popupStatus,
    refreshStatusButton,
    runPollButton,
    summaryCard,
    statusPill,
    updatedAt
  } = elements

  let currentConfig = {
    language: 'user',
    theme: 'user',
    userLanguage: 'en',
    userThemeMode: 'SYSTEM'
  }
  let currentLocale = resolveLanguagePreference?.(currentConfig) || 'en'
  let primaryActionMode = 'poll'
  let lastRenderedStatus = null

  function t(key, params) {
    return translate?.(currentLocale, key, params) || key
  }

  function setPrimaryAction(mode) {
    primaryActionMode = mode === 'signin' ? 'signin' : 'poll'
    runPollButton.disabled = false
    runPollButton.textContent = primaryActionMode === 'signin'
      ? t('popup.signIn')
      : t('popup.runPoll')
  }

  function applyConfigState(config) {
    currentConfig = {
      ...currentConfig,
      ...(config || {})
    }
    currentLocale = resolveLanguagePreference?.(currentConfig) || currentLocale
    localizePopupPage?.(targetDocument, currentLocale)
    applyThemePreference?.(targetDocument, currentConfig.theme || 'user', currentConfig.userThemeMode || 'SYSTEM')
  }

  async function refreshPopup({ preserveBanner = false, showFeedback = false } = {}) {
    if (showFeedback) {
      return withBusy(refreshStatusButton, t('status.refreshing'), async () => {
        showRefreshingState()
        await loadAndRender({ preserveBanner, showFeedback })
      })
    }
    showRefreshingState()
    await loadAndRender({ preserveBanner, showFeedback })
  }

  async function loadAndRender({ preserveBanner, showFeedback }) {
    try {
      if (!preserveBanner) {
        clearStatusBanner(popupStatus)
      }
      const config = await loadConfig()
      applyConfigState(config)
      if (!config?.serverUrl || !config?.token) {
        setPrimaryAction('signin')
        renderView(disconnectedView(t('popup.openSettingsToSignIn'), t))
        if (showFeedback) {
          showStatusBanner(popupStatus, 'warning', t('popup.signInBeforeRefresh'))
        }
        return
      }
      setPrimaryAction('poll')
      const status = await fetchStatus(config.serverUrl, config.token)
      await saveUserPreferences?.(status.user)
      applyConfigState({
        ...config,
        userLanguage: status.user?.language,
        userThemeMode: status.user?.themeMode
      })
      lastRenderedStatus = status
      await sendMessage({ type: 'refresh-status' })
      renderView(deriveStatusView(config.serverUrl, status, { now: Date.now(), translate: t }))
      if (showFeedback) {
        showStatusBanner(popupStatus, 'success', t('popup.refreshSuccess'))
      }
    } catch (error) {
      if (isInvalidExtensionAuthError(error)) {
        await handleInvalidAuth(error)
        return
      }
      setPrimaryAction('poll')
      renderView(disconnectedView(error.message || t('errors.loadFailed'), t))
      showStatusBanner(popupStatus, 'error', error.message || t('errors.loadFailed'))
    } finally {
      clearRefreshingState()
    }
  }

  async function startPoll() {
    if (primaryActionMode === 'signin') {
      await openOptionsPage()
      return
    }
    await withBusy(runPollButton, t('status.starting'), async () => {
      const config = await loadConfig()
      if (!config?.serverUrl || !config?.token) {
        setPrimaryAction('signin')
        renderView(disconnectedView(t('popup.openSettingsToSignIn'), t))
        return
      }
      const optimisticStatus = applyOptimisticPollingStatus(lastRenderedStatus)
      if (optimisticStatus) {
        lastRenderedStatus = optimisticStatus
        renderView(deriveStatusView(config.serverUrl, optimisticStatus, { now: Date.now(), translate: t }))
      } else {
        showRefreshingState()
      }
      await sendMessage({ type: 'manual-poll-triggered', serverUrl: config.serverUrl })
      let result
      try {
        result = await runPoll(config.serverUrl, config.token)
      } catch (error) {
        if (isInvalidExtensionAuthError(error)) {
          await handleInvalidAuth(error)
          return
        }
        throw error
      }
      if (!result.accepted || !result.started) {
        showStatusBanner(popupStatus, 'warning', result.message || 'InboxBridge could not start a poll right now.')
        await refreshPopup({ preserveBanner: true })
      } else {
        showStatusBanner(popupStatus, 'success', result.message || 'InboxBridge started your polling run.')
      }
    })
  }

  async function openConfiguredTab(suffix = '') {
    const config = await loadConfig()
    if (config?.serverUrl) {
      await openTab(`${config.serverUrl}${suffix}`)
    }
  }

  async function withBusy(button, label, work) {
    const originalText = button.textContent
    button.disabled = true
    button.textContent = label
    try {
      await work()
    } finally {
      button.disabled = false
      button.textContent = originalText
    }
  }

  function showRefreshingState() {
    if (summaryCard) {
      summaryCard.classList?.add?.('is-refreshing')
      summaryCard.className = summaryCard.className || 'summary-card'
      if (!summaryCard.className.includes('is-refreshing')) {
        summaryCard.className = `${summaryCard.className} is-refreshing`.trim()
      }
    }
    statusPill.textContent = t('status.refreshing')
    statusPill.className = 'status-pill info'
    updatedAt.textContent = t('status.refreshing')
  }

  function clearRefreshingState() {
    if (summaryCard?.classList?.remove) {
      summaryCard.classList.remove('is-refreshing')
      return
    }
    if (summaryCard?.className) {
      summaryCard.className = summaryCard.className.replace(/\bis-refreshing\b/g, '').replace(/\s+/g, ' ').trim()
    }
  }

  function renderView(view) {
    runPollButton.disabled = primaryActionMode === 'poll' ? view.runPollDisabled : false
    connectionCopy.textContent = view.connectionCopy
    statusPill.textContent = view.statusLabel
    statusPill.className = `status-pill ${view.statusTone}`
    updatedAt.textContent = view.updatedText
    metricImported.textContent = view.metrics.imported
    metricFetched.textContent = view.metrics.fetched
    metricDuplicates.textContent = view.metrics.duplicates
    metricErrors.textContent = view.metrics.errors
    attentionCount.textContent = view.attentionCount
    healthyState.hidden = !view.healthy
    errorList.innerHTML = ''
    if (view.healthy) {
      errorList.hidden = true
      return
    }
    errorList.hidden = false
    view.errorSources.forEach((source) => {
      const item = errorList.ownerDocument.createElement('li')
      item.className = 'error-item'
      item.innerHTML = `
        <div class="error-item-title">${escapeHtml(source.label || source.sourceId)}</div>
        <div class="error-item-copy">${escapeHtml(source.lastError || 'Needs attention')}</div>
      `
      errorList.appendChild(item)
    })
  }

  async function handleInvalidAuth(error) {
    await clearConfig?.()
    setPrimaryAction('signin')
    lastRenderedStatus = null
    renderView(disconnectedView(t('popup.openSettingsToSignIn'), t))
    await sendMessage?.({ type: 'refresh-status' })
    await sendMessage?.({ type: 'refresh-context-menus' })
    showStatusBanner(popupStatus, 'warning', error.message || t('popup.openSettingsToSignIn'))
  }

  function initialize() {
    openSettingsButton.addEventListener('click', () => openOptionsPage())
    openAppButton.addEventListener('click', () => openConfiguredTab(''))
    openRemoteButton.addEventListener('click', () => openConfiguredTab('/remote'))
    refreshStatusButton.addEventListener('click', () => refreshPopup({ showFeedback: true }))
    runPollButton.addEventListener('click', () => startPoll())
    onMessage?.((message) => {
      if (message?.type !== 'extension-status-updated') {
        return false
      }
      if (message.errorMessage) {
        renderView(disconnectedView(message.errorMessage, t))
        return false
      }
      if (message.status && message.serverUrl) {
        applyConfigState({
          ...currentConfig,
          userLanguage: message.status.user?.language,
          userThemeMode: message.status.user?.themeMode
        })
        lastRenderedStatus = message.status
        renderView(deriveStatusView(message.serverUrl, message.status, { now: Date.now(), translate: t }))
        clearRefreshingState()
      }
      return false
    })
  }

  return {
    initialize,
    refreshPopup,
    renderView,
    startPoll
  }
}

function applyOptimisticPollingStatus(status) {
  if (!status) {
    return null
  }
  return {
    ...status,
    poll: {
      ...(status.poll || {}),
      canRun: false,
      running: true,
      state: 'RUNNING',
      updatedAt: new Date().toISOString()
    }
  }
}
