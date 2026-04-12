import { redeemExtensionBrowserAuth, fetchStatus, loginExtension, startExtensionBrowserAuth } from '../../shared/src/api.js'
import { browserRuntime, openTab, sendMessage } from '../../shared/src/browser.js'
import { completeBrowserSignIn } from '../../shared/src/browser-auth.js'
import {
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
  sessionToConfig
} from '../../shared/src/config.js'
import { localizeOptionsPage, resolveLanguagePreference, translate } from '../../shared/src/i18n.js'
import { createOptionsController } from '../../shared/src/options-controller.js'
import { applyThemePreference } from '../../shared/src/theme.js'

export function hideStatus(target) {
  target.hidden = true
  target.className = 'status-banner'
}

export function showStatus(target, tone, text) {
  target.hidden = false
  target.className = `status-banner ${tone}`
  target.textContent = text
}

async function openBrowserSignInWindow(url) {
  const opened = globalThis.open?.(url, '_blank', 'popup=yes,width=480,height=720')
  if (opened) {
    return opened
  }
  return openTab(url)
}

const controller = createOptionsController({
  deps: {
    applyThemePreference,
    clearConfig,
    completeBrowserSignIn: (params) => completeBrowserSignIn({
      ...params,
      openWindow: openBrowserSignInWindow,
      redeemExtensionBrowserAuth,
      startExtensionBrowserAuth
    }),
    detectServerUrl,
    ensureOriginPermission,
    ensureNotificationPermission,
    ensureTabPermission,
    fetchStatus,
    getBrowserMetadata() {
      const manifest = browserRuntime().getManifest()
      return {
        browserFamily: 'chromium',
        extensionVersion: manifest?.version || 'unknown',
        label: 'Chromium browser extension'
      }
    },
    hideStatus,
    localizeOptionsPage,
    loginExtension,
    loadConfig,
    normalizeServerUrl,
    resolveLanguagePreference,
    saveConfig,
    saveLanguagePreference,
    saveNotificationPreferences,
    saveThemePreference,
    saveUserPreferences,
    sendMessage,
    sessionToConfig,
    showStatus,
    targetDocument: document,
    translate
  },
  elements: {
    authSummaryCard: document.getElementById('auth-summary-card'),
    authSummaryText: document.getElementById('settings-signed-in-copy'),
    clearButton: document.getElementById('clear-button'),
    detectButton: document.getElementById('detect-button'),
    notifyErrorsInput: document.getElementById('notify-errors'),
    notifyManualPollSuccessInput: document.getElementById('notify-manual-poll-success'),
    form: document.getElementById('settings-form'),
    languageInput: document.getElementById('language'),
    passwordInput: document.getElementById('password'),
    saveButton: document.getElementById('save-button'),
    passwordField: document.getElementById('password-field'),
    serverUrlInput: document.getElementById('server-url'),
    serverUrlField: document.getElementById('server-url-field'),
    statusBanner: document.getElementById('settings-status'),
    testButton: document.getElementById('test-button'),
    themeInput: document.getElementById('theme'),
    usernameInput: document.getElementById('username'),
    usernameField: document.getElementById('username-field')
  }
})

controller.bind()
void controller.initialize()
