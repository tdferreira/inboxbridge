import { useState } from 'react'
import { apiErrorText } from '@/lib/api'
import { pollErrorNotification, translatedNotification } from '@/lib/notifications'
import { buildSourceEmailAccountTargetId, extractSourceEmailAccountId } from '@/lib/sectionTargets'

const DEFAULT_SYSTEM_POLLING_FORM = {
  pollEnabledMode: 'DEFAULT',
  pollIntervalOverride: '',
  fetchWindowOverride: '',
  manualTriggerLimitCountOverride: '',
  manualTriggerLimitWindowSecondsOverride: '',
  sourceHostMinSpacingOverride: '',
  sourceHostMaxConcurrencyOverride: '',
  destinationProviderMinSpacingOverride: '',
  destinationProviderMaxConcurrencyOverride: '',
  throttleLeaseTtlOverride: '',
  adaptiveThrottleMaxMultiplierOverride: '',
  successJitterRatioOverride: '',
  maxSuccessJitterOverride: ''
}

const DEFAULT_USER_POLLING_FORM = {
  pollEnabledMode: 'DEFAULT',
  pollIntervalOverride: '',
  fetchWindowOverride: ''
}

function notificationTargetForPollErrors(errorDetails = [], messages = [], fallbackTarget) {
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
  const rawSourceTarget = rawMessages
    .map((message) => extractSourceEmailAccountId(message))
    .find(Boolean)
  if (rawSourceTarget) {
    return buildSourceEmailAccountTargetId(rawSourceTarget)
  }
  return fallbackTarget
}

export function usePollingControllers({
  authOptions,
  errorText,
  isAdmin = false,
  language,
  loadAppData,
  openConfirmation,
  closeConfirmation,
  pushNotification,
  t,
  withPending
}) {
  const [userPollingSettings, setUserPollingSettings] = useState(null)
  const [userPollingForm, setUserPollingForm] = useState(DEFAULT_USER_POLLING_FORM)
  const [userPollingFormDirty, setUserPollingFormDirty] = useState(false)
  const [showUserPollingDialog, setShowUserPollingDialog] = useState(false)
  const [systemPollingForm, setSystemPollingForm] = useState(DEFAULT_SYSTEM_POLLING_FORM)
  const [systemPollingFormDirty, setSystemPollingFormDirty] = useState(false)
  const [showSystemPollingDialog, setShowSystemPollingDialog] = useState(false)
  const [runningPoll, setRunningPoll] = useState(false)
  const [runningUserPoll, setRunningUserPoll] = useState(false)
  const [livePoll, setLivePoll] = useState(null)

  function applyLoadedUserPolling(userPollingPayload) {
    setUserPollingSettings(userPollingPayload)
    if (!userPollingFormDirty && userPollingPayload) {
      setUserPollingForm({
        pollEnabledMode: userPollingPayload.pollEnabledOverride === null
          ? 'DEFAULT'
          : userPollingPayload.pollEnabledOverride ? 'ENABLED' : 'DISABLED',
        pollIntervalOverride: userPollingPayload.pollIntervalOverride || '',
        fetchWindowOverride: userPollingPayload.fetchWindowOverride === null ? '' : String(userPollingPayload.fetchWindowOverride)
      })
    }
  }

  function applyLoadedSystemPolling(systemPollingPayload) {
    if (!systemPollingFormDirty && systemPollingPayload) {
      setSystemPollingForm({
        pollEnabledMode: systemPollingPayload.pollEnabledOverride === null
          ? 'DEFAULT'
          : systemPollingPayload.pollEnabledOverride ? 'ENABLED' : 'DISABLED',
        pollIntervalOverride: systemPollingPayload.pollIntervalOverride || '',
        fetchWindowOverride: systemPollingPayload.fetchWindowOverride === null ? '' : String(systemPollingPayload.fetchWindowOverride),
        manualTriggerLimitCountOverride: systemPollingPayload.manualTriggerLimitCountOverride === null ? '' : String(systemPollingPayload.manualTriggerLimitCountOverride),
        manualTriggerLimitWindowSecondsOverride: systemPollingPayload.manualTriggerLimitWindowSecondsOverride === null ? '' : String(systemPollingPayload.manualTriggerLimitWindowSecondsOverride),
        sourceHostMinSpacingOverride: systemPollingPayload.sourceHostMinSpacingOverride || '',
        sourceHostMaxConcurrencyOverride: systemPollingPayload.sourceHostMaxConcurrencyOverride === null ? '' : String(systemPollingPayload.sourceHostMaxConcurrencyOverride),
        destinationProviderMinSpacingOverride: systemPollingPayload.destinationProviderMinSpacingOverride || '',
        destinationProviderMaxConcurrencyOverride: systemPollingPayload.destinationProviderMaxConcurrencyOverride === null ? '' : String(systemPollingPayload.destinationProviderMaxConcurrencyOverride),
        throttleLeaseTtlOverride: systemPollingPayload.throttleLeaseTtlOverride || '',
        adaptiveThrottleMaxMultiplierOverride: systemPollingPayload.adaptiveThrottleMaxMultiplierOverride === null ? '' : String(systemPollingPayload.adaptiveThrottleMaxMultiplierOverride),
        successJitterRatioOverride: systemPollingPayload.successJitterRatioOverride === null ? '' : String(systemPollingPayload.successJitterRatioOverride),
        maxSuccessJitterOverride: systemPollingPayload.maxSuccessJitterOverride || ''
      })
    }
  }

  function resetPollingControllers() {
    setUserPollingSettings(null)
    setUserPollingForm(DEFAULT_USER_POLLING_FORM)
    setUserPollingFormDirty(false)
    setShowUserPollingDialog(false)
    setSystemPollingForm(DEFAULT_SYSTEM_POLLING_FORM)
    setSystemPollingFormDirty(false)
    setShowSystemPollingDialog(false)
    setRunningPoll(false)
    setRunningUserPoll(false)
    setLivePoll(null)
  }

  function handleUserPollingFormChange(updater) {
    setUserPollingFormDirty(true)
    setUserPollingForm((current) => typeof updater === 'function' ? updater(current) : updater)
  }

  function handleSystemPollingFormChange(updater) {
    setSystemPollingFormDirty(true)
    setSystemPollingForm((current) => typeof updater === 'function' ? updater(current) : updater)
  }

  async function runPoll() {
    if (livePoll?.running) {
      return
    }
    const singleUserMode = authOptions.multiUserEnabled === false
    openConfirmation({
      actionKey: 'runPoll',
      body: t(singleUserMode ? 'system.runPollConfirmBodySingleUser' : 'system.runPollConfirmBody'),
      confirmLabel: t(singleUserMode ? 'system.runPollConfirmActionSingleUser' : 'system.runPollConfirmAction'),
      confirmLoadingLabel: t('system.runPollLoading'),
      confirmTone: 'primary',
      onConfirm: async () => {
        closeConfirmation()
        setRunningPoll(true)
        await withPending('runPoll', async () => {
          try {
            const notificationGroup = 'global-poll'
            const response = await fetch('/api/admin/poll/run', { method: 'POST' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('runPoll')))
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
              throw new Error(JSON.stringify(structuredError))
            }
            const completedMessageKey = payload.spamJunkMessageCount > 0
              ? 'notifications.pollFinishedWithSpam'
              : 'notifications.pollFinished'
            pushNotification({
              groupKey: notificationGroup,
              message: translatedNotification(completedMessageKey, { fetched: payload.fetched, imported: payload.imported, duplicates: payload.duplicates, errors: payload.errors.length, spamJunkCount: payload.spamJunkMessageCount }),
              replaceGroup: true,
              targetId: 'system-dashboard-section',
              tone: 'success'
            })
            await loadAppData()
          } catch (err) {
            let rawMessage = err.message || errorText('runPoll')
            try {
              rawMessage = JSON.parse(rawMessage)
            } catch {
              // Keep plain-text errors as-is so the notification formatter can handle them.
            }
            pushNotification({
              copyText: pollErrorNotification(rawMessage),
              groupKey: 'global-poll',
              message: pollErrorNotification(rawMessage),
              replaceGroup: true,
              targetId: notificationTargetForPollErrors(
                Array.isArray(rawMessage) ? rawMessage : [rawMessage],
                Array.isArray(rawMessage) ? rawMessage : [rawMessage],
                'system-dashboard-section'
              ),
              tone: 'error'
            })
          } finally {
            setRunningPoll(false)
          }
        })
      },
      title: t(singleUserMode ? 'system.runPollConfirmTitleSingleUser' : 'system.runPollConfirmTitle')
    })
  }

  async function runUserPoll() {
    if (livePoll?.running) {
      return
    }
    setRunningUserPoll(true)
    await withPending('runUserPoll', async () => {
      try {
        const notificationGroup = 'user-poll'
        const response = await fetch('/api/app/poll/run', { method: 'POST' })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('runUserPoll')))
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
          throw new Error(JSON.stringify(structuredError))
        }
        const completedMessageKey = payload.spamJunkMessageCount > 0
          ? 'notifications.userPollFinishedWithSpam'
          : 'notifications.userPollFinished'
        pushNotification({
          groupKey: notificationGroup,
          message: translatedNotification(completedMessageKey, { fetched: payload.fetched, imported: payload.imported, duplicates: payload.duplicates, errors: payload.errors.length, spamJunkCount: payload.spamJunkMessageCount }),
          replaceGroup: true,
          targetId: 'user-polling-section',
          tone: 'success'
        })
        await loadAppData()
      } catch (err) {
        let rawMessage = err.message || errorText('runUserPoll')
        try {
          rawMessage = JSON.parse(rawMessage)
        } catch {
          // Keep plain-text errors as-is so the notification formatter can handle them.
        }
        pushNotification({
          copyText: pollErrorNotification(rawMessage),
          groupKey: 'user-poll',
          message: pollErrorNotification(rawMessage),
          replaceGroup: true,
          targetId: notificationTargetForPollErrors(
            Array.isArray(rawMessage) ? rawMessage : [rawMessage],
            Array.isArray(rawMessage) ? rawMessage : [rawMessage],
            'user-polling-section'
          ),
          tone: 'error'
        })
      } finally {
        setRunningUserPoll(false)
      }
    })
  }

  async function savePollingSettings(event) {
    event.preventDefault()
    await withPending('pollingSettingsSave', async () => {
      try {
        const response = await fetch('/api/admin/polling-settings', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            pollEnabledOverride: systemPollingForm.pollEnabledMode === 'DEFAULT'
              ? null
              : systemPollingForm.pollEnabledMode === 'ENABLED',
            pollIntervalOverride: systemPollingForm.pollIntervalOverride.trim() || null,
            fetchWindowOverride: systemPollingForm.fetchWindowOverride.trim() === ''
              ? null
              : Number(systemPollingForm.fetchWindowOverride),
            manualTriggerLimitCountOverride: systemPollingForm.manualTriggerLimitCountOverride.trim() === ''
              ? null
              : Number(systemPollingForm.manualTriggerLimitCountOverride),
            manualTriggerLimitWindowSecondsOverride: systemPollingForm.manualTriggerLimitWindowSecondsOverride.trim() === ''
              ? null
              : Number(systemPollingForm.manualTriggerLimitWindowSecondsOverride),
            sourceHostMinSpacingOverride: systemPollingForm.sourceHostMinSpacingOverride.trim() || null,
            sourceHostMaxConcurrencyOverride: systemPollingForm.sourceHostMaxConcurrencyOverride.trim() === ''
              ? null
              : Number(systemPollingForm.sourceHostMaxConcurrencyOverride),
            destinationProviderMinSpacingOverride: systemPollingForm.destinationProviderMinSpacingOverride.trim() || null,
            destinationProviderMaxConcurrencyOverride: systemPollingForm.destinationProviderMaxConcurrencyOverride.trim() === ''
              ? null
              : Number(systemPollingForm.destinationProviderMaxConcurrencyOverride),
            throttleLeaseTtlOverride: systemPollingForm.throttleLeaseTtlOverride.trim() || null,
            adaptiveThrottleMaxMultiplierOverride: systemPollingForm.adaptiveThrottleMaxMultiplierOverride.trim() === ''
              ? null
              : Number(systemPollingForm.adaptiveThrottleMaxMultiplierOverride),
            successJitterRatioOverride: systemPollingForm.successJitterRatioOverride.trim() === ''
              ? null
              : Number(systemPollingForm.successJitterRatioOverride),
            maxSuccessJitterOverride: systemPollingForm.maxSuccessJitterOverride.trim() || null
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('savePollingSettings')))
        }
        setSystemPollingFormDirty(false)
        setShowSystemPollingDialog(false)
        pushNotification({ message: translatedNotification('notifications.pollingUpdated'), targetId: 'system-dashboard-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.savePollingSettings'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.savePollingSettings'),
          targetId: 'system-dashboard-section',
          tone: 'error'
        })
      }
    })
  }

  async function resetPollingSettings() {
    await withPending('pollingSettingsSave', async () => {
      try {
        const response = await fetch('/api/admin/polling-settings', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            pollEnabledOverride: null,
            pollIntervalOverride: null,
            fetchWindowOverride: null,
            manualTriggerLimitCountOverride: null,
            manualTriggerLimitWindowSecondsOverride: null
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('resetPollingSettings')))
        }
        setSystemPollingForm(DEFAULT_SYSTEM_POLLING_FORM)
        setSystemPollingFormDirty(false)
        setShowSystemPollingDialog(false)
        pushNotification({ message: translatedNotification('notifications.pollingReset'), targetId: 'system-dashboard-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetPollingSettings'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetPollingSettings'),
          targetId: 'system-dashboard-section',
          tone: 'error'
        })
      }
    })
  }

  async function saveUserPollingSettings(event) {
    event.preventDefault()
    await withPending('userPollingSettingsSave', async () => {
      try {
        const response = await fetch('/api/app/polling-settings', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            pollEnabledOverride: userPollingForm.pollEnabledMode === 'DEFAULT'
              ? null
              : userPollingForm.pollEnabledMode === 'ENABLED',
            pollIntervalOverride: userPollingForm.pollIntervalOverride.trim() || null,
            fetchWindowOverride: userPollingForm.fetchWindowOverride.trim() === ''
              ? null
              : Number(userPollingForm.fetchWindowOverride)
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('saveUserPollingSettings')))
        }
        setUserPollingFormDirty(false)
        setShowUserPollingDialog(false)
        pushNotification({ message: translatedNotification('notifications.userPollingUpdated'), targetId: 'user-polling-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveUserPollingSettings'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveUserPollingSettings'),
          targetId: 'user-polling-section',
          tone: 'error'
        })
      }
    })
  }

  async function resetUserPollingSettings() {
    await withPending('userPollingSettingsSave', async () => {
      try {
        const response = await fetch('/api/app/polling-settings', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            pollEnabledOverride: null,
            pollIntervalOverride: null,
            fetchWindowOverride: null
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('resetUserPollingSettings')))
        }
        setUserPollingForm(DEFAULT_USER_POLLING_FORM)
        setUserPollingFormDirty(false)
        setShowUserPollingDialog(false)
        pushNotification({ message: translatedNotification('notifications.userPollingReset'), targetId: 'user-polling-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetUserPollingSettings'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.resetUserPollingSettings'),
          targetId: 'user-polling-section',
          tone: 'error'
        })
      }
    })
  }

  function applyLivePoll(nextLivePoll) {
    setLivePoll(nextLivePoll?.running ? nextLivePoll : null)
  }

  async function invokeLivePollAction(endpoint, fallbackErrorKey = 'runUserPoll') {
    const response = await fetch(endpoint, { method: 'POST' })
    if (!response.ok) {
      throw new Error(await apiErrorText(response, errorText(fallbackErrorKey)))
    }
    const payload = await response.json()
    applyLivePoll(payload)
    return payload
  }

  async function pauseLivePoll() {
    await withPending('pauseLivePoll', async () => {
      const endpoint = isAdmin ? '/api/admin/poll/live/pause' : '/api/poll/live/pause'
      try {
        await invokeLivePollAction(endpoint)
      } catch (err) {
        pushNotification({
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runUserPoll'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runUserPoll'),
          targetId: 'user-polling-section',
          tone: 'error'
        })
      }
    })
  }

  async function resumeLivePoll() {
    await withPending('resumeLivePoll', async () => {
      const endpoint = isAdmin ? '/api/admin/poll/live/resume' : '/api/poll/live/resume'
      try {
        await invokeLivePollAction(endpoint)
      } catch (err) {
        pushNotification({
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runUserPoll'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runUserPoll'),
          targetId: 'user-polling-section',
          tone: 'error'
        })
      }
    })
  }

  async function stopLivePoll() {
    await withPending('stopLivePoll', async () => {
      const endpoint = isAdmin ? '/api/admin/poll/live/stop' : '/api/poll/live/stop'
      try {
        await invokeLivePollAction(endpoint, 'runPoll')
      } catch (err) {
        pushNotification({
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runPoll'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runPoll'),
          targetId: isAdmin ? 'system-dashboard-section' : 'user-polling-section',
          tone: 'error'
        })
      }
    })
  }

  async function moveLivePollSourceNext(sourceId) {
    if (!sourceId) return
    await withPending(`moveLivePollSource:${sourceId}`, async () => {
      const base = isAdmin ? '/api/admin/poll/live/sources' : '/api/poll/live/sources'
      try {
        await invokeLivePollAction(`${base}/${encodeURIComponent(sourceId)}/move-next`)
      } catch (err) {
        pushNotification({
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runPoll'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runPoll'),
          targetId: 'source-email-accounts-section',
          tone: 'error'
        })
      }
    })
  }

  async function retryLivePollSource(sourceId) {
    if (!sourceId) return
    await withPending(`retryLivePollSource:${sourceId}`, async () => {
      const base = isAdmin ? '/api/admin/poll/live/sources' : '/api/poll/live/sources'
      try {
        await invokeLivePollAction(`${base}/${encodeURIComponent(sourceId)}/retry`)
      } catch (err) {
        pushNotification({
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runPoll'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.runPoll'),
          targetId: 'source-email-accounts-section',
          tone: 'error'
        })
      }
    })
  }

  return {
    applyLoadedSystemPolling,
    applyLoadedUserPolling,
    applyLivePoll,
    handleSystemPollingFormChange,
    handleUserPollingFormChange,
    livePoll,
    moveLivePollSourceNext,
    openSystemPollingDialog: () => setShowSystemPollingDialog(true),
    openUserPollingDialog: () => setShowUserPollingDialog(true),
    pauseLivePoll,
    resetPollingControllers,
    resetPollingSettings,
    resetUserPollingSettings,
    resumeLivePoll,
    retryLivePollSource,
    runLivePollSnapshotLoad: async () => {
      const endpoint = isAdmin ? '/api/admin/poll/live' : '/api/poll/live'
      try {
        const response = await fetch(endpoint)
        if (!response.ok) {
          return null
        }
        const payload = await response.json()
        applyLivePoll(payload)
        return payload
      } catch {
        return null
      }
    },
    runPoll,
    runUserPoll,
    runningPoll,
    runningUserPoll,
    savePollingSettings,
    saveUserPollingSettings,
    setShowSystemPollingDialog,
    setShowUserPollingDialog,
    showSystemPollingDialog,
    showUserPollingDialog,
    stopLivePoll,
    systemPollingForm,
    systemPollingFormDirty,
    userPollingForm,
    userPollingFormDirty,
    userPollingSettings
  }
}
