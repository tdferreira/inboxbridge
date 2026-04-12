/**
 * Keeps the options page logic testable by isolating the imperative form wiring
 * from the shared config, passkey, and extension-auth dependencies.
 */
export function createOptionsController({
  deps,
  elements
}) {
  const {
    applyThemePreference,
    completeBrowserSignIn,
    localizeOptionsPage,
    clearConfig,
    ensureTabPermission,
    ensureNotificationPermission,
    detectServerUrl,
    ensureOriginPermission,
    fetchStatus,
    getBrowserMetadata,
    hideStatus,
    loadConfig,
    loginExtension,
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
    targetDocument,
    translate,
  } = deps

  const {
    authSummaryCard,
    authSummaryText,
    clearButton,
    detectButton,
    notifyErrorsInput,
    notifyManualPollSuccessInput,
    form,
    languageInput,
    passwordField,
    passwordInput,
    saveButton,
    serverUrlField,
    serverUrlInput,
    statusBanner,
    testButton,
    themeInput,
    usernameField,
    usernameInput
  } = elements

  let currentConfig = {
    language: 'user',
    notifyErrors: false,
    notifyManualPollSuccess: false,
    theme: 'user',
    userLanguage: 'en',
    userThemeMode: 'SYSTEM'
  }
  let currentLocale = resolveLanguagePreference?.(currentConfig) || 'en'

  function t(key, params) {
    return translate?.(currentLocale, key, params) || key
  }

  function clearFieldInvalidState(field, input) {
    field?.classList?.remove('field-invalid')
    if (input) {
      input.ariaInvalid = 'false'
      input.classList?.remove('input-invalid')
    }
  }

  function markFieldInvalid(field, input) {
    field?.classList?.add('field-invalid')
    if (input) {
      input.ariaInvalid = 'true'
      input.classList?.add('input-invalid')
    }
  }

  function clearRequiredFieldErrors() {
    clearFieldInvalidState(serverUrlField, serverUrlInput)
    clearFieldInvalidState(usernameField, usernameInput)
    clearFieldInvalidState(passwordField, passwordInput)
  }

  function validateRequiredFields() {
    clearRequiredFieldErrors()
    const missingServerUrl = !serverUrlInput?.value?.trim()
    const missingUsername = !usernameInput?.value?.trim()
    const missingPassword = !passwordInput?.value
    if (missingServerUrl) {
      markFieldInvalid(serverUrlField, serverUrlInput)
    }
    if (missingUsername) {
      markFieldInvalid(usernameField, usernameInput)
    }
    if (missingPassword) {
      markFieldInvalid(passwordField, passwordInput)
    }
    return !(missingServerUrl || missingUsername || missingPassword)
  }

  function isSignedIn(config = currentConfig) {
    return Boolean(config?.serverUrl && config?.token)
  }

  function setIdleButtonLabel(button, text) {
    if (!button) {
      return
    }
    button.idleText = text
    button.textContent = text
  }

  function syncAuthenticationUi(config = currentConfig) {
    const signedIn = isSignedIn(config)
    const userLabel = config?.username || 'InboxBridge'
    setIdleButtonLabel(saveButton, t(signedIn ? 'settings.signOut' : 'settings.signIn'))
    setIdleButtonLabel(detectButton, t('settings.useCurrentTab'))
    if (authSummaryCard) {
      authSummaryCard.hidden = !signedIn
    }
    if (authSummaryText) {
      authSummaryText.textContent = signedIn
        ? t('settings.signedInAs', { username: userLabel })
        : t('settings.signedOutHelp')
    }
    if (serverUrlField) {
      serverUrlField.hidden = signedIn
    }
    if (usernameField) {
      usernameField.hidden = signedIn
    }
    if (passwordField) {
      passwordField.hidden = signedIn
    }
    if (serverUrlInput) {
      serverUrlInput.readOnly = signedIn
      serverUrlInput.disabled = signedIn
    }
    if (usernameInput) {
      usernameInput.readOnly = signedIn
      usernameInput.disabled = signedIn
    }
    if (passwordInput) {
      passwordInput.value = signedIn ? '' : passwordInput.value
      passwordInput.readOnly = signedIn
      passwordInput.disabled = signedIn
    }
    if (detectButton) {
      detectButton.disabled = signedIn
    }
    if (testButton) {
      testButton.disabled = !signedIn
    }
  }

  function applyConfigState(config) {
    currentConfig = {
      ...currentConfig,
      ...(config || {})
    }
    currentLocale = resolveLanguagePreference?.(currentConfig) || currentLocale
    localizeOptionsPage?.(targetDocument, currentLocale, {
      languageValue: languageInput?.value || currentConfig.language || 'user',
      themeValue: themeInput?.value || currentConfig.theme || 'user'
    })
    applyThemePreference?.(targetDocument, currentConfig.theme || 'user', currentConfig.userThemeMode || 'SYSTEM')
    syncAuthenticationUi(currentConfig)
  }

  async function initialize() {
    const config = await loadConfig()
    const theme = config?.theme || 'user'
    const language = config?.language || 'user'
    if (languageInput) {
      languageInput.value = language
    }
    if (themeInput) {
      themeInput.value = theme
    }
    if (notifyErrorsInput) {
      notifyErrorsInput.checked = Boolean(config?.notifyErrors)
    }
    if (notifyManualPollSuccessInput) {
      notifyManualPollSuccessInput.checked = Boolean(config?.notifyManualPollSuccess)
    }
    applyConfigState({ ...config, language, theme })
    if (config?.serverUrl) {
      serverUrlInput.value = config.serverUrl
    } else {
      const detected = await detectServerUrl().catch(() => null)
      if (detected) {
        serverUrlInput.value = detected
      }
    }
    if (config?.username) {
      usernameInput.value = config.username
    }
  }

  async function connect(event) {
    event?.preventDefault?.()
    if (isSignedIn()) {
      await signOut()
      return
    }
    if (!validateRequiredFields()) {
      showStatus(statusBanner, 'error', t('errors.requiredFields'))
      return
    }
    await withBusy(saveButton, async () => {
      const serverUrl = normalizeServerUrl(serverUrlInput.value)
      const granted = await ensureOriginPermission(serverUrl)
      if (!granted) {
        throw new Error(t('errors.extensionOriginPermission'))
      }

      const metadata = getBrowserMetadata()
      let authResponse = await loginExtension(serverUrl, {
        username: usernameInput.value.trim(),
        password: passwordInput.value,
        label: metadata.label,
        browserFamily: metadata.browserFamily,
        extensionVersion: metadata.extensionVersion
      })

      if (authResponse?.status === 'PASSKEY_REQUIRED') {
        showStatus(statusBanner, 'neutral', t('status.completeBrowserSignIn'))
        const session = await completeBrowserSignIn({
          browserFamily: metadata.browserFamily,
          extensionVersion: metadata.extensionVersion,
          label: metadata.label,
          serverUrl
        })
        authResponse = {
          session,
          status: 'AUTHENTICATED'
        }
      }

      const normalized = sessionToConfig(serverUrl, authResponse.session)
      if (normalized?.serverUrl && normalized.serverUrl !== serverUrl) {
        const grantedCanonicalOrigin = await ensureOriginPermission(normalized.serverUrl)
        if (!grantedCanonicalOrigin) {
          throw new Error(t('errors.extensionOriginPermission'))
        }
      }
      normalized.language = languageInput?.value || 'user'
      normalized.notifyErrors = Boolean(notifyErrorsInput?.checked)
      normalized.notifyManualPollSuccess = Boolean(notifyManualPollSuccessInput?.checked)
      normalized.theme = themeInput?.value || 'user'
      await saveConfig(normalized)
      applyConfigState(normalized)
      serverUrlInput.value = normalized.serverUrl
      usernameInput.value = normalized.username || usernameInput.value.trim()
      passwordInput.value = ''
      clearRequiredFieldErrors()
      await sendMessage({ type: 'refresh-status' })
      await sendMessage({ type: 'refresh-context-menus' })
      showStatus(statusBanner, 'success', t('status.connectedAs', {
        username: normalized.username || 'InboxBridge'
      }))
    })
  }

  async function testConnection() {
    await withBusy(testButton, async () => {
      const config = await loadConfig()
      if (!config?.serverUrl || !config?.token) {
        throw new Error(t('status.signInFirst'))
      }
      const status = await fetchStatus(config.serverUrl, config.token)
      await saveUserPreferences?.(status.user)
      applyConfigState({
        ...config,
        userLanguage: status.user?.language,
        userThemeMode: status.user?.themeMode
      })
      showStatus(statusBanner, 'success', t('status.connectedAs', {
        username: status.user?.displayName || status.user?.username || config.username || 'InboxBridge'
      }))
    })
  }

  async function detectCurrentTab() {
    await withBusy(detectButton, async () => {
      const granted = await ensureTabPermission()
      if (!granted) {
        throw new Error(t('errors.tabPermission'))
      }
      const detected = await detectServerUrl()
      if (!detected) {
        throw new Error(t('errors.openInboxBridgeTab'))
      }
      serverUrlInput.value = detected
      showStatus(statusBanner, 'neutral', t('status.detectedUrl'))
    })
  }

  async function clearSettings() {
    await clearConfig()
    passwordInput.value = ''
    usernameInput.value = ''
    clearRequiredFieldErrors()
    applyConfigState({ serverUrl: '', token: '', username: '' })
    await sendMessage({ type: 'refresh-status' })
    await sendMessage({ type: 'refresh-context-menus' })
    showStatus(statusBanner, 'neutral', t('status.cleared'))
  }

  async function signOut() {
    await withBusy(saveButton, async () => {
      const preservedConfig = {
        language: currentConfig.language,
        notifyErrors: currentConfig.notifyErrors,
        notifyManualPollSuccess: currentConfig.notifyManualPollSuccess,
        serverUrl: currentConfig.serverUrl,
        theme: currentConfig.theme,
        userLanguage: currentConfig.userLanguage,
        userThemeMode: currentConfig.userThemeMode,
        username: currentConfig.username
      }
      await clearConfig()
      passwordInput.value = ''
      clearRequiredFieldErrors()
      applyConfigState({ ...preservedConfig, token: '', refreshToken: '' })
      await sendMessage({ type: 'refresh-status' })
      await sendMessage({ type: 'refresh-context-menus' })
      showStatus(statusBanner, 'neutral', t('status.signedOut'))
    })
  }

  async function changeTheme() {
    const theme = themeInput.value
    applyConfigState({ theme })
    await saveThemePreference?.(theme)
    showStatus(statusBanner, 'neutral', t('status.themeSet'))
  }

  async function changeLanguage() {
    const language = languageInput.value
    await saveLanguagePreference?.(language)
    applyConfigState({ language })
    await sendMessage({ type: 'refresh-context-menus' })
    showStatus(statusBanner, 'neutral', t('status.languageSet'))
  }

  async function changeNotificationSetting(key, input) {
    const enabled = Boolean(input?.checked)
    try {
      if (enabled) {
        const granted = await ensureNotificationPermission?.()
        if (!granted) {
          throw new Error(t('errors.notificationPermission'))
        }
      }
      await saveNotificationPreferences?.({ [key]: enabled })
      applyConfigState({ [key]: enabled })
      showStatus(statusBanner, 'neutral', t('status.notificationsSet'))
    } catch (error) {
      if (input) {
        input.checked = !enabled
      }
      showStatus(statusBanner, 'error', error.message || t('errors.requestFailed'))
    }
  }

  async function withBusy(button, work) {
    const originalText = button.idleText || button.textContent
    try {
      button.disabled = true
      button.textContent = t('status.working')
      hideStatus(statusBanner)
      await work()
    } catch (error) {
      showStatus(statusBanner, 'error', error.message || t('errors.requestFailed'))
    } finally {
      button.disabled = false
      button.textContent = button.idleText || originalText
    }
  }

  function bind() {
    form.addEventListener('submit', (event) => connect(event))
    testButton.addEventListener('click', () => testConnection())
    detectButton.addEventListener('click', () => detectCurrentTab())
    clearButton.addEventListener('click', () => clearSettings())
    serverUrlInput?.addEventListener('input', () => clearFieldInvalidState(serverUrlField, serverUrlInput))
    usernameInput?.addEventListener('input', () => clearFieldInvalidState(usernameField, usernameInput))
    passwordInput?.addEventListener('input', () => clearFieldInvalidState(passwordField, passwordInput))
    languageInput?.addEventListener('change', () => void changeLanguage())
    notifyErrorsInput?.addEventListener('change', () => void changeNotificationSetting('notifyErrors', notifyErrorsInput))
    notifyManualPollSuccessInput?.addEventListener('change', () => void changeNotificationSetting('notifyManualPollSuccess', notifyManualPollSuccessInput))
    themeInput?.addEventListener('change', () => void changeTheme())
  }

  return {
    bind,
    clearSettings,
    changeTheme,
    connect,
    detectCurrentTab,
    initialize,
    testConnection
  }
}
