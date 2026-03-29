import { useState } from 'react'
import { apiErrorText } from './api'
import { formatPollError } from './formatters'

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
            pushNotification({ groupKey: notificationGroup, message: t('notifications.pollStarted'), targetId: 'system-dashboard-section', tone: 'warning' })
            const response = await fetch('/api/admin/poll/run', { method: 'POST' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('runPoll')))
            }
            const payload = await response.json()
            if (payload.errorDetails?.length || payload.errors?.length) {
              const formattedErrors = payload.errorDetails?.length
                ? payload.errorDetails.map((detail) => formatPollError(detail, language))
                : payload.errors.map((message) => formatPollError(message, language))
              throw new Error(formattedErrors.join('\n'))
            }
            const completedMessageKey = payload.spamJunkMessageCount > 0
              ? 'notifications.pollFinishedWithSpam'
              : 'notifications.pollFinished'
            pushNotification({ groupKey: notificationGroup, message: t(completedMessageKey, { fetched: payload.fetched, imported: payload.imported, duplicates: payload.duplicates, errors: payload.errors.length, spamJunkCount: payload.spamJunkMessageCount }), replaceGroup: true, targetId: 'system-dashboard-section', tone: 'success' })
            await loadAppData()
          } catch (err) {
            const message = formatPollError(err.message || errorText('runPoll'), language)
            pushNotification({ copyText: message, groupKey: 'global-poll', message, replaceGroup: true, targetId: 'system-dashboard-section', tone: 'error' })
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
        pushNotification({ groupKey: notificationGroup, message: t('notifications.userPollStarted'), targetId: 'user-polling-section', tone: 'warning' })
        const response = await fetch('/api/app/poll/run', { method: 'POST' })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('runUserPoll')))
        }
        const payload = await response.json()
        if (payload.errorDetails?.length || payload.errors?.length) {
          const formattedErrors = payload.errorDetails?.length
            ? payload.errorDetails.map((detail) => formatPollError(detail, language))
            : payload.errors.map((message) => formatPollError(message, language))
          throw new Error(formattedErrors.join('\n'))
        }
        const completedMessageKey = payload.spamJunkMessageCount > 0
          ? 'notifications.userPollFinishedWithSpam'
          : 'notifications.userPollFinished'
        pushNotification({ groupKey: notificationGroup, message: t(completedMessageKey, { fetched: payload.fetched, imported: payload.imported, duplicates: payload.duplicates, errors: payload.errors.length, spamJunkCount: payload.spamJunkMessageCount }), replaceGroup: true, targetId: 'user-polling-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        const message = formatPollError(err.message || errorText('runUserPoll'), language)
        pushNotification({ copyText: message, groupKey: 'user-poll', message, replaceGroup: true, targetId: 'user-polling-section', tone: 'error' })
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
        pushNotification({ message: t('notifications.pollingUpdated'), targetId: 'system-dashboard-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('savePollingSettings'), message: err.message || errorText('savePollingSettings'), targetId: 'system-dashboard-section', tone: 'error' })
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
        pushNotification({ message: t('notifications.pollingReset'), targetId: 'system-dashboard-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('resetPollingSettings'), message: err.message || errorText('resetPollingSettings'), targetId: 'system-dashboard-section', tone: 'error' })
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
        pushNotification({ message: t('notifications.userPollingUpdated'), targetId: 'user-polling-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('saveUserPollingSettings'), message: err.message || errorText('saveUserPollingSettings'), targetId: 'user-polling-section', tone: 'error' })
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
        pushNotification({ message: t('notifications.userPollingReset'), targetId: 'user-polling-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('resetUserPollingSettings'), message: err.message || errorText('resetUserPollingSettings'), targetId: 'user-polling-section', tone: 'error' })
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