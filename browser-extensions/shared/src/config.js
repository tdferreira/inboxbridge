import {
  containsApiPermission,
  containsOriginPermission,
  queryTabs,
  requestApiPermission,
  requestOriginPermission,
  storageGet,
  storageRemove,
  storageSet
} from './browser.js'
import { refreshExtensionAuth } from './api.js'
import { LANGUAGE_FOLLOW_USER, normalizeLanguagePreference, normalizeSupportedLanguage } from './i18n.js'
import { decryptJson, encryptJson } from './secure-store.js'
import { normalizeThemePreference } from './theme.js'

const STORAGE_KEY = 'inboxbridgeExtensionConfig'
const REFRESH_SKEW_MS = 60_000
const DEFAULT_NOTIFICATION_SETTINGS = Object.freeze({
  notifyErrors: false,
  notifyManualPollSuccess: false
})

function normalizeStoredUserLanguage(value) {
  return value ? normalizeSupportedLanguage(value) : ''
}

function normalizeStoredUserThemeMode(value) {
  const normalized = String(value || '').trim().toUpperCase()
  return normalized || ''
}

function normalizeNotificationSetting(value, fallback = false) {
  return typeof value === 'boolean' ? value : fallback
}

function isBrowserPageTab(url) {
  return /^(about|chrome|chrome-extension|edge|moz-extension|opera|vivaldi|brave):/i.test(String(url || ''))
}

function isInboxBridgeTab(tab) {
  const url = String(tab?.url || '')
  if (!/^https?:/i.test(url) || isBrowserPageTab(url)) {
    return false
  }
  const title = String(tab?.title || '')
  if (/InboxBridge/i.test(title)) {
    return true
  }
  try {
    const parsed = new URL(url)
    return parsed.pathname === '/remote' || parsed.pathname.startsWith('/oauth/')
  } catch {
    return false
  }
}

function selectDetectionCandidate(tabs) {
  const inboxBridgeTabs = (Array.isArray(tabs) ? tabs : []).filter(isInboxBridgeTab)
  if (inboxBridgeTabs.length === 0) {
    return null
  }
  const activeHttpTab = inboxBridgeTabs.find((tab) => tab.active)
  if (activeHttpTab) {
    return activeHttpTab
  }
  return [...inboxBridgeTabs].sort((left, right) => Number(right?.lastAccessed || 0) - Number(left?.lastAccessed || 0))[0] || null
}

export function createConfigStore({
  containsApiPermission,
  containsOriginPermission,
  currentTime = () => Date.now(),
  decryptJson,
  encryptJson,
  queryTabs,
  refreshExtensionAuth,
  requestApiPermission,
  requestOriginPermission,
  storageGet,
  storageRemove,
  storageSet
}) {
  async function loadConfig() {
    const stored = await storageGet(STORAGE_KEY)
    const config = stored?.[STORAGE_KEY] || null
    if (!config) {
      return null
    }
    const theme = normalizeThemePreference(config.theme)
    const language = normalizeLanguagePreference(config.language)
    const notifyErrors = normalizeNotificationSetting(config.notifyErrors, DEFAULT_NOTIFICATION_SETTINGS.notifyErrors)
    const notifyManualPollSuccess = normalizeNotificationSetting(
      config.notifyManualPollSuccess,
      DEFAULT_NOTIFICATION_SETTINGS.notifyManualPollSuccess
    )
    const auth = config.auth ? await decryptJson(config.auth) : null
    if (!auth) {
      return {
        language,
        notifyErrors,
        notifyManualPollSuccess,
        serverUrl: config.serverUrl || '',
        theme,
        userLanguage: normalizeStoredUserLanguage(config.userLanguage),
        userThemeMode: normalizeStoredUserThemeMode(config.userThemeMode)
      }
    }
    let normalized = {
      accessTokenExpiresAt: auth.accessTokenExpiresAt || null,
      language,
      notifyErrors,
      notifyManualPollSuccess,
      refreshToken: auth.refreshToken || '',
      refreshTokenExpiresAt: auth.refreshTokenExpiresAt || null,
      serverUrl: config.serverUrl || '',
      theme,
      token: auth.accessToken || '',
      userLanguage: normalizeStoredUserLanguage(config.userLanguage),
      userThemeMode: normalizeStoredUserThemeMode(config.userThemeMode),
      username: auth.username || ''
    }
    if (shouldRefresh(normalized, currentTime())) {
      try {
        const refreshed = await refreshExtensionAuth(normalized.serverUrl, normalized.refreshToken)
        normalized = mapSessionToConfig(normalized.serverUrl, refreshed?.session)
        normalized.language = language
        normalized.notifyErrors = notifyErrors
        normalized.notifyManualPollSuccess = notifyManualPollSuccess
        normalized.theme = theme
        await saveConfig(normalized)
      } catch {
        await storageSet({
          [STORAGE_KEY]: {
            serverUrl: config.serverUrl || '',
            language,
            theme,
            userLanguage: normalizeStoredUserLanguage(config.userLanguage),
            userThemeMode: normalizeStoredUserThemeMode(config.userThemeMode)
          }
        })
        return {
          language,
          notifyErrors,
          notifyManualPollSuccess,
          serverUrl: config.serverUrl || '',
          theme,
          userLanguage: normalizeStoredUserLanguage(config.userLanguage),
          userThemeMode: normalizeStoredUserThemeMode(config.userThemeMode)
        }
      }
    }
    return normalized
  }

  async function saveConfig(config) {
    const payload = {
      language: normalizeLanguagePreference(config?.language),
      notifyErrors: normalizeNotificationSetting(config?.notifyErrors, DEFAULT_NOTIFICATION_SETTINGS.notifyErrors),
      notifyManualPollSuccess: normalizeNotificationSetting(
        config?.notifyManualPollSuccess,
        DEFAULT_NOTIFICATION_SETTINGS.notifyManualPollSuccess
      ),
      serverUrl: config?.serverUrl || '',
      theme: normalizeThemePreference(config?.theme),
      userLanguage: normalizeStoredUserLanguage(config?.userLanguage),
      userThemeMode: normalizeStoredUserThemeMode(config?.userThemeMode)
    }
    if (config?.token || config?.refreshToken) {
      payload.auth = await encryptJson({
        accessToken: config.token || '',
        accessTokenExpiresAt: config.accessTokenExpiresAt || null,
        refreshToken: config.refreshToken || null,
        refreshTokenExpiresAt: config.refreshTokenExpiresAt || null,
        username: config.username || ''
      })
    }
    await storageSet({ [STORAGE_KEY]: payload })
  }

  async function clearConfig() {
    const stored = await storageGet(STORAGE_KEY)
    const config = stored?.[STORAGE_KEY] || null
    if (!config) {
      await storageRemove(STORAGE_KEY)
      return
    }
    await storageSet({
      [STORAGE_KEY]: {
        serverUrl: config.serverUrl || '',
        theme: normalizeThemePreference(config.theme),
        language: normalizeLanguagePreference(config.language),
        notifyErrors: normalizeNotificationSetting(config.notifyErrors, DEFAULT_NOTIFICATION_SETTINGS.notifyErrors),
        notifyManualPollSuccess: normalizeNotificationSetting(
          config.notifyManualPollSuccess,
          DEFAULT_NOTIFICATION_SETTINGS.notifyManualPollSuccess
        ),
        userLanguage: normalizeStoredUserLanguage(config.userLanguage),
        userThemeMode: normalizeStoredUserThemeMode(config.userThemeMode)
      }
    })
  }

  async function saveThemePreference(theme) {
    const stored = await storageGet(STORAGE_KEY)
    const config = stored?.[STORAGE_KEY] || null
    await storageSet({
      [STORAGE_KEY]: {
        ...config,
        serverUrl: config?.serverUrl || '',
        theme: normalizeThemePreference(theme)
      }
    })
  }

  async function saveLanguagePreference(language) {
    const stored = await storageGet(STORAGE_KEY)
    const config = stored?.[STORAGE_KEY] || null
    await storageSet({
      [STORAGE_KEY]: {
        ...config,
        serverUrl: config?.serverUrl || '',
        language: normalizeLanguagePreference(language)
      }
    })
  }

  async function saveUserPreferences(user) {
    const stored = await storageGet(STORAGE_KEY)
    const config = stored?.[STORAGE_KEY] || null
    await storageSet({
      [STORAGE_KEY]: {
        ...config,
        serverUrl: config?.serverUrl || '',
        userLanguage: normalizeStoredUserLanguage(user?.language),
        userThemeMode: normalizeStoredUserThemeMode(user?.themeMode)
      }
    })
  }

  async function saveNotificationPreferences(preferences) {
    const stored = await storageGet(STORAGE_KEY)
    const config = stored?.[STORAGE_KEY] || null
    await storageSet({
      [STORAGE_KEY]: {
        ...config,
        serverUrl: config?.serverUrl || '',
        notifyErrors: normalizeNotificationSetting(
          preferences?.notifyErrors,
          normalizeNotificationSetting(config?.notifyErrors, DEFAULT_NOTIFICATION_SETTINGS.notifyErrors)
        ),
        notifyManualPollSuccess: normalizeNotificationSetting(
          preferences?.notifyManualPollSuccess,
          normalizeNotificationSetting(config?.notifyManualPollSuccess, DEFAULT_NOTIFICATION_SETTINGS.notifyManualPollSuccess)
        )
      }
    })
  }

  async function detectServerUrl() {
    const tabs = await queryTabs({ currentWindow: true })
    const candidate = selectDetectionCandidate(tabs)
    if (!candidate?.url) {
      return null
    }
    return deriveServerUrlFromTab(candidate.url)
  }

  function originPattern(serverUrl) {
    const normalized = new URL(serverUrl)
    return `${normalized.origin}/*`
  }

  async function ensureOriginPermission(serverUrl) {
    const pattern = originPattern(serverUrl)
    try {
      return await requestOriginPermission(pattern)
    } catch (error) {
      if (await containsOriginPermission(pattern)) {
        return true
      }
      throw error
    }
  }

  async function ensureTabPermission() {
    try {
      return await requestApiPermission('tabs')
    } catch (error) {
      if (await containsApiPermission('tabs')) {
        return true
      }
      throw error
    }
  }

  async function ensureNotificationPermission() {
    if (await containsApiPermission('notifications')) {
      return true
    }
    return requestApiPermission('notifications')
  }

  return {
    clearConfig,
    detectServerUrl,
    ensureOriginPermission,
    ensureNotificationPermission,
    ensureTabPermission,
    loadConfig,
    normalizeServerUrl,
    saveConfig,
    saveLanguagePreference,
    saveNotificationPreferences,
    saveThemePreference,
    saveUserPreferences,
    sessionToConfig: mapSessionToConfig
  }
}

function shouldRefresh(config, now) {
  if (!config?.refreshToken) {
    return false
  }
  if (!config?.accessTokenExpiresAt) {
    return false
  }
  return new Date(config.accessTokenExpiresAt).getTime() - now <= REFRESH_SKEW_MS
}

function deriveServerUrlFromTab(tabUrl) {
  const parsed = new URL(tabUrl)
  if (parsed.pathname === '/remote' || parsed.pathname.startsWith('/oauth/')) {
    parsed.pathname = ''
  } else {
    parsed.pathname = parsed.pathname.replace(/\/+$/, '')
  }
  parsed.hash = ''
  parsed.search = ''
  return normalizeServerUrl(parsed.toString())
}

export function normalizeServerUrl(value) {
  if (!value || !value.trim()) {
    throw new Error('Enter your InboxBridge URL.')
  }
  let normalized
  try {
    normalized = new URL(value.trim())
  } catch {
    throw new Error('Enter a valid InboxBridge URL.')
  }
  const isLocalHttp = normalized.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(normalized.hostname)
  if (normalized.protocol !== 'https:' && !isLocalHttp) {
    throw new Error('Use HTTPS, or HTTP only for localhost/127.0.0.1 during local testing.')
  }
  normalized.hash = ''
  normalized.search = ''
  normalized.pathname = normalized.pathname.replace(/\/+$/, '')
  return normalized.toString().replace(/\/+$/, '')
}

function mapSessionToConfig(serverUrl, session) {
  return {
    accessTokenExpiresAt: session?.tokens?.accessExpiresAt || null,
    language: LANGUAGE_FOLLOW_USER,
    refreshToken: session?.tokens?.refreshToken || '',
    refreshTokenExpiresAt: session?.tokens?.refreshExpiresAt || null,
    serverUrl: normalizeServerUrl(session?.publicBaseUrl || serverUrl || ''),
    token: session?.tokens?.accessToken || '',
    userLanguage: normalizeStoredUserLanguage(session?.user?.language),
    userThemeMode: normalizeStoredUserThemeMode(session?.user?.themeMode),
    username: session?.user?.username || session?.user?.displayName || ''
  }
}

const defaultStore = createConfigStore({
  containsApiPermission,
  containsOriginPermission,
  decryptJson,
  encryptJson,
  queryTabs,
  refreshExtensionAuth,
  requestApiPermission,
  requestOriginPermission,
  storageGet,
  storageRemove,
  storageSet
})

export const clearConfig = defaultStore.clearConfig
export const detectServerUrl = defaultStore.detectServerUrl
export const ensureOriginPermission = defaultStore.ensureOriginPermission
export const ensureNotificationPermission = defaultStore.ensureNotificationPermission
export const ensureTabPermission = defaultStore.ensureTabPermission
export const loadConfig = defaultStore.loadConfig
export const saveConfig = defaultStore.saveConfig
export const saveLanguagePreference = defaultStore.saveLanguagePreference
export const saveNotificationPreferences = defaultStore.saveNotificationPreferences
export const saveThemePreference = defaultStore.saveThemePreference
export const saveUserPreferences = defaultStore.saveUserPreferences
export const sessionToConfig = defaultStore.sessionToConfig
