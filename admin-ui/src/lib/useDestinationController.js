import { normalizeDestinationProviderConfig } from './emailProviderPresets'

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

  function currentLinkedOAuthProvider() {
    if (!destinationMeta?.linked && !destinationMeta?.oauthConnected) {
      return null
    }
    const linkedProvider = destinationMeta?.provider || normalizedDestinationConfig.provider
    return linkedProvider === 'GMAIL_API' ? 'GOOGLE' : 'MICROSOFT'
  }

  function requestedLinkedOAuthProvider() {
    if (normalizedDestinationConfig.provider === 'GMAIL_API') {
      return 'GOOGLE'
    }
    if (normalizedDestinationConfig.authMethod === 'OAUTH2' && normalizedDestinationConfig.oauthProvider === 'MICROSOFT') {
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

  async function persistDestinationConfig() {
    await withPending('destinationSave', async () => {
      try {
        const response = await fetch('/api/app/destination-config', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            provider: normalizedDestinationConfig.provider,
            host: normalizedDestinationConfig.host,
            port: normalizedDestinationConfig.port === '' ? null : Number(normalizedDestinationConfig.port),
            tls: normalizedDestinationConfig.tls,
            authMethod: normalizedDestinationConfig.authMethod,
            oauthProvider: normalizedDestinationConfig.oauthProvider,
            username: normalizedDestinationConfig.username,
            password: normalizedDestinationConfig.password,
            folder: normalizedDestinationConfig.folder
          })
        })
        if (!response.ok) {
          throw new Error(await errorText('saveDestinationConfiguration', response))
        }
        pushNotification({ message: t('notifications.destinationSaved'), targetId: 'destination-mailbox-section', tone: 'success' })
        await loadAppData()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || t('errors.saveDestinationConfiguration'), message: err.message || t('errors.saveDestinationConfiguration'), targetId: 'destination-mailbox-section', tone: 'error' })
        throw err
      }
    })
  }

  async function saveDestinationConfig(event) {
    event.preventDefault()
    const currentProvider = currentLinkedOAuthProvider()
    const requestedProvider = requestedLinkedOAuthProvider()
    if (currentProvider && currentProvider !== requestedProvider) {
      linkedDestinationReplacementConfirmation('destinationSave', async () => {
        closeConfirmation()
        await persistDestinationConfig()
      })
      return
    }
    await persistDestinationConfig()
  }

  async function performDestinationSave(fetchCall, fallbackKey) {
    const response = await fetchCall()
    if (!response.ok) {
      throw new Error(await errorText(fallbackKey, response))
    }
    return response.json()
  }

  async function unlinkDestinationAccount() {
    openConfirmation({
      actionKey: 'destinationUnlink',
      body: t(destinationConfig.provider === 'GMAIL_API' ? 'gmail.unlinkConfirmBody' : 'destination.unlinkConfirmBody'),
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
            if (destinationConfig.provider === 'GMAIL_API' && payload.providerRevocationAttempted && !payload.providerRevoked) {
              pushNotification({
                autoCloseMs: null,
                copyText: t('notifications.gmailUnlinkedRevokeFailed'),
                message: t('notifications.gmailUnlinkedRevokeFailed'),
                targetId: 'destination-mailbox-section',
                tone: 'warning'
              })
            } else {
              pushNotification({ message: t('notifications.destinationUnlinked'), targetId: 'destination-mailbox-section', tone: 'success' })
            }
            await loadAppData()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || t('errors.unlinkDestinationAccount'), message: err.message || t('errors.unlinkDestinationAccount'), targetId: 'destination-mailbox-section', tone: 'error' })
          }
        })
      },
      title: t(destinationConfig.provider === 'GMAIL_API' ? 'gmail.unlinkConfirmTitle' : 'destination.unlinkConfirmTitle')
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

  async function startDestinationOAuth() {
    if (normalizedDestinationConfig.provider === 'GMAIL_API') {
      await startGoogleOAuthSelf()
      return
    }
    await startMicrosoftDestinationOAuth()
  }

  return {
    saveDestinationConfig,
    startDestinationOAuth,
    unlinkDestinationAccount
  }
}