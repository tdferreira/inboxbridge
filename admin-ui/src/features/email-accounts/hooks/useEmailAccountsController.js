import { useEffect, useMemo, useRef, useState } from 'react'
import { apiErrorText } from '@/lib/api'
import { isOauthRevokedError } from '@/lib/formatters'
import { pollErrorNotification, translatedNotification } from '@/lib/notifications'
import { buildSourceEmailAccountTargetId, extractSourceEmailAccountId } from '@/lib/sectionTargets'
import { applyEmailAccountPreset, DEFAULT_EMAIL_ACCOUNT_FORM, normalizeEmailAccountForm } from '@/lib/sourceEmailAccountForm'
import { statsTimezoneHeader } from '@/lib/statsTimezone'

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

const FOLDER_FETCH_RETRY_WINDOW_MS = 2000

function normalizeLoadedEmailAccount(emailAccount) {
  const emailAccountId = emailAccount?.emailAccountId || emailAccount?.emailAccountId || ''
  return {
    ...emailAccount,
    emailAccountId: emailAccountId,
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
    fetchMode: emailAccountForm.fetchMode,
    customLabel: emailAccountForm.customLabel,
    markReadAfterPoll: emailAccountForm.markReadAfterPoll,
    postPollAction: emailAccountForm.postPollAction,
    postPollTargetFolder: emailAccountForm.postPollTargetFolder
  }
}

function sourceFolderSignature(emailAccountForm) {
  return JSON.stringify({
    authMethod: emailAccountForm.authMethod,
    emailAccountId: emailAccountForm.emailAccountId,
    host: emailAccountForm.host,
    oauthProvider: emailAccountForm.oauthProvider,
    originalEmailAccountId: emailAccountForm.originalEmailAccountId,
    password: emailAccountForm.password,
    port: emailAccountForm.port,
    protocol: emailAccountForm.protocol,
    tls: emailAccountForm.tls,
    username: emailAccountForm.username
  })
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
  const validIds = new Set(userEmailAccounts.map((emailAccount) => emailAccount.emailAccountId))
  for (const emailAccount of systemEmailAccounts) {
    validIds.add(emailAccount.id)
  }
  return Object.fromEntries(Object.entries(currentStats).filter(([emailAccountId]) => validIds.has(emailAccountId)))
}

/**
 * Orchestrates source-email-account editing, polling actions, folder loading,
 * per-source stats refreshes, and dialog state for both DB-backed and
 * read-only env-backed source entries.
 */
export function useEmailAccountsController({
  activeBatchPollSourceIds = [],
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
  const [emailAccountFolders, setEmailAccountFolders] = useState([])
  const [emailAccountFoldersLoading, setEmailAccountFoldersLoading] = useState(false)
  const [emailAccountTestResult, setEmailAccountTestResult] = useState(null)
  const [userEmailAccounts, setUserEmailAccounts] = useState([])
  const [expandedFetcherLoadingId, setExpandedFetcherLoadingId] = useState(null)
  const [fetcherStatsById, setFetcherStatsById] = useState({})
  const [fetcherStatsLoadingId, setFetcherStatsLoadingId] = useState(null)
  const [showFetcherDialog, setShowFetcherDialog] = useState(false)
  const [showFetcherPollingDialog, setShowFetcherPollingDialog] = useState(false)
  const [fetcherPollingTarget, setFetcherPollingTarget] = useState(null)
  const [fetcherPollingForm, setFetcherPollingForm] = useState(DEFAULT_SOURCE_POLLING_FORM)
  const folderFetchSuccessSignatureRef = useRef('')
  const folderFetchAttemptRef = useRef({ signature: '', startedAt: 0 })

  const visibleFetchers = useMemo(() => {
    const databaseFetchers = userEmailAccounts.map((emailAccount) => ({
      ...emailAccount,
      emailAccountId: emailAccount.emailAccountId,
      managementSource: 'DATABASE',
      oauthConnected: deriveOauthConnected(emailAccount),
      canDelete: true,
      canEdit: true,
      canConnectOAuth: authOptions.sourceOAuthProviders.includes(emailAccount.oauthProvider) && emailAccount.authMethod === 'OAUTH2',
      canConfigurePolling: true,
      canRunPoll: emailAccount.enabled !== false
    }))
    const envFetchers = sessionUsername === 'admin'
      ? (systemDashboardEmailAccounts || []).map((emailAccount) => ({
        emailAccountId: emailAccount.id || emailAccount.emailAccountId,
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
        fetchMode: emailAccount.fetchMode,
        customLabel: emailAccount.customLabel,
        markReadAfterPoll: emailAccount.markReadAfterPoll,
        postPollAction: emailAccount.postPollAction,
        postPollTargetFolder: emailAccount.postPollTargetFolder,
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
        canRunPoll: emailAccount.enabled !== false
      }))
      : []

    return [...databaseFetchers, ...envFetchers]
      .sort((left, right) => left.emailAccountId.localeCompare(right.emailAccountId))
  }, [authOptions.sourceOAuthProviders, sessionUsername, systemDashboardEmailAccounts, userEmailAccounts])

  function translateProviderLabel(provider) {
    return translatedNotification(provider === 'GOOGLE' ? 'oauthProvider.google' : 'oauthProvider.microsoft')
  }

  const connectingEmailAccountId = visibleFetchers.find((emailAccount) => isPending(`microsoftOAuth:${emailAccount.emailAccountId}`) || isPending(`googleSourceOAuth:${emailAccount.emailAccountId}`))?.emailAccountId || null
  const deletingEmailAccountId = userEmailAccounts.find((emailAccount) => isPending(`bridgeDelete:${emailAccount.emailAccountId}`))?.emailAccountId || null
  const fetcherPollLoadingIds = useMemo(() => {
    const loadingIds = new Set()
    for (const emailAccount of visibleFetchers) {
      if (isPending(`bridgePoll:${emailAccount.emailAccountId}`)) {
        loadingIds.add(emailAccount.emailAccountId)
      }
    }
    for (const activeBatchPollSourceId of activeBatchPollSourceIds) {
      const activeFetcher = visibleFetchers.find((fetcher) => fetcher.emailAccountId === activeBatchPollSourceId)
      if (activeFetcher?.enabled !== false) {
        loadingIds.add(activeBatchPollSourceId)
      }
    }
    return [...loadingIds]
  }, [activeBatchPollSourceIds, isPending, visibleFetchers])
  const fetcherPollingLoading = fetcherPollingTarget
    ? isPending(`fetcherPollingSave:${fetcherPollingTarget.emailAccountId}`) || isPending(`fetcherPollingLoad:${fetcherPollingTarget.emailAccountId}`)
    : false

  function applyLoadedEmailAccounts(emailAccountsPayload, adminEmailAccounts = []) {
    const nextUserEmailAccounts = Array.isArray(emailAccountsPayload)
      ? emailAccountsPayload.map(normalizeLoadedEmailAccount)
      : []
    const nextSystemEmailAccounts = Array.isArray(adminEmailAccounts)
      ? adminEmailAccounts.map((emailAccount) => ({
        ...emailAccount,
        id: emailAccount.emailAccountId || emailAccount.id || emailAccount.emailAccountId || ''
      }))
      : []
    setUserEmailAccounts(nextUserEmailAccounts)
    setFetcherStatsById((current) => pruneFetcherStats(current, nextUserEmailAccounts, nextSystemEmailAccounts))
  }

  function handleEmailAccountFormChange(updater) {
    setEmailAccountDuplicateError('')
    setEmailAccountTestResult(null)
    setEmailAccountForm((current) => {
      const next = normalizeEmailAccountForm(typeof updater === 'function' ? updater(current) : updater, authOptions)
      if (sourceFolderSignature(current) !== sourceFolderSignature(next)) {
        setEmailAccountFolders([])
        folderFetchSuccessSignatureRef.current = ''
      }
      return next
    })
  }

  function applyEmailAccountPresetSelection(presetId) {
    setEmailAccountTestResult(null)
    setEmailAccountForm((current) => applyEmailAccountPreset(current, presetId, authOptions))
  }

  function openAddFetcherDialog() {
    setEmailAccountDuplicateError('')
    setEmailAccountFolders([])
    setEmailAccountTestResult(null)
    folderFetchSuccessSignatureRef.current = ''
    folderFetchAttemptRef.current = { signature: '', startedAt: 0 }
    setEmailAccountForm(DEFAULT_EMAIL_ACCOUNT_FORM)
    setShowFetcherDialog(true)
  }

  function editEmailAccount(emailAccount) {
    setEmailAccountFolders([])
    setEmailAccountTestResult(null)
    folderFetchSuccessSignatureRef.current = ''
    folderFetchAttemptRef.current = { signature: '', startedAt: 0 }
    handleEmailAccountFormChange({
      originalEmailAccountId: emailAccount.emailAccountId,
      emailAccountId: emailAccount.emailAccountId,
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
      fetchMode: emailAccount.fetchMode || 'POLLING',
      customLabel: emailAccount.customLabel,
      markReadAfterPoll: emailAccount.markReadAfterPoll ?? false,
      postPollAction: emailAccount.postPollAction || 'NONE',
      postPollTargetFolder: emailAccount.postPollTargetFolder || ''
    })
    setShowFetcherDialog(true)
  }

  function closeFetcherDialog() {
    setEmailAccountFolders([])
    setEmailAccountTestResult(null)
    folderFetchSuccessSignatureRef.current = ''
    folderFetchAttemptRef.current = { signature: '', startedAt: 0 }
    setShowFetcherDialog(false)
  }

  async function loadEmailAccountFolders(formOverride = emailAccountForm, options = {}) {
    const { suppressErrors = false } = options
    const payload = buildEmailAccountRequestPayload(formOverride)
    if (payload.protocol !== 'IMAP') {
      setEmailAccountFolders([])
      return []
    }
    setEmailAccountFoldersLoading(true)
    const signature = sourceFolderSignature(formOverride)
    folderFetchAttemptRef.current = { signature, startedAt: Date.now() }
    try {
      const response = await fetch('/api/app/email-accounts/folders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      })
      if (!response.ok) {
        throw new Error(await apiErrorText(response, errorText('loadMailFetcherFolders')))
      }
      const folderPayload = await response.json()
      const folders = Array.isArray(folderPayload?.folders) ? folderPayload.folders : []
      setEmailAccountFolders(folders)
      folderFetchSuccessSignatureRef.current = signature
      return folders
    } catch (err) {
      setEmailAccountFolders([])
      if (!suppressErrors) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadMailFetcherFolders'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadMailFetcherFolders'),
          targetId: 'source-email-accounts-section',
          tone: 'error'
        })
      }
      throw err
    } finally {
      setEmailAccountFoldersLoading(false)
    }
  }

  function ensureEmailAccountFoldersForForm(formOverride = emailAccountForm, options = {}) {
    const { suppressErrors = true } = options
    const signature = sourceFolderSignature(formOverride)
    const now = Date.now()
    const lastAttempt = folderFetchAttemptRef.current
    if (
      formOverride.protocol !== 'IMAP'
      || emailAccountFoldersLoading
      || (emailAccountFolders.length > 0 && folderFetchSuccessSignatureRef.current === signature)
      || folderFetchSuccessSignatureRef.current === signature
      || (lastAttempt.signature === signature && now - lastAttempt.startedAt < FOLDER_FETCH_RETRY_WINDOW_MS)
    ) {
      return Promise.resolve(emailAccountFolders)
    }
    return loadEmailAccountFolders(formOverride, { suppressErrors })
  }

  function handleFolderInputFocus() {
    if (!showFetcherDialog) {
      return
    }
    ensureEmailAccountFoldersForForm(emailAccountForm, { suppressErrors: true }).catch(() => {})
  }

  function handleFolderInputActivity(nextValue = '') {
    if (
      !showFetcherDialog
      || emailAccountFolders.length > 0
      || emailAccountFoldersLoading
      || String(nextValue).trim().length !== 1
    ) {
      return
    }
    ensureEmailAccountFoldersForForm(emailAccountForm, { suppressErrors: true }).catch(() => {})
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
    if (!fetcher?.emailAccountId) return
    const { suppressErrors = false } = options
    setFetcherStatsLoadingId(fetcher.emailAccountId)
    try {
      const endpointPrefix = fetcher.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
      const response = await fetch(`${endpointPrefix}/${encodeURIComponent(fetcher.emailAccountId)}/polling-stats`, {
        headers: statsTimezoneHeader()
      })
      if (!response.ok) {
        throw new Error(await apiErrorText(response, errorText('loadMailAccountStatistics')))
      }
      const payload = await response.json()
      setFetcherStatsById((current) => ({ ...current, [fetcher.emailAccountId]: payload }))
    } catch (err) {
      if (!suppressErrors) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadMailAccountStatistics'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadMailAccountStatistics'),
          targetId: 'source-email-accounts-section',
          tone: 'error'
        })
      }
    } finally {
      setFetcherStatsLoadingId((current) => current === fetcher.emailAccountId ? null : current)
    }
  }

  async function loadScopedTimelineBundle(endpoint, fallbackMessage) {
    const response = await fetch(endpoint, {
      headers: statsTimezoneHeader()
    })
    if (!response.ok) {
      throw new Error(await apiErrorText(response, fallbackMessage))
    }
    const payload = await response.json()
    return {
      imports: payload.importTimelines?.custom || [],
      duplicates: payload.duplicateTimelines?.custom || [],
      errors: payload.errorTimelines?.custom || [],
      manualRuns: payload.manualRunTimelines?.custom || [],
      scheduledRuns: payload.scheduledRunTimelines?.custom || [],
      idleRuns: payload.idleRunTimelines?.custom || []
    }
  }

  async function loadFetcherCustomRange(fetcher, range) {
    const search = new URLSearchParams({ from: range.from })
    if (range.to) search.set('to', range.to)
    const endpointPrefix = fetcher.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
    return loadScopedTimelineBundle(
      `${endpointPrefix}/${encodeURIComponent(fetcher.emailAccountId)}/polling-stats/range?${search.toString()}`,
      t('pollingStats.customRangeLoadError')
    )
  }

  async function upsertEmailAccountForm(options = {}) {
    const { connectMicrosoftAfterSave = false, formOverride = emailAccountForm } = options
    const normalizedEmailAccountId = formOverride.emailAccountId.trim()
    const originalEmailAccountId = formOverride.originalEmailAccountId.trim()
    const duplicateFetcher = visibleFetchers.find((fetcher) => (
      fetcher.emailAccountId === normalizedEmailAccountId && fetcher.emailAccountId !== originalEmailAccountId
    ))
    if (duplicateFetcher) {
      const duplicateMessage = t('emailAccounts.duplicateId', { emailAccountId: normalizedEmailAccountId })
      setEmailAccountDuplicateError(duplicateMessage)
      pushNotification({ autoCloseMs: null, message: translatedNotification('emailAccounts.duplicateId', { emailAccountId: normalizedEmailAccountId }), targetId: 'source-email-accounts-section', tone: 'error' })
      return null
    }
    const actionKey = connectMicrosoftAfterSave ? 'bridgeSaveConnect' : 'bridgeSave'
    return withPending(actionKey, async () => {
      try {
        const response = await fetch('/api/app/email-accounts', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildEmailAccountRequestPayload(formOverride))
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('saveMailFetcher')))
        }
        const payload = await response.json()
        setEmailAccountDuplicateError('')
        setEmailAccountTestResult(null)
        if (!connectMicrosoftAfterSave) {
          pushNotification({ message: translatedNotification('notifications.emailAccountSaved', { emailAccountId: formOverride.emailAccountId }), targetId: 'source-email-accounts-section', tone: 'success' })
          setEmailAccountForm(DEFAULT_EMAIL_ACCOUNT_FORM)
          setShowFetcherDialog(false)
          await loadAppData()
        } else {
          pushNotification({
            message: translatedNotification('notifications.emailAccountSavedStartingProviderOAuth', {
              emailAccountId: payload.emailAccountId || payload.emailAccountId || formOverride.emailAccountId,
              provider: formOverride.oauthProvider === 'GOOGLE'
                ? translateProviderLabel('GOOGLE')
                : translateProviderLabel('MICROSOFT')
            }),
            targetId: 'source-email-accounts-section',
            tone: 'warning'
          })
        }
        return payload
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveMailFetcher'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveMailFetcher'), targetId: 'source-email-accounts-section', tone: 'error' })
        return null
      }
    })
  }

  async function saveEmailAccount(event) {
    event.preventDefault()
    await upsertEmailAccountForm()
  }

  async function saveEmailAccountWithoutValidation() {
    await upsertEmailAccountForm({
      formOverride: {
        ...emailAccountForm,
        enabled: false
      }
    })
  }

  async function saveEmailAccountAndConnectOAuth() {
    const payload = await upsertEmailAccountForm({ connectMicrosoftAfterSave: true })
    const savedBridgeId = payload?.emailAccountId || payload?.emailAccountId || emailAccountForm.emailAccountId?.trim()
    if (savedBridgeId) {
      startSourceOAuth(savedBridgeId, emailAccountForm.oauthProvider)
    }
  }

  async function testEmailAccountConnection() {
    await withPending('bridgeConnectionTest', async () => {
      try {
        const requestForm = emailAccountForm
        const response = await fetch('/api/app/email-accounts/test-connection', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildEmailAccountRequestPayload(requestForm))
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('testMailFetcherConnection')))
        }
        const payload = await response.json()
        const tlsAutoApplied = Boolean(payload.tlsRecommended && !requestForm.tls)
        const nextForm = tlsAutoApplied
          ? normalizeEmailAccountForm({
            ...requestForm,
            tls: true,
            port: payload.recommendedTlsPort || requestForm.port
          }, authOptions)
          : requestForm
        if (tlsAutoApplied) {
          setEmailAccountForm(nextForm)
        }
        const message = payload.message || t('emailAccounts.testSuccess')
        setEmailAccountTestResult({
          ...payload,
          port: tlsAutoApplied ? (payload.recommendedTlsPort || payload.port) : payload.port,
          tls: tlsAutoApplied ? true : payload.tls,
          message,
          tone: 'success'
        })
        if (nextForm.protocol === 'IMAP') {
          await loadEmailAccountFolders(nextForm, { suppressErrors: true })
        }
        pushNotification({
          message,
          targetId: 'source-email-accounts-section',
          tone: tlsAutoApplied ? 'warning' : 'success'
        })
      } catch (err) {
        const message = err.message || errorText('testMailFetcherConnection')
        setEmailAccountTestResult({ message, tone: 'error' })
        pushNotification({ autoCloseMs: null, copyText: message, message, targetId: 'source-email-accounts-section', tone: 'error' })
      }
    })
  }

  async function deleteEmailAccount(emailAccountId, openConfirmation) {
    openConfirmation({
      actionKey: `bridgeDelete:${emailAccountId}`,
      body: t('emailAccount.deleteConfirmBody', { emailAccountId }),
      confirmLabel: t('emailAccount.delete'),
      confirmLoadingLabel: t('emailAccount.deleteLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`bridgeDelete:${emailAccountId}`, async () => {
          try {
            const response = await fetch(`/api/app/email-accounts/${encodeURIComponent(emailAccountId)}`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('deleteMailFetcher')))
            }
            pushNotification({ message: translatedNotification('notifications.emailAccountDeleted', { emailAccountId }), targetId: 'source-email-accounts-section', tone: 'success' })
            await loadAppData()
            openConfirmation(null)
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.deleteMailFetcher'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.deleteMailFetcher'), targetId: 'source-email-accounts-section', tone: 'error' })
          }
        })
      },
      title: t('emailAccount.deleteConfirmTitle')
    })
  }

  async function openFetcherPollingDialog(fetcher) {
    setFetcherPollingTarget(fetcher)
    await withPending(`fetcherPollingLoad:${fetcher.emailAccountId}`, async () => {
      try {
        const endpointPrefix = fetcher.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(fetcher.emailAccountId)}/polling-settings`)
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('loadFetcherPollingSettings')))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        setShowFetcherPollingDialog(true)
      } catch (err) {
        setFetcherPollingTarget(null)
        pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadFetcherPollingSettings'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadFetcherPollingSettings'), targetId: 'source-email-accounts-section', tone: 'error' })
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
    await withPending(`fetcherPollingSave:${fetcherPollingTarget.emailAccountId}`, async () => {
      try {
        const endpointPrefix = fetcherPollingTarget.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(fetcherPollingTarget.emailAccountId)}/polling-settings`, {
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
        pushNotification({ message: translatedNotification('notifications.fetcherPollingSaved', { emailAccountId: fetcherPollingTarget.emailAccountId }), targetId: 'source-email-accounts-section', tone: 'success' })
        await loadAppData()
        closeFetcherPollingDialog()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveFetcherPollingSettings'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveFetcherPollingSettings'), targetId: 'source-email-accounts-section', tone: 'error' })
      }
    })
  }

  async function resetFetcherPollingSettings() {
    if (!fetcherPollingTarget) return
    await withPending(`fetcherPollingSave:${fetcherPollingTarget.emailAccountId}`, async () => {
      try {
        const endpointPrefix = fetcherPollingTarget.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(fetcherPollingTarget.emailAccountId)}/polling-settings`, {
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
        pushNotification({ message: translatedNotification('notifications.fetcherPollingReset', { emailAccountId: fetcherPollingTarget.emailAccountId }), targetId: 'source-email-accounts-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetFetcherPollingSettings'), message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetFetcherPollingSettings'), targetId: 'source-email-accounts-section', tone: 'error' })
      }
    })
  }

  function notificationTargetForPollErrors(errorDetails = [], messages = [], fallbackTarget = 'source-email-accounts-section') {
    const details = Array.isArray(errorDetails) ? errorDetails : []
    if (details.some((detail) => detail?.code === 'gmail_account_not_linked' || detail?.code === 'gmail_access_revoked')) {
      return 'destination-mailbox-section'
    }
    const sourceTarget = details
      .map((detail) => extractSourceEmailAccountId(detail))
      .find(Boolean)
    if (sourceTarget) {
      return buildSourceEmailAccountTargetId(sourceTarget)
    }
    const rawMessages = Array.isArray(messages) ? messages : []
    if (rawMessages.some((message) => typeof message === 'string'
      && (message.includes('The Gmail destination is not linked for this account')
        || message.includes('The linked Gmail account no longer grants InboxBridge access')))) {
      return 'destination-mailbox-section'
    }
    const rawSourceTarget = rawMessages
      .map((message) => extractSourceEmailAccountId(message))
      .find(Boolean)
    if (rawSourceTarget) {
      return buildSourceEmailAccountTargetId(rawSourceTarget)
    }
    return fallbackTarget
  }

  async function runFetcherPoll(fetcherOrBridgeId) {
    const fetcher = typeof fetcherOrBridgeId === 'string'
      ? visibleFetchers.find((entry) => entry.emailAccountId === fetcherOrBridgeId)
      : fetcherOrBridgeId
    const emailAccountId = typeof fetcherOrBridgeId === 'string' ? fetcherOrBridgeId : fetcherOrBridgeId?.emailAccountId
    if (!emailAccountId || fetcher?.enabled === false || fetcher?.canRunPoll === false) {
      return
    }
    await withPending(`bridgePoll:${emailAccountId}`, async () => {
      try {
        const notificationGroup = `fetcher-poll:${emailAccountId}`
        pushNotification({
          autoCloseMs: 10000,
          groupKey: notificationGroup,
          message: translatedNotification('notifications.fetcherPollStarted', { emailAccountId }),
          targetId: 'source-email-accounts-section',
          tone: 'warning'
        })
        const endpointPrefix = fetcher?.managementSource === 'ENVIRONMENT' ? '/api/admin/email-accounts' : '/api/app/email-accounts'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(emailAccountId)}/poll/run`, { method: 'POST' })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('runMailFetcherPoll')))
        }
        const payload = await response.json()
        if (payload.errorDetails?.length || payload.errors?.length) {
          const structuredError = payload.errorDetails?.length
            ? payload.errorDetails.length === 1
              ? payload.errorDetails[0]
              : payload.errorDetails
            : payload.errors.length === 1
              ? payload.errors[0]
              : payload.errors
          throw Object.assign(new Error(JSON.stringify(structuredError)), {
            notificationTargetId: notificationTargetForPollErrors(payload.errorDetails, payload.errors)
          })
        }
        const completedMessageKey = payload.spamJunkMessageCount > 0
          ? 'notifications.fetcherPollCompletedWithSpam'
          : 'notifications.fetcherPollCompleted'
        pushNotification({
          groupKey: notificationGroup,
          message: translatedNotification(completedMessageKey, {
            emailAccountId,
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
        let message = err.message || errorText('runMailFetcherPoll')
        try {
          message = JSON.parse(message)
        } catch {
          // Keep plain-text errors as-is so the shared formatter can localize them later.
        }
        pushNotification({
          copyText: pollErrorNotification(message),
          groupKey: `fetcher-poll:${emailAccountId}`,
          message: pollErrorNotification(message),
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

  async function toggleEmailAccountEnabled(fetcher) {
    if (!fetcher?.emailAccountId || fetcher.managementSource === 'ENVIRONMENT' || fetcher.canEdit === false) {
      return
    }
    const nextEnabled = fetcher.enabled === false
    await withPending(`bridgeToggleEnabled:${fetcher.emailAccountId}`, async () => {
      try {
        const response = await fetch('/api/app/email-accounts', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            originalEmailAccountId: fetcher.emailAccountId,
            emailAccountId: fetcher.emailAccountId,
            enabled: nextEnabled,
            protocol: fetcher.protocol,
            host: fetcher.host,
            port: fetcher.port,
            tls: fetcher.tls,
            authMethod: fetcher.authMethod,
            oauthProvider: fetcher.oauthProvider,
            username: fetcher.username,
            password: '',
            oauthRefreshToken: '',
            folder: fetcher.folder,
            unreadOnly: fetcher.unreadOnly,
            fetchMode: fetcher.fetchMode || 'POLLING',
            customLabel: fetcher.customLabel,
            markReadAfterPoll: fetcher.markReadAfterPoll,
            postPollAction: fetcher.postPollAction,
            postPollTargetFolder: fetcher.postPollTargetFolder
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('saveMailFetcher')))
        }
        pushNotification({
          message: translatedNotification(
            nextEnabled ? 'notifications.bridgeEnabled' : 'notifications.bridgeDisabled',
            { emailAccountId: fetcher.emailAccountId }
          ),
          targetId: 'source-email-accounts-section',
          tone: 'success'
        })
        await loadAppData()
      } catch (err) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveMailFetcher'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveMailFetcher'),
          targetId: 'source-email-accounts-section',
          tone: 'error'
        })
      }
    })
  }

  async function refreshFetcherState(fetcher, expanded) {
    if (!expanded) {
      return
    }
    setExpandedFetcherLoadingId(fetcher.emailAccountId)
    try {
      await Promise.all([
        refreshSectionData('sourceEmailAccountsCollapsed', () => loadAppData({ suppressErrors: true })),
        loadFetcherStats(fetcher, { suppressErrors: true })
      ])
    } finally {
      setExpandedFetcherLoadingId((current) => current === fetcher.emailAccountId ? null : current)
    }
  }

  useEffect(() => {
    if (!showFetcherDialog) {
      setEmailAccountDuplicateError('')
    }
  }, [showFetcherDialog])

  useEffect(() => {
    if (!showFetcherDialog || emailAccountForm.protocol !== 'IMAP' || !emailAccountForm.originalEmailAccountId) {
      return
    }
    ensureEmailAccountFoldersForForm(emailAccountForm, { suppressErrors: true }).catch(() => {})
  }, [emailAccountForm.originalEmailAccountId, emailAccountForm.protocol, showFetcherDialog])

  return {
    applyEmailAccountPreset: applyEmailAccountPresetSelection,
    applyLoadedEmailAccounts,
    emailAccountDuplicateError,
    emailAccountForm,
    emailAccountFolders,
    emailAccountFoldersLoading,
    emailAccountTestResult,
    closeFetcherDialog,
    closeFetcherPollingDialog,
    connectingEmailAccountId,
    deleteEmailAccount,
    deletingEmailAccountId,
    editEmailAccount,
    expandedFetcherLoadingId,
    fetcherPollLoadingIds,
    fetcherPollingForm,
    fetcherPollingLoading,
    fetcherPollingTarget: showFetcherPollingDialog ? fetcherPollingTarget : null,
    fetcherStatsById,
    fetcherStatsLoadingId,
    handleEmailAccountFormChange,
    handleFolderInputActivity,
    handleFolderInputFocus,
    handleFetcherPollingFormChange,
    loadFetcherCustomRange,
    openAddFetcherDialog,
    openFetcherPollingDialog,
    refreshFetcherState,
    resetFetcherPollingSettings,
    runFetcherPoll,
    saveEmailAccount,
    saveEmailAccountWithoutValidation,
    saveEmailAccountAndConnectOAuth,
    saveFetcherPollingSettings,
    showFetcherDialog,
    startSourceOAuth,
    testEmailAccountConnection,
    togglingEmailAccountId: visibleFetchers.find((emailAccount) => isPending(`bridgeToggleEnabled:${emailAccount.emailAccountId}`))?.emailAccountId || null,
    toggleEmailAccountEnabled,
    runnableUserEmailAccounts: userEmailAccounts.filter((emailAccount) => emailAccount.enabled !== false),
    userEmailAccounts,
    visibleFetchers
  }
}
