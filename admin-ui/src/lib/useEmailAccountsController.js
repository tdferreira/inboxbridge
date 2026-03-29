import { useEffect, useMemo, useState } from 'react'
import { apiErrorText } from './api'
import { formatPollError, isOauthRevokedError } from './formatters'
import { applyEmailAccountPreset, DEFAULT_EMAIL_ACCOUNT_FORM, normalizeEmailAccountForm } from './sourceEmailAccountForm'

const DEFAULT_SOURCE_POLLING_FORM = {
  pollEnabledMode: 'DEFAULT',
  pollIntervalOverride: '',
  fetchWindowOverride: '',
  basePollEnabled: true,
  basePollInterval: '5m',
  baseFetchWindow: 50,
  effectivePollEnabled: true,
  effectivePollInterval: '5m',
  effectiveFetchWindow: 50,
  isDirty: false
}

function normalizeLoadedEmailAccount(emailAccount) {
  const emailAccountId = emailAccount?.emailAccountId || emailAccount?.bridgeId || ''
  return {
    ...emailAccount,
    bridgeId: emailAccountId,
    emailAccountId
  }
}

function buildEmailAccountRequestPayload(emailAccountForm) {
  return {
    originalEmailAccountId: emailAccountForm.originalEmailAccountId,
    emailAccountId: emailAccountForm.emailAccountId,
    enabled: emailAccountForm.enabled,
    protocol: emailAccountForm.protocol,
    host: emailAccountForm.host,
    port: emailAccountForm.port,
    tls: emailAccountForm.tls,
    authMethod: emailAccountForm.authMethod,
    oauthProvider: emailAccountForm.oauthProvider,
    username: emailAccountForm.username,
    password: emailAccountForm.password,
    oauthRefreshToken: emailAccountForm.oauthRefreshToken,
    folder: emailAccountForm.folder,
    unreadOnly: emailAccountForm.unreadOnly,
    customLabel: emailAccountForm.customLabel
  }
}

function normalizeSourcePollingForm(payload) {
  return {
    pollEnabledMode: payload.pollEnabledOverride === null
      ? 'DEFAULT'
      : payload.pollEnabledOverride ? 'ENABLED' : 'DISABLED',
    pollIntervalOverride: payload.pollIntervalOverride || '',
    fetchWindowOverride: payload.fetchWindowOverride === null ? '' : String(payload.fetchWindowOverride),
    basePollEnabled: payload.basePollEnabled,
    basePollInterval: payload.basePollInterval,
    baseFetchWindow: payload.baseFetchWindow,
    effectivePollEnabled: payload.effectivePollEnabled,
    effectivePollInterval: payload.effectivePollInterval,
    effectiveFetchWindow: payload.effectiveFetchWindow,
    isDirty: false
  }
}

function deriveOauthConnected(emailAccount) {
  if (emailAccount.authMethod !== 'OAUTH2') return false
  if (isOauthRevokedError(emailAccount.lastEvent?.error) || isOauthRevokedError(emailAccount.pollingState?.lastFailureReason)) {
    return false
  }
  return emailAccount.oauthConnected === true
    || emailAccount.oauthRefreshTokenConfigured === true
    || emailAccount.tokenStorageMode === 'DATABASE'
    || emailAccount.tokenStorageMode === 'ENVIRONMENT'
}

function pruneFetcherStats(currentStats, userEmailAccounts, systemEmailAccounts) {
  const validIds = new Set(userEmailAccounts.map((emailAccount) => emailAccount.bridgeId))
  for (const emailAccount of systemEmailAccounts) {
    validIds.add(emailAccount.id)
  }
  return Object.fromEntries(Object.entries(currentStats).filter(([emailAccountId]) => validIds.has(emailAccountId)))
}

export function useEmailAccountsController({
  authOptions,
  errorText,
  isPending,
  language,
  loadAppData,
  pushNotification,
  refreshSectionData,
  sessionUsername,
  systemDashboardEmailAccounts,
  t,
  withPending
}) {
  const [emailAccountForm, setEmailAccountForm] = useState(DEFAULT_EMAIL_ACCOUNT_FORM)
  const [emailAccountDuplicateError, setEmailAccountDuplicateError] = useState('')
  const [emailAccountTestResult, setEmailAccountTestResult] = useState(null)
  const [userEmailAccounts, setUserEmailAccounts] = useState([])
  const [expandedFetcherLoadingId, setExpandedFetcherLoadingId] = useState(null)
  const [fetcherStatsById, setFetcherStatsById] = useState({})
  const [fetcherStatsLoadingId, setFetcherStatsLoadingId] = useState(null)
  const [showFetcherDialog, setShowFetcherDialog] = useState(false)
  const [showFetcherPollingDialog, setShowFetcherPollingDialog] = useState(false)
  const [fetcherPollingTarget, setFetcherPollingTarget] = useState(null)
  const [fetcherPollingForm, setFetcherPollingForm] = useState(DEFAULT_SOURCE_POLLING_FORM)

  const visibleFetchers = useMemo(() => {
    const databaseFetchers = userEmailAccounts.map((emailAccount) => ({
      ...emailAccount,
      bridgeId: emailAccount.bridgeId,
      managementSource: 'DATABASE',
      oauthConnected: deriveOauthConnected(emailAccount),
      canDelete: true,
      canEdit: true,
      canConnectOAuth: authOptions.sourceOAuthProviders.includes(emailAccount.oauthProvider) && emailAccount.authMethod === 'OAUTH2',
      canConfigurePolling: true,
      canRunPoll: true
    }))
    const envFetchers = sessionUsername === 'admin'
      ? (systemDashboardEmailAccounts || []).map((emailAccount) => ({
        bridgeId: emailAccount.id,
        enabled: emailAccount.enabled,
        effectivePollEnabled: emailAccount.effectivePollEnabled,
        effectivePollInterval: emailAccount.effectivePollInterval,
        effectiveFetchWindow: emailAccount.effectiveFetchWindow,
        protocol: emailAccount.protocol,
        authMethod: emailAccount.authMethod,
        oauthProvider: emailAccount.oauthProvider,
        host: emailAccount.host,
        port: emailAccount.port,
        tls: emailAccount.tls,
        folder: emailAccount.folder,
        unreadOnly: emailAccount.unreadOnly,
        customLabel: emailAccount.customLabel,
        tokenStorageMode: emailAccount.tokenStorageMode,
        totalImportedMessages: emailAccount.totalImportedMessages,
        lastImportedAt: emailAccount.lastImportedAt,
        lastEvent: emailAccount.lastEvent,
        pollingState: emailAccount.pollingState,
        oauthConnected: deriveOauthConnected(emailAccount),
        managementSource: 'ENVIRONMENT',
        canDelete: false,
        canEdit: false,
        canConnectOAuth: authOptions.sourceOAuthProviders.includes(emailAccount.oauthProvider) && emailAccount.authMethod === 'OAUTH2',
        canConfigurePolling: true,
        canRunPoll: true
      }))
      : []

    return [...databaseFetchers, ...envFetchers]
      .sort((left, right) => left.bridgeId.localeCompare(right.bridgeId))
  }, [authOptions.sourceOAuthProviders, sessionUsername, systemDashboardEmailAccounts, userEmailAccounts])

  const connectingEmailAccountId = visibleFetchers.find((emailAccount) => isPending(`microsoftOAuth:${emailAccount.bridgeId}`) || isPending(`googleSourceOAuth:${emailAccount.bridgeId}`))?.bridgeId || null
  const deletingEmailAccountId = userEmailAccounts.find((emailAccount) => isPending(`bridgeDelete:${emailAccount.bridgeId}`))?.bridgeId || null
  const fetcherPollLoadingId = visibleFetchers.find((emailAccount) => isPending(`bridgePoll:${emailAccount.bridgeId}`))?.bridgeId || null
  const fetcherPollingLoading = fetcherPollingTarget
    ? isPending(`fetcherPollingSave:${fetcherPollingTarget.bridgeId}`) || isPending(`fetcherPollingLoad:${fetcherPollingTarget.bridgeId}`)
    : false

  function applyLoadedEmailAccounts(emailAccountsPayload, adminEmailAccounts = []) {
    const nextUserEmailAccounts = Array.isArray(emailAccountsPayload)
      ? emailAccountsPayload.map(normalizeLoadedEmailAccount)
      : []
    const nextSystemEmailAccounts = Array.isArray(adminEmailAccounts)
      ? adminEmailAccounts.map((emailAccount) => ({
        ...emailAccount,
        id: emailAccount.emailAccountId || emailAccount.id || emailAccount.bridgeId || ''
      }))
      : []
    setUserEmailAccounts(nextUserEmailAccounts)
    setFetcherStatsById((current) => pruneFetcherStats(current, nextUserEmailAccounts, nextSystemEmailAccounts))
  }

  function handleEmailAccountFormChange(updater) {
    setEmailAccountDuplicateError('')
    setEmailAccountTestResult(null)
    setEmailAccountForm((current) => normalizeEmailAccountForm(typeof updater === 'function' ? updater(current) : updater, authOptions))
  }

  function applyEmailAccountPresetSelection(presetId) {
    setEmailAccountTestResult(null)
    setEmailAccountForm((current) => applyEmailAccountPreset(current, presetId, authOptions))
  }

  function openAddFetcherDialog() {
    setEmailAccountDuplicateError('')
    setEmailAccountTestResult(null)
    setEmailAccountForm(DEFAULT_EMAIL_ACCOUNT_FORM)
    setShowFetcherDialog(true)
  }

  function editEmailAccount(emailAccount) {
    setEmailAccountTestResult(null)
    handleEmailAccountFormChange({
      originalEmailAccountId: emailAccount.bridgeId,
      emailAccountId: emailAccount.bridgeId,
      enabled: emailAccount.enabled,
      protocol: emailAccount.protocol,
      host: emailAccount.host,
      port: emailAccount.port,
      tls: emailAccount.tls,
      authMethod: emailAccount.authMethod,
      oauthProvider: emailAccount.oauthProvider,
      username: emailAccount.username,
      password: '',
      oauthRefreshToken: '',
      folder: emailAccount.folder,
      unreadOnly: emailAccount.unreadOnly,
      customLabel: emailAccount.customLabel
    })
    setShowFetcherDialog(true)
  }

  function closeFetcherDialog() {
    setEmailAccountTestResult(null)
    setShowFetcherDialog(false)
  }

  function startGoogleSourceOAuth(sourceId) {
    withPending(`googleSourceOAuth:${sourceId}`, async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign(`/api/google-oauth/start/source?sourceId=${encodeURIComponent(sourceId)}&lang=${encodeURIComponent(language)}`)
          resolve()
        }, 75)
      })
    })
  }

  function startMicrosoftOAuth(sourceId) {
    withPending(`microsoftOAuth:${sourceId}`, async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign(`/api/microsoft-oauth/start?sourceId=${encodeURIComponent(sourceId)}&lang=${encodeURIComponent(language)}`)
          resolve()
        }, 75)
      })
    })
  }

  function startSourceOAuth(sourceId, provider) {
    if (provider === 'GOOGLE') {
      startGoogleSourceOAuth(sourceId)
      return
    }
    startMicrosoftOAuth(sourceId)
  }

  async function loadFetcherStats(fetcher, options = {}) {
    if (!fetcher?.bridgeId) return
    const { suppressErrors = false } = options
    setFetcherStatsLoadingId(fetcher.bridgeId)
    try {
      const endpointPrefix = fetcher.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
      const response = await fetch(`${endpointPrefix}/${encodeURIComponent(fetcher.bridgeId)}/polling-stats`)
      if (!response.ok) {
        throw new Error(await apiErrorText(response, errorText('loadMailAccountStatistics')))
      }
      const payload = await response.json()
      setFetcherStatsById((current) => ({ ...current, [fetcher.bridgeId]: payload }))
    } catch (err) {
      if (!suppressErrors) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message || errorText('loadMailAccountStatistics'),
          message: err.message || errorText('loadMailAccountStatistics'),
          targetId: 'source-email-accounts-section',
          tone: 'error'
        })
      }
    } finally {
      setFetcherStatsLoadingId((current) => current === fetcher.bridgeId ? null : current)
    }
  }

  async function loadScopedTimelineBundle(endpoint, fallbackMessage) {
    const response = await fetch(endpoint)
    if (!response.ok) {
      throw new Error(await apiErrorText(response, fallbackMessage))
    }
    const payload = await response.json()
    return {
      imports: payload.importTimelines?.custom || [],
      duplicates: payload.duplicateTimelines?.custom || [],
      errors: payload.errorTimelines?.custom || [],
      manualRuns: payload.manualRunTimelines?.custom || [],
      scheduledRuns: payload.scheduledRunTimelines?.custom || []
    }
  }

  async function loadFetcherCustomRange(fetcher, range) {
    const search = new URLSearchParams({ from: range.from })
    if (range.to) search.set('to', range.to)
    const endpointPrefix = fetcher.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
    return loadScopedTimelineBundle(
      `${endpointPrefix}/${encodeURIComponent(fetcher.bridgeId)}/polling-stats/range?${search.toString()}`,
      t('pollingStats.customRangeLoadError')
    )
  }

  async function upsertEmailAccountForm(options = {}) {
    const { connectMicrosoftAfterSave = false } = options
    const normalizedEmailAccountId = emailAccountForm.emailAccountId.trim()
    const originalEmailAccountId = emailAccountForm.originalEmailAccountId.trim()
    const duplicateFetcher = visibleFetchers.find((fetcher) => (
      fetcher.bridgeId === normalizedEmailAccountId && fetcher.bridgeId !== originalEmailAccountId
    ))
    if (duplicateFetcher) {
      const duplicateMessage = t('emailAccounts.duplicateId', { bridgeId: normalizedEmailAccountId })
      setEmailAccountDuplicateError(duplicateMessage)
      pushNotification({ autoCloseMs: null, copyText: duplicateMessage, message: duplicateMessage, targetId: 'source-email-accounts-section', tone: 'error' })
      return null
    }
    const actionKey = connectMicrosoftAfterSave ? 'bridgeSaveConnect' : 'bridgeSave'
    return withPending(actionKey, async () => {
      try {
        const response = await fetch('/api/app/email-accounts', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildEmailAccountRequestPayload(emailAccountForm))
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('saveMailFetcher')))
        }
        const payload = await response.json()
        setEmailAccountDuplicateError('')
        setEmailAccountTestResult(null)
        if (!connectMicrosoftAfterSave) {
          pushNotification({ message: t('notifications.emailAccountSaved', { bridgeId: emailAccountForm.emailAccountId }), targetId: 'source-email-accounts-section', tone: 'success' })
          setEmailAccountForm(DEFAULT_EMAIL_ACCOUNT_FORM)
          setShowFetcherDialog(false)
          await loadAppData()
        } else {
          pushNotification({
            message: t('notifications.emailAccountSavedStartingProviderOAuth', {
              bridgeId: payload.emailAccountId || payload.bridgeId || emailAccountForm.emailAccountId,
              provider: emailAccountForm.oauthProvider === 'GOOGLE' ? t('oauthProvider.google') : t('oauthProvider.microsoft')
            }),
            targetId: 'source-email-accounts-section',
            tone: 'warning'
          })
        }
        return payload
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('saveMailFetcher'), message: err.message || errorText('saveMailFetcher'), targetId: 'source-email-accounts-section', tone: 'error' })
        return null
      }
    })
  }

  async function saveEmailAccount(event) {
    event.preventDefault()
    await upsertEmailAccountForm()
  }

  async function saveEmailAccountAndConnectOAuth() {
    const payload = await upsertEmailAccountForm({ connectMicrosoftAfterSave: true })
    const savedBridgeId = payload?.emailAccountId || payload?.bridgeId || emailAccountForm.emailAccountId?.trim()
    if (savedBridgeId) {
      startSourceOAuth(savedBridgeId, emailAccountForm.oauthProvider)
    }
  }

  async function testEmailAccountConnection() {
    await withPending('bridgeConnectionTest', async () => {
      try {
        const response = await fetch('/api/app/email-accounts/test-connection', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildEmailAccountRequestPayload(emailAccountForm))
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('testMailFetcherConnection')))
        }
        const payload = await response.json()
        const message = payload.message || t('emailAccounts.testSuccess')
        setEmailAccountTestResult({ ...payload, message, tone: 'success' })
        pushNotification({ message, targetId: 'source-email-accounts-section', tone: 'success' })
      } catch (err) {
        const message = err.message || errorText('testMailFetcherConnection')
        setEmailAccountTestResult({ message, tone: 'error' })
        pushNotification({ autoCloseMs: null, copyText: message, message, targetId: 'source-email-accounts-section', tone: 'error' })
      }
    })
  }

  async function deleteEmailAccount(bridgeId, openConfirmation) {
    openConfirmation({
      actionKey: `bridgeDelete:${bridgeId}`,
      body: t('emailAccount.deleteConfirmBody', { bridgeId }),
      confirmLabel: t('emailAccount.delete'),
      confirmLoadingLabel: t('emailAccount.deleteLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`bridgeDelete:${bridgeId}`, async () => {
          try {
            const response = await fetch(`/api/app/email-accounts/${encodeURIComponent(bridgeId)}`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('deleteMailFetcher')))
            }
            pushNotification({ message: t('notifications.emailAccountDeleted', { bridgeId }), targetId: 'source-email-accounts-section', tone: 'success' })
            await loadAppData()
            openConfirmation(null)
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || errorText('deleteMailFetcher'), message: err.message || errorText('deleteMailFetcher'), targetId: 'source-email-accounts-section', tone: 'error' })
          }
        })
      },
      title: t('emailAccount.deleteConfirmTitle')
    })
  }

  async function openFetcherPollingDialog(fetcher) {
    setFetcherPollingTarget(fetcher)
    await withPending(`fetcherPollingLoad:${fetcher.bridgeId}`, async () => {
      try {
        const endpointPrefix = fetcher.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(fetcher.bridgeId)}/polling-settings`)
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('loadFetcherPollingSettings')))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        setShowFetcherPollingDialog(true)
      } catch (err) {
        setFetcherPollingTarget(null)
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('loadFetcherPollingSettings'), message: err.message || errorText('loadFetcherPollingSettings'), targetId: 'source-email-accounts-section', tone: 'error' })
      }
    })
  }

  function closeFetcherPollingDialog() {
    setShowFetcherPollingDialog(false)
    setFetcherPollingTarget(null)
    setFetcherPollingForm(DEFAULT_SOURCE_POLLING_FORM)
  }

  function handleFetcherPollingFormChange(updater) {
    setFetcherPollingForm((current) => typeof updater === 'function' ? updater(current) : updater)
  }

  async function saveFetcherPollingSettings(event) {
    event.preventDefault()
    if (!fetcherPollingTarget) return
    await withPending(`fetcherPollingSave:${fetcherPollingTarget.bridgeId}`, async () => {
      try {
        const endpointPrefix = fetcherPollingTarget.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(fetcherPollingTarget.bridgeId)}/polling-settings`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            pollEnabledOverride: fetcherPollingForm.pollEnabledMode === 'DEFAULT'
              ? null
              : fetcherPollingForm.pollEnabledMode === 'ENABLED',
            pollIntervalOverride: fetcherPollingForm.pollIntervalOverride.trim() || null,
            fetchWindowOverride: fetcherPollingForm.fetchWindowOverride === '' ? null : Number(fetcherPollingForm.fetchWindowOverride)
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('saveFetcherPollingSettings')))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        pushNotification({ message: t('notifications.fetcherPollingSaved', { bridgeId: fetcherPollingTarget.bridgeId }), targetId: 'source-email-accounts-section', tone: 'success' })
        await loadAppData()
        closeFetcherPollingDialog()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('saveFetcherPollingSettings'), message: err.message || errorText('saveFetcherPollingSettings'), targetId: 'source-email-accounts-section', tone: 'error' })
      }
    })
  }

  async function resetFetcherPollingSettings() {
    if (!fetcherPollingTarget) return
    await withPending(`fetcherPollingSave:${fetcherPollingTarget.bridgeId}`, async () => {
      try {
        const endpointPrefix = fetcherPollingTarget.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(fetcherPollingTarget.bridgeId)}/polling-settings`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            pollEnabledOverride: null,
            pollIntervalOverride: null,
            fetchWindowOverride: null
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('resetFetcherPollingSettings')))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        pushNotification({ message: t('notifications.fetcherPollingReset', { bridgeId: fetcherPollingTarget.bridgeId }), targetId: 'source-email-accounts-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('resetFetcherPollingSettings'), message: err.message || errorText('resetFetcherPollingSettings'), targetId: 'source-email-accounts-section', tone: 'error' })
      }
    })
  }

  function notificationTargetForPollErrors(errorDetails = [], messages = [], fallbackTarget = 'source-email-accounts-section') {
    const details = Array.isArray(errorDetails) ? errorDetails : []
    if (details.some((detail) => detail?.code === 'gmail_account_not_linked' || detail?.code === 'gmail_access_revoked')) {
      return 'destination-mailbox-section'
    }
    const rawMessages = Array.isArray(messages) ? messages : []
    if (rawMessages.some((message) => typeof message === 'string'
      && (message.includes('The Gmail destination is not linked for this account')
        || message.includes('The linked Gmail account no longer grants InboxBridge access')))) {
      return 'destination-mailbox-section'
    }
    return fallbackTarget
  }

  async function runFetcherPoll(fetcherOrBridgeId) {
    const fetcher = typeof fetcherOrBridgeId === 'string'
      ? visibleFetchers.find((entry) => entry.bridgeId === fetcherOrBridgeId)
      : fetcherOrBridgeId
    const bridgeId = typeof fetcherOrBridgeId === 'string' ? fetcherOrBridgeId : fetcherOrBridgeId?.bridgeId
    if (!bridgeId) {
      return
    }
    await withPending(`bridgePoll:${bridgeId}`, async () => {
      try {
        const notificationGroup = `fetcher-poll:${bridgeId}`
        pushNotification({
          autoCloseMs: 10000,
          groupKey: notificationGroup,
          message: t('notifications.fetcherPollStarted', { bridgeId }),
          targetId: 'source-email-accounts-section',
          tone: 'warning'
        })
        const endpointPrefix = fetcher?.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(bridgeId)}/poll/run`, { method: 'POST' })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('runMailFetcherPoll')))
        }
        const payload = await response.json()
        if (payload.errorDetails?.length || payload.errors?.length) {
          const formattedErrors = payload.errorDetails?.length
            ? payload.errorDetails.map((detail) => formatPollError(detail, language))
            : payload.errors.map((message) => formatPollError(message, language))
          throw Object.assign(new Error(formattedErrors.join('\n')), {
            notificationTargetId: notificationTargetForPollErrors(payload.errorDetails, payload.errors)
          })
        }
        const completedMessageKey = payload.spamJunkMessageCount > 0
          ? 'notifications.fetcherPollCompletedWithSpam'
          : 'notifications.fetcherPollCompleted'
        pushNotification({
          groupKey: notificationGroup,
          message: t(completedMessageKey, {
            bridgeId,
            fetched: payload.fetched,
            imported: payload.imported,
            duplicates: payload.duplicates,
            spamJunkCount: payload.spamJunkMessageCount
          }),
          replaceGroup: true,
          targetId: 'source-email-accounts-section',
          tone: 'success'
        })
      } catch (err) {
        const message = formatPollError(err.message || errorText('runMailFetcherPoll'), language)
        pushNotification({
          copyText: message,
          groupKey: `fetcher-poll:${bridgeId}`,
          message,
          replaceGroup: true,
          targetId: err.notificationTargetId || notificationTargetForPollErrors([], [err.message || '']),
          tone: 'error'
        })
      } finally {
        await loadAppData({ suppressErrors: true })
        if (fetcher) {
          await loadFetcherStats(fetcher, { suppressErrors: true })
        }
      }
    })
  }

  async function refreshFetcherState(fetcher, expanded) {
    if (!expanded) {
      return
    }
    setExpandedFetcherLoadingId(fetcher.bridgeId)
    try {
      await Promise.all([
        refreshSectionData('sourceEmailAccountsCollapsed', () => loadAppData({ suppressErrors: true })),
        loadFetcherStats(fetcher, { suppressErrors: true })
      ])
    } finally {
      setExpandedFetcherLoadingId((current) => current === fetcher.bridgeId ? null : current)
    }
  }

  useEffect(() => {
    if (!showFetcherDialog) {
      setEmailAccountDuplicateError('')
    }
  }, [showFetcherDialog])

  return {
    applyEmailAccountPreset: applyEmailAccountPresetSelection,
    applyLoadedEmailAccounts,
    emailAccountDuplicateError,
    emailAccountForm,
    emailAccountTestResult,
    closeFetcherDialog,
    closeFetcherPollingDialog,
    connectingEmailAccountId,
    deleteEmailAccount,
    deletingEmailAccountId,
    editEmailAccount,
    expandedFetcherLoadingId,
    fetcherPollLoadingId,
    fetcherPollingForm,
    fetcherPollingLoading,
    fetcherPollingTarget: showFetcherPollingDialog ? fetcherPollingTarget : null,
    fetcherStatsById,
    fetcherStatsLoadingId,
    handleEmailAccountFormChange,
    handleFetcherPollingFormChange,
    loadFetcherCustomRange,
    openAddFetcherDialog,
    openFetcherPollingDialog,
    refreshFetcherState,
    resetFetcherPollingSettings,
    runFetcherPoll,
    saveEmailAccount,
    saveEmailAccountAndConnectOAuth,
    saveFetcherPollingSettings,
    showFetcherDialog,
    startSourceOAuth,
    testEmailAccountConnection,
    userEmailAccounts,
    visibleFetchers
  }
}