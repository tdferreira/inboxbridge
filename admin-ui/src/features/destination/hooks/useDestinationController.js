import { normalizeDestinationProviderConfig } from '@/lib/emailProviderPresets'
import { pollErrorNotification, translatedNotification } from '@/lib/notifications'

export function useDestinationController({
  closeConfirmation,
  destinationConfig,
  destinationMeta,
  errorText,
  language,
  loadAppData,
  openConfirmation,
  pushNotification,
  t,
  withPending
}) {
  const normalizedDestinationConfig = normalizeDestinationProviderConfig(destinationConfig)

  function resolveNormalizedConfig(configOverride) {
    return normalizeDestinationProviderConfig(configOverride || destinationConfig)
  }

  function buildDestinationRequestBody(configOverride) {
    const resolvedConfig = resolveNormalizedConfig(configOverride)
    return {
      provider: resolvedConfig.provider,
      host: resolvedConfig.host,
      port: resolvedConfig.port === '' ? null : Number(resolvedConfig.port),
      tls: resolvedConfig.tls,
      authMethod: resolvedConfig.authMethod,
      oauthProvider: resolvedConfig.oauthProvider,
      username: resolvedConfig.username,
      password: resolvedConfig.password,
      folder: resolvedConfig.folder
    }
  }

  function currentLinkedOAuthProvider() {
    if (!destinationMeta?.linked && !destinationMeta?.oauthConnected) {
      return null
    }
    const linkedProvider = destinationMeta?.provider || normalizedDestinationConfig.provider
    return linkedProvider === 'GMAIL_API' ? 'GOOGLE' : 'MICROSOFT'
  }

  function requestedLinkedOAuthProvider(configOverride) {
    const resolvedConfig = resolveNormalizedConfig(configOverride)
    if (resolvedConfig.provider === 'GMAIL_API') {
      return 'GOOGLE'
    }
    if (resolvedConfig.authMethod === 'OAUTH2' && resolvedConfig.oauthProvider === 'MICROSOFT') {
      return 'MICROSOFT'
    }
    return null
  }

  function linkedDestinationReplacementConfirmation(actionKey, onConfirm) {
    const currentProvider = currentLinkedOAuthProvider()
    if (!currentProvider) {
      return false
    }
    openConfirmation({
      actionKey,
      body: t(currentProvider === 'GOOGLE' ? 'destination.replaceLinkedGoogleConfirmBody' : 'destination.replaceLinkedMicrosoftConfirmBody'),
      confirmLabel: t('common.confirm'),
      confirmLoadingLabel: t('common.confirm'),
      confirmTone: 'danger',
      onConfirm,
      title: t('destination.replaceLinkedConfirmTitle')
    })
    return true
  }

  async function persistDestinationConfig(configOverride) {
    await withPending('destinationSave', async () => {
      try {
        const response = await fetch('/api/app/destination-config', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildDestinationRequestBody(configOverride))
        })
        if (!response.ok) {
          throw new Error(await errorText('saveDestinationConfiguration', response))
        }
        pushNotification({ message: translatedNotification('notifications.destinationSaved'), targetId: 'destination-mailbox-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveDestinationConfiguration'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveDestinationConfiguration'),
          targetId: 'destination-mailbox-section',
          tone: 'error'
        })
        throw err
      }
    })
  }

  async function saveDestinationConfig(configOverride, event) {
    if (configOverride && typeof configOverride.preventDefault === 'function' && event == null) {
      event = configOverride
      configOverride = null
    }
    event?.preventDefault?.()
    const currentProvider = currentLinkedOAuthProvider()
    const requestedProvider = requestedLinkedOAuthProvider(configOverride)
    if (currentProvider && currentProvider !== requestedProvider) {
      linkedDestinationReplacementConfirmation('destinationSave', async () => {
        closeConfirmation()
        await persistDestinationConfig(configOverride)
      })
      return
    }
    await persistDestinationConfig(configOverride)
  }

  async function testDestinationConnection(configOverride) {
    return withPending('destinationConnectionTest', async () => {
      const response = await fetch('/api/app/destination-config/test-connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildDestinationRequestBody(configOverride))
      })
      if (!response.ok) {
        throw new Error(await errorText('testDestinationConnection', response))
      }
      return response.json()
    })
  }

  async function performDestinationSave(fetchCall, fallbackKey) {
    const response = await fetchCall()
    if (!response.ok) {
      throw new Error(await errorText(fallbackKey, response))
    }
    return response.json()
  }

  async function unlinkDestinationAccount() {
    const linkedProvider = destinationMeta?.provider || normalizedDestinationConfig.provider
    openConfirmation({
      actionKey: 'destinationUnlink',
      body: t(linkedProvider === 'GMAIL_API' ? 'gmail.unlinkConfirmBody' : 'destination.unlinkConfirmBody'),
      confirmLabel: t('destination.unlink'),
      confirmLoadingLabel: t('destination.unlinkLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending('destinationUnlink', async () => {
          try {
            const payload = await performDestinationSave(
              () => fetch('/api/account/destination-link', { method: 'DELETE' }),
              'unlinkDestinationAccount'
            )
            closeConfirmation()
            if (linkedProvider === 'GMAIL_API' && payload.providerRevocationAttempted && !payload.providerRevoked) {
              pushNotification({
                autoCloseMs: null,
                copyText: translatedNotification('notifications.gmailUnlinkedRevokeFailed'),
                message: translatedNotification('notifications.gmailUnlinkedRevokeFailed'),
                targetId: 'destination-mailbox-section',
                tone: 'warning'
              })
            } else if (linkedProvider !== 'GMAIL_API') {
              pushNotification({
                autoCloseMs: null,
                copyText: translatedNotification('notifications.microsoftDestinationUnlinked'),
                message: translatedNotification('notifications.microsoftDestinationUnlinked'),
                targetId: 'destination-mailbox-section',
                tone: 'warning'
              })
            } else {
              pushNotification({ message: translatedNotification('notifications.destinationUnlinked'), targetId: 'destination-mailbox-section', tone: 'success' })
            }
            await loadAppData()
          } catch (err) {
            pushNotification({
              autoCloseMs: null,
              copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.unlinkDestinationAccount'),
              message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.unlinkDestinationAccount'),
              targetId: 'destination-mailbox-section',
              tone: 'error'
            })
          }
        })
      },
      title: t(linkedProvider === 'GMAIL_API' ? 'gmail.unlinkConfirmTitle' : 'destination.unlinkConfirmTitle')
    })
  }

  function navigateToGoogleOAuthSelf() {
    return withPending('googleOAuthSelf', async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign(`/api/google-oauth/start/self?lang=${encodeURIComponent(language)}`)
          resolve()
        }, 75)
      })
    })
  }

  async function startGoogleOAuthSelf() {
    const currentProvider = currentLinkedOAuthProvider()
    if (!currentProvider) {
      if (destinationMeta?.provider && destinationMeta.provider !== 'GMAIL_API') {
        await persistDestinationConfig()
      }
      await navigateToGoogleOAuthSelf()
      return
    }
    if (currentProvider !== 'GOOGLE') {
      linkedDestinationReplacementConfirmation('googleOAuthSelf', async () => {
        closeConfirmation()
        await persistDestinationConfig()
        navigateToGoogleOAuthSelf()
      })
      return
    }
    openConfirmation({
      actionKey: 'googleOAuthSelf',
      body: t('gmail.reconnectConfirmBody'),
      confirmLabel: t('gmail.reconnect'),
      confirmLoadingLabel: t('gmail.reconnectLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        closeConfirmation()
          await navigateToGoogleOAuthSelf()
      },
      title: t('gmail.reconnectConfirmTitle')
    })
  }

  function navigateToMicrosoftDestinationOAuth() {
    return withPending('microsoftDestinationOAuth', async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign(`/api/microsoft-oauth/start/destination?lang=${encodeURIComponent(language)}`)
          resolve()
        }, 75)
      })
    })
  }

  async function startMicrosoftDestinationOAuth() {
    const currentProvider = currentLinkedOAuthProvider()
    const requestedProvider = requestedLinkedOAuthProvider()
    if (currentProvider && (currentProvider !== requestedProvider || currentProvider === 'MICROSOFT')) {
      linkedDestinationReplacementConfirmation('microsoftDestinationOAuth', async () => {
        closeConfirmation()
        await persistDestinationConfig()
        await navigateToMicrosoftDestinationOAuth()
      })
      return
    }
    await persistDestinationConfig()
    await navigateToMicrosoftDestinationOAuth()
  }

  async function saveDestinationConfigAndAuthenticate(configOverride) {
    const resolvedConfig = resolveNormalizedConfig(configOverride)
    if (resolvedConfig.provider === 'GMAIL_API') {
      const currentProvider = currentLinkedOAuthProvider()
      if (!currentProvider) {
        await persistDestinationConfig(configOverride)
        await navigateToGoogleOAuthSelf()
        return
      }
      if (currentProvider !== 'GOOGLE') {
        linkedDestinationReplacementConfirmation('googleOAuthSelf', async () => {
          closeConfirmation()
          await persistDestinationConfig(configOverride)
          await navigateToGoogleOAuthSelf()
        })
        return
      }
      openConfirmation({
        actionKey: 'googleOAuthSelf',
        body: t('gmail.reconnectConfirmBody'),
        confirmLabel: t('gmail.reconnect'),
        confirmLoadingLabel: t('gmail.reconnectLoading'),
        confirmTone: 'danger',
        onConfirm: async () => {
          closeConfirmation()
          await persistDestinationConfig(configOverride)
          await navigateToGoogleOAuthSelf()
        },
        title: t('gmail.reconnectConfirmTitle')
      })
      return
    }
    const currentProvider = currentLinkedOAuthProvider()
    const requestedProvider = requestedLinkedOAuthProvider(configOverride)
    if (currentProvider && (currentProvider !== requestedProvider || currentProvider === 'MICROSOFT')) {
      linkedDestinationReplacementConfirmation('microsoftDestinationOAuth', async () => {
        closeConfirmation()
        await persistDestinationConfig(configOverride)
        await navigateToMicrosoftDestinationOAuth()
      })
      return
    }
    await persistDestinationConfig(configOverride)
    await navigateToMicrosoftDestinationOAuth()
  }

  async function startDestinationOAuth() {
    if (normalizedDestinationConfig.provider === 'GMAIL_API') {
      await startGoogleOAuthSelf()
      return
    }
    await startMicrosoftDestinationOAuth()
  }

  return {
    saveDestinationConfig,
    saveDestinationConfigAndAuthenticate,
    startDestinationOAuth,
    testDestinationConnection,
    unlinkDestinationAccount
  }
}
