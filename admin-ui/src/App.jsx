import { useEffect, useMemo, useRef, useState } from 'react'
import AuthScreen from './components/auth/AuthScreen'
import Banner from './components/common/Banner'
import ConfirmationDialog from './components/common/ConfirmationDialog'
import LoadingScreen from './components/common/LoadingScreen'
import ModalDialog from './components/common/ModalDialog'
import PasswordPanel from './components/account/PasswordPanel'
import PasskeyPanel from './components/account/PasskeyPanel'
import PasskeyRegistrationDialog from './components/account/PasskeyRegistrationDialog'
import PasswordResetDialog from './components/admin/PasswordResetDialog'
import GmailAccountSection from './components/gmail/GmailAccountSection'
import HeroPanel from './components/layout/HeroPanel'
import PreferencesDialog from './components/layout/PreferencesDialog'
import SetupGuidePanel from './components/layout/SetupGuidePanel'
import SystemDashboardSection from './components/admin/SystemDashboardSection'
import SystemPollingSettingsDialog from './components/admin/SystemPollingSettingsDialog'
import UserPollingSettingsSection from './components/polling/UserPollingSettingsSection'
import UserPollingSettingsDialog from './components/polling/UserPollingSettingsDialog'
import UserManagementSection from './components/admin/UserManagementSection'
import UserBridgesSection from './components/bridges/UserBridgesSection'
import { apiErrorText } from './lib/api'
import { findEmailProviderPreset } from './lib/emailProviderPresets'
import { normalizePasskeyError, parseCreateOptions, parseGetOptions, passkeysSupported, serializeCredential } from './lib/passkeys'
import { buildSetupGuideState } from './lib/setupGuide'
import { languageOptions, normalizeLocale, translate } from './lib/i18n'

const REFRESH_MS = 30000

const DEFAULT_LOGIN_FORM = { username: 'admin', password: 'nimda' }
const DEFAULT_REGISTER_FORM = { username: '', password: '', confirmPassword: '' }
const DEFAULT_PASSWORD_FORM = { currentPassword: '', newPassword: '', confirmNewPassword: '' }
const DEFAULT_ADMIN_RESET_PASSWORD_FORM = { newPassword: '', confirmNewPassword: '' }
const DEFAULT_CREATE_USER_FORM = { username: '', password: '', confirmPassword: '', role: 'USER' }
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
const DEFAULT_USER_POLLING_STATS = {
  totalImportedMessages: 0,
  configuredMailFetchers: 0,
  enabledMailFetchers: 0,
  sourcesWithErrors: 0,
  importsByDay: [],
  importTimelines: {}
}
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
  const [sectionRefreshLoading, setSectionRefreshLoading] = useState({})
  const [pendingActions, setPendingActions] = useState({})
  const [selectedUserLoading, setSelectedUserLoading] = useState(false)
  const [expandedFetcherLoadingId, setExpandedFetcherLoadingId] = useState(null)

  const [loginForm, setLoginForm] = useState(DEFAULT_LOGIN_FORM)
  const [registerForm, setRegisterForm] = useState(DEFAULT_REGISTER_FORM)
  const [passwordForm, setPasswordForm] = useState(DEFAULT_PASSWORD_FORM)
  const [adminResetPasswordForm, setAdminResetPasswordForm] = useState(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
  const [createUserForm, setCreateUserForm] = useState(DEFAULT_CREATE_USER_FORM)
  const [passkeyLabel, setPasskeyLabel] = useState('')

  const [gmailConfig, setGmailConfig] = useState(DEFAULT_GMAIL_CONFIG)
  const [gmailMeta, setGmailMeta] = useState(null)
  const [userPollingSettings, setUserPollingSettings] = useState(null)
  const [userPollingStats, setUserPollingStats] = useState(DEFAULT_USER_POLLING_STATS)
  const [userPollingForm, setUserPollingForm] = useState(DEFAULT_USER_POLLING_FORM)
  const [userPollingFormDirty, setUserPollingFormDirty] = useState(false)

  const [bridgeForm, setBridgeForm] = useState(DEFAULT_BRIDGE_FORM)
  const [bridgeDuplicateError, setBridgeDuplicateError] = useState('')
  const [bridgeTestResult, setBridgeTestResult] = useState(null)

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
  const [securityTab, setSecurityTab] = useState('password')
  const [showPasswordResetDialog, setShowPasswordResetDialog] = useState(false)
  const [showPasskeyRegistrationDialog, setShowPasskeyRegistrationDialog] = useState(false)
  const [passwordResetTarget, setPasswordResetTarget] = useState(null)
  const [showCreateUserDialog, setShowCreateUserDialog] = useState(false)
  const [showFetcherDialog, setShowFetcherDialog] = useState(false)
  const [showFetcherPollingDialog, setShowFetcherPollingDialog] = useState(false)
  const [fetcherPollingTarget, setFetcherPollingTarget] = useState(null)
  const [fetcherPollingForm, setFetcherPollingForm] = useState(DEFAULT_SOURCE_POLLING_FORM)
  const [confirmationDialog, setConfirmationDialog] = useState(null)
  const [systemPollingForm, setSystemPollingForm] = useState(DEFAULT_SYSTEM_POLLING_FORM)
  const [systemPollingFormDirty, setSystemPollingFormDirty] = useState(false)
  const [dismissedPersistentNotifications, setDismissedPersistentNotifications] = useState({})
  const [language, setLanguage] = useState(() => normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language))
  const [registerOpen, setRegisterOpen] = useState(false)
  const [showPreferencesDialog, setShowPreferencesDialog] = useState(false)
  const [showUserPollingDialog, setShowUserPollingDialog] = useState(false)
  const [showSystemPollingDialog, setShowSystemPollingDialog] = useState(false)
  const notificationTimersRef = useRef(new Map())
  const t = useMemo(() => (key, params) => translate(language, key, params), [language])
  const selectableLanguages = useMemo(() => languageOptions.map((value) => ({
    value,
    label: translate(language, `language.${value}`)
  })), [language])
  const securityDialogDirty = Boolean(
    passwordForm.currentPassword.trim()
    || passwordForm.newPassword.trim()
    || passwordForm.confirmNewPassword.trim()
  )

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

  function isSectionRefreshing(sectionKey) {
    return Boolean(sectionRefreshLoading[sectionKey])
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
    setBridgeTestResult(null)
    setBridgeForm((current) => normalizeBridgeForm(typeof updater === 'function' ? updater(current) : updater))
  }

  function applyBridgePreset(presetId) {
    const preset = findEmailProviderPreset(presetId)
    if (!preset.values) {
      return
    }
    setBridgeTestResult(null)
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
        fetch('/api/app/polling-stats'),
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
      const [gmailPayload, userPollingPayload, userPollingStatsPayload, bridgesPayload, uiPreferencesPayload, passkeysPayload, adminPayload, usersPayload] = payloads

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
      setUserPollingStats({
        ...DEFAULT_USER_POLLING_STATS,
        ...(userPollingStatsPayload || {})
      })
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

  async function refreshSectionData(sectionKey, loader) {
    setSectionRefreshLoading((current) => ({ ...current, [sectionKey]: true }))
    try {
      await loader()
    } finally {
      setSectionRefreshLoading((current) => {
        const next = { ...current }
        delete next[sectionKey]
        return next
      })
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
      refreshSectionData('userManagementCollapsed', async () => {
        await loadAppData()
        await loadSelectedUserConfiguration(selectedUserId)
      })
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
      setShowCreateUserDialog(false)
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
            const response = await fetch('/api/account/password', {
              method: 'DELETE',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ currentPassword: passwordForm.currentPassword })
            })
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
        setAuthError(normalizePasskeyError(err, t, 'login'))
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
        setShowPasskeyRegistrationDialog(false)
        pushNotification({ message: t('notifications.passkeyRegistered'), targetId: 'passkey-panel-section', tone: 'success' })
        await loadAppData()
        await loadSession()
      } catch (err) {
        const message = normalizePasskeyError(err, t, 'registration')
        pushNotification({ autoCloseMs: null, copyText: message, message, targetId: 'passkey-panel-section', tone: 'error' })
      }
    })
  }

  function openPasskeyRegistrationDialog() {
    setPasskeyLabel('')
    setShowPasskeyRegistrationDialog(true)
  }

  function closePasskeyRegistrationDialog() {
    setShowPasskeyRegistrationDialog(false)
    setPasskeyLabel('')
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

  async function unlinkGmailAccount() {
    openConfirmation({
      actionKey: 'gmailUnlink',
      body: t('gmail.unlinkConfirmBody'),
      confirmLabel: t('gmail.unlink'),
      confirmLoadingLabel: t('gmail.unlinkLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending('gmailUnlink', async () => {
          try {
            const response = await fetch('/api/account/gmail-link', { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, 'Unable to unlink Gmail account'))
            }
            const payload = await response.json()
            setConfirmationDialog(null)
            if (payload.providerRevocationAttempted && !payload.providerRevoked) {
              pushNotification({
                autoCloseMs: null,
                copyText: t('notifications.gmailUnlinkedRevokeFailed'),
                message: t('notifications.gmailUnlinkedRevokeFailed'),
                targetId: 'gmail-destination-section',
                tone: 'warning'
              })
            } else {
              pushNotification({ message: t('notifications.gmailUnlinked'), targetId: 'gmail-destination-section', tone: 'success' })
            }
            await loadAppData()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to unlink Gmail account', message: err.message || 'Unable to unlink Gmail account', targetId: 'gmail-destination-section', tone: 'error' })
          }
        })
      },
      title: t('gmail.unlinkConfirmTitle')
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
        setBridgeTestResult(null)
        pushNotification({ message: t('notifications.bridgeSaved', { bridgeId: bridgeForm.bridgeId }), targetId: 'source-bridges-section', tone: 'success' })
        setBridgeForm(DEFAULT_BRIDGE_FORM)
        setShowFetcherDialog(false)
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to save mail fetcher', message: err.message || 'Unable to save mail fetcher', targetId: 'source-bridges-section', tone: 'error' })
      }
    })
  }

  async function testBridgeConnection() {
    await withPending('bridgeConnectionTest', async () => {
      try {
        const response = await fetch('/api/app/bridges/test-connection', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(bridgeForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to test mail fetcher connection'))
        }
        const payload = await response.json()
        const message = payload.message || t('bridges.testSuccess')
        setBridgeTestResult({ ...payload, message, tone: 'success' })
        pushNotification({ message, targetId: 'source-bridges-section', tone: 'success' })
      } catch (err) {
        const message = err.message || 'Unable to test mail fetcher connection'
        setBridgeTestResult({ message, tone: 'error' })
        pushNotification({ autoCloseMs: null, copyText: message, message, targetId: 'source-bridges-section', tone: 'error' })
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

  async function openFetcherPollingDialog(fetcher) {
    setFetcherPollingTarget(fetcher)
    await withPending(`fetcherPollingLoad:${fetcher.bridgeId}`, async () => {
      try {
        const endpointPrefix = fetcher.managementSource === 'ENVIRONMENT' ? '/api/admin/bridges' : '/api/app/bridges'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(fetcher.bridgeId)}/polling-settings`)
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to load fetcher polling settings'))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        setShowFetcherPollingDialog(true)
      } catch (err) {
        setFetcherPollingTarget(null)
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to load fetcher polling settings', message: err.message || 'Unable to load fetcher polling settings', targetId: 'source-bridges-section', tone: 'error' })
      }
    })
  }

  function closeFetcherPollingDialog() {
    setShowFetcherPollingDialog(false)
    setFetcherPollingTarget(null)
    setFetcherPollingForm(DEFAULT_SOURCE_POLLING_FORM)
  }

  async function saveFetcherPollingSettings(event) {
    event.preventDefault()
    if (!fetcherPollingTarget) return
    await withPending(`fetcherPollingSave:${fetcherPollingTarget.bridgeId}`, async () => {
      try {
        const endpointPrefix = fetcherPollingTarget.managementSource === 'ENVIRONMENT' ? '/api/admin/bridges' : '/api/app/bridges'
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
          throw new Error(await apiErrorText(response, 'Unable to save fetcher polling settings'))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        pushNotification({ message: t('notifications.fetcherPollingSaved', { bridgeId: fetcherPollingTarget.bridgeId }), targetId: 'source-bridges-section', tone: 'success' })
        await loadAppData()
        closeFetcherPollingDialog()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to save fetcher polling settings', message: err.message || 'Unable to save fetcher polling settings', targetId: 'source-bridges-section', tone: 'error' })
      }
    })
  }

  async function resetFetcherPollingSettings() {
    if (!fetcherPollingTarget) return
    await withPending(`fetcherPollingSave:${fetcherPollingTarget.bridgeId}`, async () => {
      try {
        const endpointPrefix = fetcherPollingTarget.managementSource === 'ENVIRONMENT' ? '/api/admin/bridges' : '/api/app/bridges'
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
          throw new Error(await apiErrorText(response, 'Unable to reset fetcher polling settings'))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        pushNotification({ message: t('notifications.fetcherPollingReset', { bridgeId: fetcherPollingTarget.bridgeId }), targetId: 'source-bridges-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to reset fetcher polling settings', message: err.message || 'Unable to reset fetcher polling settings', targetId: 'source-bridges-section', tone: 'error' })
      }
    })
  }

  async function runFetcherPoll(bridgeId) {
    const fetcher = visibleFetchers.find((entry) => entry.bridgeId === bridgeId)
    await withPending(`bridgePoll:${bridgeId}`, async () => {
      try {
        pushNotification({
          autoCloseMs: 10000,
          message: t('notifications.fetcherPollStarted', { bridgeId }),
          targetId: 'source-bridges-section',
          tone: 'warning'
        })
        const endpointPrefix = fetcher?.managementSource === 'ENVIRONMENT' ? '/api/admin/bridges' : '/api/app/bridges'
        const response = await fetch(`${endpointPrefix}/${encodeURIComponent(bridgeId)}/poll/run`, { method: 'POST' })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to run mail fetcher poll'))
        }
        const payload = await response.json()
        if (payload.errors?.length) {
          throw new Error(payload.errors.join('\n'))
        }
        const completedMessageKey = payload.spamJunkMessageCount > 0
          ? 'notifications.fetcherPollCompletedWithSpam'
          : 'notifications.fetcherPollCompleted'
        pushNotification({
          message: t(completedMessageKey, {
            bridgeId,
            fetched: payload.fetched,
            imported: payload.imported,
            duplicates: payload.duplicates,
            spamJunkCount: payload.spamJunkMessageCount
          }),
          targetId: 'source-bridges-section',
          tone: 'success'
        })
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to run mail fetcher poll', message: err.message || 'Unable to run mail fetcher poll', targetId: 'source-bridges-section', tone: 'error' })
      } finally {
        await loadAppData()
      }
    })
  }

  async function refreshFetcherState(_fetcher, expanded) {
    if (!expanded) {
      return
    }
    setExpandedFetcherLoadingId(_fetcher.bridgeId)
    try {
      await refreshSectionData('sourceBridgesCollapsed', loadAppData)
    } finally {
      setExpandedFetcherLoadingId((current) => current === _fetcher.bridgeId ? null : current)
    }
  }

  async function createUser(event) {
    event.preventDefault()
    const normalizedUsername = createUserForm.username.trim()
    if (users.some((user) => user.username.toLowerCase() === normalizedUsername.toLowerCase())) {
      pushNotification({ autoCloseMs: null, message: t('users.duplicateUsername', { username: normalizedUsername }), targetId: 'user-management-section', tone: 'error' })
      return false
    }
    await withPending('createUser', async () => {
      try {
        const response = await fetch('/api/admin/users', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            username: normalizedUsername,
            password: createUserForm.password,
            role: createUserForm.role
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to create user'))
        }
        const payload = await response.json()
        setCreateUserForm(DEFAULT_CREATE_USER_FORM)
        setShowCreateUserDialog(false)
        pushNotification({ message: t('notifications.userCreated', { username: payload.username }), targetId: 'user-management-section', tone: 'success' })
        await loadAppData()
        setSelectedUserId(payload.id)
        return true
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to create user', message: err.message || 'Unable to create user', targetId: 'user-management-section', tone: 'error' })
        return false
      }
    })
  }

  function toggleExpandedUser(userId) {
    setSelectedUserConfig((current) => current && current.user.id === userId && selectedUserId === userId ? null : current)
    setSelectedUserId((current) => current === userId ? null : userId)
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
    if (!passwordResetTarget) return
    await withPending(`resetPassword:${passwordResetTarget.id}`, async () => {
      try {
        const response = await fetch(`/api/admin/users/${passwordResetTarget.id}/password-reset`, {
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
        setPasswordResetTarget(null)
        pushNotification({ autoCloseMs: null, message: t('notifications.temporaryPasswordSet', { username: payload.username }), targetId: 'user-management-section', tone: 'warning' })
        await loadAppData()
        setSelectedUserId(payload.id)
        await loadSelectedUserConfiguration(payload.id)
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || 'Unable to reset password', message: err.message || 'Unable to reset password', targetId: 'user-management-section', tone: 'error' })
      }
    })
  }

  async function resetUserPasskeys(user) {
    if (!user) return
    openConfirmation({
      actionKey: `resetPasskeys:${user.id}`,
      body: t('users.resetPasskeysConfirmBody', { username: user.username }),
      confirmLabel: t('users.resetPasskeys'),
      confirmLoadingLabel: t('users.resetPasskeysLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`resetPasskeys:${user.id}`, async () => {
          try {
            const response = await fetch(`/api/admin/users/${user.id}/passkeys`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, 'Unable to reset passkeys'))
            }
            const payload = await response.json()
            setConfirmationDialog(null)
            pushNotification({ message: t('notifications.passkeysRemoved', { count: payload.deleted, username: user.username }), targetId: 'user-management-section', tone: 'success' })
            await loadAppData()
            await loadSelectedUserConfiguration(user.id)
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
        const completedMessageKey = payload.spamJunkMessageCount > 0
          ? 'notifications.pollFinishedWithSpam'
          : 'notifications.pollFinished'
        pushNotification({ message: t(completedMessageKey, { fetched: payload.fetched, imported: payload.imported, duplicates: payload.duplicates, errors: payload.errors.length, spamJunkCount: payload.spamJunkMessageCount }), targetId: 'system-dashboard-section', tone: 'success' })
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
        setShowSystemPollingDialog(false)
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
        setShowSystemPollingDialog(false)
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
        setShowUserPollingDialog(false)
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
        setShowUserPollingDialog(false)
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

  async function toggleSection(sectionKey) {
    setTouchedSections((current) => ({ ...current, [sectionKey]: true }))
    const expanding = uiPreferences[sectionKey]
    const nextPreferences = {
      ...uiPreferences,
      [sectionKey]: !uiPreferences[sectionKey]
    }
    setUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      await persistUiPreferences(nextPreferences)
    }
    if (expanding) {
      await refreshSectionData(sectionKey, async () => {
        await loadAppData()
        if (sectionKey === 'userManagementCollapsed' && selectedUserId) {
          await loadSelectedUserConfiguration(selectedUserId)
        }
      })
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
      setSecurityTab('password')
    }
    if (targetId === 'passkey-panel-section') {
      setShowSecurityPanel(true)
      setSecurityTab('passkeys')
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
    setBridgeTestResult(null)
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
    setBridgeTestResult(null)
    setBridgeForm(DEFAULT_BRIDGE_FORM)
    setShowFetcherDialog(true)
  }

  const visibleFetchers = useMemo(() => {
      const deriveOauthConnected = (bridge) => bridge.authMethod === 'OAUTH2'
        && (
          bridge.oauthConnected === true
          || bridge.oauthRefreshTokenConfigured === true
          || bridge.tokenStorageMode === 'DATABASE'
          || bridge.tokenStorageMode === 'ENVIRONMENT'
        )
      const databaseFetchers = myBridges.map((bridge) => ({
        ...bridge,
        bridgeId: bridge.bridgeId,
        managementSource: 'DATABASE',
        oauthConnected: deriveOauthConnected(bridge),
        canDelete: true,
        canEdit: true,
        canConnectMicrosoft: bridge.authMethod === 'OAUTH2' && bridge.oauthProvider === 'MICROSOFT',
        canConfigurePolling: true,
        canRunPoll: true
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
        oauthConnected: deriveOauthConnected(bridge),
        managementSource: 'ENVIRONMENT',
        canDelete: false,
        canEdit: false,
        canConnectMicrosoft: bridge.authMethod === 'OAUTH2' && bridge.oauthProvider === 'MICROSOFT',
        canConfigurePolling: true,
        canRunPoll: true
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
    if (!session || !setupGuideState.allStepsComplete) {
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
  }, [session, setupGuideState.allStepsComplete])

  useEffect(() => {
    setShowPasswordResetDialog(false)
    setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
    setPasswordResetTarget(null)
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

  const duplicateCreateUsername = useMemo(() => {
    const normalized = createUserForm.username.trim().toLowerCase()
    if (!normalized) {
      return false
    }
    return users.some((user) => user.username.toLowerCase() === normalized)
  }, [createUserForm.username, users])

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
          loadingData={loadingData}
          onOpenPreferences={() => setShowPreferencesDialog(true)}
          onOpenSecurityDialog={() => {
            setSecurityTab('password')
            setShowSecurityPanel(true)
          }}
          onRefresh={handleRefresh}
          onSignOut={handleLogout}
          refreshLoading={isPending('refresh')}
          session={session}
          signOutLoading={isPending('logout')}
          t={t}
        />

        {showPreferencesDialog ? (
          <PreferencesDialog
            language={language}
            languageOptions={selectableLanguages}
            onClose={() => setShowPreferencesDialog(false)}
            onLanguageChange={handleLanguageChange}
            onPersistLayoutChange={handlePersistLayoutChange}
            persistLayout={uiPreferences.persistLayout}
            savingLayout={isPending('uiPreferences')}
            t={t}
          />
        ) : null}

        {showSecurityPanel ? (
          <ModalDialog
            className="security-dialog"
            closeLabel={t('hero.hideSecurity')}
            isDirty={securityDialogDirty}
            onClose={() => setShowSecurityPanel(false)}
            size="wide"
            title={t('hero.security')}
            unsavedChangesMessage={t('common.unsavedChangesConfirm')}
          >
            <div className="security-dialog-content">
              <p className="section-copy">{t('hero.showSecurityHint')}</p>
              <div aria-label={t('hero.security')} className="security-tab-list" role="tablist">
                <button
                  aria-controls="security-password-tabpanel"
                  aria-selected={securityTab === 'password'}
                  className={`secondary security-tab-button ${securityTab === 'password' ? 'security-tab-button-active' : ''}`.trim()}
                  id="security-password-tab"
                  onClick={() => setSecurityTab('password')}
                  role="tab"
                  type="button"
                >
                  {t('password.title')}
                </button>
                <button
                  aria-controls="security-passkeys-tabpanel"
                  aria-selected={securityTab === 'passkeys'}
                  className={`secondary security-tab-button ${securityTab === 'passkeys' ? 'security-tab-button-active' : ''}`.trim()}
                  id="security-passkeys-tab"
                  onClick={() => setSecurityTab('passkeys')}
                  role="tab"
                  type="button"
                >
                  {t('passkey.title')}
                </button>
              </div>
              {securityTab === 'password' ? (
                <div aria-labelledby="security-password-tab" className="security-tab-panel" id="security-password-tabpanel" role="tabpanel">
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
                </div>
              ) : (
                <div aria-labelledby="security-passkeys-tab" className="security-tab-panel" id="security-passkeys-tabpanel" role="tabpanel">
                  <PasskeyPanel
                    createLoading={isPending('passkeyCreate')}
                    deleteLoadingId={myPasskeys.find((passkey) => isPending(`passkeyDelete:${passkey.id}`))?.id || null}
                    onDeletePasskey={handleDeletePasskey}
                    onOpenRegistrationDialog={openPasskeyRegistrationDialog}
                    passwordConfigured={session.passwordConfigured}
                    passkeys={myPasskeys}
                    supported={passkeysSupported()}
                    t={t}
                    locale={language}
                  />
                </div>
              )}
            </div>
          </ModalDialog>
        ) : null}

        {showPasskeyRegistrationDialog ? (
          <PasskeyRegistrationDialog
            isLoading={isPending('passkeyCreate')}
            onClose={closePasskeyRegistrationDialog}
            onPasskeyLabelChange={setPasskeyLabel}
            onSubmit={handlePasskeyRegistration}
            passkeyLabel={passkeyLabel}
            t={t}
          />
        ) : null}

        <SetupGuidePanel
          collapsed={uiPreferences.quickSetupCollapsed}
          onFocusSection={focusSection}
          sectionLoading={isSectionRefreshing('quickSetupCollapsed')}
          onToggleCollapse={() => toggleSection('quickSetupCollapsed')}
          savingLayout={isPending('uiPreferences')}
          steps={setupGuideState.steps}
          t={t}
        />

        <div className="notification-stack" aria-live="polite">
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
        </div>

        <GmailAccountSection
          collapsed={uiPreferences.gmailDestinationCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          gmailConfig={gmailConfig}
          gmailMeta={gmailMeta}
          isAdmin={session.role === 'ADMIN'}
          locale={language}
          oauthLoading={isPending('googleOAuthSelf')}
          onCollapseToggle={() => toggleSection('gmailDestinationCollapsed')}
          onConnectOAuth={startGoogleOAuthSelf}
          onUnlinkOAuth={unlinkGmailAccount}
          onSave={saveGmailConfig}
          saveLoading={isPending('gmailSave')}
          sectionLoading={isSectionRefreshing('gmailDestinationCollapsed')}
          setGmailConfig={setGmailConfig}
          t={t}
          unlinkLoading={isPending('gmailUnlink')}
        />

        <UserPollingSettingsSection
          collapsed={uiPreferences.userPollingCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          hasFetchers={myBridges.length > 0}
          onCollapseToggle={() => toggleSection('userPollingCollapsed')}
          onOpenEditor={() => setShowUserPollingDialog(true)}
          pollingStats={userPollingStats}
          pollingSettings={userPollingSettings}
          sectionLoading={isSectionRefreshing('userPollingCollapsed')}
          t={t}
        />

        {showUserPollingDialog && userPollingSettings ? (
          <UserPollingSettingsDialog
            isDirty={userPollingFormDirty}
            onClose={() => setShowUserPollingDialog(false)}
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
        ) : null}

        {showSystemPollingDialog && systemDashboard?.polling ? (
          <SystemPollingSettingsDialog
            isDirty={systemPollingFormDirty}
            onClose={() => setShowSystemPollingDialog(false)}
            onPollingFormChange={(updater) => {
              setSystemPollingFormDirty(true)
              setSystemPollingForm((current) => typeof updater === 'function' ? updater(current) : updater)
            }}
            onResetPollingSettings={resetPollingSettings}
            onSavePollingSettings={savePollingSettings}
            pollingSettings={systemDashboard.polling}
            pollingSettingsForm={systemPollingForm}
            pollingSettingsLoading={isPending('pollingSettingsSave')}
            t={t}
          />
        ) : null}

        <UserBridgesSection
          bridgeForm={bridgeForm}
          collapsed={uiPreferences.sourceBridgesCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          connectingBridgeId={visibleFetchers.find((bridge) => isPending(`microsoftOAuth:${bridge.bridgeId}`))?.bridgeId || null}
          deletingBridgeId={myBridges.find((bridge) => isPending(`bridgeDelete:${bridge.bridgeId}`))?.bridgeId || null}
          duplicateIdError={bridgeDuplicateError}
          fetcherDialogOpen={showFetcherDialog}
          fetcherPollLoadingId={visibleFetchers.find((bridge) => isPending(`bridgePoll:${bridge.bridgeId}`))?.bridgeId || null}
          fetcherPollingDialog={showFetcherPollingDialog ? fetcherPollingTarget : null}
          fetcherPollingForm={fetcherPollingForm}
          fetcherPollingLoading={fetcherPollingTarget ? isPending(`fetcherPollingSave:${fetcherPollingTarget.bridgeId}`) || isPending(`fetcherPollingLoad:${fetcherPollingTarget.bridgeId}`) : false}
          fetcherRefreshLoadingId={expandedFetcherLoadingId}
          fetchers={visibleFetchers}
          onAddFetcher={openAddFetcherDialog}
          onApplyPreset={applyBridgePreset}
          onBridgeFormChange={handleBridgeFormChange}
          onCloseDialog={() => {
            setBridgeTestResult(null)
            setShowFetcherDialog(false)
          }}
          onClosePollingDialog={closeFetcherPollingDialog}
          onCollapseToggle={() => toggleSection('sourceBridgesCollapsed')}
          onConfigureFetcherPolling={openFetcherPollingDialog}
          onConnectMicrosoft={startMicrosoftOAuth}
          onDeleteBridge={deleteBridge}
          onEditBridge={editBridge}
          onFetcherPollingFormChange={(updater) => setFetcherPollingForm((current) => typeof updater === 'function' ? updater(current) : updater)}
          onFetcherToggleExpand={refreshFetcherState}
          onResetFetcherPollingSettings={resetFetcherPollingSettings}
          onRunFetcherPoll={runFetcherPoll}
          onSaveBridge={saveBridge}
          onSaveFetcherPollingSettings={saveFetcherPollingSettings}
          onTestConnection={testBridgeConnection}
          saveLoading={isPending('bridgeSave')}
          testConnectionLoading={isPending('bridgeConnectionTest')}
          testResult={bridgeTestResult}
          sectionLoading={isSectionRefreshing('sourceBridgesCollapsed')}
          t={t}
          locale={language}
        />

        {session.role === 'ADMIN' ? (
          <SystemDashboardSection
            collapsed={uiPreferences.systemDashboardCollapsed}
            collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
            dashboard={systemDashboard}
            onCollapseToggle={() => toggleSection('systemDashboardCollapsed')}
            onOpenEditor={() => setShowSystemPollingDialog(true)}
            onRunPoll={runPoll}
            runningPoll={runningPoll}
            sectionLoading={isSectionRefreshing('systemDashboardCollapsed')}
            t={t}
            locale={language}
          />
        ) : null}
        {session.role === 'ADMIN' && authOptions.multiUserEnabled ? (
          <>
            <UserManagementSection
              collapsed={uiPreferences.userManagementCollapsed}
              collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
              createUserDialogOpen={showCreateUserDialog}
              createUserForm={createUserForm}
              createUserLoading={isPending('createUser')}
              duplicateUsername={duplicateCreateUsername}
              expandedUserId={selectedUserId}
              onCloseCreateUserDialog={() => {
                setShowCreateUserDialog(false)
                setCreateUserForm(DEFAULT_CREATE_USER_FORM)
              }}
              onCollapseToggle={() => toggleSection('userManagementCollapsed')}
              onCreateUser={createUser}
              onCreateUserFormChange={setCreateUserForm}
              onForcePasswordChange={requestForcePasswordChange}
              onOpenCreateUserDialog={() => {
                setCreateUserForm(DEFAULT_CREATE_USER_FORM)
                setShowCreateUserDialog(true)
              }}
              onOpenResetPasswordDialog={(user) => {
                setPasswordResetTarget(user)
                setShowPasswordResetDialog(true)
              }}
              onResetUserPasskeys={resetUserPasskeys}
              onToggleExpandUser={toggleExpandedUser}
              onToggleUserActive={requestToggleUserActive}
              onUpdateUser={updateUser}
              selectedUserConfig={selectedUserConfig}
              selectedUserLoading={selectedUserLoading}
              session={session}
              updatingPasskeysResetUserId={selectedUserConfig && isPending(`resetPasskeys:${selectedUserConfig.user.id}`) ? selectedUserConfig.user.id : null}
              updatingUserId={selectedUserConfig && isPending(`updateUser:${selectedUserConfig.user.id}`) ? selectedUserConfig.user.id : null}
              users={users}
              sectionLoading={isSectionRefreshing('userManagementCollapsed')}
              t={t}
              locale={language}
            />
          </>
        ) : null}

        {showPasswordResetDialog && passwordResetTarget ? (
          <PasswordResetDialog
            onClose={() => {
              setShowPasswordResetDialog(false)
              setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
              setPasswordResetTarget(null)
            }}
            onFormChange={setAdminResetPasswordForm}
            onSubmit={resetSelectedUserPassword}
            passwordLoading={passwordResetTarget && isPending(`resetPassword:${passwordResetTarget.id}`)}
            resetPasswordForm={adminResetPasswordForm}
            t={t}
            username={passwordResetTarget.username}
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
