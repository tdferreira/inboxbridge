import { useState } from 'react'
import { apiErrorText } from './api'
import { pollErrorNotification, translatedNotification } from './notifications'
import { buildSourceEmailAccountTargetId, extractSourceEmailAccountId } from './sectionTargets'

const DEFAULT_SYSTEM_POLLING_FORM = {
  pollEnabledMode: 'DEFAULT',
  pollIntervalOverride: '',
  fetchWindowOverride: '',
  manualTriggerLimitCountOverride: '',
  manualTriggerLimitWindowSecondsOverride: ''
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
        manualTriggerLimitWindowSecondsOverride: systemPollingPayload.manualTriggerLimitWindowSecondsOverride === null ? '' : String(systemPollingPayload.manualTriggerLimitWindowSecondsOverride)
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
            pushNotification({ groupKey: notificationGroup, message: translatedNotification('notifications.pollStarted'), targetId: 'system-dashboard-section', tone: 'warning' })
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
    setRunningUserPoll(true)
    await withPending('runUserPoll', async () => {
      try {
        const notificationGroup = 'user-poll'
        pushNotification({ groupKey: notificationGroup, message: translatedNotification('notifications.userPollStarted'), targetId: 'user-polling-section', tone: 'warning' })
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
              : Number(systemPollingForm.manualTriggerLimitWindowSecondsOverride)
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

  return {
    applyLoadedSystemPolling,
    applyLoadedUserPolling,
    handleSystemPollingFormChange,
    handleUserPollingFormChange,
    openSystemPollingDialog: () => setShowSystemPollingDialog(true),
    openUserPollingDialog: () => setShowUserPollingDialog(true),
    resetPollingControllers,
    resetPollingSettings,
    resetUserPollingSettings,
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
    systemPollingForm,
    systemPollingFormDirty,
    userPollingForm,
    userPollingFormDirty,
    userPollingSettings
  }
}
