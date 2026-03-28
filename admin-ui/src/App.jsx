import { Suspense, lazy, useEffect, useMemo, useRef, useState } from 'react'
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
import NotificationsDialog from './components/layout/NotificationsDialog'
import PreferencesDialog from './components/layout/PreferencesDialog'
import SetupGuidePanel from './components/layout/SetupGuidePanel'
import WorkspaceSectionWindow from './components/layout/WorkspaceSectionWindow'
import OAuthAppsSection from './components/admin/OAuthAppsSection'
import SystemDashboardSection from './components/admin/SystemDashboardSection'
import SystemOAuthAppsDialog from './components/admin/SystemOAuthAppsDialog'
import SystemPollingSettingsDialog from './components/admin/SystemPollingSettingsDialog'
import UserPollingSettingsSection from './components/polling/UserPollingSettingsSection'
import UserPollingSettingsDialog from './components/polling/UserPollingSettingsDialog'
import UserManagementSection from './components/admin/UserManagementSection'
import UserBridgesSection from './components/bridges/UserBridgesSection'
import { apiErrorText } from './lib/api'
import { findEmailProviderPreset } from './lib/emailProviderPresets'
import { formatPollError, isOauthRevokedError } from './lib/formatters'
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
  layoutEditEnabled: false,
  quickSetupCollapsed: false,
  quickSetupDismissed: false,
  quickSetupPinnedVisible: false,
  gmailDestinationCollapsed: false,
  userPollingCollapsed: false,
  userStatsCollapsed: false,
  sourceBridgesCollapsed: false,
  adminQuickSetupCollapsed: false,
  systemDashboardCollapsed: false,
  oauthAppsCollapsed: false,
  globalStatsCollapsed: false,
  userManagementCollapsed: false,
  userSectionOrder: ['quickSetup', 'gmail', 'userPolling', 'userStats', 'sourceBridges'],
  adminSectionOrder: ['adminQuickSetup', 'systemDashboard', 'oauthApps', 'globalStats', 'userManagement'],
  language: 'en'
}
const DEFAULT_SYSTEM_POLLING_FORM = {
  pollEnabledMode: 'DEFAULT',
  pollIntervalOverride: '',
  fetchWindowOverride: '',
  manualTriggerLimitCountOverride: '',
  manualTriggerLimitWindowSecondsOverride: ''
}
const DEFAULT_SYSTEM_OAUTH_SETTINGS = {
  effectiveMultiUserEnabled: true,
  multiUserEnabledOverride: null,
  googleDestinationUser: 'me',
  googleRedirectUri: '',
  googleClientId: '',
  googleClientSecret: '',
  googleClientSecretConfigured: false,
  googleRefreshToken: '',
  googleRefreshTokenConfigured: false,
  microsoftClientId: '',
  microsoftRedirectUri: '',
  microsoftClientSecret: '',
  microsoftClientSecretConfigured: false,
  secureStorageConfigured: true
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
  errorPolls: 0,
  importsByDay: [],
  importTimelines: {},
  duplicateTimelines: {},
  errorTimelines: {},
  manualRunTimelines: {},
  scheduledRunTimelines: {},
  health: {
    activeMailFetchers: 0,
    coolingDownMailFetchers: 0,
    failingMailFetchers: 0,
    disabledMailFetchers: 0
  },
  providerBreakdown: [],
  manualRuns: 0,
  scheduledRuns: 0,
  averagePollDurationMillis: 0
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
  multiUserEnabled: true,
  microsoftOAuthAvailable: true,
  googleOAuthAvailable: true,
  sourceOAuthProviders: ['MICROSOFT', 'GOOGLE']
}
const DEFAULT_ADMIN_WORKSPACE = 'user'
const SECTION_HIGHLIGHT_MS = 2600
const NOTIFICATION_AUTO_CLOSE_MS = {
  success: 8000,
  warning: 12000,
  error: 16000
}
const PollingStatisticsSection = lazy(() => import('./components/stats/PollingStatisticsSection'))

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
  const [fetcherStatsById, setFetcherStatsById] = useState({})
  const [fetcherStatsLoadingId, setFetcherStatsLoadingId] = useState(null)

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
  const [runningUserPoll, setRunningUserPoll] = useState(false)
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
  const [systemOAuthSettings, setSystemOAuthSettings] = useState(DEFAULT_SYSTEM_OAUTH_SETTINGS)
  const [systemOAuthSettingsDirty, setSystemOAuthSettingsDirty] = useState(false)
  const [systemOAuthEditorProvider, setSystemOAuthEditorProvider] = useState('google')
  const [dismissedPersistentNotifications, setDismissedPersistentNotifications] = useState({})
  const [language, setLanguage] = useState(() => normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language))
  const [registerOpen, setRegisterOpen] = useState(false)
  const [showPreferencesDialog, setShowPreferencesDialog] = useState(false)
  const [showNotificationsDialog, setShowNotificationsDialog] = useState(false)
  const [showUserPollingDialog, setShowUserPollingDialog] = useState(false)
  const [showSystemPollingDialog, setShowSystemPollingDialog] = useState(false)
  const [showSystemOAuthAppsDialog, setShowSystemOAuthAppsDialog] = useState(false)
  const [adminWorkspace, setAdminWorkspace] = useState(DEFAULT_ADMIN_WORKSPACE)
  const [dragState, setDragState] = useState(null)
  const notificationTimersRef = useRef(new Map())
  const t = useMemo(() => (key, params) => translate(language, key, params), [language])
  const notificationTimestampFormatter = useMemo(
    () => new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeStyle: 'medium' }),
    [language]
  )
  const errorText = (key) => t(`errors.${key}`)
  const selectableLanguages = useMemo(() => languageOptions.map((value) => ({
    value,
    label: translate(language, `language.${value}`)
  })), [language])
  const securityDialogDirty = Boolean(
    passwordForm.currentPassword.trim()
    || passwordForm.newPassword.trim()
    || passwordForm.confirmNewPassword.trim()
  )
  const isAdmin = session?.role === 'ADMIN'

  function normalizeUiPreferences(payload) {
    const nextUiPreferences = {
      ...DEFAULT_UI_PREFERENCES,
      ...(payload || {})
    }
    return {
      ...nextUiPreferences,
      ...(nextUiPreferences.persistLayout ? {} : {
        layoutEditEnabled: false,
        quickSetupCollapsed: false,
        quickSetupDismissed: false,
        quickSetupPinnedVisible: false,
        gmailDestinationCollapsed: false,
        userPollingCollapsed: false,
        userStatsCollapsed: false,
        sourceBridgesCollapsed: false,
        adminQuickSetupCollapsed: false,
        systemDashboardCollapsed: false,
        oauthAppsCollapsed: false,
        globalStatsCollapsed: false,
        userManagementCollapsed: false
      }),
      userSectionOrder: Array.isArray(nextUiPreferences.userSectionOrder) && nextUiPreferences.userSectionOrder.length
        ? [...nextUiPreferences.userSectionOrder]
        : [...DEFAULT_UI_PREFERENCES.userSectionOrder],
      adminSectionOrder: Array.isArray(nextUiPreferences.adminSectionOrder) && nextUiPreferences.adminSectionOrder.length
        ? [...nextUiPreferences.adminSectionOrder]
        : [...DEFAULT_UI_PREFERENCES.adminSectionOrder],
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
    const availableSourceProviders = Array.isArray(authOptions.sourceOAuthProviders) ? authOptions.sourceOAuthProviders : []
    if (next.authMethod === 'PASSWORD') {
      next.oauthProvider = 'NONE'
      next.oauthRefreshToken = ''
    }
    if (next.authMethod === 'OAUTH2') {
      next.password = ''
      if (!next.oauthProvider || next.oauthProvider === 'NONE') {
        next.oauthProvider = availableSourceProviders[0] || 'NONE'
      }
    }
    if (next.authMethod === 'OAUTH2' && availableSourceProviders.length === 0) {
      next.authMethod = 'PASSWORD'
      next.oauthProvider = 'NONE'
      next.oauthRefreshToken = ''
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
      ...preset.values,
      ...(presetId === 'gmail' && authOptions.sourceOAuthProviders.includes('GOOGLE')
        ? { authMethod: 'OAUTH2', oauthProvider: 'GOOGLE' }
        : {})
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

  function hideFloatingNotification(id) {
    const timer = notificationTimersRef.current.get(id)
    if (timer) {
      window.clearTimeout(timer)
      notificationTimersRef.current.delete(id)
    }
    setNotifications((current) => current.map((notification) => (
      notification.id === id
        ? { ...notification, floatingVisible: false }
        : notification
    )))
  }

  function clearAllNotifications() {
    notificationTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    notificationTimersRef.current.clear()
    setNotifications([])
  }

  function pushNotification({
    autoCloseMs,
    copyText = '',
    groupKey = null,
    message,
    replaceGroup = false,
    supersedesGroupKeys = [],
    targetId = null,
    tone = 'success'
  }) {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`
    const resolvedAutoCloseMs = typeof autoCloseMs === 'number'
      ? autoCloseMs
      : (NOTIFICATION_AUTO_CLOSE_MS[tone] || NOTIFICATION_AUTO_CLOSE_MS.success)
    setNotifications((current) => [...current.filter((notification) => {
      if (replaceGroup && groupKey && notification.groupKey === groupKey) {
        return false
      }
      if (notification.groupKey && supersedesGroupKeys.includes(notification.groupKey)) {
        return false
      }
      return true
    }), {
      autoCloseMs: resolvedAutoCloseMs,
      copyText,
      createdAt: Date.now(),
      floatingVisible: true,
      groupKey,
      id,
      message,
      targetId,
      tone
    }])
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
        throw new Error(`${errorText('loadSession')} (${response.status})`)
      }
      const payload = await response.json()
      setSession(payload)
      setAuthError('')
    } catch (err) {
      setAuthError(err.message || errorText('loadSession'))
    } finally {
      setAuthLoading(false)
    }
  }

  async function loadAuthOptions() {
    try {
      const response = await fetch('/api/auth/options')
      if (!response.ok) {
        throw new Error(errorText('loadAuthOptions'))
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
        throw new Error(await apiErrorText(response, errorText('loadUserConfiguration')))
      }
      setSelectedUserConfig(await response.json())
    } catch (err) {
      pushNotification({ autoCloseMs: null, copyText: err.message || errorText('loadUserConfiguration'), message: err.message || errorText('loadUserConfiguration'), targetId: 'user-management-section', tone: 'error' })
    } finally {
      setSelectedUserLoading(false)
    }
  }

  async function loadFetcherStats(fetcher, options = {}) {
    if (!fetcher?.bridgeId) return
    const { suppressErrors = false } = options
    setFetcherStatsLoadingId(fetcher.bridgeId)
    try {
      const endpointPrefix = fetcher.managementSource === 'ENVIRONMENT' ? '/api/admin/bridges' : '/api/app/bridges'
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
          targetId: 'source-bridges-section',
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

  async function loadUserCustomRange(range) {
    const search = new URLSearchParams({ from: range.from })
    if (range.to) search.set('to', range.to)
    return loadScopedTimelineBundle(`/api/app/polling-stats/range?${search.toString()}`, t('pollingStats.customRangeLoadError'))
  }

  async function loadGlobalCustomRange(range) {
    const search = new URLSearchParams({ from: range.from })
    if (range.to) search.set('to', range.to)
    return loadScopedTimelineBundle(`/api/admin/polling-stats/range?${search.toString()}`, t('pollingStats.customRangeLoadError'))
  }

  async function loadFetcherCustomRange(fetcher, range) {
    const search = new URLSearchParams({ from: range.from })
    if (range.to) search.set('to', range.to)
    const endpointPrefix = fetcher.managementSource === 'ENVIRONMENT' ? '/api/admin/bridges' : '/api/app/bridges'
    return loadScopedTimelineBundle(
      `${endpointPrefix}/${encodeURIComponent(fetcher.bridgeId)}/polling-stats/range?${search.toString()}`,
      t('pollingStats.customRangeLoadError')
    )
  }

  async function loadAdminUserCustomRange(userId, range) {
    const search = new URLSearchParams({ from: range.from })
    if (range.to) search.set('to', range.to)
    return loadScopedTimelineBundle(`/api/admin/users/${userId}/polling-stats/range?${search.toString()}`, t('pollingStats.customRangeLoadError'))
  }

  async function loadAppData(options = {}) {
    const { suppressErrors = false } = options
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
        requests.push(fetch('/api/admin/oauth-app-settings'))
      }
      if (session.role === 'ADMIN' && authOptions.multiUserEnabled) {
        requests.push(fetch('/api/admin/users'))
      }

      const responses = await Promise.all(requests)
      if (responses.some((response) => !response.ok)) {
        const firstFailed = responses.find((response) => !response.ok)
        throw new Error(await apiErrorText(firstFailed, errorText('loadAdminData')))
      }

      const payloads = await Promise.all(responses.map((response) => response.json()))
      const [gmailPayload, userPollingPayload, userPollingStatsPayload, bridgesPayload, uiPreferencesPayload, passkeysPayload, adminPayload, oauthSettingsPayload, usersPayload] = payloads

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
      setFetcherStatsById((current) => {
        const validIds = new Set((Array.isArray(bridgesPayload) ? bridgesPayload : []).map((bridge) => bridge.bridgeId))
        for (const bridge of (adminPayload?.bridges || [])) {
          validIds.add(bridge.id)
        }
        return Object.fromEntries(Object.entries(current).filter(([bridgeId]) => validIds.has(bridgeId)))
      })
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
          fetchWindowOverride: adminPayload.polling.fetchWindowOverride === null ? '' : String(adminPayload.polling.fetchWindowOverride),
          manualTriggerLimitCountOverride: adminPayload.polling.manualTriggerLimitCountOverride === null ? '' : String(adminPayload.polling.manualTriggerLimitCountOverride),
          manualTriggerLimitWindowSecondsOverride: adminPayload.polling.manualTriggerLimitWindowSecondsOverride === null ? '' : String(adminPayload.polling.manualTriggerLimitWindowSecondsOverride)
        })
      }
        if (oauthSettingsPayload) {
          setSystemOAuthSettings({
            ...DEFAULT_SYSTEM_OAUTH_SETTINGS,
            ...oauthSettingsPayload,
            googleDestinationUser: oauthSettingsPayload.googleDestinationUser || 'me',
            googleRedirectUri: oauthSettingsPayload.googleRedirectUri || `${window.location.origin}/api/google-oauth/callback`,
            googleClientSecret: '',
            googleRefreshToken: '',
            microsoftClientSecret: ''
          })
        }
      } else {
        setSystemDashboard(null)
        setSystemOAuthSettings(DEFAULT_SYSTEM_OAUTH_SETTINGS)
      }
      if (usersPayload) {
        setUsers(usersPayload)
        const availableUserIds = new Set(usersPayload.map((user) => user.id))
        setSelectedUserId((current) => (current && availableUserIds.has(current) ? current : null))
        setSelectedUserConfig((current) => (current && availableUserIds.has(current.user.id) ? current : null))
      } else {
        setUsers([])
        setSelectedUserId(null)
        setSelectedUserConfig(null)
      }
      if (session.role !== 'ADMIN') {
        setAdminWorkspace(DEFAULT_ADMIN_WORKSPACE)
      }
    } catch (err) {
      if (!suppressErrors) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('loadApplicationData'), message: err.message || errorText('loadApplicationData'), tone: 'error' })
      }
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
    if (!uiPreferences.layoutEditEnabled && dragState) {
      setDragState(null)
    }
  }, [dragState, uiPreferences.layoutEditEnabled])

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
        hideFloatingNotification(notification.id)
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
          throw new Error(await apiErrorText(response, errorText('loginFailed')))
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
        setAuthError(err.message || errorText('loginFailed'))
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
          throw new Error(await apiErrorText(response, errorText('registrationFailed')))
        }
        const payload = await response.json()
        setRegisterForm(DEFAULT_REGISTER_FORM)
        setRegisterOpen(false)
        pushNotification({ message: payload.message || t('notifications.registrationSubmitted'), tone: 'success' })
      } catch (err) {
        setAuthError(err.message || errorText('registrationFailed'))
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
          throw new Error(await apiErrorText(response, errorText('changePassword')))
        }
        setPasswordForm(DEFAULT_PASSWORD_FORM)
        pushNotification({ message: t('notifications.passwordUpdated'), targetId: 'password-panel-section', tone: 'success' })
        await loadSession()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('changePassword'), message: err.message || errorText('changePassword'), targetId: 'password-panel-section', tone: 'error' })
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
              throw new Error(await apiErrorText(response, errorText('removePassword')))
            }
            setConfirmationDialog(null)
            setPasswordForm(DEFAULT_PASSWORD_FORM)
            pushNotification({ autoCloseMs: null, message: t('notifications.passwordRemoved'), targetId: 'password-panel-section', tone: 'warning' })
            await loadAppData()
            await loadSession()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || errorText('removePassword'), message: err.message || errorText('removePassword'), targetId: 'password-panel-section', tone: 'error' })
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
      throw new Error(await apiErrorText(finishResponse, errorText('passkeySignInFailed')))
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
          throw new Error(await apiErrorText(startResponse, errorText('startPasskeySignIn')))
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
          throw new Error(await apiErrorText(startResponse, errorText('startPasskeyRegistration')))
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
          throw new Error(await apiErrorText(finishResponse, errorText('registerPasskey')))
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
              throw new Error(await apiErrorText(response, errorText('removePasskey')))
            }
            setConfirmationDialog(null)
            pushNotification({ message: t('notifications.passkeyRemoved'), targetId: 'passkey-panel-section', tone: 'success' })
            await loadAppData()
            await loadSession()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || errorText('removePasskey'), message: err.message || errorText('removePasskey'), targetId: 'passkey-panel-section', tone: 'error' })
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
          throw new Error(await apiErrorText(response, errorText('saveGmailConfiguration')))
        }
        pushNotification({ message: t('notifications.gmailSaved'), targetId: 'gmail-destination-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('saveGmailConfiguration'), message: err.message || errorText('saveGmailConfiguration'), targetId: 'gmail-destination-section', tone: 'error' })
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
              throw new Error(await apiErrorText(response, errorText('unlinkGmailAccount')))
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
            pushNotification({ autoCloseMs: null, copyText: err.message || errorText('unlinkGmailAccount'), message: err.message || errorText('unlinkGmailAccount'), targetId: 'gmail-destination-section', tone: 'error' })
          }
        })
      },
      title: t('gmail.unlinkConfirmTitle')
    })
  }

  async function upsertBridgeForm(options = {}) {
    const { connectMicrosoftAfterSave = false } = options
    const normalizedBridgeId = bridgeForm.bridgeId.trim()
    const originalBridgeId = bridgeForm.originalBridgeId.trim()
    const duplicateFetcher = visibleFetchers.find((fetcher) => (
      fetcher.bridgeId === normalizedBridgeId && fetcher.bridgeId !== originalBridgeId
    ))
    if (duplicateFetcher) {
      const duplicateMessage = t('bridges.duplicateId', { bridgeId: normalizedBridgeId })
      setBridgeDuplicateError(duplicateMessage)
      pushNotification({ autoCloseMs: null, copyText: duplicateMessage, message: duplicateMessage, targetId: 'source-bridges-section', tone: 'error' })
      return null
    }
    const actionKey = connectMicrosoftAfterSave ? 'bridgeSaveConnect' : 'bridgeSave'
    return withPending(actionKey, async () => {
      try {
        const response = await fetch('/api/app/bridges', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(bridgeForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('saveMailFetcher')))
        }
        const payload = await response.json()
        setBridgeDuplicateError('')
        setBridgeTestResult(null)
        if (!connectMicrosoftAfterSave) {
          pushNotification({ message: t('notifications.bridgeSaved', { bridgeId: bridgeForm.bridgeId }), targetId: 'source-bridges-section', tone: 'success' })
          setBridgeForm(DEFAULT_BRIDGE_FORM)
          setShowFetcherDialog(false)
          await loadAppData()
        } else {
          pushNotification({
            message: t('notifications.bridgeSavedStartingProviderOAuth', {
              bridgeId: payload.bridgeId || bridgeForm.bridgeId,
              provider: bridgeForm.oauthProvider === 'GOOGLE' ? t('oauthProvider.google') : t('oauthProvider.microsoft')
            }),
            targetId: 'source-bridges-section',
            tone: 'warning'
          })
        }
        return payload
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('saveMailFetcher'), message: err.message || errorText('saveMailFetcher'), targetId: 'source-bridges-section', tone: 'error' })
        return null
      }
    })
  }

  async function saveBridge(event) {
    event.preventDefault()
    await upsertBridgeForm()
  }

  async function saveBridgeAndConnectOAuth() {
    const payload = await upsertBridgeForm({ connectMicrosoftAfterSave: true })
    const savedBridgeId = payload?.bridgeId || bridgeForm.bridgeId?.trim()
    if (savedBridgeId) {
      startSourceOAuth(savedBridgeId, bridgeForm.oauthProvider)
    }
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
          throw new Error(await apiErrorText(response, errorText('testMailFetcherConnection')))
        }
        const payload = await response.json()
        const message = payload.message || t('bridges.testSuccess')
        setBridgeTestResult({ ...payload, message, tone: 'success' })
        pushNotification({ message, targetId: 'source-bridges-section', tone: 'success' })
      } catch (err) {
        const message = err.message || errorText('testMailFetcherConnection')
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
              throw new Error(await apiErrorText(response, errorText('deleteMailFetcher')))
            }
            setConfirmationDialog(null)
            pushNotification({ message: t('notifications.bridgeDeleted', { bridgeId }), targetId: 'source-bridges-section', tone: 'success' })
            await loadAppData()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || errorText('deleteMailFetcher'), message: err.message || errorText('deleteMailFetcher'), targetId: 'source-bridges-section', tone: 'error' })
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
          throw new Error(await apiErrorText(response, errorText('loadFetcherPollingSettings')))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        setShowFetcherPollingDialog(true)
      } catch (err) {
        setFetcherPollingTarget(null)
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('loadFetcherPollingSettings'), message: err.message || errorText('loadFetcherPollingSettings'), targetId: 'source-bridges-section', tone: 'error' })
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
          throw new Error(await apiErrorText(response, errorText('saveFetcherPollingSettings')))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        pushNotification({ message: t('notifications.fetcherPollingSaved', { bridgeId: fetcherPollingTarget.bridgeId }), targetId: 'source-bridges-section', tone: 'success' })
        await loadAppData()
        closeFetcherPollingDialog()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('saveFetcherPollingSettings'), message: err.message || errorText('saveFetcherPollingSettings'), targetId: 'source-bridges-section', tone: 'error' })
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
          throw new Error(await apiErrorText(response, errorText('resetFetcherPollingSettings')))
        }
        const payload = await response.json()
        setFetcherPollingForm(normalizeSourcePollingForm(payload))
        pushNotification({ message: t('notifications.fetcherPollingReset', { bridgeId: fetcherPollingTarget.bridgeId }), targetId: 'source-bridges-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('resetFetcherPollingSettings'), message: err.message || errorText('resetFetcherPollingSettings'), targetId: 'source-bridges-section', tone: 'error' })
      }
    })
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
          targetId: 'source-bridges-section',
          tone: 'warning'
        })
        const endpointPrefix = fetcher?.managementSource === 'ENVIRONMENT' ? '/api/admin/bridges' : '/api/app/bridges'
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
          targetId: 'source-bridges-section',
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

  async function refreshFetcherState(_fetcher, expanded) {
    if (!expanded) {
      return
    }
    setExpandedFetcherLoadingId(_fetcher.bridgeId)
    try {
      await Promise.all([
        refreshSectionData('sourceBridgesCollapsed', () => loadAppData({ suppressErrors: true })),
        loadFetcherStats(_fetcher, { suppressErrors: true })
      ])
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
          throw new Error(await apiErrorText(response, errorText('createUser')))
        }
        const payload = await response.json()
        setCreateUserForm(DEFAULT_CREATE_USER_FORM)
        setShowCreateUserDialog(false)
        pushNotification({ message: t('notifications.userCreated', { username: payload.username }), targetId: 'user-management-section', tone: 'success' })
        await loadAppData()
        setSelectedUserId(payload.id)
        return true
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('createUser'), message: err.message || errorText('createUser'), targetId: 'user-management-section', tone: 'error' })
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
          throw new Error(await apiErrorText(response, errorText('updateUser')))
        }
        const payload = await response.json()
        pushNotification({ message: successMessage || t('notifications.userUpdated', { username: payload.username }), targetId: 'user-management-section', tone: 'success' })
        await loadAppData()
        setSelectedUserId(payload.id)
        await loadSelectedUserConfiguration(payload.id)
        return true
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('updateUser'), message: err.message || errorText('updateUser'), targetId: 'user-management-section', tone: 'error' })
        return false
      }
    })
  }

  function requestToggleMultiUserMode(enabled) {
    const turningOn = Boolean(enabled)
    openConfirmation({
      actionKey: 'multiUserModeSave',
      body: turningOn
        ? t('users.switchToMultiUserConfirmBody')
        : t('users.switchToSingleUserConfirmBody', { username: session?.username || 'admin' }),
      confirmLabel: turningOn ? t('users.switchToMultiUser') : t('users.switchToSingleUser'),
      confirmLoadingLabel: t('users.confirmLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending('multiUserModeSave', async () => {
          try {
            const response = await fetch('/api/admin/users/mode', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ multiUserEnabled: enabled })
            })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('saveMultiUserMode')))
            }
            await response.json()
            setConfirmationDialog(null)
            setSelectedUserId(null)
            setSelectedUserConfig(null)
            await loadAuthOptions()
            await loadAppData()
            pushNotification({
              message: enabled ? t('notifications.multiUserEnabled') : t('notifications.singleUserEnabled'),
              targetId: 'user-management-section',
              tone: 'success'
            })
          } catch (err) {
            pushNotification({
              autoCloseMs: null,
              copyText: err.message || errorText('saveMultiUserMode'),
              message: err.message || errorText('saveMultiUserMode'),
              targetId: 'user-management-section',
              tone: 'error'
            })
          }
        })
      },
      title: turningOn ? t('users.switchToMultiUserConfirmTitle') : t('users.switchToSingleUserConfirmTitle')
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
          throw new Error(await apiErrorText(response, errorText('resetPassword')))
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
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('resetPassword'), message: err.message || errorText('resetPassword'), targetId: 'user-management-section', tone: 'error' })
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
              throw new Error(await apiErrorText(response, errorText('resetPasskeys')))
            }
            const payload = await response.json()
            setConfirmationDialog(null)
            pushNotification({ message: t('notifications.passkeysRemoved', { count: payload.deleted, username: user.username }), targetId: 'user-management-section', tone: 'success' })
            await loadAppData()
            await loadSelectedUserConfiguration(user.id)
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || errorText('resetPasskeys'), message: err.message || errorText('resetPasskeys'), targetId: 'user-management-section', tone: 'error' })
          }
        })
      },
      title: t('users.resetPasskeysConfirmTitle')
    })
  }

  function requestDeleteUser(user) {
    if (!user) return
    openConfirmation({
      actionKey: `deleteUser:${user.id}`,
      body: t('users.deleteConfirmBody', { username: user.username }),
      confirmLabel: t('users.delete'),
      confirmLoadingLabel: t('users.deleteLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`deleteUser:${user.id}`, async () => {
          try {
            const response = await fetch(`/api/admin/users/${user.id}`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('deleteUser')))
            }
            setConfirmationDialog(null)
            if (selectedUserId === user.id) {
              setSelectedUserId(null)
              setSelectedUserConfig(null)
            }
            await loadAppData()
            pushNotification({ message: t('notifications.userDeleted', { username: user.username }), targetId: 'user-management-section', tone: 'success' })
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || errorText('deleteUser'), message: err.message || errorText('deleteUser'), targetId: 'user-management-section', tone: 'error' })
          }
        })
      },
      title: t('users.deleteConfirmTitle')
    })
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
        setConfirmationDialog(null)
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

  async function saveSystemOAuthSettings(event) {
    event.preventDefault()
    await withPending('systemOAuthSettingsSave', async () => {
      try {
        const response = await fetch('/api/admin/oauth-app-settings', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            multiUserEnabledOverride: systemOAuthSettings.multiUserEnabledOverride,
            googleDestinationUser: systemOAuthSettings.googleDestinationUser,
            googleRedirectUri: systemOAuthSettings.googleRedirectUri,
            googleClientId: systemOAuthSettings.googleClientId,
            googleClientSecret: systemOAuthSettings.googleClientSecret || null,
            googleRefreshToken: systemOAuthSettings.googleRefreshToken || null,
            microsoftClientId: systemOAuthSettings.microsoftClientId,
            microsoftClientSecret: systemOAuthSettings.microsoftClientSecret || null
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('saveOAuthApps')))
        }
        const payload = await response.json()
        setSystemOAuthSettings({
          ...DEFAULT_SYSTEM_OAUTH_SETTINGS,
          ...payload,
          googleDestinationUser: payload.googleDestinationUser || 'me',
          googleRedirectUri: payload.googleRedirectUri || `${window.location.origin}/api/google-oauth/callback`,
          googleClientSecret: '',
          googleRefreshToken: '',
          microsoftClientSecret: ''
        })
        setSystemOAuthSettingsDirty(false)
        setShowSystemOAuthAppsDialog(false)
        pushNotification({ message: t('notifications.oauthAppsUpdated'), targetId: 'oauth-apps-section', tone: 'success' })
        await loadAuthOptions()
        await loadAppData()
      } catch (err) {
        pushNotification({ copyText: err.message || errorText('saveOAuthApps'), message: err.message || errorText('saveOAuthApps'), targetId: 'oauth-apps-section', tone: 'error' })
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

  function navigateToGoogleOAuthSelf() {
    withPending('googleOAuthSelf', async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign(`/api/google-oauth/start/self?lang=${encodeURIComponent(language)}`)
          resolve()
        }, 75)
      })
    })
  }

  function startGoogleOAuthSelf() {
    if (!gmailMeta?.refreshTokenConfigured) {
      navigateToGoogleOAuthSelf()
      return
    }
    openConfirmation({
      actionKey: 'googleOAuthSelf',
      body: t('gmail.reconnectConfirmBody'),
      confirmLabel: t('gmail.reconnect'),
      confirmLoadingLabel: t('gmail.reconnectLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        setConfirmationDialog(null)
        navigateToGoogleOAuthSelf()
      },
      title: t('gmail.reconnectConfirmTitle')
    })
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
          throw new Error(await apiErrorText(response, errorText('saveLayoutPreference')))
        }
        const payload = await response.json()
        const normalized = normalizeUiPreferences(payload)
        setUiPreferences(normalized)
        setLanguage(normalized.language)
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('saveLayoutPreference'), message: err.message || errorText('saveLayoutPreference'), tone: 'error' })
      }
    })
  }

  function applyOrderedSectionIds(availableIds, preferredIds) {
    const ordered = preferredIds.filter((sectionId) => availableIds.includes(sectionId))
    return [...ordered, ...availableIds.filter((sectionId) => !ordered.includes(sectionId))]
  }

  async function updateUiPreferencesLocally(nextPreferences) {
    const normalized = normalizeUiPreferences(nextPreferences)
    setUiPreferences(normalized)
    setLanguage(normalized.language)
    if (normalized.persistLayout) {
      await persistUiPreferences(normalized)
    }
  }

  async function moveSection(workspaceKey, sectionId, direction) {
    const preferenceKey = workspaceKey === 'admin' ? 'adminSectionOrder' : 'userSectionOrder'
    const currentOrder = [...uiPreferences[preferenceKey]]
    const currentIndex = currentOrder.indexOf(sectionId)
    if (currentIndex < 0) {
      return
    }
    const targetIndex = direction === 'up' ? currentIndex - 1 : currentIndex + 1
    if (targetIndex < 0 || targetIndex >= currentOrder.length) {
      return
    }
    const nextOrder = [...currentOrder]
    ;[nextOrder[currentIndex], nextOrder[targetIndex]] = [nextOrder[targetIndex], nextOrder[currentIndex]]
    await updateUiPreferencesLocally({
      ...uiPreferences,
      [preferenceKey]: nextOrder
    })
  }

  async function reorderSections(workspaceKey, draggedId, targetIndex) {
    const preferenceKey = workspaceKey === 'admin' ? 'adminSectionOrder' : 'userSectionOrder'
    const currentOrder = [...uiPreferences[preferenceKey]]
    const currentIndex = currentOrder.indexOf(draggedId)
    if (currentIndex < 0) {
      return
    }
    const boundedTargetIndex = Math.max(0, Math.min(targetIndex, currentOrder.length))
    const [movedSection] = currentOrder.splice(currentIndex, 1)
    const adjustedTargetIndex = currentIndex < boundedTargetIndex ? boundedTargetIndex - 1 : boundedTargetIndex
    currentOrder.splice(adjustedTargetIndex, 0, movedSection)
    await updateUiPreferencesLocally({
      ...uiPreferences,
      [preferenceKey]: currentOrder
    })
  }

  async function resetLayoutPreferences() {
    await updateUiPreferencesLocally({
      ...uiPreferences,
      ...DEFAULT_UI_PREFERENCES,
      persistLayout: uiPreferences.persistLayout,
      language
    })
    pushNotification({ message: t('notifications.layoutReset'), tone: 'success' })
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

  async function dismissQuickSetupGuide() {
    handleQuickSetupVisibilityChange(false)
  }

  function handlePersistLayoutChange(enabled) {
    const nextPreferences = {
      ...uiPreferences,
      persistLayout: enabled
    }
    setUiPreferences(nextPreferences)
    persistUiPreferences(nextPreferences)
  }

  function handleLayoutEditChange(enabled) {
    const nextPreferences = {
      ...uiPreferences,
      layoutEditEnabled: enabled
    }
    setUiPreferences(nextPreferences)
    persistUiPreferences(nextPreferences)
  }

  function startLayoutEditingFromPreferences() {
    setShowPreferencesDialog(false)
    handleLayoutEditChange(true)
  }

  function handleQuickSetupVisibilityChange(visible) {
    const nextPreferences = {
      ...uiPreferences,
      quickSetupPinnedVisible: visible,
      quickSetupDismissed: visible ? false : userSetupGuideState.allStepsComplete,
      quickSetupCollapsed: visible ? false : true
    }
    setUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      persistUiPreferences(nextPreferences)
    }
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

  function notificationTargetForPollErrors(errorDetails = [], messages = [], fallbackTarget = 'source-bridges-section') {
    const details = Array.isArray(errorDetails) ? errorDetails : []
    if (details.some((detail) => detail?.code === 'gmail_account_not_linked' || detail?.code === 'gmail_access_revoked')) {
      return 'gmail-destination-section'
    }
    const rawMessages = Array.isArray(messages) ? messages : []
    if (rawMessages.some((message) => typeof message === 'string'
      && (message.includes('The Gmail account is not linked for this destination')
        || message.includes('The linked Gmail account no longer grants InboxBridge access')))) {
      return 'gmail-destination-section'
    }
    return fallbackTarget
  }

  function highlightTarget(target) {
    target.classList.remove('section-focus-highlight')
    void target.offsetWidth
    target.classList.add('section-focus-highlight')
    window.setTimeout(() => {
      target.classList.remove('section-focus-highlight')
    }, SECTION_HIGHLIGHT_MS)
  }

  function focusTarget(targetId, sectionKey = null) {
    const derivedSectionKey = sectionKey || ({
      'gmail-destination-section': 'gmailDestinationCollapsed',
      'user-polling-section': 'userPollingCollapsed',
      'user-polling-stats-section': 'userStatsCollapsed',
      'source-bridges-section': 'sourceBridgesCollapsed',
      'system-dashboard-section': 'systemDashboardCollapsed',
      'oauth-apps-section': 'oauthAppsCollapsed',
      'global-polling-stats-section': 'globalStatsCollapsed',
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
    if (targetId === 'user-management-section' || targetId === 'system-dashboard-section' || targetId === 'oauth-apps-section' || targetId === 'global-polling-stats-section') {
      setAdminWorkspace('admin')
    }
    if (targetId === 'gmail-destination-section' || targetId === 'user-polling-section' || targetId === 'user-polling-stats-section' || targetId === 'source-bridges-section') {
      setAdminWorkspace('user')
    }
    window.setTimeout(() => {
      const target = document.getElementById(targetId)
      if (!target) {
        return
      }
      target.scrollIntoView({ behavior: 'smooth', block: 'start' })
      target.focus()
      highlightTarget(target)
    }, 150)
  }

  function focusNotificationTarget(targetId) {
    setShowNotificationsDialog(false)
    focusTarget(targetId)
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
      const deriveOauthConnected = (bridge) => {
        if (bridge.authMethod !== 'OAUTH2') return false
        if (isOauthRevokedError(bridge.lastEvent?.error) || isOauthRevokedError(bridge.pollingState?.lastFailureReason)) {
          return false
        }
        return bridge.oauthConnected === true
          || bridge.oauthRefreshTokenConfigured === true
          || bridge.tokenStorageMode === 'DATABASE'
          || bridge.tokenStorageMode === 'ENVIRONMENT'
      }
      const databaseFetchers = myBridges.map((bridge) => ({
        ...bridge,
        bridgeId: bridge.bridgeId,
        managementSource: 'DATABASE',
        oauthConnected: deriveOauthConnected(bridge),
        canDelete: true,
        canEdit: true,
        canConnectOAuth: authOptions.sourceOAuthProviders.includes(bridge.oauthProvider) && bridge.authMethod === 'OAUTH2',
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
        canConnectOAuth: authOptions.sourceOAuthProviders.includes(bridge.oauthProvider) && bridge.authMethod === 'OAUTH2',
        canConfigurePolling: true,
        canRunPoll: true
      }))
      : []

    return [...databaseFetchers, ...envFetchers]
      .sort((left, right) => left.bridgeId.localeCompare(right.bridgeId))
  }, [authOptions.sourceOAuthProviders, myBridges, session?.username, systemDashboard?.bridges])

  const userSetupGuideState = buildSetupGuideState({
    gmailMeta,
    myBridges,
    session,
    systemDashboard,
    users,
    workspace: 'user',
    systemOAuthSettings,
    t
  })

  const adminSetupGuideState = buildSetupGuideState({
    gmailMeta,
    myBridges,
    session,
    systemDashboard,
    users,
    workspace: 'admin',
    systemOAuthSettings,
    t
  })

  useEffect(() => {
    if (!session) {
      return
    }
    if (!userSetupGuideState.allStepsComplete && uiPreferences.quickSetupDismissed) {
      const next = {
        ...uiPreferences,
        quickSetupDismissed: false,
        quickSetupCollapsed: false
      }
      setUiPreferences(next)
      if (next.persistLayout) {
        persistUiPreferences(next)
      }
      return
    }
    if (userSetupGuideState.allStepsComplete && !uiPreferences.quickSetupPinnedVisible && !uiPreferences.quickSetupDismissed) {
      const next = {
        ...uiPreferences,
        quickSetupCollapsed: true,
        quickSetupDismissed: true
      }
      setUiPreferences(next)
      if (next.persistLayout) {
        persistUiPreferences(next)
      }
    }
  }, [session, userSetupGuideState.allStepsComplete, uiPreferences])

  useEffect(() => {
    setShowPasswordResetDialog(false)
    setAdminResetPasswordForm(DEFAULT_ADMIN_RESET_PASSWORD_FORM)
    setPasswordResetTarget(null)
  }, [selectedUserId])

  useEffect(() => {
    if (!dragState) {
      return undefined
    }

    document.body.classList.add('workspace-layout-dragging')

    async function finishDrag() {
      const current = dragState
      setDragState(null)
      await reorderSections(current.workspaceKey, current.draggedId, current.targetIndex)
    }

    function handlePointerUp() {
      finishDrag()
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        document.body.classList.remove('workspace-layout-dragging')
        setDragState(null)
      }
    }

    window.addEventListener('pointerup', handlePointerUp)
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.classList.remove('workspace-layout-dragging')
      window.removeEventListener('pointerup', handlePointerUp)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [dragState])

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

  const visibleNotifications = useMemo(
    () => notifications.filter((notification) => notification.floatingVisible !== false),
    [notifications]
  )

  const notificationHistory = useMemo(
    () => [...notifications].sort((left, right) => right.createdAt - left.createdAt),
    [notifications]
  )

  function notificationTimestamp(notification) {
    if (!notification?.createdAt) {
      return ''
    }
    return t('notifications.createdAt', { value: notificationTimestampFormatter.format(new Date(notification.createdAt)) })
  }

  const duplicateCreateUsername = useMemo(() => {
    const normalized = createUserForm.username.trim().toLowerCase()
    if (!normalized) {
      return false
    }
    return users.some((user) => user.username.toLowerCase() === normalized)
  }, [createUserForm.username, users])

  const userWorkspaceSections = [
    {
      id: 'quickSetup',
      render: () => userSetupGuideState.allStepsComplete && uiPreferences.quickSetupDismissed ? null : (
        <SetupGuidePanel
          collapsed={uiPreferences.quickSetupCollapsed}
          dismissable={userSetupGuideState.allStepsComplete}
          onDismiss={dismissQuickSetupGuide}
          onFocusSection={focusSection}
          sectionLoading={isSectionRefreshing('quickSetupCollapsed')}
          onToggleCollapse={() => toggleSection('quickSetupCollapsed')}
          savingLayout={isPending('uiPreferences')}
          steps={userSetupGuideState.steps}
          t={t}
        />
      )
    },
    {
      id: 'gmail',
      render: () => (
        <GmailAccountSection
          collapsed={uiPreferences.gmailDestinationCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          gmailConfig={gmailConfig}
          gmailMeta={gmailMeta}
          isAdmin={false}
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
      )
    },
    {
      id: 'userPolling',
      render: () => (
        <UserPollingSettingsSection
          collapsed={uiPreferences.userPollingCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          hasFetchers={myBridges.length > 0}
          onCollapseToggle={() => toggleSection('userPollingCollapsed')}
          onOpenEditor={() => setShowUserPollingDialog(true)}
          onRunPoll={runUserPoll}
          pollingSettings={userPollingSettings}
          runningPoll={runningUserPoll}
          sectionLoading={isSectionRefreshing('userPollingCollapsed')}
          t={t}
        />
      )
    },
    {
      id: 'userStats',
      render: () => (
        <Suspense fallback={<div className="muted-box">{t('common.refreshingSection')}</div>}>
          <PollingStatisticsSection
            collapsed={uiPreferences.userStatsCollapsed}
            collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
            copy={t('pollingStats.userCopy')}
            customRangeLoader={loadUserCustomRange}
            id="user-polling-stats-section"
            onCollapseToggle={() => toggleSection('userStatsCollapsed')}
            sectionLoading={isSectionRefreshing('userPollingCollapsed')}
            stats={userPollingStats}
            t={t}
            title={t('pollingStats.userTitle')}
          />
        </Suspense>
      )
    },
    {
      id: 'sourceBridges',
      render: () => (
        <UserBridgesSection
          bridgeForm={bridgeForm}
          collapsed={uiPreferences.sourceBridgesCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          connectingBridgeId={visibleFetchers.find((bridge) => isPending(`microsoftOAuth:${bridge.bridgeId}`) || isPending(`googleSourceOAuth:${bridge.bridgeId}`))?.bridgeId || null}
          deletingBridgeId={myBridges.find((bridge) => isPending(`bridgeDelete:${bridge.bridgeId}`))?.bridgeId || null}
          duplicateIdError={bridgeDuplicateError}
          fetcherDialogOpen={showFetcherDialog}
          fetcherPollLoadingId={visibleFetchers.find((bridge) => isPending(`bridgePoll:${bridge.bridgeId}`))?.bridgeId || null}
          fetcherPollingDialog={showFetcherPollingDialog ? fetcherPollingTarget : null}
          fetcherPollingForm={fetcherPollingForm}
          fetcherPollingLoading={fetcherPollingTarget ? isPending(`fetcherPollingSave:${fetcherPollingTarget.bridgeId}`) || isPending(`fetcherPollingLoad:${fetcherPollingTarget.bridgeId}`) : false}
          fetcherRefreshLoadingId={expandedFetcherLoadingId}
          fetcherStatsById={fetcherStatsById}
          fetcherStatsLoadingId={fetcherStatsLoadingId}
          fetchers={visibleFetchers}
          onLoadFetcherCustomRange={loadFetcherCustomRange}
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
          onConnectOAuth={startSourceOAuth}
          onDeleteBridge={deleteBridge}
          onEditBridge={editBridge}
          onFetcherPollingFormChange={(updater) => setFetcherPollingForm((current) => typeof updater === 'function' ? updater(current) : updater)}
          onFetcherToggleExpand={refreshFetcherState}
          onResetFetcherPollingSettings={resetFetcherPollingSettings}
          onRunFetcherPoll={runFetcherPoll}
          onSaveBridge={saveBridge}
          onSaveBridgeAndConnectOAuth={saveBridgeAndConnectOAuth}
          onSaveFetcherPollingSettings={saveFetcherPollingSettings}
          onTestConnection={testBridgeConnection}
          saveLoading={isPending('bridgeSave')}
          saveAndConnectLoading={isPending('bridgeSaveConnect')}
          testConnectionLoading={isPending('bridgeConnectionTest')}
          testResult={bridgeTestResult}
          sectionLoading={isSectionRefreshing('sourceBridgesCollapsed')}
          t={t}
          locale={language}
          availableOAuthProviders={authOptions.sourceOAuthProviders}
        />
      )
    }
  ]

  const adminWorkspaceSections = [
    {
      id: 'adminQuickSetup',
      render: () => adminSetupGuideState.allStepsComplete ? null : (
        <SetupGuidePanel
          collapsed={uiPreferences.adminQuickSetupCollapsed}
          dismissable={false}
          onDismiss={() => {}}
          onFocusSection={focusSection}
          sectionLoading={isSectionRefreshing('adminQuickSetupCollapsed')}
          onToggleCollapse={() => toggleSection('adminQuickSetupCollapsed')}
          savingLayout={isPending('uiPreferences')}
          steps={adminSetupGuideState.steps}
          t={t}
        />
      )
    },
    {
      id: 'systemDashboard',
      render: () => (
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
      )
    },
    {
      id: 'oauthApps',
      render: () => (
        <OAuthAppsSection
          collapsed={uiPreferences.oauthAppsCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          oauthSettings={systemOAuthSettings}
          onCollapseToggle={() => toggleSection('oauthAppsCollapsed')}
          onEditGoogle={() => {
            setSystemOAuthSettingsDirty(false)
            setSystemOAuthEditorProvider('google')
            setShowSystemOAuthAppsDialog(true)
          }}
          onEditMicrosoft={() => {
            setSystemOAuthSettingsDirty(false)
            setSystemOAuthEditorProvider('microsoft')
            setShowSystemOAuthAppsDialog(true)
          }}
          sectionLoading={isSectionRefreshing('oauthAppsCollapsed')}
          t={t}
        />
      )
    },
    {
      id: 'globalStats',
      render: () => (
        <Suspense fallback={<div className="muted-box">{t('common.refreshingSection')}</div>}>
          <PollingStatisticsSection
            collapsed={uiPreferences.globalStatsCollapsed}
            collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
            copy={t('pollingStats.globalCopy')}
            customRangeLoader={loadGlobalCustomRange}
            id="global-polling-stats-section"
            onCollapseToggle={() => toggleSection('globalStatsCollapsed')}
            sectionLoading={isSectionRefreshing('systemDashboardCollapsed')}
            stats={systemDashboard?.stats || null}
            t={t}
            title={t('pollingStats.globalTitle')}
          />
        </Suspense>
      )
    },
    {
      id: 'userManagement',
      render: () => (
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
          onDeleteUser={requestDeleteUser}
          onLoadUserCustomRange={loadAdminUserCustomRange}
          onResetUserPasskeys={resetUserPasskeys}
          onToggleExpandUser={toggleExpandedUser}
          onToggleUserActive={requestToggleUserActive}
          onUpdateUser={updateUser}
          selectedUserConfig={selectedUserConfig}
          selectedUserLoading={selectedUserLoading}
          session={session}
          multiUserEnabled={authOptions.multiUserEnabled}
          modeToggleLoading={isPending('multiUserModeSave')}
          onToggleMultiUserEnabled={requestToggleMultiUserMode}
          updatingPasskeysResetUserId={selectedUserConfig && isPending(`resetPasskeys:${selectedUserConfig.user.id}`) ? selectedUserConfig.user.id : null}
          updatingUserId={selectedUserConfig && (
            isPending(`updateUser:${selectedUserConfig.user.id}`)
            || isPending(`deleteUser:${selectedUserConfig.user.id}`)
          ) ? selectedUserConfig.user.id : null}
          users={users}
          sectionLoading={isSectionRefreshing('userManagementCollapsed')}
          t={t}
          locale={language}
        />
      )
    }
  ]

  const orderedUserWorkspaceSections = applyOrderedSectionIds(
    userWorkspaceSections.map((section) => section.id),
    uiPreferences.userSectionOrder
  )
  const orderedAdminWorkspaceSections = applyOrderedSectionIds(
    adminWorkspaceSections.map((section) => section.id),
    uiPreferences.adminSectionOrder
  )

  function renderWorkspaceSections(workspaceKey, sections, orderedIds) {
    function renderDropPlaceholder(key) {
      return (
        <div className="workspace-section-drop-placeholder" key={key}>
          <div className="workspace-section-drop-placeholder-inner" />
        </div>
      )
    }

    const visibleSections = orderedIds
      .map((sectionId) => sections.find((entry) => entry.id === sectionId))
      .filter(Boolean)
      .map((section) => ({ section, content: section.render() }))
      .filter((entry) => Boolean(entry.content))
    const renderedSections = []
    visibleSections.forEach(({ section, content }, index) => {
      if (dragState?.workspaceKey === workspaceKey && dragState.targetIndex === index) {
        renderedSections.push(renderDropPlaceholder(`${workspaceKey}-${section.id}-placeholder-before`))
      }
      renderedSections.push(
        <WorkspaceSectionWindow
          canMoveDown={index < orderedIds.length - 1}
          canMoveUp={index > 0}
          dragHandleLabel={t('preferences.dragSection')}
          dragging={dragState?.workspaceKey === workspaceKey && dragState.draggedId === section.id}
          key={section.id}
          layoutEditing={uiPreferences.layoutEditEnabled}
          moveDownLabel={t('preferences.moveSectionDown')}
          moveUpLabel={t('preferences.moveSectionUp')}
          onDragHandlePointerDown={(event) => {
            if (!uiPreferences.layoutEditEnabled) {
              return
            }
            event.preventDefault()
            setDragState({
              workspaceKey,
              draggedId: section.id,
              targetIndex: index
            })
          }}
          onPointerMove={(event) => {
            if (!dragState || dragState.workspaceKey !== workspaceKey) {
              return
            }
            const bounds = event.currentTarget.getBoundingClientRect()
            const nextTargetIndex = event.clientY < (bounds.top + bounds.height / 2) ? index : index + 1
            setDragState((current) => current && current.workspaceKey === workspaceKey
              ? { ...current, targetIndex: nextTargetIndex }
              : current)
          }}
          onMoveDown={() => moveSection(workspaceKey, section.id, 'down')}
          onMoveUp={() => moveSection(workspaceKey, section.id, 'up')}
        >
          {content}
        </WorkspaceSectionWindow>
      )
    })
    if (dragState?.workspaceKey === workspaceKey && dragState.targetIndex === visibleSections.length) {
      renderedSections.push(renderDropPlaceholder(`${workspaceKey}-placeholder-end`))
    }
    return renderedSections
  }

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
          layoutEditing={uiPreferences.layoutEditEnabled}
          language={language}
          loadingData={loadingData}
          onExitLayoutEditing={() => handleLayoutEditChange(false)}
          onOpenNotifications={() => setShowNotificationsDialog(true)}
          onOpenPreferences={() => setShowPreferencesDialog(true)}
          onOpenSecurityDialog={() => {
            setSecurityTab('password')
            setShowSecurityPanel(true)
          }}
          onRefresh={handleRefresh}
          onSignOut={handleLogout}
          notificationCount={notificationHistory.length}
          refreshLoading={isPending('refresh')}
          session={session}
          signOutLoading={isPending('logout')}
          t={t}
        />

        {showPreferencesDialog ? (
          <PreferencesDialog
            canHideQuickSetup={userSetupGuideState.allStepsComplete}
            layoutEditEnabled={uiPreferences.layoutEditEnabled}
            language={language}
            languageOptions={selectableLanguages}
            onClose={() => setShowPreferencesDialog(false)}
            onExitLayoutEditing={() => handleLayoutEditChange(false)}
            onLanguageChange={handleLanguageChange}
            onPersistLayoutChange={handlePersistLayoutChange}
            onQuickSetupVisibilityChange={handleQuickSetupVisibilityChange}
            onResetLayout={resetLayoutPreferences}
            onStartLayoutEditing={startLayoutEditingFromPreferences}
            persistLayout={uiPreferences.persistLayout}
            quickSetupVisible={uiPreferences.quickSetupPinnedVisible || !userSetupGuideState.allStepsComplete}
            savingLayout={isPending('uiPreferences')}
            t={t}
          />
        ) : null}

        {showNotificationsDialog ? (
          <NotificationsDialog
            notifications={notificationHistory}
            onClearAll={clearAllNotifications}
            onClose={() => setShowNotificationsDialog(false)}
            onDismissNotification={dismissNotification}
            onFocusNotification={focusNotificationTarget}
            notificationTitle={notificationTimestamp}
            t={t}
          />
        ) : null}

        {showSystemOAuthAppsDialog ? (
          <SystemOAuthAppsDialog
            isDirty={systemOAuthSettingsDirty}
            oauthSettings={systemOAuthSettings}
            oauthSettingsLoading={isPending('systemOAuthSettingsSave')}
            onClose={() => {
              setSystemOAuthSettingsDirty(false)
              setShowSystemOAuthAppsDialog(false)
            }}
            onOauthSettingsChange={(updater) => {
              setSystemOAuthSettingsDirty(true)
              setSystemOAuthSettings((current) => typeof updater === 'function' ? updater(current) : updater)
            }}
            onSave={saveSystemOAuthSettings}
            provider={systemOAuthEditorProvider}
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

        <div className="notification-stack" aria-live="polite">
          {[...persistentNotifications, ...visibleNotifications].map((notification) => (
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
              title={notificationTimestamp(notification)}
            >
              {notification.message}
            </Banner>
          ))}
        </div>

        {isAdmin ? (
          <div aria-label={t('workspace.title')} className="workspace-switcher surface-card" role="tablist">
            <button
              aria-selected={adminWorkspace === 'user'}
              className={`workspace-switcher-button ${adminWorkspace === 'user' ? 'workspace-switcher-button-active' : ''}`.trim()}
              onClick={() => setAdminWorkspace('user')}
              role="tab"
              type="button"
            >
              {t('workspace.user')}
            </button>
            <button
              aria-selected={adminWorkspace === 'admin'}
              className={`workspace-switcher-button ${adminWorkspace === 'admin' ? 'workspace-switcher-button-active' : ''}`.trim()}
              onClick={() => setAdminWorkspace('admin')}
              role="tab"
              type="button"
            >
              {t('workspace.admin')}
            </button>
          </div>
        ) : null}

        {!isAdmin || adminWorkspace === 'user'
          ? renderWorkspaceSections('user', userWorkspaceSections, orderedUserWorkspaceSections).filter(Boolean)
          : null}

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

        {isAdmin && adminWorkspace === 'admin'
          ? renderWorkspaceSections('admin', adminWorkspaceSections, orderedAdminWorkspaceSections).filter(Boolean)
          : null}

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
