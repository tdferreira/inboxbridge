import { useEffect, useMemo, useRef, useState } from 'react'
import AuthScreen from './components/auth/AuthScreen'
import Banner from './components/common/Banner'
import ConfirmationDialog from './components/common/ConfirmationDialog'
import LoadingScreen from './components/common/LoadingScreen'
import PasswordPanel from './components/account/PasswordPanel'
import PasskeyPanel from './components/account/PasskeyPanel'
import PasswordResetDialog from './components/admin/PasswordResetDialog'
import GmailDestinationSection from './components/gmail/GmailDestinationSection'
import HeroPanel from './components/layout/HeroPanel'
import SetupGuidePanel from './components/layout/SetupGuidePanel'
import SystemDashboardSection from './components/admin/SystemDashboardSection'
import UserPollingSettingsSection from './components/polling/UserPollingSettingsSection'
import UserManagementSection from './components/admin/UserManagementSection'
import UserBridgesSection from './components/bridges/UserBridgesSection'
import { apiErrorText } from './lib/api'
import { findEmailProviderPreset } from './lib/emailProviderPresets'
import { parseCreateOptions, parseGetOptions, passkeysSupported, serializeCredential } from './lib/passkeys'
import { buildSetupGuideState } from './lib/setupGuide'
import { languageOptions, normalizeLocale, translate } from './lib/i18n'

const REFRESH_MS = 30000

const DEFAULT_LOGIN_FORM = { username: 'admin', password: 'nimda' }
const DEFAULT_REGISTER_FORM = { username: '', password: '', confirmPassword: '' }
const DEFAULT_PASSWORD_FORM = { currentPassword: '', newPassword: '', confirmNewPassword: '' }
const DEFAULT_ADMIN_RESET_PASSWORD_FORM = { newPassword: '', confirmNewPassword: '' }
const DEFAULT_CREATE_USER_FORM = { username: '', password: '', role: 'USER' }
const DEFAULT_GMAIL_CONFIG = {
  destinationUser: 'me',
  clientId: '',
  clientSecret: '',
  refreshToken: '',
  redirectUri: '',
  createMissingLabels: true,
  neverMarkSpam: false,
  processForCalendar: false
}
const DEFAULT_BRIDGE_FORM = {
  originalBridgeId: '',
  bridgeId: '',
  enabled: true,
  protocol: 'IMAP',
  host: '',
  port: 993,
  tls: true,
  authMethod: 'PASSWORD',
  oauthProvider: 'NONE',
  username: '',
  password: '',
  oauthRefreshToken: '',
  folder: 'INBOX',
  unreadOnly: false,
  customLabel: ''
}
const DEFAULT_UI_PREFERENCES = {
  persistLayout: false,
  quickSetupCollapsed: false,
  gmailDestinationCollapsed: false,
  userPollingCollapsed: false,
  sourceBridgesCollapsed: false,
  systemDashboardCollapsed: false,
  userManagementCollapsed: false,
  language: 'en'
}
const DEFAULT_SYSTEM_POLLING_FORM = {
  pollEnabledMode: 'DEFAULT',
  pollIntervalOverride: '',
  fetchWindowOverride: ''
}
const DEFAULT_USER_POLLING_FORM = {
  pollEnabledMode: 'DEFAULT',
  pollIntervalOverride: '',
  fetchWindowOverride: ''
}
const DEFAULT_AUTH_OPTIONS = {
  multiUserEnabled: true
}

/**
 * Coordinates admin-ui data fetching and browser interactions while delegating
 * UI structure to smaller reusable components.
 */
function App() {
  const [session, setSession] = useState(null)
  const [authOptions, setAuthOptions] = useState(DEFAULT_AUTH_OPTIONS)
  const [authLoading, setAuthLoading] = useState(true)
  const [authError, setAuthError] = useState('')
  const [notifications, setNotifications] = useState([])
  const [loadingData, setLoadingData] = useState(false)
  const [pendingActions, setPendingActions] = useState({})
  const [selectedUserLoading, setSelectedUserLoading] = useState(false)

  const [loginForm, setLoginForm] = useState(DEFAULT_LOGIN_FORM)
  const [registerForm, setRegisterForm] = useState(DEFAULT_REGISTER_FORM)
  const [passwordForm, setPasswordForm] = useState(DEFAULT_PASSWORD_FORM)
  const [adminResetPasswordForm, setAdminResetPasswordForm] = useState(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
  const [createUserForm, setCreateUserForm] = useState(DEFAULT_CREATE_USER_FORM)
  const [passkeyLabel, setPasskeyLabel] = useState('')

  const [gmailConfig, setGmailConfig] = useState(DEFAULT_GMAIL_CONFIG)
  const [gmailMeta, setGmailMeta] = useState(null)
  const [userPollingSettings, setUserPollingSettings] = useState(null)
  const [userPollingForm, setUserPollingForm] = useState(DEFAULT_USER_POLLING_FORM)
  const [userPollingFormDirty, setUserPollingFormDirty] = useState(false)

  const [bridgeForm, setBridgeForm] = useState(DEFAULT_BRIDGE_FORM)
  const [bridgeDuplicateError, setBridgeDuplicateError] = useState('')

  const [myBridges, setMyBridges] = useState([])
  const [myPasskeys, setMyPasskeys] = useState([])
  const [systemDashboard, setSystemDashboard] = useState(null)
  const [users, setUsers] = useState([])
  const [selectedUserId, setSelectedUserId] = useState(null)
  const [selectedUserConfig, setSelectedUserConfig] = useState(null)
  const [runningPoll, setRunningPoll] = useState(false)
  const [uiPreferences, setUiPreferences] = useState(DEFAULT_UI_PREFERENCES)
  const [uiPreferencesLoadedForUserId, setUiPreferencesLoadedForUserId] = useState(null)
  const [touchedSections, setTouchedSections] = useState({})
  const [showSecurityPanel, setShowSecurityPanel] = useState(false)
  const [showPasswordResetDialog, setShowPasswordResetDialog] = useState(false)
  const [showFetcherDialog, setShowFetcherDialog] = useState(false)
  const [confirmationDialog, setConfirmationDialog] = useState(null)
  const [systemPollingForm, setSystemPollingForm] = useState(DEFAULT_SYSTEM_POLLING_FORM)
  const [systemPollingFormDirty, setSystemPollingFormDirty] = useState(false)
  const [dismissedPersistentNotifications, setDismissedPersistentNotifications] = useState({})
  const [language, setLanguage] = useState(() => normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language))
  const [registerOpen, setRegisterOpen] = useState(false)
  const notificationTimersRef = useRef(new Map())
  const t = useMemo(() => (key, params) => translate(language, key, params), [language])
  const selectableLanguages = useMemo(() => languageOptions.map((value) => ({
    value,
    label: translate(language, `language.${value}`)
  })), [language])

  function normalizeUiPreferences(payload) {
    const nextUiPreferences = {
      ...DEFAULT_UI_PREFERENCES,
      ...(payload || {})
    }
    return {
      ...nextUiPreferences,
      ...(nextUiPreferences.persistLayout ? {} : {
        quickSetupCollapsed: false,
        gmailDestinationCollapsed: false,
        userPollingCollapsed: false,
        sourceBridgesCollapsed: false,
        systemDashboardCollapsed: false,
        userManagementCollapsed: false
      }),
      language: normalizeLocale(nextUiPreferences.language)
    }
  }

  function isPending(actionKey) {
    return Boolean(pendingActions[actionKey])
  }

  async function withPending(actionKey, action) {
    setPendingActions((current) => ({ ...current, [actionKey]: true }))
    try {
      return await action()
    } finally {
      setPendingActions((current) => {
        const next = { ...current }
        delete next[actionKey]
        return next
      })
    }
  }

  function resetTransientMessages() {
    setAuthError('')
  }

  function openConfirmation(dialog) {
    setConfirmationDialog(dialog)
  }

  function normalizeBridgeForm(nextBridgeForm) {
    const next = { ...nextBridgeForm }
    if (next.authMethod === 'PASSWORD') {
      next.oauthProvider = 'NONE'
      next.oauthRefreshToken = ''
    }
    if (next.authMethod === 'OAUTH2') {
      next.password = ''
      if (!next.oauthProvider || next.oauthProvider === 'NONE') {
        next.oauthProvider = 'MICROSOFT'
      }
    }
    return next
  }

  function handleBridgeFormChange(updater) {
    setBridgeDuplicateError('')
    setBridgeForm((current) => normalizeBridgeForm(typeof updater === 'function' ? updater(current) : updater))
  }

  function applyBridgePreset(presetId) {
    const preset = findEmailProviderPreset(presetId)
    if (!preset.values) {
      return
    }
    handleBridgeFormChange((current) => ({
      ...current,
      ...preset.values
    }))
  }

  function dismissNotification(id) {
    const timer = notificationTimersRef.current.get(id)
    if (timer) {
      window.clearTimeout(timer)
      notificationTimersRef.current.delete(id)
    }
    setNotifications((current) => current.filter((notification) => notification.id !== id))
  }

  function pushNotification({
    autoCloseMs = 10000,
    copyText = '',
    message,
    targetId = null,
    tone = 'success'
  }) {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`
    setNotifications((current) => [...current, { autoCloseMs, copyText, id, message, targetId, tone }])
  }

  async function loadSession() {
    try {
      const response = await fetch('/api/auth/me')
      if (response.status === 401) {
        setSession(null)
        setAuthError('')
        return
      }
      if (!response.ok) {
        throw new Error(`Unable to load session (${response.status})`)
      }
      const payload = await response.json()
      setSession(payload)
      setAuthError('')
    } catch (err) {
      setAuthError(err.message || 'Unable to load session')
    } finally {
      setAuthLoading(false)
    }
  }

  async function loadAuthOptions() {
    try {
      const response = await fetch('/api/auth/options')
      if (!response.ok) {
        throw new Error('Unable to load auth options')
      }
      const payload = await response.json()
      setAuthOptions({
        ...DEFAULT_AUTH_OPTIONS,
        ...(payload || {})
      })
    } catch (_err) {
      setAuthOptions(DEFAULT_AUTH_OPTIONS)
    }
  }

  async function loadSelectedUserConfiguration(userId) {
    if (!session || session.role !== 'ADMIN' || !authOptions.multiUserEnabled || !userId) return
    setSelectedUserLoading(true)
    try {
      const response = await fetch(`/api/admin/users/${userId}/configuration`)
      if (!response.ok) {
        throw new Error(await apiErrorText(response, 'Unable to load user configuration'))
      }
      setSelectedUserConfig(await response.json())
    } catch (err) {
      pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to load user configuration', message: err.message || 'Unable to load user configuration', targetId: 'user-management-section', tone: 'error' })
    } finally {
      setSelectedUserLoading(false)
    }
  }

  async function loadAppData() {
    if (!session) return
    setLoadingData(true)
    try {
      const requests = [
        fetch('/api/app/gmail-config'),
        fetch('/api/app/polling-settings'),
        fetch('/api/app/bridges'),
        fetch('/api/app/ui-preferences'),
        fetch('/api/account/passkeys')
      ]

      if (session.role === 'ADMIN') {
        requests.push(fetch('/api/admin/dashboard'))
      }
      if (session.role === 'ADMIN' && authOptions.multiUserEnabled) {
        requests.push(fetch('/api/admin/users'))
      }

      const responses = await Promise.all(requests)
      if (responses.some((response) => !response.ok)) {
        const firstFailed = responses.find((response) => !response.ok)
        throw new Error(await apiErrorText(firstFailed, 'Unable to load admin data'))
      }

      const payloads = await Promise.all(responses.map((response) => response.json()))
      const [gmailPayload, userPollingPayload, bridgesPayload, uiPreferencesPayload, passkeysPayload, adminPayload, usersPayload] = payloads

      setGmailMeta(gmailPayload)
      setGmailConfig({
        ...DEFAULT_GMAIL_CONFIG,
        destinationUser: gmailPayload.destinationUser || 'me',
        redirectUri: gmailPayload.redirectUri || gmailPayload.defaultRedirectUri || `${window.location.origin}/api/google-oauth/callback`,
        createMissingLabels: gmailPayload.createMissingLabels,
        neverMarkSpam: gmailPayload.neverMarkSpam,
        processForCalendar: gmailPayload.processForCalendar
      })
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
      setMyBridges(Array.isArray(bridgesPayload) ? bridgesPayload : [])
      setMyPasskeys(Array.isArray(passkeysPayload) ? passkeysPayload : [])
      if (uiPreferencesLoadedForUserId !== session.id) {
        const nextUiPreferences = normalizeUiPreferences(uiPreferencesPayload)
        setUiPreferences(nextUiPreferences)
        setLanguage(nextUiPreferences.language)
        setUiPreferencesLoadedForUserId(session.id)
        setTouchedSections({})
      }

      if (adminPayload) {
        setSystemDashboard(adminPayload)
        if (!systemPollingFormDirty && adminPayload.polling) {
          setSystemPollingForm({
            pollEnabledMode: adminPayload.polling.pollEnabledOverride === null
              ? 'DEFAULT'
              : adminPayload.polling.pollEnabledOverride ? 'ENABLED' : 'DISABLED',
            pollIntervalOverride: adminPayload.polling.pollIntervalOverride || '',
            fetchWindowOverride: adminPayload.polling.fetchWindowOverride === null ? '' : String(adminPayload.polling.fetchWindowOverride)
          })
        }
      } else {
        setSystemDashboard(null)
      }
      if (usersPayload) {
        setUsers(usersPayload)
        if (!selectedUserId && usersPayload.length > 0) {
          setSelectedUserId(usersPayload[0].id)
        }
      } else {
        setUsers([])
        setSelectedUserId(null)
        setSelectedUserConfig(null)
      }
    } catch (err) {
      pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to load application data', message: err.message || 'Unable to load application data', tone: 'error' })
    } finally {
      setLoadingData(false)
    }
  }

  useEffect(() => {
    loadAuthOptions()
    loadSession()
  }, [])

  useEffect(() => {
    if (!session) return
    loadAppData()
    const timer = window.setInterval(loadAppData, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [authOptions.multiUserEnabled, session])

  useEffect(() => {
    setDismissedPersistentNotifications({})
  }, [session?.id])

  useEffect(() => {
    window.localStorage.setItem('inboxbridge.language', language)
  }, [language])

  useEffect(() => {
    if (selectedUserId) {
      loadSelectedUserConfiguration(selectedUserId)
    }
  }, [authOptions.multiUserEnabled, selectedUserId, session?.role])

  useEffect(() => {
    notifications.forEach((notification) => {
      if (!notification.autoCloseMs || notificationTimersRef.current.has(notification.id)) {
        return
      }
      const timer = window.setTimeout(() => {
        dismissNotification(notification.id)
      }, notification.autoCloseMs)
      notificationTimersRef.current.set(notification.id, timer)
    })
  }, [notifications])

  useEffect(() => () => {
    notificationTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    notificationTimersRef.current.clear()
  }, [])

  async function handleLogin(event) {
    event.preventDefault()
    resetTransientMessages()
    await withPending('login', async () => {
      try {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(loginForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Login failed'))
        }
        const payload = await response.json()
        if (payload.status === 'PASSKEY_REQUIRED' && payload.passkeyChallenge) {
          await completePasskeyLogin(payload.passkeyChallenge)
          return
        }
        setSession(payload.user)
        if (!payload.user.mustChangePassword) {
          pushNotification({
            autoCloseMs: 10000,
            message: t('notifications.signedIn'),
            targetId: null,
            tone: 'success'
          })
        }
      } catch (err) {
        setAuthError(err.message || 'Login failed')
      }
    })
  }

  async function handleRegister(event) {
    event.preventDefault()
    resetTransientMessages()
    await withPending('register', async () => {
      try {
        const response = await fetch('/api/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(registerForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Registration failed'))
        }
        const payload = await response.json()
        setRegisterForm(DEFAULT_REGISTER_FORM)
        setRegisterOpen(false)
        pushNotification({ message: payload.message || t('notifications.registrationSubmitted'), tone: 'success' })
      } catch (err) {
        setAuthError(err.message || 'Registration failed')
      }
    })
  }

  async function handleLogout() {
    await withPending('logout', async () => {
      await fetch('/api/auth/logout', { method: 'POST' })
      setSession(null)
      setSystemDashboard(null)
      setUsers([])
      setSelectedUserConfig(null)
      setSelectedUserId(null)
      setUiPreferences(DEFAULT_UI_PREFERENCES)
      setUiPreferencesLoadedForUserId(null)
      setTouchedSections({})
      setShowSecurityPanel(false)
      setSystemPollingForm(DEFAULT_SYSTEM_POLLING_FORM)
      setSystemPollingFormDirty(false)
      setUserPollingSettings(null)
      setUserPollingForm(DEFAULT_USER_POLLING_FORM)
      setUserPollingFormDirty(false)
      setNotifications([])
      setRegisterOpen(false)
      setConfirmationDialog(null)
    })
  }

  async function handlePasswordChange(event) {
    event.preventDefault()
    await withPending('passwordChange', async () => {
      try {
        const response = await fetch('/api/account/password', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(passwordForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to change password'))
        }
        setPasswordForm(DEFAULT_PASSWORD_FORM)
        pushNotification({ message: t('notifications.passwordUpdated'), targetId: 'password-panel-section', tone: 'success' })
        await loadSession()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to change password', message: err.message || 'Unable to change password', targetId: 'password-panel-section', tone: 'error' })
      }
    })
  }

  async function handlePasswordRemoval() {
    openConfirmation({
      actionKey: 'passwordRemove',
      body: t('password.removeConfirmBody'),
      confirmLabel: t('password.remove'),
      confirmLoadingLabel: t('password.removeLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending('passwordRemove', async () => {
          try {
            const response = await fetch('/api/account/password', { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, 'Unable to remove password'))
            }
            setConfirmationDialog(null)
            setPasswordForm(DEFAULT_PASSWORD_FORM)
            pushNotification({ autoCloseMs: null, message: t('notifications.passwordRemoved'), targetId: 'password-panel-section', tone: 'warning' })
            await loadAppData()
            await loadSession()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to remove password', message: err.message || 'Unable to remove password', targetId: 'password-panel-section', tone: 'error' })
          }
        })
      },
      title: t('password.removeConfirmTitle')
    })
  }

  async function completePasskeyLogin(challenge) {
    const credential = await navigator.credentials.get({ publicKey: parseGetOptions(challenge.publicKeyJson) })
    if (!credential) {
      throw new Error(t('errors.passkeyCancelled'))
    }
    const finishResponse = await fetch('/api/auth/passkey/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ceremonyId: challenge.ceremonyId,
        credentialJson: serializeCredential(credential)
      })
    })
    if (!finishResponse.ok) {
      throw new Error(await apiErrorText(finishResponse, 'Passkey sign-in failed'))
    }
    const payload = await finishResponse.json()
    setSession(payload.user)
    pushNotification({ message: t('notifications.signedInWithPasskey'), tone: 'success' })
  }

  async function handlePasskeyLogin() {
    resetTransientMessages()
    await withPending('passkeyLogin', async () => {
      try {
        if (!passkeysSupported()) {
          throw new Error(t('errors.passkeyUnsupported'))
        }
        const startResponse = await fetch('/api/auth/passkey/options', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: loginForm.username.trim() || null })
        })
        if (!startResponse.ok) {
          throw new Error(await apiErrorText(startResponse, 'Unable to start passkey sign-in'))
        }
        const startPayload = await startResponse.json()
        await completePasskeyLogin(startPayload)
      } catch (err) {
        setAuthError(err.message || 'Passkey sign-in failed')
      }
    })
  }

  async function handlePasskeyRegistration(event) {
    event.preventDefault()
    await withPending('passkeyCreate', async () => {
      try {
        if (!passkeysSupported()) {
          throw new Error(t('errors.passkeyUnsupported'))
        }
        const startResponse = await fetch('/api/account/passkeys/options', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ label: passkeyLabel })
        })
        if (!startResponse.ok) {
          throw new Error(await apiErrorText(startResponse, 'Unable to start passkey registration'))
        }
        const startPayload = await startResponse.json()
        const credential = await navigator.credentials.create({ publicKey: parseCreateOptions(startPayload.publicKeyJson) })
        if (!credential) {
          throw new Error(t('errors.passkeyRegistrationCancelled'))
        }
        const finishResponse = await fetch('/api/account/passkeys/verify', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            ceremonyId: startPayload.ceremonyId,
            credentialJson: serializeCredential(credential)
          })
        })
        if (!finishResponse.ok) {
          throw new Error(await apiErrorText(finishResponse, 'Unable to register passkey'))
        }
        setPasskeyLabel('')
        pushNotification({ message: t('notifications.passkeyRegistered'), targetId: 'passkey-panel-section', tone: 'success' })
        await loadAppData()
        await loadSession()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to register passkey', message: err.message || 'Unable to register passkey', targetId: 'passkey-panel-section', tone: 'error' })
      }
    })
  }

  async function handleDeletePasskey(passkeyId) {
    openConfirmation({
      actionKey: `passkeyDelete:${passkeyId}`,
      body: t('passkey.removeConfirmBody'),
      confirmLabel: t('passkey.remove'),
      confirmLoadingLabel: t('passkey.removeLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`passkeyDelete:${passkeyId}`, async () => {
          try {
            const response = await fetch(`/api/account/passkeys/${passkeyId}`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, 'Unable to remove passkey'))
            }
            setConfirmationDialog(null)
            pushNotification({ message: t('notifications.passkeyRemoved'), targetId: 'passkey-panel-section', tone: 'success' })
            await loadAppData()
            await loadSession()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to remove passkey', message: err.message || 'Unable to remove passkey', targetId: 'passkey-panel-section', tone: 'error' })
          }
        })
      },
      title: t('passkey.removeConfirmTitle')
    })
  }

  async function saveGmailConfig(event) {
    event.preventDefault()
    await withPending('gmailSave', async () => {
      try {
        const response = await fetch('/api/app/gmail-config', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(gmailConfig)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to save Gmail configuration'))
        }
        pushNotification({ message: t('notifications.gmailSaved'), targetId: 'gmail-destination-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to save Gmail configuration', message: err.message || 'Unable to save Gmail configuration', targetId: 'gmail-destination-section', tone: 'error' })
      }
    })
  }

  async function saveBridge(event) {
    event.preventDefault()
    const normalizedBridgeId = bridgeForm.bridgeId.trim()
    const originalBridgeId = bridgeForm.originalBridgeId.trim()
    const duplicateFetcher = visibleFetchers.find((fetcher) => (
      fetcher.bridgeId === normalizedBridgeId && fetcher.bridgeId !== originalBridgeId
    ))
    if (duplicateFetcher) {
      const duplicateMessage = t('bridges.duplicateId', { bridgeId: normalizedBridgeId })
      setBridgeDuplicateError(duplicateMessage)
      pushNotification({ autoCloseMs: null, copyText: duplicateMessage, message: duplicateMessage, targetId: 'source-bridges-section', tone: 'error' })
      return
    }
    await withPending('bridgeSave', async () => {
      try {
        const response = await fetch('/api/app/bridges', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(bridgeForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to save mail fetcher'))
        }
        setBridgeDuplicateError('')
        pushNotification({ message: t('notifications.bridgeSaved', { bridgeId: bridgeForm.bridgeId }), targetId: 'source-bridges-section', tone: 'success' })
        setBridgeForm(DEFAULT_BRIDGE_FORM)
        setShowFetcherDialog(false)
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to save mail fetcher', message: err.message || 'Unable to save mail fetcher', targetId: 'source-bridges-section', tone: 'error' })
      }
    })
  }

  async function deleteBridge(bridgeId) {
    openConfirmation({
      actionKey: `bridgeDelete:${bridgeId}`,
      body: t('bridge.deleteConfirmBody', { bridgeId }),
      confirmLabel: t('bridge.delete'),
      confirmLoadingLabel: t('bridge.deleteLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`bridgeDelete:${bridgeId}`, async () => {
          try {
            const response = await fetch(`/api/app/bridges/${encodeURIComponent(bridgeId)}`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, 'Unable to delete mail fetcher'))
            }
            setConfirmationDialog(null)
            pushNotification({ message: t('notifications.bridgeDeleted', { bridgeId }), targetId: 'source-bridges-section', tone: 'success' })
            await loadAppData()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to delete mail fetcher', message: err.message || 'Unable to delete mail fetcher', targetId: 'source-bridges-section', tone: 'error' })
          }
        })
      },
      title: t('bridge.deleteConfirmTitle')
    })
  }

  async function createUser(event) {
    event.preventDefault()
    await withPending('createUser', async () => {
      try {
        const response = await fetch('/api/admin/users', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(createUserForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to create user'))
        }
        const payload = await response.json()
        setCreateUserForm(DEFAULT_CREATE_USER_FORM)
        pushNotification({ message: t('notifications.userCreated', { username: payload.username }), targetId: 'user-management-section', tone: 'success' })
        await loadAppData()
        setSelectedUserId(payload.id)
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to create user', message: err.message || 'Unable to create user', targetId: 'user-management-section', tone: 'error' })
      }
    })
  }

  async function updateUser(userId, patch, successMessage) {
    await withPending(`updateUser:${userId}`, async () => {
      try {
        const response = await fetch(`/api/admin/users/${userId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(patch)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to update user'))
        }
        const payload = await response.json()
        pushNotification({ message: successMessage || `Updated ${payload.username}.`, targetId: 'user-management-section', tone: 'success' })
        await loadAppData()
        setSelectedUserId(payload.id)
        await loadSelectedUserConfiguration(payload.id)
        return true
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to update user', message: err.message || 'Unable to update user', targetId: 'user-management-section', tone: 'error' })
        return false
      }
    })
  }

  function requestToggleUserActive(user) {
    if (!user) return
    const actionKey = `updateUser:${user.id}`
    const active = Boolean(user.active)
    openConfirmation({
      actionKey,
      body: active
        ? t('users.suspendConfirmBody', { username: user.username })
        : t('users.reactivateConfirmBody', { username: user.username }),
      confirmLabel: active ? t('users.suspend') : t('users.reactivate'),
      confirmLoadingLabel: t('users.confirmLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        const success = await updateUser(
          user.id,
          { active: !active },
          active
            ? t('notifications.userSuspended', { username: user.username })
            : t('notifications.userReactivated', { username: user.username })
        )
        if (success) {
          setConfirmationDialog(null)
        }
      },
      title: active ? t('users.suspendConfirmTitle') : t('users.reactivateConfirmTitle')
    })
  }

  function requestForcePasswordChange(user) {
    if (!user) return
    openConfirmation({
      actionKey: `updateUser:${user.id}`,
      body: t('users.forcePasswordChangeConfirmBody', { username: user.username }),
      confirmLabel: t('users.confirmAction'),
      confirmLoadingLabel: t('users.confirmLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        const success = await updateUser(
          user.id,
          { mustChangePassword: true },
          t('notifications.forcedPasswordReset', { username: user.username })
        )
        if (success) {
          setConfirmationDialog(null)
        }
      },
      title: t('users.forcePasswordChangeConfirmTitle')
    })
  }

  async function resetSelectedUserPassword(event) {
    event.preventDefault()
    if (!selectedUserConfig) return
    await withPending(`resetPassword:${selectedUserConfig.user.id}`, async () => {
      try {
        const response = await fetch(`/api/admin/users/${selectedUserConfig.user.id}/password-reset`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(adminResetPasswordForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to reset password'))
        }
        const payload = await response.json()
        setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
        setShowPasswordResetDialog(false)
        pushNotification({ autoCloseMs: null, message: t('notifications.temporaryPasswordSet', { username: payload.username }), targetId: 'user-management-section', tone: 'warning' })
        await loadAppData()
        setSelectedUserId(payload.id)
        await loadSelectedUserConfiguration(payload.id)
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to reset password', message: err.message || 'Unable to reset password', targetId: 'user-management-section', tone: 'error' })
      }
    })
  }

  async function resetSelectedUserPasskeys() {
    if (!selectedUserConfig) return
    openConfirmation({
      actionKey: `resetPasskeys:${selectedUserConfig.user.id}`,
      body: t('users.resetPasskeysConfirmBody', { username: selectedUserConfig.user.username }),
      confirmLabel: t('users.resetPasskeys'),
      confirmLoadingLabel: t('users.resetPasskeysLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`resetPasskeys:${selectedUserConfig.user.id}`, async () => {
          try {
            const response = await fetch(`/api/admin/users/${selectedUserConfig.user.id}/passkeys`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, 'Unable to reset passkeys'))
            }
            const payload = await response.json()
            setConfirmationDialog(null)
            pushNotification({ message: t('notifications.passkeysRemoved', { count: payload.deleted, username: selectedUserConfig.user.username }), targetId: 'user-management-section', tone: 'success' })
            await loadAppData()
            await loadSelectedUserConfiguration(selectedUserConfig.user.id)
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to reset passkeys', message: err.message || 'Unable to reset passkeys', targetId: 'user-management-section', tone: 'error' })
          }
        })
      },
      title: t('users.resetPasskeysConfirmTitle')
    })
  }

  async function runPoll() {
    setRunningPoll(true)
    await withPending('runPoll', async () => {
      try {
        const response = await fetch('/api/admin/poll/run', { method: 'POST' })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to run poll'))
        }
        const payload = await response.json()
        pushNotification({ message: t('notifications.pollFinished', { fetched: payload.fetched, imported: payload.imported, duplicates: payload.duplicates, errors: payload.errors.length }), targetId: 'system-dashboard-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to run poll', message: err.message || 'Unable to run poll', targetId: 'system-dashboard-section', tone: 'error' })
      } finally {
        setRunningPoll(false)
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
              : Number(systemPollingForm.fetchWindowOverride)
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to save polling settings'))
        }
        setSystemPollingFormDirty(false)
        pushNotification({ message: t('notifications.pollingUpdated'), targetId: 'system-dashboard-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to save polling settings', message: err.message || 'Unable to save polling settings', targetId: 'system-dashboard-section', tone: 'error' })
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
            fetchWindowOverride: null
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to reset polling settings'))
        }
        setSystemPollingForm(DEFAULT_SYSTEM_POLLING_FORM)
        setSystemPollingFormDirty(false)
        pushNotification({ message: t('notifications.pollingReset'), targetId: 'system-dashboard-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to reset polling settings', message: err.message || 'Unable to reset polling settings', targetId: 'system-dashboard-section', tone: 'error' })
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
          throw new Error(await apiErrorText(response, 'Unable to save your polling settings'))
        }
        setUserPollingFormDirty(false)
        pushNotification({ message: t('notifications.userPollingUpdated'), targetId: 'user-polling-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to save your polling settings', message: err.message || 'Unable to save your polling settings', targetId: 'user-polling-section', tone: 'error' })
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
          throw new Error(await apiErrorText(response, 'Unable to reset your polling settings'))
        }
        setUserPollingForm(DEFAULT_USER_POLLING_FORM)
        setUserPollingFormDirty(false)
        pushNotification({ message: t('notifications.userPollingReset'), targetId: 'user-polling-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to reset your polling settings', message: err.message || 'Unable to reset your polling settings', targetId: 'user-polling-section', tone: 'error' })
      }
    })
  }

  function startGoogleOAuthSelf() {
    withPending('googleOAuthSelf', async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign('/api/google-oauth/start/self')
          resolve()
        }, 75)
      })
    })
  }

  function startGoogleOAuthSystem() {
    withPending('googleOAuthSystem', async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign('/api/google-oauth/start/system')
          resolve()
        }, 75)
      })
    })
  }

  function startMicrosoftOAuth(sourceId) {
    withPending(`microsoftOAuth:${sourceId}`, async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign(`/api/microsoft-oauth/start?sourceId=${encodeURIComponent(sourceId)}`)
          resolve()
        }, 75)
      })
    })
  }

  async function handleRefresh() {
    await withPending('refresh', async () => {
      await loadAppData()
    })
  }

  async function persistUiPreferences(nextPreferences) {
    await withPending('uiPreferences', async () => {
      try {
        const response = await fetch('/api/app/ui-preferences', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(nextPreferences)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to save layout preference'))
        }
        const payload = await response.json()
        const normalized = normalizeUiPreferences(payload)
        setUiPreferences(normalized)
        setLanguage(normalized.language)
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || t('notifications.layoutSaveFailed'), message: err.message || t('notifications.layoutSaveFailed'), tone: 'error' })
      }
    })
  }

  function toggleSection(sectionKey) {
    setTouchedSections((current) => ({ ...current, [sectionKey]: true }))
    const nextPreferences = {
      ...uiPreferences,
      [sectionKey]: !uiPreferences[sectionKey]
    }
    setUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      persistUiPreferences(nextPreferences)
    }
  }

  function handlePersistLayoutChange(enabled) {
    const nextPreferences = {
      ...uiPreferences,
      persistLayout: enabled
    }
    setUiPreferences(nextPreferences)
    persistUiPreferences(nextPreferences)
  }

  function handleLanguageChange(nextLanguage) {
    const normalized = normalizeLocale(nextLanguage)
    setLanguage(normalized)
    const nextPreferences = {
      ...uiPreferences,
      language: normalized
    }
    setUiPreferences(nextPreferences)
    persistUiPreferences(nextPreferences)
  }

  function focusSection(event, step) {
    event.preventDefault()
    focusTarget(step.targetId, step.sectionKey)
  }

  function focusTarget(targetId, sectionKey = null) {
    const derivedSectionKey = sectionKey || ({
      'gmail-destination-section': 'gmailDestinationCollapsed',
      'user-polling-section': 'userPollingCollapsed',
      'source-bridges-section': 'sourceBridgesCollapsed',
      'system-dashboard-section': 'systemDashboardCollapsed',
      'user-management-section': 'userManagementCollapsed'
    }[targetId] || null)
    if (derivedSectionKey && uiPreferences[derivedSectionKey]) {
      setTouchedSections((current) => ({ ...current, [derivedSectionKey]: true }))
      const nextPreferences = {
        ...uiPreferences,
        [derivedSectionKey]: false
      }
      setUiPreferences(nextPreferences)
      if (nextPreferences.persistLayout) {
        persistUiPreferences(nextPreferences)
      }
    }
    if (targetId === 'password-panel-section') {
      setShowSecurityPanel(true)
    }
    if (targetId === 'passkey-panel-section') {
      setShowSecurityPanel(true)
    }
    window.setTimeout(() => {
      const target = document.getElementById(targetId)
      if (!target) {
        return
      }
      target.scrollIntoView({ behavior: 'smooth', block: 'start' })
      target.focus()
    }, 150)
  }

  function editBridge(bridge) {
    handleBridgeFormChange({
      originalBridgeId: bridge.bridgeId,
      bridgeId: bridge.bridgeId,
      enabled: bridge.enabled,
      protocol: bridge.protocol,
      host: bridge.host,
      port: bridge.port,
      tls: bridge.tls,
      authMethod: bridge.authMethod,
      oauthProvider: bridge.oauthProvider,
      username: bridge.username,
      password: '',
      oauthRefreshToken: '',
      folder: bridge.folder,
      unreadOnly: bridge.unreadOnly,
      customLabel: bridge.customLabel
    })
    setShowFetcherDialog(true)
  }

  function openAddFetcherDialog() {
    setBridgeDuplicateError('')
    setBridgeForm(DEFAULT_BRIDGE_FORM)
    setShowFetcherDialog(true)
  }

  const visibleFetchers = useMemo(() => {
    const databaseFetchers = myBridges.map((bridge) => ({
      ...bridge,
      bridgeId: bridge.bridgeId,
      managementSource: 'DATABASE',
      canDelete: true,
      canEdit: true,
      canConnectMicrosoft: bridge.authMethod === 'OAUTH2' && bridge.oauthProvider === 'MICROSOFT'
    }))
    const envFetchers = session?.username === 'admin'
      ? (systemDashboard?.bridges || []).map((bridge) => ({
        bridgeId: bridge.id,
        enabled: bridge.enabled,
        effectivePollEnabled: bridge.effectivePollEnabled,
        effectivePollInterval: bridge.effectivePollInterval,
        effectiveFetchWindow: bridge.effectiveFetchWindow,
        protocol: bridge.protocol,
        authMethod: bridge.authMethod,
        oauthProvider: bridge.oauthProvider,
        host: bridge.host,
        port: bridge.port,
        tls: bridge.tls,
        folder: bridge.folder,
        unreadOnly: bridge.unreadOnly,
        customLabel: bridge.customLabel,
        tokenStorageMode: bridge.tokenStorageMode,
        totalImportedMessages: bridge.totalImportedMessages,
        lastImportedAt: bridge.lastImportedAt,
        lastEvent: bridge.lastEvent,
        pollingState: bridge.pollingState,
        managementSource: 'ENVIRONMENT',
        canDelete: false,
        canEdit: false,
        canConnectMicrosoft: bridge.authMethod === 'OAUTH2' && bridge.oauthProvider === 'MICROSOFT'
      }))
      : []

    return [...databaseFetchers, ...envFetchers]
      .sort((left, right) => left.bridgeId.localeCompare(right.bridgeId))
  }, [myBridges, session?.username, systemDashboard?.bridges])

  const setupGuideState = buildSetupGuideState({
    gmailMeta,
    myBridges,
    session,
    systemDashboard,
    t
  })

  useEffect(() => {
    if (!session || uiPreferences.persistLayout || touchedSections.quickSetupCollapsed || !setupGuideState.allStepsComplete) {
      return
    }
    setUiPreferences((current) => {
      if (current.quickSetupCollapsed) {
        return current
      }
      return {
        ...current,
        quickSetupCollapsed: true
      }
    })
  }, [session, setupGuideState.allStepsComplete, touchedSections.quickSetupCollapsed, uiPreferences.persistLayout])

  useEffect(() => {
    setShowPasswordResetDialog(false)
    setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
  }, [selectedUserId])

  useEffect(() => {
    if (!showFetcherDialog) {
      setBridgeDuplicateError('')
    }
  }, [showFetcherDialog])

  const persistentNotifications = useMemo(() => {
    const items = []
    if (session?.mustChangePassword && !dismissedPersistentNotifications.mustChangePassword) {
      items.push({
        id: 'must-change-password',
        message: t('notifications.mustChangePassword'),
        targetId: 'password-panel-section',
        tone: 'warning',
        persistentKey: 'mustChangePassword'
      })
    }
    return items
  }, [dismissedPersistentNotifications.mustChangePassword, session?.mustChangePassword, t])

  if (authLoading) {
    return <LoadingScreen label={t('app.loading')} />
  }

  if (!session) {
    return (
        <AuthScreen
          authError={authError}
          loginLoading={isPending('login')}
          loginForm={loginForm}
          multiUserEnabled={authOptions.multiUserEnabled}
          notice=""
          onCloseRegisterDialog={() => setRegisterOpen(false)}
          onLogin={handleLogin}
          onLoginChange={setLoginForm}
          onPasskeyLogin={handlePasskeyLogin}
          onOpenRegisterDialog={() => setRegisterOpen(true)}
          registerLoading={isPending('register')}
          registerOpen={registerOpen}
          passkeyLoading={isPending('passkeyLogin')}
          passkeysSupported={passkeysSupported()}
          onRegister={handleRegister}
          onRegisterChange={setRegisterForm}
          registerForm={registerForm}
          t={t}
      />
    )
  }

  return (
    <div className="page-shell">
      <main className="dashboard">
        <HeroPanel
          language={language}
          languageOptions={selectableLanguages}
          loadingData={loadingData}
          onLanguageChange={handleLanguageChange}
          onRefresh={handleRefresh}
          onSignOut={handleLogout}
          onToggleSecurityPanel={() => setShowSecurityPanel((current) => !current)}
          refreshLoading={isPending('refresh')}
          securityPanelVisible={showSecurityPanel}
          session={session}
          signOutLoading={isPending('logout')}
          t={t}
        />

        {showSecurityPanel ? (
          <section className="app-columns">
            <PasswordPanel
              onPasswordChange={handlePasswordChange}
              onPasswordFormChange={setPasswordForm}
              onPasswordRemove={handlePasswordRemoval}
              passkeyCount={session.passkeyCount}
              passwordConfigured={session.passwordConfigured}
              passwordForm={passwordForm}
              passwordLoading={isPending('passwordChange')}
              passwordRemoveLoading={isPending('passwordRemove')}
              t={t}
            />
            <PasskeyPanel
              createLoading={isPending('passkeyCreate')}
              deleteLoadingId={myPasskeys.find((passkey) => isPending(`passkeyDelete:${passkey.id}`))?.id || null}
              onCreatePasskey={handlePasskeyRegistration}
              onDeletePasskey={handleDeletePasskey}
              onPasskeyLabelChange={setPasskeyLabel}
              passwordConfigured={session.passwordConfigured}
              passkeyLabel={passkeyLabel}
              passkeys={myPasskeys}
              supported={passkeysSupported()}
              t={t}
              locale={language}
            />
          </section>
        ) : null}

        <SetupGuidePanel
          collapsed={uiPreferences.quickSetupCollapsed}
          onFocusSection={focusSection}
          onPersistLayoutChange={handlePersistLayoutChange}
          onToggleCollapse={() => toggleSection('quickSetupCollapsed')}
          persistLayout={uiPreferences.persistLayout}
          savingLayout={isPending('uiPreferences')}
          steps={setupGuideState.steps}
          t={t}
        />

        {[...persistentNotifications, ...notifications].map((notification) => (
          <Banner
            key={notification.id}
            copyText={notification.copyText}
            copiedLabel={t('common.copied')}
            copyLabel={t('common.copyError')}
            onDismiss={() => {
              if (notification.persistentKey) {
                setDismissedPersistentNotifications((current) => ({ ...current, [notification.persistentKey]: true }))
                return
              }
              dismissNotification(notification.id)
            }}
            onFocus={notification.targetId ? () => focusTarget(notification.targetId) : undefined}
            dismissLabel={t('common.dismissNotification')}
            focusLabel={t('common.focusSection')}
            tone={notification.tone}
          >
            {notification.message}
          </Banner>
        ))}

        <GmailDestinationSection
          collapsed={uiPreferences.gmailDestinationCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          gmailConfig={gmailConfig}
          gmailMeta={gmailMeta}
          isAdmin={session.role === 'ADMIN'}
          locale={language}
          oauthLoading={isPending('googleOAuthSelf')}
          onCollapseToggle={() => toggleSection('gmailDestinationCollapsed')}
          onConnectOAuth={startGoogleOAuthSelf}
          onSave={saveGmailConfig}
          saveLoading={isPending('gmailSave')}
          setGmailConfig={setGmailConfig}
          t={t}
        />

        <UserPollingSettingsSection
          collapsed={uiPreferences.userPollingCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          hasFetchers={myBridges.length > 0}
          onCollapseToggle={() => toggleSection('userPollingCollapsed')}
          onPollingFormChange={(updater) => {
            setUserPollingFormDirty(true)
            setUserPollingForm((current) => typeof updater === 'function' ? updater(current) : updater)
          }}
          onResetPollingSettings={resetUserPollingSettings}
          onSavePollingSettings={saveUserPollingSettings}
          pollingSettings={userPollingSettings}
          pollingSettingsForm={userPollingForm}
          pollingSettingsLoading={isPending('userPollingSettingsSave')}
          t={t}
        />

        <UserBridgesSection
          bridgeForm={bridgeForm}
          collapsed={uiPreferences.sourceBridgesCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          connectingBridgeId={visibleFetchers.find((bridge) => isPending(`microsoftOAuth:${bridge.bridgeId}`))?.bridgeId || null}
          deletingBridgeId={myBridges.find((bridge) => isPending(`bridgeDelete:${bridge.bridgeId}`))?.bridgeId || null}
          duplicateIdError={bridgeDuplicateError}
          fetcherDialogOpen={showFetcherDialog}
          fetchers={visibleFetchers}
          onAddFetcher={openAddFetcherDialog}
          onApplyPreset={applyBridgePreset}
          onBridgeFormChange={handleBridgeFormChange}
          onCloseDialog={() => setShowFetcherDialog(false)}
          onCollapseToggle={() => toggleSection('sourceBridgesCollapsed')}
          onConnectMicrosoft={startMicrosoftOAuth}
          onDeleteBridge={deleteBridge}
          onEditBridge={editBridge}
          onSaveBridge={saveBridge}
          saveLoading={isPending('bridgeSave')}
          t={t}
          locale={language}
        />

        {session.role === 'ADMIN' ? (
          <SystemDashboardSection
            collapsed={uiPreferences.systemDashboardCollapsed}
            collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
            dashboard={systemDashboard}
            onCollapseToggle={() => toggleSection('systemDashboardCollapsed')}
            onPollingFormChange={(updater) => {
              setSystemPollingFormDirty(true)
              setSystemPollingForm((current) => typeof updater === 'function' ? updater(current) : updater)
            }}
            onResetPollingSettings={resetPollingSettings}
            onRunPoll={runPoll}
            onSavePollingSettings={savePollingSettings}
            pollingSettingsForm={systemPollingForm}
            pollingSettingsLoading={isPending('pollingSettingsSave')}
            runningPoll={runningPoll}
            t={t}
            locale={language}
          />
        ) : null}
        {session.role === 'ADMIN' && authOptions.multiUserEnabled ? (
          <>
            <UserManagementSection
              collapsed={uiPreferences.userManagementCollapsed}
              collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
              createUserForm={createUserForm}
              createUserLoading={isPending('createUser')}
              onCollapseToggle={() => toggleSection('userManagementCollapsed')}
              onCreateUser={createUser}
              onCreateUserFormChange={setCreateUserForm}
              onForcePasswordChange={requestForcePasswordChange}
              onOpenResetPasswordDialog={() => setShowPasswordResetDialog(true)}
              onResetUserPasskeys={resetSelectedUserPasskeys}
              onSelectUser={setSelectedUserId}
              onToggleUserActive={requestToggleUserActive}
              onUpdateUser={updateUser}
              resetPasswordForm={adminResetPasswordForm}
              setResetPasswordForm={setAdminResetPasswordForm}
              selectedUserConfig={selectedUserConfig}
              selectedUserId={selectedUserId}
              selectedUserLoading={selectedUserLoading}
              session={session}
              updatingPasskeysResetUserId={selectedUserConfig && isPending(`resetPasskeys:${selectedUserConfig.user.id}`) ? selectedUserConfig.user.id : null}
              updatingUserId={selectedUserConfig && isPending(`updateUser:${selectedUserConfig.user.id}`) ? selectedUserConfig.user.id : null}
              users={users}
              t={t}
              locale={language}
            />
          </>
        ) : null}

        {showPasswordResetDialog && selectedUserConfig ? (
          <PasswordResetDialog
            onClose={() => {
              setShowPasswordResetDialog(false)
              setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
            }}
            onFormChange={setAdminResetPasswordForm}
            onSubmit={resetSelectedUserPassword}
            passwordLoading={selectedUserConfig && isPending(`resetPassword:${selectedUserConfig.user.id}`)}
            resetPasswordForm={adminResetPasswordForm}
            t={t}
            username={selectedUserConfig.user.username}
          />
        ) : null}

        {confirmationDialog ? (
          <ConfirmationDialog
            body={confirmationDialog.body}
            cancelLabel={t('common.cancel')}
            closeDisabled={isPending(confirmationDialog.actionKey)}
            confirmLabel={confirmationDialog.confirmLabel}
            confirmLoading={isPending(confirmationDialog.actionKey)}
            confirmLoadingLabel={confirmationDialog.confirmLoadingLabel}
            confirmTone={confirmationDialog.confirmTone}
            onCancel={() => {
              if (!isPending(confirmationDialog.actionKey)) {
                setConfirmationDialog(null)
              }
            }}
            onConfirm={confirmationDialog.onConfirm}
            title={confirmationDialog.title}
          />
        ) : null}
      </main>
    </div>
  )
}

export default App
