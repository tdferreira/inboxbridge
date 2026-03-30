import { useEffect, useMemo, useRef, useState } from 'react'
import { BrowserRouter, useLocation, useNavigate } from 'react-router-dom'
import AuthScreen from './components/auth/AuthScreen'
import Banner from './components/common/Banner'
import ConfirmationDialog from './components/common/ConfirmationDialog'
import LoadingScreen from './components/common/LoadingScreen'
import ModalDialog from './components/common/ModalDialog'
import PasswordPanel from './components/account/PasswordPanel'
import PasskeyPanel from './components/account/PasskeyPanel'
import PasskeyRegistrationDialog from './components/account/PasskeyRegistrationDialog'
import PasswordResetDialog from './components/admin/PasswordResetDialog'
import HeroPanel from './components/layout/HeroPanel'
import NotificationsDialog from './components/layout/NotificationsDialog'
import PreferencesDialog from './components/layout/PreferencesDialog'
import SystemOAuthAppsDialog from './components/admin/SystemOAuthAppsDialog'
import SystemPollingSettingsDialog from './components/admin/SystemPollingSettingsDialog'
import UserPollingSettingsDialog from './components/polling/UserPollingSettingsDialog'
import WorkspaceSectionList from './components/layout/WorkspaceSectionList'
import { buildAdminWorkspaceSections, buildUserWorkspaceSections } from './components/layout/workspaceSectionBuilders'
import { apiErrorText } from './lib/api'
import { buildSetupGuideState } from './lib/setupGuide'
import { normalizeDestinationProviderConfig } from './lib/emailProviderPresets'
import { pollErrorNotification, resolveNotificationContent, translatedNotification } from './lib/notifications'
import { isSourceEmailAccountTargetId } from './lib/sectionTargets'
import { normalizeLocale, translate } from './lib/i18n'
import { useAuthSecurityController } from './lib/useAuthSecurityController'
import { useDestinationController } from './lib/useDestinationController'
import { useEmailAccountsController } from './lib/useEmailAccountsController'
import { usePollingControllers } from './lib/usePollingControllers'
import { useUserManagementController } from './lib/useUserManagementController'
import { useWorkspacePreferencesController } from './lib/useWorkspacePreferencesController'
import { applyOrderedSectionIds } from './lib/workspacePreferences'
import { buildWorkspacePath, canonicalWorkspacePath, resolveWorkspaceRoute } from './lib/workspaceRoutes'

const REFRESH_MS = 30000

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
  microsoftOAuthAvailable: true,
  googleOAuthAvailable: true,
  sourceOAuthProviders: ['MICROSOFT', 'GOOGLE']
}
const SECTION_HIGHLIGHT_MS = 2600
const NOTIFICATION_AUTO_CLOSE_MS = {
  success: 8000,
  warning: 12000,
  error: 16000
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
  const [dismissedPersistentNotifications, setDismissedPersistentNotifications] = useState({})
  const [language, setLanguage] = useState(() => normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language))
  const [showSystemOAuthAppsDialog, setShowSystemOAuthAppsDialog] = useState(false)
  const notificationTimersRef = useRef(new Map())
  const selectedUserLoaderRef = useRef(null)
  const t = useMemo(() => (key, params) => translate(language, key, params), [language])
  const notificationTimestampFormatter = useMemo(
    () => new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeStyle: 'medium' }),
    [language]
  )
  const errorText = (key) => t(`errors.${key}`)
  const auth = useAuthSecurityController({
    closeConfirmation: () => setConfirmationDialog(null),
    errorText,
    loadAppData,
    onLogoutReset: async () => {
      setSystemDashboard(null)
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
    uiPreferences
  } = layout

  function setAdminWorkspace(nextWorkspace, options = {}) {
    const explicitUserRoute = nextWorkspace === 'user' && Boolean(options.explicitUserRoute ?? (isAdmin && location.pathname !== '/'))
    const nextPath = buildWorkspacePath(language, nextWorkspace, { explicitUserRoute })
    if (location.pathname === nextPath) {
      return
    }
    navigate(nextPath, { replace: Boolean(options.replace) })
  }

  const apiErrorMessage = async (key, response) => apiErrorText(response, errorText(key))
  const emailAccounts = useEmailAccountsController({
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
  const polling = usePollingControllers({
    authOptions,
    closeConfirmation: () => setConfirmationDialog(null),
    errorText,
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
      copyText: copyText ?? message,
      createdAt: Date.now(),
      floatingVisible: true,
      groupKey,
      id,
      message,
      targetId,
      tone
    }])
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
      const [destinationPayload, userPollingPayload, userPollingStatsPayload, emailAccountsPayload, uiPreferencesPayload, passkeysPayload, adminPayload, oauthSettingsPayload, usersPayload] = payloads
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
      applyLoadedUiPreferences(uiPreferencesPayload, session.id)

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
      } else {
        setSystemDashboard(null)
        setSystemOAuthSettings(DEFAULT_SYSTEM_OAUTH_SETTINGS)
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
    auth.loadSession()
  }, [])

  useEffect(() => {
    if (!session) return
    loadAppData()
    const timer = window.setInterval(loadAppData, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [authOptions.multiUserEnabled, session])

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

  async function handleRefresh() {
    await withPending('refresh', async () => {
      await loadAppData()
    })
  }

  async function dismissQuickSetupGuide() {
    handleQuickSetupVisibilityChange(false, userSetupGuideState.allStepsComplete)
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
    const derivedSectionKey = sectionKey || ({
      'destination-mailbox-section': 'destinationMailboxCollapsed',
      'user-polling-section': 'userPollingCollapsed',
      'user-polling-stats-section': 'userStatsCollapsed',
      'source-email-accounts-section': 'sourceEmailAccountsCollapsed',
      'system-dashboard-section': 'systemDashboardCollapsed',
      'oauth-apps-section': 'oauthAppsCollapsed',
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
    if (targetId === 'user-management-section' || targetId === 'system-dashboard-section' || targetId === 'oauth-apps-section' || targetId === 'global-polling-stats-section') {
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
      handleQuickSetupVisibilityChange(true, userSetupGuideState.allStepsComplete)
      return
    }
    if (userSetupGuideState.allStepsComplete && !uiPreferences.quickSetupPinnedVisible && !uiPreferences.quickSetupDismissed) {
      handleQuickSetupVisibilityChange(false, userSetupGuideState.allStepsComplete)
    }
  }, [handleQuickSetupVisibilityChange, session, uiPreferences, userSetupGuideState.allStepsComplete])

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
    authOptions,
    focusSection,
    isPending,
    isSectionRefreshing,
    language,
    loadAdminUserCustomRange,
    loadGlobalCustomRange,
    polling,
    session,
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

  if (auth.authLoading) {
    return <LoadingScreen label={t('app.loading')} />
  }

  if (!session) {
    return (
        <AuthScreen
          authError={auth.authError}
          loginLoading={isPending('login')}
          loginForm={auth.loginForm}
          multiUserEnabled={authOptions.multiUserEnabled}
          notice=""
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

        {showPreferencesDialog ? (
          <PreferencesDialog
            canHideQuickSetup={userSetupGuideState.allStepsComplete}
            layoutEditEnabled={uiPreferences.layoutEditEnabled}
            language={language}
            languageOptions={selectableLanguages}
            onClose={closePreferencesDialog}
            onExitLayoutEditing={commitLayoutEditingChanges}
            onLanguageChange={handleLanguageChange}
            onPersistLayoutChange={handlePersistLayoutChange}
            onQuickSetupVisibilityChange={(visible) => handleQuickSetupVisibilityChange(visible, userSetupGuideState.allStepsComplete)}
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
              ) : (
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
