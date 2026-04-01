import { useEffect, useMemo, useRef, useState } from 'react'
import { BrowserRouter, useLocation, useNavigate } from 'react-router-dom'
import AuthScreen from './components/auth/AuthScreen'
import Banner from './components/common/Banner'
import ConfirmationDialog from './components/common/ConfirmationDialog'
import DeviceLocationPrompt from './components/common/DeviceLocationPrompt'
import LoadingScreen from './components/common/LoadingScreen'
import ModalDialog from './components/common/ModalDialog'
import PasswordPanel from './components/account/PasswordPanel'
import PasskeyPanel from './components/account/PasskeyPanel'
import SessionsPanel from './components/account/SessionsPanel'
import PasskeyRegistrationDialog from './components/account/PasskeyRegistrationDialog'
import AuthSecuritySettingsDialog from './components/admin/AuthSecuritySettingsDialog'
import PasswordResetDialog from './components/admin/PasswordResetDialog'
import HeroPanel from './components/layout/HeroPanel'
import NotificationsDialog from './components/layout/NotificationsDialog'
import PreferencesDialog from './components/layout/PreferencesDialog'
import SystemOAuthAppsDialog from './components/admin/SystemOAuthAppsDialog'
import SystemPollingSettingsDialog from './components/admin/SystemPollingSettingsDialog'
import UserPollingSettingsDialog from './components/polling/UserPollingSettingsDialog'
import WorkspaceSectionList from './components/layout/WorkspaceSectionList'
import { buildAdminWorkspaceSections, buildUserWorkspaceSections } from './components/layout/workspaceSectionBuilders'
import { apiErrorText, installSecureApiFetch } from './lib/api'
import { requestSessionDeviceLocation } from './lib/api'
import { buildSetupGuideState } from './lib/setupGuide'
import { normalizeDestinationProviderConfig } from './lib/emailProviderPresets'
import { pollErrorNotification, resolveNotificationContent, translatedNotification } from './lib/notifications'
import { normalizeNotificationHistory, notificationHistoriesEqual } from './lib/notificationHistory'
import { isRecentSessionTargetId, isSourceEmailAccountTargetId } from './lib/sectionTargets'
import { normalizeLocale, translate } from './lib/i18n'
import { enforceHttpsIfNeeded } from './lib/httpsRedirect'
import { useAuthSecurityController } from './lib/useAuthSecurityController'
import { useDestinationController } from './lib/useDestinationController'
import { useEmailAccountsController } from './lib/useEmailAccountsController'
import { usePollingControllers } from './lib/usePollingControllers'
import { useUserManagementController } from './lib/useUserManagementController'
import { useWorkspacePreferencesController } from './lib/useWorkspacePreferencesController'
import { applyOrderedSectionIds } from './lib/workspacePreferences'
import { computeWorkspaceDropTargetIndex } from './lib/workspaceDrag'
import { buildWorkspacePath, canonicalWorkspacePath, resolveWorkspaceRoute } from './lib/workspaceRoutes'
import { useSessionDeviceLocation } from './lib/useSessionDeviceLocation'

const REFRESH_MS = 30000
const DEFAULT_AUTH_SECURITY_FORM = {
  loginFailureThresholdOverride: '',
  loginInitialBlockOverride: '',
  loginMaxBlockOverride: '',
  registrationChallengeMode: 'DEFAULT',
  registrationChallengeTtlOverride: '',
  registrationChallengeProviderOverride: '',
  registrationTurnstileSiteKeyOverride: '',
  registrationTurnstileSecret: '',
  registrationHcaptchaSiteKeyOverride: '',
  registrationHcaptchaSecret: '',
  geoIpMode: 'DEFAULT',
  geoIpPrimaryProviderOverride: '',
  geoIpFallbackProvidersOverride: '',
  geoIpCacheTtlOverride: '',
  geoIpProviderCooldownOverride: '',
  geoIpRequestTimeoutOverride: '',
  geoIpIpinfoToken: ''
}

const DEFAULT_DESTINATION_CONFIG = {
  provider: 'GMAIL_API',
  destinationUser: 'me',
  clientId: '',
  clientSecret: '',
  refreshToken: '',
  redirectUri: '',
  createMissingLabels: true,
  neverMarkSpam: false,
  processForCalendar: false,
  host: '',
  port: 993,
  tls: true,
  authMethod: 'PASSWORD',
  oauthProvider: 'NONE',
  username: '',
  password: '',
  folder: 'INBOX'
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
const DEFAULT_AUTH_SECURITY_SETTINGS = {
  defaultLoginFailureThreshold: 5,
  loginFailureThresholdOverride: null,
  effectiveLoginFailureThreshold: 5,
  defaultLoginInitialBlock: 'PT5M',
  loginInitialBlockOverride: null,
  effectiveLoginInitialBlock: 'PT5M',
  defaultLoginMaxBlock: 'PT1H',
  loginMaxBlockOverride: null,
  effectiveLoginMaxBlock: 'PT1H',
  defaultRegistrationChallengeEnabled: true,
  registrationChallengeEnabledOverride: null,
  effectiveRegistrationChallengeEnabled: true,
  defaultRegistrationChallengeTtl: 'PT10M',
  registrationChallengeTtlOverride: null,
  effectiveRegistrationChallengeTtl: 'PT10M',
  defaultRegistrationChallengeProvider: 'ALTCHA',
  registrationChallengeProviderOverride: null,
  effectiveRegistrationChallengeProvider: 'ALTCHA',
  availableRegistrationCaptchaProviders: 'ALTCHA, TURNSTILE, HCAPTCHA',
  defaultRegistrationTurnstileSiteKey: '',
  registrationTurnstileSiteKeyOverride: null,
  registrationTurnstileConfigured: false,
  defaultRegistrationHcaptchaSiteKey: '',
  registrationHcaptchaSiteKeyOverride: null,
  registrationHcaptchaConfigured: false,
  defaultGeoIpEnabled: false,
  geoIpEnabledOverride: null,
  effectiveGeoIpEnabled: false,
  defaultGeoIpPrimaryProvider: 'IPWHOIS',
  geoIpPrimaryProviderOverride: null,
  effectiveGeoIpPrimaryProvider: 'IPWHOIS',
  defaultGeoIpFallbackProviders: 'IPAPI_CO,IP_API,IPINFO_LITE',
  geoIpFallbackProvidersOverride: null,
  effectiveGeoIpFallbackProviders: 'IPAPI_CO,IP_API,IPINFO_LITE',
  defaultGeoIpCacheTtl: 'PT720H',
  geoIpCacheTtlOverride: null,
  effectiveGeoIpCacheTtl: 'PT720H',
  defaultGeoIpProviderCooldown: 'PT5M',
  geoIpProviderCooldownOverride: null,
  effectiveGeoIpProviderCooldown: 'PT5M',
  defaultGeoIpRequestTimeout: 'PT3S',
  geoIpRequestTimeoutOverride: null,
  effectiveGeoIpRequestTimeout: 'PT3S',
  availableGeoIpProviders: 'IPWHOIS, IPAPI_CO, IP_API, IPINFO_LITE',
  geoIpIpinfoTokenConfigured: false,
  secureStorageConfigured: true
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
const DEFAULT_AUTH_OPTIONS = {
  multiUserEnabled: true,
  bootstrapLoginPrefillEnabled: false,
  microsoftOAuthAvailable: true,
  googleOAuthAvailable: true,
  registrationChallengeEnabled: true,
  registrationChallengeProvider: 'ALTCHA',
  sourceOAuthProviders: ['MICROSOFT', 'GOOGLE']
}
const SECTION_HIGHLIGHT_MS = 2600
const NOTIFICATION_AUTO_CLOSE_MS = {
  success: 8000,
  warning: 12000,
  error: 16000
}

function authSecurityFormFromSettings(settings) {
  return {
    ...DEFAULT_AUTH_SECURITY_FORM,
    loginFailureThresholdOverride: settings.loginFailureThresholdOverride ?? '',
    loginInitialBlockOverride: settings.loginInitialBlockOverride ?? '',
    loginMaxBlockOverride: settings.loginMaxBlockOverride ?? '',
    registrationChallengeMode: settings.registrationChallengeEnabledOverride == null
      ? 'DEFAULT'
      : (settings.registrationChallengeEnabledOverride ? 'ENABLED' : 'DISABLED'),
    registrationChallengeTtlOverride: settings.registrationChallengeTtlOverride ?? '',
    registrationChallengeProviderOverride: settings.registrationChallengeProviderOverride ?? '',
    registrationTurnstileSiteKeyOverride: settings.registrationTurnstileSiteKeyOverride ?? '',
    registrationTurnstileSecret: '',
    registrationHcaptchaSiteKeyOverride: settings.registrationHcaptchaSiteKeyOverride ?? '',
    registrationHcaptchaSecret: '',
    geoIpMode: settings.geoIpEnabledOverride == null
      ? 'DEFAULT'
      : (settings.geoIpEnabledOverride ? 'ENABLED' : 'DISABLED'),
    geoIpPrimaryProviderOverride: settings.geoIpPrimaryProviderOverride ?? '',
    geoIpFallbackProvidersOverride: settings.geoIpFallbackProvidersOverride ?? '',
    geoIpCacheTtlOverride: settings.geoIpCacheTtlOverride ?? '',
    geoIpProviderCooldownOverride: settings.geoIpProviderCooldownOverride ?? '',
    geoIpRequestTimeoutOverride: settings.geoIpRequestTimeoutOverride ?? '',
    geoIpIpinfoToken: ''
  }
}
/**
 * Coordinates admin-ui data fetching and browser interactions while delegating
 * UI structure to smaller reusable components.
 */
function AppContent() {
  const location = useLocation()
  const navigate = useNavigate()
  const [authOptions, setAuthOptions] = useState(DEFAULT_AUTH_OPTIONS)
  const [notifications, setNotifications] = useState([])
  const [loadingData, setLoadingData] = useState(false)
  const [sectionRefreshLoading, setSectionRefreshLoading] = useState({})
  const [pendingActions, setPendingActions] = useState({})
  const [activeBatchPollSourceIds, setActiveBatchPollSourceIds] = useState([])

  const [destinationConfig, setDestinationConfig] = useState(DEFAULT_DESTINATION_CONFIG)
  const [destinationMeta, setDestinationMeta] = useState(null)
  const [destinationFolders, setDestinationFolders] = useState([])
  const [destinationFoldersLoading, setDestinationFoldersLoading] = useState(false)
  const [userPollingStats, setUserPollingStats] = useState(DEFAULT_USER_POLLING_STATS)

  const [systemDashboard, setSystemDashboard] = useState(null)
  const [confirmationDialog, setConfirmationDialog] = useState(null)
  const [systemOAuthSettings, setSystemOAuthSettings] = useState(DEFAULT_SYSTEM_OAUTH_SETTINGS)
  const [systemOAuthSettingsDirty, setSystemOAuthSettingsDirty] = useState(false)
  const [systemOAuthEditorProvider, setSystemOAuthEditorProvider] = useState('google')
  const [authSecuritySettings, setAuthSecuritySettings] = useState(DEFAULT_AUTH_SECURITY_SETTINGS)
  const [authSecuritySettingsForm, setAuthSecuritySettingsForm] = useState(DEFAULT_AUTH_SECURITY_FORM)
  const [authSecuritySettingsDirty, setAuthSecuritySettingsDirty] = useState(false)
  const [dismissedPersistentNotifications, setDismissedPersistentNotifications] = useState({})
  const [language, setLanguage] = useState(() => normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language))
  const [showSystemOAuthAppsDialog, setShowSystemOAuthAppsDialog] = useState(false)
  const [showAuthSecurityDialog, setShowAuthSecurityDialog] = useState(false)
  const notificationTimersRef = useRef(new Map())
  const selectedUserLoaderRef = useRef(null)
  const sessionActivityPollerRef = useRef(null)
  const liveEventsRef = useRef(null)
  const livePollApplyRef = useRef(null)
  const pushNotificationRef = useRef(null)
  const [liveEventsConnected, setLiveEventsConnected] = useState(false)
  const t = useMemo(() => (key, params) => translate(language, key, params), [language])
  const notificationTimestampFormatter = useMemo(
    () => new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeStyle: 'medium' }),
    [language]
  )
  const errorText = (key) => t(`errors.${key}`)
  const auth = useAuthSecurityController({
    bootstrapLoginPrefillEnabled: authOptions.bootstrapLoginPrefillEnabled,
    closeConfirmation: () => setConfirmationDialog(null),
    errorText,
    loadAppData,
    onLogoutReset: async () => {
      setSystemDashboard(null)
      setAuthSecuritySettings(DEFAULT_AUTH_SECURITY_SETTINGS)
      resetLayoutState()
      polling.resetPollingControllers()
      userManagement.resetUserManagementState()
      setNotifications([])
      setConfirmationDialog(null)
    },
    openConfirmation,
    pushNotification,
    t,
    withPending
  })
  const session = auth.session
  const deviceLocation = useSessionDeviceLocation({
    captureLocation: async (payload) => {
      await requestSessionDeviceLocation(payload)
      auth.setSession((current) => current ? { ...current, deviceLocationCaptured: true } : current)
    },
    session,
    storageScope: 'browser',
    t
  })
  const isAdmin = session?.role === 'ADMIN'
  const workspaceRoute = useMemo(() => resolveWorkspaceRoute(location.pathname), [location.pathname])
  const adminWorkspace = isAdmin && workspaceRoute.workspace === 'admin' ? 'admin' : 'user'
  const layout = useWorkspacePreferencesController({
    language,
    pushNotification,
    session,
    setLanguage,
    withPending
  })
  const {
    applyLoadedUiPreferences,
    closeNotificationsDialog,
    closePreferencesDialog,
    commitLayoutEditingChanges,
    discardLayoutEditingChanges,
    dragState,
    expandSection,
    handleLanguageChange,
    handleLayoutEditChange,
    handlePersistLayoutChange,
    handleQuickSetupVisibilityChange,
    hasUnsavedLayoutEdits,
    moveSection,
    openNotificationsDialog,
    openPreferencesDialog,
    reorderSections,
    resetLayoutPreferences,
    resetLayoutState,
    selectableLanguages,
    setDragState,
    showNotificationsDialog,
    showPreferencesDialog,
    startLayoutEditingFromPreferences,
    toggleSection,
    uiPreferencesLoadedForUserId,
    uiPreferences,
    replaceNotificationHistory
  } = layout

  function setAdminWorkspace(nextWorkspace, options = {}) {
    const nextPath = buildWorkspacePath(language, nextWorkspace)
    if (location.pathname === nextPath) {
      return
    }
    navigate(nextPath, { replace: Boolean(options.replace) })
  }

  const apiErrorMessage = async (key, response) => apiErrorText(response, errorText(key))
  const polling = usePollingControllers({
    authOptions,
    closeConfirmation: () => setConfirmationDialog(null),
    errorText,
    isAdmin,
    language,
    loadAppData,
    openConfirmation,
    pushNotification,
    t,
    withPending
  })
  const emailAccounts = useEmailAccountsController({
    activeBatchPollSourceIds,
    authOptions,
    errorText,
    isPending,
    language,
    loadAppData,
    pushNotification,
    refreshSectionData,
    sessionUsername: session?.username,
    systemDashboardEmailAccounts: systemDashboard?.emailAccounts || systemDashboard?.bridges,
    t,
    withPending
  })
  const destination = useDestinationController({
    closeConfirmation: () => setConfirmationDialog(null),
    destinationConfig,
    destinationMeta,
    errorText: apiErrorMessage,
    language,
    loadAppData,
    openConfirmation,
    pushNotification,
    t,
    withPending
  })
  const userManagement = useUserManagementController({
    authOptions,
    closeConfirmation: () => setConfirmationDialog(null),
    errorText,
    loadAppData,
    loadAuthOptions,
    openConfirmation,
    pushNotification,
    session,
    t,
    withPending
  })

  useEffect(() => {
    livePollApplyRef.current = polling.applyLivePoll
    pushNotificationRef.current = pushNotification
  }, [polling.applyLivePoll, pushNotification])

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

  function openConfirmation(dialog) {
    setConfirmationDialog(dialog)
  }

  async function handleDiscardLayoutChanges() {
    if (!hasUnsavedLayoutEdits) {
      await discardLayoutEditingChanges()
      return
    }
    openConfirmation({
      actionKey: 'discardLayoutChanges',
      body: t('confirm.discardLayoutChangesBody'),
      cancelLabel: t('common.no'),
      confirmLabel: t('hero.discardLayoutChanges'),
      confirmLoadingLabel: t('hero.discardLayoutChangesLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending('discardLayoutChanges', async () => {
          await discardLayoutEditingChanges()
          setConfirmationDialog(null)
        })
      },
      title: t('confirm.discardLayoutChanges')
    })
  }

  function dismissNotification(id) {
    const timer = notificationTimersRef.current.get(id)
    if (timer) {
      window.clearTimeout(timer)
      notificationTimersRef.current.delete(id)
    }
    setNotifications((current) => normalizeNotificationHistory(
      current.filter((notification) => notification.id !== id)
    ))
  }

  function hideFloatingNotification(id) {
    const timer = notificationTimersRef.current.get(id)
    if (timer) {
      window.clearTimeout(timer)
      notificationTimersRef.current.delete(id)
    }
    setNotifications((current) => normalizeNotificationHistory(
      current.map((notification) => (
        notification.id === id
          ? { ...notification, floatingVisible: false }
          : notification
      ))
    ))
  }

  function clearAllNotifications() {
    notificationTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    notificationTimersRef.current.clear()
    setNotifications([])
  }

  function pushNotification({
    autoCloseMs,
    copyText,
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
    setNotifications((current) => normalizeNotificationHistory([
      ...current.filter((notification) => {
        if (replaceGroup && groupKey && notification.groupKey === groupKey) {
          return false
        }
        if (notification.groupKey && supersedesGroupKeys.includes(notification.groupKey)) {
          return false
        }
        return true
      }),
      {
        autoCloseMs: resolvedAutoCloseMs,
        copyText: copyText ?? message,
        createdAt: Date.now(),
        floatingVisible: true,
        groupKey,
        id,
        message,
        targetId,
        tone
      }
    ]))
  }

  function resolveNotificationText(notification) {
    return resolveNotificationContent(notification?.message, language)
  }

  function resolveNotificationCopyText(notification) {
    return resolveNotificationContent(notification?.copyText, language)
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

  async function loadAdminUserCustomRange(userId, range) {
    const search = new URLSearchParams({ from: range.from })
    if (range.to) search.set('to', range.to)
    return loadScopedTimelineBundle(`/api/admin/users/${userId}/polling-stats/range?${search.toString()}`, t('pollingStats.customRangeLoadError'))
  }

  async function fetchDestinationConfig() {
    try {
      const response = await fetch('/api/app/destination-config')
      if (response.status !== 404) {
        return response
      }
    } catch {
      // Fall back to the legacy endpoint for stale local mocks and old test fixtures.
    }
    return fetch('/api/app/gmail-config')
  }

  async function loadAppData(options = {}) {
    const { suppressErrors = false } = options
    if (!session) return
    setLoadingData(true)
    try {
      const requests = [
        fetchDestinationConfig(),
        fetch('/api/app/polling-settings'),
        fetch('/api/app/polling-stats'),
        fetch('/api/app/email-accounts'),
        fetch('/api/app/ui-preferences'),
        fetch('/api/account/passkeys')
      ]

      if (session.role === 'ADMIN') {
        requests.push(fetch('/api/admin/dashboard'))
        requests.push(fetch('/api/admin/oauth-app-settings'))
        requests.push(fetch('/api/admin/auth-security-settings'))
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
      const [destinationPayload, userPollingPayload, userPollingStatsPayload, emailAccountsPayload, uiPreferencesPayload, passkeysPayload, adminPayload, oauthSettingsPayload, authSecurityPayload, usersPayload] = payloads
      const normalizedAdminPayload = adminPayload
        ? {
          ...adminPayload,
          emailAccounts: adminPayload.emailAccounts || adminPayload.bridges || []
        }
        : null

      setDestinationMeta({
        ...destinationPayload,
        configured: destinationPayload.configured ?? false,
        provider: destinationPayload.provider || 'GMAIL_API',
        defaultRedirectUri: destinationPayload.googleRedirectUri || destinationPayload.defaultRedirectUri || `${window.location.origin}/api/google-oauth/callback`,
        linked: destinationPayload.linked ?? destinationPayload.refreshTokenConfigured ?? false,
        refreshTokenConfigured: destinationPayload.linked ?? destinationPayload.refreshTokenConfigured ?? false,
        sharedGoogleClientConfigured: destinationPayload.sharedGoogleClientConfigured ?? destinationPayload.sharedClientConfigured ?? false,
        sharedClientConfigured: destinationPayload.sharedGoogleClientConfigured ?? destinationPayload.sharedClientConfigured ?? false,
        oauthConnected: destinationPayload.oauthConnected ?? destinationPayload.refreshTokenConfigured ?? false,
        passwordConfigured: destinationPayload.passwordConfigured ?? false
      })
      setDestinationConfig(normalizeDestinationProviderConfig({
        ...DEFAULT_DESTINATION_CONFIG,
        provider: destinationPayload.provider || 'GMAIL_API',
        destinationUser: destinationPayload.destinationUser || 'me',
        redirectUri: destinationPayload.googleRedirectUri || `${window.location.origin}/api/google-oauth/callback`,
        host: destinationPayload.host || '',
        port: destinationPayload.port || 993,
        tls: destinationPayload.tls ?? true,
        authMethod: destinationPayload.authMethod || 'PASSWORD',
        oauthProvider: destinationPayload.oauthProvider || 'NONE',
        username: destinationPayload.username || '',
        folder: destinationPayload.folder || 'INBOX'
      }))
      setUserPollingStats({
        ...DEFAULT_USER_POLLING_STATS,
        ...(userPollingStatsPayload || {})
      })
      polling.applyLoadedUserPolling(userPollingPayload)
      emailAccounts.applyLoadedEmailAccounts(emailAccountsPayload, normalizedAdminPayload?.emailAccounts || [])
      auth.applyLoadedPasskeys(passkeysPayload)
      const shouldHydrateNotificationHistory = uiPreferencesLoadedForUserId !== session.id
      applyLoadedUiPreferences(uiPreferencesPayload, session.id)
      if (shouldHydrateNotificationHistory) {
        setNotifications(normalizeNotificationHistory(uiPreferencesPayload?.notificationHistory))
      }

      if (normalizedAdminPayload) {
        setSystemDashboard(normalizedAdminPayload)
        polling.applyLoadedSystemPolling(normalizedAdminPayload.polling)
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
        if (authSecurityPayload) {
          const normalizedAuthSecurity = {
            ...DEFAULT_AUTH_SECURITY_SETTINGS,
            ...authSecurityPayload
          }
          setAuthSecuritySettings(normalizedAuthSecurity)
          setAuthSecuritySettingsForm(authSecurityFormFromSettings(normalizedAuthSecurity))
          setAuthSecuritySettingsDirty(false)
        }
      } else {
        setSystemDashboard(null)
        setSystemOAuthSettings(DEFAULT_SYSTEM_OAUTH_SETTINGS)
        setAuthSecuritySettings(DEFAULT_AUTH_SECURITY_SETTINGS)
      }
      if (usersPayload) {
        userManagement.applyLoadedUsers(usersPayload)
      } else {
        userManagement.applyLoadedUsers(null)
      }
    } catch (err) {
      if (!suppressErrors) {
        const errorMessage = err.message || errorText('loadApplicationData')
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadApplicationData'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadApplicationData'),
          tone: 'error'
        })
      }
    } finally {
      setLoadingData(false)
    }
  }

  async function refreshRuntimeState({ announceNewSessions = false, suppressSessionErrors = false } = {}) {
    const pendingLoads = [loadAppData()]
    if (sessionActivityPollerRef.current) {
      pendingLoads.push(
        sessionActivityPollerRef.current({
          announceNewSessions,
          suppressErrors: suppressSessionErrors
        })
      )
    }
    await Promise.all(pendingLoads)
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
    installSecureApiFetch()
    enforceHttpsIfNeeded(window.location)
    loadAuthOptions()
    auth.loadSession()
  }, [])

  useEffect(() => {
    sessionActivityPollerRef.current = auth.pollSessionActivity
  }, [auth.pollSessionActivity])

  useEffect(() => {
    if (!session) return
    void refreshRuntimeState({ announceNewSessions: true, suppressSessionErrors: true })
    void polling.runLivePollSnapshotLoad()
    const timer = window.setInterval(() => {
      void refreshRuntimeState({ announceNewSessions: true, suppressSessionErrors: true })
    }, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [authOptions.multiUserEnabled, isAdmin, session])

  useEffect(() => {
    if (!session || (!polling.runningPoll && !polling.runningUserPoll)) {
      setActiveBatchPollSourceIds([])
      return
    }
    if (liveEventsConnected) {
      return
    }

    let cancelled = false

    async function refreshPollStatus() {
      try {
        const response = await fetch(isAdmin ? '/api/admin/poll/live' : '/api/poll/live')
        if (!response.ok) {
          return
        }
        const payload = await response.json()
        if (!cancelled) {
          setActiveBatchPollSourceIds(payload?.running
            ? (payload.sources || []).filter((source) => source.state === 'RUNNING').map((source) => source.sourceId)
            : [])
        }
      } catch {
        if (!cancelled) {
          setActiveBatchPollSourceIds([])
        }
      }
    }

    void refreshPollStatus()
    const timer = window.setInterval(() => {
      void refreshPollStatus()
    }, 1000)

    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [isAdmin, liveEventsConnected, polling.livePoll?.running, polling.runningPoll, polling.runningUserPoll, session])

  useEffect(() => {
    setActiveBatchPollSourceIds(polling.livePoll?.running
      ? (polling.livePoll.sources || []).filter((source) => source.state === 'RUNNING').map((source) => source.sourceId)
      : [])
  }, [polling.livePoll?.running, polling.livePoll?.sources])

  useEffect(() => {
    if (!session) {
      polling.applyLivePoll(null)
      setLiveEventsConnected(false)
      if (liveEventsRef.current) {
        liveEventsRef.current.close()
        liveEventsRef.current = null
      }
      return
    }
    if (typeof window.EventSource !== 'function') {
      setLiveEventsConnected(false)
      return
    }

    const endpoint = isAdmin ? '/api/admin/poll/events' : '/api/poll/events'
    const eventSource = new window.EventSource(endpoint)
    liveEventsRef.current = eventSource

    const handleLiveEvent = (event) => {
      try {
        const payload = JSON.parse(event.data)
        if (payload?.poll && livePollApplyRef.current) {
          livePollApplyRef.current(payload.poll)
        }
        if (payload?.notification && pushNotificationRef.current) {
          pushNotificationRef.current(payload.notification)
        }
        setLiveEventsConnected(true)
      } catch {
        setLiveEventsConnected(false)
      }
    }

    eventSource.onmessage = handleLiveEvent
    ;[
      'poll-snapshot',
      'poll-run-started',
      'poll-run-pausing',
      'poll-run-paused',
      'poll-run-resumed',
      'poll-run-stopping',
      'poll-run-finished',
      'poll-source-started',
      'poll-source-finished',
      'poll-source-reprioritized',
      'poll-source-retry-queued',
      'notification-created'
    ].forEach((eventName) => eventSource.addEventListener(eventName, handleLiveEvent))

    eventSource.onerror = () => {
      setLiveEventsConnected(false)
      eventSource.close()
      if (liveEventsRef.current === eventSource) {
        liveEventsRef.current = null
      }
    }

    return () => {
      eventSource.close()
      if (liveEventsRef.current === eventSource) {
        liveEventsRef.current = null
      }
    }
  }, [isAdmin, session])

  useEffect(() => {
    if (!session) {
      return
    }
    if (uiPreferencesLoadedForUserId !== session.id) {
      return
    }
    const nextPath = canonicalWorkspacePath(location.pathname, language, isAdmin)
    if (nextPath && nextPath !== location.pathname) {
      navigate(nextPath, { replace: true })
    }
  }, [isAdmin, language, location.pathname, navigate, session, uiPreferencesLoadedForUserId])

  useEffect(() => {
    let cancelled = false

    async function loadDestinationFolders() {
      if (!session?.id || !destinationMeta?.linked || !destinationMeta?.provider || destinationMeta.provider === 'GMAIL_API') {
        setDestinationFolders([])
        setDestinationFoldersLoading(false)
        return
      }

      setDestinationFoldersLoading(true)
      try {
        const response = await fetch('/api/app/destination-config/folders')
        if (!response.ok) {
          if (!cancelled) {
            setDestinationFolders([])
          }
          return
        }
        const payload = await response.json()
        if (!cancelled) {
          setDestinationFolders(Array.isArray(payload?.folders) ? payload.folders : [])
        }
      } catch {
        if (!cancelled) {
          setDestinationFolders([])
        }
      } finally {
        if (!cancelled) {
          setDestinationFoldersLoading(false)
        }
      }
    }

    loadDestinationFolders()
    return () => {
      cancelled = true
    }
  }, [destinationMeta?.linked, destinationMeta?.provider, session?.id])

  useEffect(() => {
    setDismissedPersistentNotifications({})
  }, [session?.id])

  useEffect(() => {
    selectedUserLoaderRef.current = userManagement.loadSelectedUserConfiguration
  }, [userManagement.loadSelectedUserConfiguration])

  useEffect(() => {
    if (userManagement.selectedUserId) {
      refreshSectionData('userManagementCollapsed', async () => {
        await loadAppData()
        if (selectedUserLoaderRef.current) {
          await selectedUserLoaderRef.current(userManagement.selectedUserId)
        }
      })
    }
  }, [authOptions.multiUserEnabled, session?.role, userManagement.selectedUserId])

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

  useEffect(() => {
    if (!session?.id || uiPreferencesLoadedForUserId !== session.id) {
      return
    }
    if (notificationHistoriesEqual(notifications, uiPreferences.notificationHistory)) {
      return
    }
    void replaceNotificationHistory(normalizeNotificationHistory(notifications))
  }, [notifications, replaceNotificationHistory, session?.id, uiPreferences.notificationHistory, uiPreferencesLoadedForUserId])

  useEffect(() => () => {
    notificationTimersRef.current.forEach((timer) => window.clearTimeout(timer))
    notificationTimersRef.current.clear()
  }, [])

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
        pushNotification({ message: translatedNotification('notifications.oauthAppsUpdated'), targetId: 'oauth-apps-section', tone: 'success' })
        await loadAuthOptions()
        await loadAppData()
      } catch (err) {
        pushNotification({
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveOAuthApps'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveOAuthApps'),
          targetId: 'oauth-apps-section',
          tone: 'error'
        })
      }
    })
  }

  async function saveAuthSecuritySettings(event) {
    event.preventDefault()
    await withPending('authSecuritySettingsSave', async () => {
      try {
        const response = await fetch('/api/admin/auth-security-settings', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            loginFailureThresholdOverride: authSecuritySettingsForm.loginFailureThresholdOverride === ''
              ? null
              : Number(authSecuritySettingsForm.loginFailureThresholdOverride),
            loginInitialBlockOverride: authSecuritySettingsForm.loginInitialBlockOverride || null,
            loginMaxBlockOverride: authSecuritySettingsForm.loginMaxBlockOverride || null,
            registrationChallengeEnabledOverride: authSecuritySettingsForm.registrationChallengeMode === 'DEFAULT'
              ? null
              : authSecuritySettingsForm.registrationChallengeMode === 'ENABLED',
            registrationChallengeTtlOverride: authSecuritySettingsForm.registrationChallengeTtlOverride || null,
            registrationChallengeProviderOverride: authSecuritySettingsForm.registrationChallengeProviderOverride || null,
            registrationTurnstileSiteKeyOverride: authSecuritySettingsForm.registrationTurnstileSiteKeyOverride || null,
            registrationTurnstileSecret: authSecuritySettingsForm.registrationTurnstileSecret || null,
            registrationHcaptchaSiteKeyOverride: authSecuritySettingsForm.registrationHcaptchaSiteKeyOverride || null,
            registrationHcaptchaSecret: authSecuritySettingsForm.registrationHcaptchaSecret || null,
            geoIpEnabledOverride: authSecuritySettingsForm.geoIpMode === 'DEFAULT'
              ? null
              : authSecuritySettingsForm.geoIpMode === 'ENABLED',
            geoIpPrimaryProviderOverride: authSecuritySettingsForm.geoIpPrimaryProviderOverride || null,
            geoIpFallbackProvidersOverride: authSecuritySettingsForm.geoIpFallbackProvidersOverride || null,
            geoIpCacheTtlOverride: authSecuritySettingsForm.geoIpCacheTtlOverride || null,
            geoIpProviderCooldownOverride: authSecuritySettingsForm.geoIpProviderCooldownOverride || null,
            geoIpRequestTimeoutOverride: authSecuritySettingsForm.geoIpRequestTimeoutOverride || null,
            geoIpIpinfoToken: authSecuritySettingsForm.geoIpIpinfoToken || null
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('saveAuthSecuritySettings')))
        }
        const payload = await response.json()
        const normalized = {
          ...DEFAULT_AUTH_SECURITY_SETTINGS,
          ...payload
        }
        setAuthSecuritySettings(normalized)
        setAuthSecuritySettingsForm(authSecurityFormFromSettings(normalized))
        setAuthSecuritySettingsDirty(false)
        setShowAuthSecurityDialog(false)
        pushNotification({ message: translatedNotification('notifications.authSecuritySettingsUpdated'), targetId: 'auth-security-section', tone: 'success' })
        await loadAuthOptions()
      } catch (err) {
        pushNotification({
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveAuthSecuritySettings'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveAuthSecuritySettings'),
          targetId: 'auth-security-section',
          tone: 'error'
        })
      }
    })
  }

  async function resetAuthSecuritySettings() {
    await withPending('authSecuritySettingsSave', async () => {
      try {
        const response = await fetch('/api/admin/auth-security-settings', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            loginFailureThresholdOverride: null,
            loginInitialBlockOverride: null,
            loginMaxBlockOverride: null,
            registrationChallengeEnabledOverride: null,
            registrationChallengeTtlOverride: null,
            registrationChallengeProviderOverride: null,
            registrationTurnstileSiteKeyOverride: null,
            registrationTurnstileSecret: '',
            registrationHcaptchaSiteKeyOverride: null,
            registrationHcaptchaSecret: '',
            geoIpEnabledOverride: null,
            geoIpPrimaryProviderOverride: null,
            geoIpFallbackProvidersOverride: null,
            geoIpCacheTtlOverride: null,
            geoIpProviderCooldownOverride: null,
            geoIpRequestTimeoutOverride: null,
            geoIpIpinfoToken: ''
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('saveAuthSecuritySettings')))
        }
        const payload = await response.json()
        const normalized = {
          ...DEFAULT_AUTH_SECURITY_SETTINGS,
          ...payload
        }
        setAuthSecuritySettings(normalized)
        setAuthSecuritySettingsForm(DEFAULT_AUTH_SECURITY_FORM)
        setAuthSecuritySettingsDirty(false)
        pushNotification({ message: translatedNotification('notifications.authSecuritySettingsReset'), targetId: 'auth-security-section', tone: 'success' })
        await loadAuthOptions()
      } catch (err) {
        pushNotification({
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveAuthSecuritySettings'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveAuthSecuritySettings'),
          targetId: 'auth-security-section',
          tone: 'error'
        })
      }
    })
  }

  async function handleRefresh() {
    await withPending('refresh', async () => {
      await refreshRuntimeState({ announceNewSessions: true, suppressSessionErrors: false })
    })
  }

  async function dismissQuickSetupGuide(workspaceKey) {
    const allStepsComplete = workspaceKey === 'admin'
      ? adminSetupGuideState.allStepsComplete
      : userSetupGuideState.allStepsComplete
    handleQuickSetupVisibilityChange(workspaceKey, false, allStepsComplete)
  }

  function focusSection(event, step) {
    event.preventDefault()
    focusTarget(step.targetId, step.sectionKey)
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
    const sourceEmailAccountTarget = isSourceEmailAccountTargetId(targetId)
    const recentSessionTarget = isRecentSessionTargetId(targetId)
    const derivedSectionKey = sectionKey || ({
      'destination-mailbox-section': 'destinationMailboxCollapsed',
      'user-polling-section': 'userPollingCollapsed',
      'user-polling-stats-section': 'userStatsCollapsed',
      'source-email-accounts-section': 'sourceEmailAccountsCollapsed',
      'system-dashboard-section': 'systemDashboardCollapsed',
      'oauth-apps-section': 'oauthAppsCollapsed',
      'auth-security-section': 'authSecurityCollapsed',
      'global-polling-stats-section': 'globalStatsCollapsed',
      'user-management-section': 'userManagementCollapsed'
    }[targetId] || (sourceEmailAccountTarget ? 'sourceEmailAccountsCollapsed' : null))
    if (derivedSectionKey && uiPreferences[derivedSectionKey]) {
      void expandSection(derivedSectionKey)
    }
    if (targetId === 'password-panel-section') {
      auth.openSecurityPanel('password')
    }
    if (targetId === 'passkey-panel-section') {
      auth.openSecurityPanel('passkeys')
    }
    if (targetId === 'security-sessions-panel-section' || recentSessionTarget) {
      auth.openSecurityPanel('sessions')
    }
    if (targetId === 'user-management-section' || targetId === 'system-dashboard-section' || targetId === 'oauth-apps-section' || targetId === 'auth-security-section' || targetId === 'global-polling-stats-section') {
      setAdminWorkspace('admin')
    }
    if (targetId === 'destination-mailbox-section' || targetId === 'user-polling-section' || targetId === 'user-polling-stats-section' || targetId === 'source-email-accounts-section' || sourceEmailAccountTarget) {
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
    closeNotificationsDialog()
    focusTarget(targetId)
  }

  const userSetupGuideState = buildSetupGuideState({
    destinationMeta,
    myEmailAccounts: emailAccounts.userEmailAccounts,
    session,
    systemDashboard,
    users: userManagement.users,
    workspace: 'user',
    systemOAuthSettings,
    t
  })

  const adminSetupGuideState = buildSetupGuideState({
    destinationMeta,
    myEmailAccounts: emailAccounts.userEmailAccounts,
    session,
    systemDashboard,
    users: userManagement.users,
    workspace: 'admin',
    systemOAuthSettings,
    t
  })

  useEffect(() => {
    if (!session) {
      return
    }
    if (!userSetupGuideState.allStepsComplete && uiPreferences.quickSetupDismissed) {
      handleQuickSetupVisibilityChange('user', true, userSetupGuideState.allStepsComplete)
      return
    }
    if (userSetupGuideState.allStepsComplete && !uiPreferences.quickSetupPinnedVisible && !uiPreferences.quickSetupDismissed) {
      handleQuickSetupVisibilityChange('user', false, userSetupGuideState.allStepsComplete)
    }
  }, [handleQuickSetupVisibilityChange, session, uiPreferences, userSetupGuideState.allStepsComplete])

  useEffect(() => {
    if (!session || !isAdmin) {
      return
    }
    if (!adminSetupGuideState.allStepsComplete && uiPreferences.adminQuickSetupDismissed) {
      handleQuickSetupVisibilityChange('admin', true, adminSetupGuideState.allStepsComplete)
      return
    }
    if (adminSetupGuideState.allStepsComplete && !uiPreferences.adminQuickSetupPinnedVisible && !uiPreferences.adminQuickSetupDismissed) {
      handleQuickSetupVisibilityChange('admin', false, adminSetupGuideState.allStepsComplete)
    }
  }, [adminSetupGuideState.allStepsComplete, handleQuickSetupVisibilityChange, isAdmin, session, uiPreferences])

  function toggleWorkspaceSection(sectionKey) {
    return toggleSection(sectionKey, async () => {
      await refreshSectionData(sectionKey, async () => {
        await loadAppData()
        if (sectionKey === 'userManagementCollapsed' && userManagement.selectedUserId) {
          await userManagement.loadSelectedUserConfiguration(userManagement.selectedUserId)
        }
      })
    })
  }

  useEffect(() => {
    if (!dragState) {
      return undefined
    }

    document.body.classList.add('workspace-layout-dragging')

    function resolveDropTargetIndex(current, clientY) {
      const workspaceList = document.querySelector(`[data-workspace-key="${current.workspaceKey}"]`)
      if (!workspaceList) {
        return current.targetIndex
      }
      const windows = Array.from(
        workspaceList.querySelectorAll('[data-workspace-section-window="true"]')
      )
      if (!windows.length) {
        return current.targetIndex
      }
      return computeWorkspaceDropTargetIndex(windows, clientY, current.targetIndex)
    }

    async function finishDrag(pointerEvent = null) {
      const current = dragState
      setDragState(null)
      await reorderSections(
        current.workspaceKey,
        current.draggedId,
        resolveDropTargetIndex(current, pointerEvent?.clientY),
        current.visibleSectionIds
      )
    }

    function handlePointerUp(event) {
      finishDrag(event)
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        document.body.classList.remove('workspace-layout-dragging')
        setDragState(null)
      }
    }

    window.addEventListener('pointerup', handlePointerUp)
    window.addEventListener('pointercancel', handlePointerUp)
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.classList.remove('workspace-layout-dragging')
      window.removeEventListener('pointerup', handlePointerUp)
      window.removeEventListener('pointercancel', handlePointerUp)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [dragState])

  const persistentNotifications = useMemo(() => {
    const items = []
    if (session?.mustChangePassword && !dismissedPersistentNotifications.mustChangePassword) {
      items.push({
        id: 'must-change-password',
        message: translatedNotification('notifications.mustChangePassword'),
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

  const resolvedNotificationHistory = useMemo(
    () => notificationHistory.map((notification) => ({
      ...notification,
      copyText: resolveNotificationCopyText(notification),
      message: resolveNotificationText(notification)
    })),
    [language, notificationHistory]
  )

  function notificationTimestamp(notification) {
    if (!notification?.createdAt) {
      return ''
    }
    return t('notifications.createdAt', { value: notificationTimestampFormatter.format(new Date(notification.createdAt)) })
  }

  const userWorkspaceSections = buildUserWorkspaceSections({
    authOptions,
    destination,
    destinationConfig,
    destinationFolders,
    destinationFoldersLoading,
    destinationMeta,
    dismissQuickSetupGuide,
    emailAccounts,
    focusSection,
    isPending,
    isSectionRefreshing,
    language,
    loadUserCustomRange,
    openConfirmation,
    polling,
    setDestinationConfig,
    t,
    toggleWorkspaceSection,
    uiPreferences,
    userPollingStats,
    userSetupGuideState
  })

  const adminWorkspaceSections = buildAdminWorkspaceSections({
    adminSetupGuideState,
    authSecuritySettings,
    authOptions,
    dismissQuickSetupGuide,
    focusSection,
    isPending,
    isSectionRefreshing,
    language,
    loadAdminUserCustomRange,
    loadGlobalCustomRange,
    polling,
    session,
    setShowAuthSecurityDialog,
    setShowSystemOAuthAppsDialog,
    setSystemOAuthEditorProvider,
    setSystemOAuthSettingsDirty,
    systemDashboard,
    systemOAuthSettings,
    t,
    toggleWorkspaceSection,
    uiPreferences,
    userManagement
  })

  const orderedUserWorkspaceSections = applyOrderedSectionIds(
    userWorkspaceSections.map((section) => section.id),
    uiPreferences.userSectionOrder
  )
  const orderedAdminWorkspaceSections = applyOrderedSectionIds(
    adminWorkspaceSections.map((section) => section.id),
    uiPreferences.adminSectionOrder
  )
  const activeWorkspaceKey = !isAdmin || adminWorkspace === 'user' ? 'user' : 'admin'
  const activeQuickSetupVisible = activeWorkspaceKey === 'admin'
    ? uiPreferences.adminQuickSetupPinnedVisible || !adminSetupGuideState.allStepsComplete
    : uiPreferences.quickSetupPinnedVisible || !userSetupGuideState.allStepsComplete
  const activeCanHideQuickSetup = activeWorkspaceKey === 'admin'
    ? adminSetupGuideState.allStepsComplete
    : userSetupGuideState.allStepsComplete

  if (auth.authLoading) {
    return <LoadingScreen label={t('app.loading')} />
  }

  if (!session) {
    return (
        <AuthScreen
          authError={auth.authError}
          language={language}
          languageOptions={selectableLanguages}
          loginStage={auth.loginStage}
          loginLoading={isPending('login')}
          loginForm={auth.loginForm}
          multiUserEnabled={authOptions.multiUserEnabled}
          notice=""
          onLanguageChange={handleLanguageChange}
          onCloseRegisterDialog={auth.closeRegisterDialog}
          onLogin={auth.handleLogin}
          onLoginChange={auth.setLoginForm}
          onPasskeyLogin={auth.handlePasskeyLogin}
          onOpenRegisterDialog={auth.openRegisterDialog}
          registerLoading={isPending('register')}
          registerOpen={auth.registerOpen}
          passkeyLoading={isPending('passkeyLogin')}
          passkeysSupported={auth.passkeysSupported}
          onRegister={auth.handleRegister}
          onRegisterChange={auth.setRegisterForm}
          registerChallenge={auth.registerChallenge}
          registerChallengeLoading={auth.registerChallengeLoading}
          registerForm={auth.registerForm}
          t={t}
      />
    )
  }

  return (
    <div className="page-shell">
      <main className="dashboard">
        <HeroPanel
          hasUnsavedLayoutChanges={hasUnsavedLayoutEdits}
          layoutEditing={uiPreferences.layoutEditEnabled}
          language={language}
          loadingData={loadingData}
          onDiscardLayoutChanges={handleDiscardLayoutChanges}
          onExitLayoutEditing={commitLayoutEditingChanges}
          onOpenNotifications={openNotificationsDialog}
          onOpenPreferences={openPreferencesDialog}
          onOpenSecurityDialog={() => auth.openSecurityPanel('password')}
          onRefresh={handleRefresh}
          onSignOut={auth.handleLogout}
          notificationCount={notificationHistory.length}
          refreshLoading={isPending('refresh')}
          session={session}
          signOutLoading={isPending('logout')}
          t={t}
        />
        {deviceLocation.shouldPrompt ? (
          <DeviceLocationPrompt
            error={deviceLocation.error}
            onDismiss={deviceLocation.dismissPrompt}
            onRequestLocation={deviceLocation.requestLocation}
            saving={deviceLocation.saving}
            success={deviceLocation.success}
            t={t}
          />
        ) : null}

        {showPreferencesDialog ? (
          <PreferencesDialog
            canHideQuickSetup={activeCanHideQuickSetup}
            layoutEditEnabled={uiPreferences.layoutEditEnabled}
            language={language}
            languageOptions={selectableLanguages}
            onClose={closePreferencesDialog}
            onExitLayoutEditing={commitLayoutEditingChanges}
            onLanguageChange={handleLanguageChange}
            onPersistLayoutChange={handlePersistLayoutChange}
            onQuickSetupVisibilityChange={(visible) => handleQuickSetupVisibilityChange(activeWorkspaceKey, visible, activeCanHideQuickSetup)}
            onResetLayout={resetLayoutPreferences}
            onStartLayoutEditing={startLayoutEditingFromPreferences}
            persistLayout={uiPreferences.persistLayout}
            quickSetupVisible={activeQuickSetupVisible}
            savingLayout={isPending('uiPreferences')}
            t={t}
          />
        ) : null}

        {showNotificationsDialog ? (
          <NotificationsDialog
            notifications={resolvedNotificationHistory}
            onClearAll={clearAllNotifications}
            onClose={closeNotificationsDialog}
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

        {showAuthSecurityDialog ? (
          <AuthSecuritySettingsDialog
            authSecuritySettings={authSecuritySettings}
            authSecuritySettingsForm={authSecuritySettingsForm}
            authSecuritySettingsLoading={isPending('authSecuritySettingsSave')}
            isDirty={authSecuritySettingsDirty}
            locale={language}
            onAuthSecurityFormChange={(updater) => {
              setAuthSecuritySettingsDirty(true)
              setAuthSecuritySettingsForm((current) => typeof updater === 'function' ? updater(current) : updater)
            }}
            onClose={() => {
              setAuthSecuritySettingsDirty(false)
              setShowAuthSecurityDialog(false)
            }}
            onResetAuthSecuritySettings={resetAuthSecuritySettings}
            onSaveAuthSecuritySettings={saveAuthSecuritySettings}
            t={t}
          />
        ) : null}

        {auth.showSecurityPanel ? (
          <ModalDialog
            className="security-dialog"
            closeLabel={t('hero.hideSecurity')}
            isDirty={auth.securityDialogDirty}
            onClose={auth.closeSecurityPanel}
            size="wide"
            title={t('hero.security')}
            unsavedChangesMessage={t('common.unsavedChangesConfirm')}
          >
            <div className="security-dialog-content">
              <p className="section-copy">{t('hero.showSecurityHint')}</p>
              <div aria-label={t('hero.security')} className="security-tab-list" role="tablist">
                <button
                  aria-controls="security-password-tabpanel"
                  aria-selected={auth.securityTab === 'password'}
                  className={`secondary security-tab-button ${auth.securityTab === 'password' ? 'security-tab-button-active' : ''}`.trim()}
                  id="security-password-tab"
                  onClick={() => auth.selectSecurityTab('password')}
                  role="tab"
                  type="button"
                >
                  {t('password.title')}
                </button>
                <button
                  aria-controls="security-passkeys-tabpanel"
                  aria-selected={auth.securityTab === 'passkeys'}
                  className={`secondary security-tab-button ${auth.securityTab === 'passkeys' ? 'security-tab-button-active' : ''}`.trim()}
                  id="security-passkeys-tab"
                  onClick={() => auth.selectSecurityTab('passkeys')}
                  role="tab"
                  type="button"
                >
                  {t('passkey.title')}
                </button>
                <button
                  aria-controls="security-sessions-tabpanel"
                  aria-selected={auth.securityTab === 'sessions'}
                  className={`secondary security-tab-button ${auth.securityTab === 'sessions' ? 'security-tab-button-active' : ''}`.trim()}
                  id="security-sessions-tab"
                  onClick={() => auth.selectSecurityTab('sessions')}
                  role="tab"
                  type="button"
                >
                  {t('sessions.title')}
                </button>
              </div>
              {auth.securityTab === 'password' ? (
                <div aria-labelledby="security-password-tab" className="security-tab-panel" id="security-password-tabpanel" role="tabpanel">
                  <PasswordPanel
                    onPasswordChange={auth.handlePasswordChange}
                    onPasswordFormChange={auth.setPasswordForm}
                    onPasswordRemove={auth.handlePasswordRemoval}
                    passkeyCount={session.passkeyCount}
                    passwordConfigured={session.passwordConfigured}
                    passwordForm={auth.passwordForm}
                    passwordLoading={isPending('passwordChange')}
                    passwordRemoveLoading={isPending('passwordRemove')}
                    t={t}
                  />
                </div>
              ) : auth.securityTab === 'passkeys' ? (
                <div aria-labelledby="security-passkeys-tab" className="security-tab-panel" id="security-passkeys-tabpanel" role="tabpanel">
                  <PasskeyPanel
                    createLoading={isPending('passkeyCreate')}
                    deleteLoadingId={auth.myPasskeys.find((passkey) => isPending(`passkeyDelete:${passkey.id}`))?.id || null}
                    onDeletePasskey={auth.handleDeletePasskey}
                    onOpenRegistrationDialog={auth.openPasskeyRegistrationDialog}
                    passwordConfigured={session.passwordConfigured}
                    passkeys={auth.myPasskeys}
                    supported={auth.passkeysSupported}
                    t={t}
                    locale={language}
                  />
                </div>
              ) : (
                <div aria-labelledby="security-sessions-tab" className="security-tab-panel" id="security-sessions-tabpanel" role="tabpanel">
                  <SessionsPanel
                    activeSessions={auth.sessionActivity.activeSessions}
                    currentSessionCanRequestDeviceLocation={deviceLocation.supported}
                    currentSessionDeviceLocationError={deviceLocation.error}
                    geoIpConfigured={auth.sessionActivity.geoIpConfigured}
                    locale={language}
                    onRequestCurrentDeviceLocation={deviceLocation.requestLocation}
                    onRevokeOtherSessions={auth.handleRevokeOtherSessions}
                    onRevokeSession={auth.handleRevokeSession}
                    recentLogins={auth.sessionActivity.recentLogins}
                    revokeLoadingId={auth.sessionActivity.activeSessions.find((sessionItem) => isPending(`sessionRevoke:${sessionItem.id}`))?.id || null}
                    revokeOthersLoading={isPending('sessionsRevokeOthers')}
                    requestCurrentDeviceLocationLoading={deviceLocation.saving}
                    t={t}
                  />
                </div>
              )}
            </div>
          </ModalDialog>
        ) : null}

        {auth.showPasskeyRegistrationDialog ? (
          <PasskeyRegistrationDialog
            isLoading={isPending('passkeyCreate')}
            onClose={auth.closePasskeyRegistrationDialog}
            onPasskeyLabelChange={auth.setPasskeyLabel}
            onSubmit={auth.handlePasskeyRegistration}
            passkeyLabel={auth.passkeyLabel}
            t={t}
          />
        ) : null}

        <div className="notification-stack" aria-live="polite">
          {[...persistentNotifications, ...visibleNotifications].map((notification) => (
            <Banner
              key={notification.id}
              copyText={resolveNotificationCopyText(notification)}
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
              {resolveNotificationText(notification)}
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
          ? (
            <WorkspaceSectionList
              dragState={dragState}
              layoutEditing={uiPreferences.layoutEditEnabled}
              moveSection={moveSection}
              orderedIds={orderedUserWorkspaceSections}
              sections={userWorkspaceSections}
              setDragState={setDragState}
              t={t}
              workspaceKey="user"
            />
            )
          : null}

        {polling.showUserPollingDialog && polling.userPollingSettings ? (
          <UserPollingSettingsDialog
            isDirty={polling.userPollingFormDirty}
            onClose={() => polling.setShowUserPollingDialog(false)}
            onPollingFormChange={polling.handleUserPollingFormChange}
            onResetPollingSettings={polling.resetUserPollingSettings}
            onSavePollingSettings={polling.saveUserPollingSettings}
            pollingSettings={polling.userPollingSettings}
            pollingSettingsForm={polling.userPollingForm}
            pollingSettingsLoading={isPending('userPollingSettingsSave')}
            t={t}
          />
        ) : null}

        {polling.showSystemPollingDialog && systemDashboard?.polling ? (
          <SystemPollingSettingsDialog
            isDirty={polling.systemPollingFormDirty}
            locale={language}
            onClose={() => polling.setShowSystemPollingDialog(false)}
            onPollingFormChange={polling.handleSystemPollingFormChange}
            onResetPollingSettings={polling.resetPollingSettings}
            onSavePollingSettings={polling.savePollingSettings}
            pollingSettings={systemDashboard.polling}
            pollingSettingsForm={polling.systemPollingForm}
            pollingSettingsLoading={isPending('pollingSettingsSave')}
            t={t}
          />
        ) : null}

        {isAdmin && adminWorkspace === 'admin'
          ? (
            <WorkspaceSectionList
              dragState={dragState}
              layoutEditing={uiPreferences.layoutEditEnabled}
              moveSection={moveSection}
              orderedIds={orderedAdminWorkspaceSections}
              sections={adminWorkspaceSections}
              setDragState={setDragState}
              t={t}
              workspaceKey="admin"
            />
            )
          : null}

        {userManagement.showPasswordResetDialog && userManagement.passwordResetTarget ? (
          <PasswordResetDialog
            onClose={userManagement.closePasswordResetDialog}
            onFormChange={userManagement.setAdminResetPasswordForm}
            onSubmit={userManagement.resetSelectedUserPassword}
            passwordLoading={userManagement.passwordResetTarget && isPending(`resetPassword:${userManagement.passwordResetTarget.id}`)}
            resetPasswordForm={userManagement.adminResetPasswordForm}
            t={t}
            username={userManagement.passwordResetTarget.username}
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

function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  )
}

export default App
