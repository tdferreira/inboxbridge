import test from 'node:test'
import assert from 'node:assert/strict'
import { createOptionsController } from '../../shared/src/options-controller.js'
import {
  createFakeBanner,
  createFakeBlock,
  createFakeButton,
  createFakeForm,
  createFakeInput
} from './test-helpers.mjs'

function createOptionsElements() {
  return {
    authSummaryCard: createFakeBlock(''),
    authSummaryText: createFakeBlock(''),
    clearButton: createFakeButton('Clear'),
    detectButton: createFakeButton('Detect URL from active tabs'),
    form: createFakeForm(),
    languageInput: createFakeInput('user'),
    notifyErrorsInput: createFakeInput(''),
    notifyManualPollSuccessInput: createFakeInput(''),
    passwordField: createFakeBlock(''),
    passwordInput: createFakeInput(''),
    saveButton: createFakeButton('Sign in'),
    serverUrlField: createFakeBlock(''),
    serverUrlInput: createFakeInput(''),
    statusBanner: createFakeBanner(),
    testButton: createFakeButton('Test connection'),
    themeInput: createFakeInput('user'),
    usernameField: createFakeBlock(''),
    usernameInput: createFakeInput('')
  }
}

test('options controller initializes saved config and signs in with rotated extension auth', async () => {
  const elements = createOptionsElements()
  let savedConfig = null
  const messages = []
  let appliedTheme = null
  const controller = createOptionsController({
    deps: {
      applyThemePreference(_document, theme, userThemeMode) {
        appliedTheme = { theme, userThemeMode }
      },
      clearConfig: async () => {},
      completeBrowserSignIn: async () => {
        throw new Error('browser sign-in should not be called')
      },
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus(target) {
        target.hidden = true
      },
      localizeOptionsPage() {},
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', username: 'alice' }),
      loginExtension: async () => ({
        status: 'AUTHENTICATED',
        session: {
          publicBaseUrl: 'https://mail.example.com',
          tokens: {
            accessToken: 'access-2',
            accessExpiresAt: '2026-04-12T11:00:00Z',
            refreshToken: 'refresh-2',
            refreshExpiresAt: '2026-05-12T11:00:00Z'
          },
          user: {
            username: 'alice'
          }
        }
      }),
      normalizeServerUrl: (value) => value.trim(),
      resolveLanguagePreference: () => 'en',
      saveConfig: async (config) => {
        savedConfig = config
      },
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async (message) => {
        messages.push(message)
      },
      sessionToConfig: (serverUrl, session) => ({
        accessTokenExpiresAt: session.tokens.accessExpiresAt,
        refreshToken: session.tokens.refreshToken,
        refreshTokenExpiresAt: session.tokens.refreshExpiresAt,
        serverUrl,
        token: session.tokens.accessToken,
        username: session.user.username
      }),
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (locale, key, params = {}) => {
        if (key === 'status.connectedAs') {
          return `Connected as ${params.username}.`
        }
        if (key === 'settings.signOut') {
          return 'Sign out'
        }
        if (key === 'settings.signIn') {
          return 'Sign in'
        }
        if (key === 'settings.signedInAs') {
          return `Logged in as ${params.username}.`
        }
        if (key === 'settings.signedOutHelp') {
          return 'Sign in with your InboxBridge URL, username, and password or passkey.'
        }
        if (key === 'status.working') {
          return 'Working…'
        }
        return key
      },
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  await controller.initialize()
  assert.equal(elements.serverUrlInput.value, 'https://mail.example.com')
  assert.equal(elements.themeInput.value, 'user')
  assert.equal(elements.languageInput.value, 'user')
  assert.equal(elements.usernameInput.value, 'alice')
  assert.equal(elements.saveButton.textContent, 'Sign in')
  assert.equal(elements.authSummaryCard.hidden, true)
  assert.equal(elements.serverUrlField.hidden, false)
  assert.equal(elements.usernameField.hidden, false)
  assert.equal(elements.passwordField.hidden, false)
  assert.equal(elements.serverUrlInput.disabled, false)
  assert.equal(elements.usernameInput.disabled, false)
  assert.equal(elements.passwordInput.disabled, false)
  assert.equal(elements.detectButton.disabled, false)
  assert.equal(elements.testButton.disabled, true)
  assert.deepEqual(appliedTheme, { theme: 'user', userThemeMode: 'SYSTEM' })

  elements.serverUrlInput.value = 'https://mail.example.com'
  elements.usernameInput.value = 'alice'
  elements.passwordInput.value = 'secret'
  await elements.form.submit()

  assert.deepEqual(savedConfig, {
    accessTokenExpiresAt: '2026-04-12T11:00:00Z',
    refreshToken: 'refresh-2',
    refreshTokenExpiresAt: '2026-05-12T11:00:00Z',
    language: 'user',
    notifyErrors: false,
    notifyManualPollSuccess: false,
    serverUrl: 'https://mail.example.com',
    theme: 'user',
    token: 'access-2',
    username: 'alice'
  })
  assert.deepEqual(messages, [{ type: 'refresh-status' }, { type: 'refresh-context-menus' }])
  assert.equal(elements.passwordInput.value, '')
  assert.equal(elements.authSummaryCard.hidden, false)
  assert.equal(elements.authSummaryText.textContent, 'Logged in as alice.')
  assert.equal(elements.serverUrlField.hidden, true)
  assert.equal(elements.usernameField.hidden, true)
  assert.equal(elements.passwordField.hidden, true)
  assert.equal(elements.statusBanner.textContent, 'Connected as alice.')
})

test('options controller completes the passkey flow when InboxBridge requires it', async () => {
  const elements = createOptionsElements()
  let browserSignInPayload = null
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {},
      completeBrowserSignIn: async (request) => {
        browserSignInPayload = request
        return {
          publicBaseUrl: 'https://mail.example.com',
          tokens: {
            accessToken: 'access-passkey',
            refreshToken: 'refresh-passkey'
          },
          user: {
            username: 'alice'
          }
        }
      },
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'firefox', extensionVersion: '0.1.0', label: 'Firefox browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => null,
      loginExtension: async () => ({
        status: 'PASSKEY_REQUIRED',
        passkeyChallenge: {
          ceremonyId: 'ceremony-1',
          publicKeyJson: '{"challenge":"AQID","allowCredentials":[]}'
        }
      }),
      normalizeServerUrl: (value) => value.trim(),
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      sessionToConfig: (_serverUrl, session) => ({
        serverUrl: 'https://mail.example.com',
        token: session.tokens.accessToken,
        refreshToken: session.tokens.refreshToken,
        username: session.user.username
      }),
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key, params = {}) => key === 'status.connectedAs' ? `Connected as ${params.username}.` : key,
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  elements.serverUrlInput.value = 'https://mail.example.com'
  elements.usernameInput.value = 'alice'
  elements.passwordInput.value = 'secret'
  await elements.form.submit()

  assert.deepEqual(browserSignInPayload, {
    browserFamily: 'firefox',
    extensionVersion: '0.1.0',
    label: 'Firefox browser extension',
    serverUrl: 'https://mail.example.com'
  })
})

test('options controller requests origin permission for the canonical public URL returned by InboxBridge', async () => {
  const elements = createOptionsElements()
  const requestedOrigins = []
  let savedConfig = null
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {},
      completeBrowserSignIn: async () => {
        throw new Error('browser sign-in should not be called')
      },
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async (serverUrl) => {
        requestedOrigins.push(serverUrl)
        return true
      },
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => null,
      loginExtension: async () => ({
        status: 'AUTHENTICATED',
        session: {
          publicBaseUrl: 'https://public.example.com',
          tokens: {
            accessToken: 'access-2',
            refreshToken: 'refresh-2'
          },
          user: {
            username: 'alice'
          }
        }
      }),
      normalizeServerUrl: (value) => value.trim(),
      resolveLanguagePreference: () => 'en',
      saveConfig: async (config) => {
        savedConfig = config
      },
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      sessionToConfig: (_serverUrl, session) => ({
        serverUrl: session.publicBaseUrl,
        token: session.tokens.accessToken,
        refreshToken: session.tokens.refreshToken,
        username: session.user.username
      }),
      showStatus() {},
      translate: (_locale, key) => key === 'status.working' ? 'Working…' : key,
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  elements.serverUrlInput.value = 'https://private.example.com'
  elements.usernameInput.value = 'alice'
  elements.passwordInput.value = 'secret'
  await elements.form.submit()

  assert.deepEqual(requestedOrigins, [
    'https://private.example.com',
    'https://public.example.com'
  ])
  assert.deepEqual(savedConfig, {
    language: 'user',
    notifyErrors: false,
    notifyManualPollSuccess: false,
    refreshToken: 'refresh-2',
    serverUrl: 'https://public.example.com',
    theme: 'user',
    token: 'access-2',
    username: 'alice'
  })
})

test('options controller marks required sign-in fields invalid when they are missing', async () => {
  const elements = createOptionsElements()
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {},
      completeBrowserSignIn: async () => null,
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus(target) {
        target.hidden = true
      },
      localizeOptionsPage() {},
      loadConfig: async () => null,
      loginExtension: async () => {
        throw new Error('login should not be called')
      },
      normalizeServerUrl: (value) => value.trim(),
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      sessionToConfig: () => null,
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key) => {
        if (key === 'errors.requiredFields') return 'Fill in the InboxBridge URL, username, and password before signing in.'
        if (key === 'status.working') return 'Working…'
        return key
      },
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  await elements.form.submit()

  assert.equal(elements.serverUrlInput.ariaInvalid, 'true')
  assert.equal(elements.usernameInput.ariaInvalid, 'true')
  assert.equal(elements.passwordInput.ariaInvalid, 'true')
  assert.match(elements.serverUrlField.className, /field-invalid/)
  assert.match(elements.usernameField.className, /field-invalid/)
  assert.match(elements.passwordField.className, /field-invalid/)
  assert.equal(elements.statusBanner.textContent, 'Fill in the InboxBridge URL, username, and password before signing in.')

  elements.serverUrlInput.value = 'https://mail.example.com'
  await elements.serverUrlInput.input()
  assert.equal(elements.serverUrlInput.ariaInvalid, 'false')
  assert.doesNotMatch(elements.serverUrlField.className, /field-invalid/)
})

test('options controller signs out from the primary action and unlocks the connection fields', async () => {
  const elements = createOptionsElements()
  const messages = []
  let cleared = false
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {
        cleared = true
      },
      completeBrowserSignIn: async () => {
        throw new Error('browser sign-in should not be called')
      },
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', username: 'alice', token: 'access-1' }),
      loginExtension: async () => {
        throw new Error('login should not be called while signed in')
      },
      normalizeServerUrl: (value) => value,
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async (message) => {
        messages.push(message)
      },
      sessionToConfig: () => null,
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key, params = {}) => {
        if (key === 'settings.signOut') return 'Sign out'
        if (key === 'settings.signIn') return 'Sign in'
        if (key === 'settings.useCurrentTab') return 'Detect URL from active tabs'
        if (key === 'settings.signedInAs') return `Logged in as ${params.username}.`
        if (key === 'settings.signedOutHelp') return 'Sign in with your InboxBridge URL, username, and password or passkey.'
        if (key === 'status.signedOut') return 'Signed out from InboxBridge in this browser.'
        if (key === 'status.working') return 'Working…'
        return key
      },
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  await controller.initialize()
  await elements.form.submit()

  assert.equal(cleared, true)
  assert.equal(elements.saveButton.textContent, 'Sign in')
  assert.equal(elements.authSummaryCard.hidden, true)
  assert.equal(elements.serverUrlField.hidden, false)
  assert.equal(elements.usernameField.hidden, false)
  assert.equal(elements.passwordField.hidden, false)
  assert.equal(elements.serverUrlInput.value, 'https://mail.example.com')
  assert.equal(elements.usernameInput.value, 'alice')
  assert.equal(elements.serverUrlInput.disabled, false)
  assert.equal(elements.usernameInput.disabled, false)
  assert.equal(elements.passwordInput.disabled, false)
  assert.equal(elements.detectButton.disabled, false)
  assert.equal(elements.testButton.disabled, true)
  assert.equal(elements.statusBanner.textContent, 'Signed out from InboxBridge in this browser.')
  assert.deepEqual(messages, [{ type: 'refresh-status' }, { type: 'refresh-context-menus' }])
})

test('options controller clears config and refreshes badge from cache', async () => {
  const elements = createOptionsElements()
  const messages = []
  let cleared = false
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {
        cleared = true
      },
      completeBrowserSignIn: async () => {
        throw new Error('browser sign-in should not be called')
      },
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => null,
      loginExtension: async () => null,
      normalizeServerUrl: (value) => value,
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async (message) => {
        messages.push(message)
      },
      sessionToConfig: () => null,
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key) => key === 'status.cleared' ? 'Saved InboxBridge sign-in was cleared from this browser.' : 'Working…',
      verifyExtensionPasskey: async () => null,
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  elements.passwordInput.value = 'secret'
  elements.usernameInput.value = 'alice'
  await elements.clearButton.click()

  assert.equal(cleared, true)
  assert.equal(elements.passwordInput.value, '')
  assert.equal(elements.usernameInput.value, '')
  assert.deepEqual(messages, [{ type: 'refresh-status' }, { type: 'refresh-context-menus' }])
})

test('options controller requests tabs permission before reading the active tab URL', async () => {
  const elements = createOptionsElements()
  let permissionChecks = 0
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {},
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => {
        permissionChecks += 1
        return true
      },
      detectServerUrl: async () => 'https://mail.example.com',
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => null,
      loginExtension: async () => null,
      normalizeServerUrl: (value) => value,
      parseGetOptions: () => null,
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      serializeCredential: () => null,
      sessionToConfig: () => null,
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key) => {
        if (key === 'status.detectedUrl') return 'Detected the InboxBridge URL from the active browser tab.'
        if (key === 'settings.useCurrentTab') return 'Detect URL from active tabs'
        if (key === 'settings.detectHint') return 'Looks through the current browser window for an already open InboxBridge tab and copies its URL here. If several match, the most recently used InboxBridge tab wins.'
        if (key === 'status.working') return 'Working…'
        return key
      },
      verifyExtensionPasskey: async () => null,
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  await elements.detectButton.click()

  assert.equal(permissionChecks, 1)
  assert.equal(elements.serverUrlInput.value, 'https://mail.example.com')
  assert.equal(elements.statusBanner.textContent, 'Detected the InboxBridge URL from the active browser tab.')
})

test('options controller requests notification permission before enabling browser alerts', async () => {
  const elements = createOptionsElements()
  let requestedNotifications = 0
  let savedPreferences = null
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {},
      ensureNotificationPermission: async () => {
        requestedNotifications += 1
        return true
      },
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => null,
      loginExtension: async () => null,
      normalizeServerUrl: (value) => value,
      parseGetOptions: () => null,
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async (preferences) => {
        savedPreferences = preferences
      },
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      serializeCredential: () => null,
      sessionToConfig: () => null,
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key) => {
        if (key === 'status.notificationsSet') return 'Browser notification preferences updated.'
        return key
      },
      verifyExtensionPasskey: async () => null,
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  elements.notifyErrorsInput.checked = true
  await elements.notifyErrorsInput.change()

  assert.equal(requestedNotifications, 1)
  assert.deepEqual(savedPreferences, { notifyErrors: true })
  assert.equal(elements.statusBanner.textContent, 'Browser notification preferences updated.')
})

test('options controller refreshes background context menus when the language changes', async () => {
  const elements = createOptionsElements()
  const messages = []
  let savedLanguage = null
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {},
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => ({ language: 'user' }),
      loginExtension: async () => null,
      normalizeServerUrl: (value) => value,
      parseGetOptions: () => null,
      resolveLanguagePreference: (config) => config.language === 'fr' ? 'fr' : 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async (language) => {
        savedLanguage = language
      },
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async (message) => {
        messages.push(message)
      },
      serializeCredential: () => null,
      sessionToConfig: () => null,
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key) => {
        if (key === 'status.languageSet') return 'Language preference updated.'
        return key
      },
      verifyExtensionPasskey: async () => null,
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  await controller.initialize()
  elements.languageInput.value = 'fr'
  await elements.languageInput.change()

  assert.equal(savedLanguage, 'fr')
  assert.deepEqual(messages, [{ type: 'refresh-context-menus' }])
  assert.equal(elements.statusBanner.textContent, 'Language preference updated.')
})

test('options controller explains when tabs permission is denied', async () => {
  const elements = createOptionsElements()
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {},
      ensureTabPermission: async () => false,
      detectServerUrl: async () => 'https://mail.example.com',
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => null,
      loginExtension: async () => null,
      normalizeServerUrl: (value) => value,
      parseGetOptions: () => null,
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      serializeCredential: () => null,
      sessionToConfig: () => null,
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key) => {
        if (key === 'errors.tabPermission') return 'Allow tab access to detect the InboxBridge URL from your active browser tab.'
        if (key === 'status.working') return 'Working…'
        return key
      },
      verifyExtensionPasskey: async () => null,
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  await elements.detectButton.click()

  assert.match(elements.statusBanner.textContent, /Allow tab access/)
  assert.equal(elements.serverUrlInput.value, '')
})

test('options controller persists the selected theme immediately', async () => {
  const elements = createOptionsElements()
  let appliedTheme = null
  let savedTheme = null
  const controller = createOptionsController({
    deps: {
      applyThemePreference(_document, theme, userThemeMode) {
        appliedTheme = { theme, userThemeMode }
      },
      clearConfig: async () => {},
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'chromium', extensionVersion: '0.1.0', label: 'Chromium browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', theme: 'user', userThemeMode: 'SYSTEM' }),
      loginExtension: async () => null,
      normalizeServerUrl: (value) => value,
      parseGetOptions: () => null,
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async (theme) => {
        savedTheme = theme
      },
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      serializeCredential: () => null,
      sessionToConfig: () => null,
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key) => {
        if (key === 'status.themeSet') return 'Theme preference updated.'
        if (key === 'status.working') return 'Working…'
        return key
      },
      verifyExtensionPasskey: async () => null,
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  await controller.initialize()
  elements.themeInput.value = 'dark-green'
  await elements.themeInput.change()

  assert.deepEqual(appliedTheme, { theme: 'dark-green', userThemeMode: 'SYSTEM' })
  assert.equal(savedTheme, 'dark-green')
  assert.equal(elements.statusBanner.textContent, 'Theme preference updated.')
})
