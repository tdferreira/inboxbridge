import test from 'node:test'
import assert from 'node:assert/strict'
import { createOptionsController } from '../../shared/src/options-controller.js'
import {
  createFakeBanner,
  createFakeBlock,
  createFakeButton,
  createFakeForm,
  createFakeInput
} from '../../chromium/tests/test-helpers.mjs'

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

test('firefox options controller switches to sign-out mode after loading an authenticated session', async () => {
  const elements = createOptionsElements()
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
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'firefox', extensionVersion: '0.1.0', label: 'Firefox browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', username: 'alice', token: 'access-1' }),
      loginExtension: async () => null,
      normalizeServerUrl: (value) => value,
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
      sessionToConfig: () => null,
      showStatus() {},
      translate: (_locale, key, params = {}) => {
        if (key === 'settings.signOut') return 'Sign out'
        if (key === 'settings.useCurrentTab') return 'Detect URL from active tabs'
        if (key === 'settings.signedInAs') return `Logged in as ${params.username}.`
        return key
      },
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  await controller.initialize()

  assert.equal(elements.saveButton.textContent, 'Sign out')
  assert.equal(elements.authSummaryCard.hidden, false)
  assert.equal(elements.authSummaryText.textContent, 'Logged in as alice.')
  assert.equal(elements.serverUrlField.hidden, true)
  assert.equal(elements.usernameField.hidden, true)
  assert.equal(elements.passwordField.hidden, true)
  assert.equal(elements.serverUrlInput.disabled, true)
  assert.equal(elements.usernameInput.disabled, true)
  assert.equal(elements.passwordInput.disabled, true)
  assert.equal(elements.detectButton.disabled, true)
  assert.equal(elements.testButton.disabled, false)
})

test('firefox options controller requests tabs permission before detecting the current tab URL', async () => {
  const elements = createOptionsElements()
  let permissionChecks = 0
  const controller = createOptionsController({
    deps: {
      applyThemePreference() {},
      clearConfig: async () => {},
      completeBrowserSignIn: async () => {
        throw new Error('browser sign-in should not be called')
      },
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => {
        permissionChecks += 1
        return true
      },
      detectServerUrl: async () => 'https://mail.example.com',
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'firefox', extensionVersion: '0.1.0', label: 'Firefox browser extension' }),
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
      sendMessage: async () => {},
      sessionToConfig: () => null,
      showStatus(target, tone, text) {
        target.hidden = false
        target.className = `status-banner ${tone}`
        target.textContent = text
      },
      translate: (_locale, key) => {
        if (key === 'status.detectedUrl') return 'Detected the InboxBridge URL from the active browser tab.'
        if (key === 'status.working') return 'Working…'
        return key
      },
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

test('firefox options controller persists the explicit theme variants', async () => {
  const elements = createOptionsElements()
  let savedTheme = null
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
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'firefox', extensionVersion: '0.1.0', label: 'Firefox browser extension' }),
      hideStatus() {},
      localizeOptionsPage() {},
      loadConfig: async () => ({ serverUrl: 'https://mail.example.com', theme: 'user', userThemeMode: 'SYSTEM' }),
      loginExtension: async () => null,
      normalizeServerUrl: (value) => value,
      resolveLanguagePreference: () => 'en',
      saveConfig: async () => {},
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async (theme) => {
        savedTheme = theme
      },
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
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
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  await controller.initialize()
  elements.themeInput.value = 'light-blue'
  await elements.themeInput.change()

  assert.equal(savedTheme, 'light-blue')
  assert.equal(elements.statusBanner.textContent, 'Theme preference updated.')
})

test('firefox options controller completes passkey sign-in and clears the password field', async () => {
  const elements = createOptionsElements()
  let savedConfig = null
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
            accessExpiresAt: '2026-04-12T11:00:00Z',
            refreshToken: 'refresh-passkey',
            refreshExpiresAt: '2026-05-12T11:00:00Z'
          },
          user: {
            username: 'alice',
            language: 'de',
            themeMode: 'LIGHT'
          }
        }
      },
      ensureNotificationPermission: async () => true,
      ensureTabPermission: async () => true,
      detectServerUrl: async () => null,
      ensureOriginPermission: async () => true,
      fetchStatus: async () => ({ user: { username: 'alice' } }),
      getBrowserMetadata: () => ({ browserFamily: 'firefox', extensionVersion: '0.1.0', label: 'Firefox browser extension' }),
      hideStatus(target) {
        target.hidden = true
      },
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
      saveConfig: async (config) => {
        savedConfig = config
      },
      saveLanguagePreference: async () => {},
      saveNotificationPreferences: async () => {},
      saveThemePreference: async () => {},
      saveUserPreferences: async () => {},
      sendMessage: async () => {},
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
        if (key === 'status.connectedAs') return `Connected as ${params.username}.`
        if (key === 'status.working') return 'Working…'
        return key
      },
      targetDocument: {}
    },
    elements
  })

  controller.bind()
  elements.serverUrlInput.value = 'https://mail.example.com'
  elements.usernameInput.value = 'alice'
  elements.passwordInput.value = 'secret'
  await elements.form.submit()

  assert.deepEqual(savedConfig, {
    accessTokenExpiresAt: '2026-04-12T11:00:00Z',
    refreshToken: 'refresh-passkey',
    refreshTokenExpiresAt: '2026-05-12T11:00:00Z',
    language: 'user',
    notifyErrors: false,
    notifyManualPollSuccess: false,
    serverUrl: 'https://mail.example.com',
    theme: 'user',
    token: 'access-passkey',
    username: 'alice'
  })
  assert.equal(elements.passwordInput.value, '')
  assert.deepEqual(browserSignInPayload, {
    browserFamily: 'firefox',
    extensionVersion: '0.1.0',
    label: 'Firefox browser extension',
    serverUrl: 'https://mail.example.com'
  })
})

test('firefox options controller requests origin permission for the canonical public URL returned by InboxBridge', async () => {
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
      getBrowserMetadata: () => ({ browserFamily: 'firefox', extensionVersion: '0.1.0', label: 'Firefox browser extension' }),
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

test('firefox options controller marks required sign-in fields invalid when they are missing', async () => {
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
      getBrowserMetadata: () => ({ browserFamily: 'firefox', extensionVersion: '0.1.0', label: 'Firefox browser extension' }),
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
})
