import { useEffect, useMemo, useRef, useState } from 'react'

import FormField from '@/shared/components/FormField'
import InfoHint from '@/shared/components/InfoHint'
import LoadingButton from '@/shared/components/LoadingButton'
import ModalDialog from '@/shared/components/ModalDialog'
import PasswordField from '@/shared/components/PasswordField'
import { DESTINATION_PROVIDER_PRESETS, findDestinationProviderPreset, normalizeDestinationProviderConfig } from '@/lib/emailProviderPresets'

/**
 * Provider-neutral destination editor that coordinates password vs OAuth
 * inputs, detected-folder selection, connection testing, and the different
 * save/re-authenticate paths for Gmail and IMAP APPEND destinations.
 */
function DestinationMailboxDialog({
  destinationConfig,
  destinationFolders = [],
  destinationFoldersLoading = false,
  destinationMeta,
  oauthLoading = false,
  onClose,
  onSave,
  onSaveAndAuthenticate,
  onTestConnection,
  onUnlink,
  saveLoading = false,
  setDestinationConfig,
  t,
  testConnectionLoading = false,
  unlinkLoading = false
}) {
  const resolvedDestinationConfig = normalizeDestinationProviderConfig(destinationConfig)
  const provider = resolvedDestinationConfig.provider || 'GMAIL_API'
  const providerPreset = findDestinationProviderPreset(provider)
  const configured = Boolean(destinationMeta?.configured)
  const isGmailProvider = provider === 'GMAIL_API'
  const usesMicrosoftOAuth = !isGmailProvider && resolvedDestinationConfig.authMethod === 'OAUTH2' && resolvedDestinationConfig.oauthProvider === 'MICROSOFT'
  const usesPassword = !isGmailProvider && !usesMicrosoftOAuth
  const savedProvider = destinationMeta?.provider || provider
  const detectedFolders = !isGmailProvider && configured && destinationMeta?.linked && savedProvider === provider ? destinationFolders : []
  const currentFolder = resolvedDestinationConfig.folder || ''
  const currentFolderDetected = detectedFolders.includes(currentFolder)
  const defaultDetectedFolder = detectedFolders.find((folder) => folder.toUpperCase() === 'INBOX') || detectedFolders[0] || ''
  const [manualFolderEntry, setManualFolderEntry] = useState(detectedFolders.length === 0)
  const [testResult, setTestResult] = useState(null)
  const initialConfigRef = useRef(normalizeDestinationProviderConfig(destinationConfig))
  const initialConfig = initialConfigRef.current
  const isDirty = JSON.stringify(initialConfig) !== JSON.stringify(resolvedDestinationConfig)
  const canLaunchOAuth = isGmailProvider || usesMicrosoftOAuth
  const showUnlinkButton = Boolean(destinationMeta?.linked) && savedProvider === provider
  const canSaveAndAuthenticate = isGmailProvider || (
    Boolean(resolvedDestinationConfig.host?.trim())
    && Boolean(resolvedDestinationConfig.username?.trim())
    && Boolean(resolvedDestinationConfig.folder?.trim())
  )
  const microsoftReauthRequired = usesMicrosoftOAuth && (
    initialConfig.provider !== resolvedDestinationConfig.provider
    || initialConfig.host !== resolvedDestinationConfig.host
    || Number(initialConfig.port || 0) !== Number(resolvedDestinationConfig.port || 0)
    || Boolean(initialConfig.tls) !== Boolean(resolvedDestinationConfig.tls)
    || initialConfig.username !== resolvedDestinationConfig.username
    || initialConfig.authMethod !== resolvedDestinationConfig.authMethod
    || initialConfig.oauthProvider !== resolvedDestinationConfig.oauthProvider
  )
  const canPlainSave = !isGmailProvider && (!usesMicrosoftOAuth || (isDirty && !microsoftReauthRequired))
  const canTestConnection = useMemo(() => {
    if (isGmailProvider) {
      return false
    }
    if (!resolvedDestinationConfig.host?.trim() || !resolvedDestinationConfig.username?.trim()) {
      return false
    }
    if (usesMicrosoftOAuth) {
      return configured && savedProvider === provider && Boolean(destinationMeta?.oauthConnected)
    }
    return Boolean(resolvedDestinationConfig.password?.trim() || (configured && savedProvider === provider && destinationMeta?.passwordConfigured))
  }, [configured, destinationMeta?.oauthConnected, destinationMeta?.passwordConfigured, isGmailProvider, provider, resolvedDestinationConfig.host, resolvedDestinationConfig.password, resolvedDestinationConfig.username, savedProvider, usesMicrosoftOAuth])

  useEffect(() => {
    if (!detectedFolders.length) {
      setManualFolderEntry(true)
      return
    }
    if (currentFolder && !currentFolderDetected) {
      setManualFolderEntry(true)
      return
    }
    setManualFolderEntry(false)
  }, [currentFolder, currentFolderDetected, detectedFolders])

  useEffect(() => {
    if (!detectedFolders.length || currentFolder || !defaultDetectedFolder) {
      return
    }
    setDestinationConfig((current) => {
      const normalizedCurrent = normalizeDestinationProviderConfig(current)
      if (normalizedCurrent.provider !== provider || (normalizedCurrent.folder || '') === defaultDetectedFolder) {
        return current
      }
      return { ...current, folder: defaultDetectedFolder }
    })
  }, [currentFolder, defaultDetectedFolder, detectedFolders, provider, setDestinationConfig])

  useEffect(() => {
    setTestResult(null)
  }, [provider, resolvedDestinationConfig.host, resolvedDestinationConfig.port, resolvedDestinationConfig.username, resolvedDestinationConfig.folder, resolvedDestinationConfig.password, resolvedDestinationConfig.tls])

  function updateProvider(nextProvider) {
    const preset = findDestinationProviderPreset(nextProvider)
    setDestinationConfig((current) => normalizeDestinationProviderConfig({
      ...current,
      provider: nextProvider,
      host: preset.values?.host ?? '',
      port: preset.values?.port ?? 993,
      tls: preset.values?.tls ?? true,
      authMethod: preset.values?.authMethod ?? 'PASSWORD',
      oauthProvider: preset.values?.oauthProvider ?? 'NONE',
      username: preset.values?.username ?? '',
      password: '',
      folder: preset.values?.folder ?? 'INBOX'
    }))
  }

  function useDetectedFolderOptions() {
    if (!defaultDetectedFolder) {
      return
    }
    setDestinationConfig((current) => ({ ...current, folder: currentFolderDetected ? current.folder : defaultDetectedFolder }))
    setManualFolderEntry(false)
  }

  async function handleSave(event) {
    event.preventDefault()
    if (!canPlainSave) {
      return
    }
    await onSave()
    onClose()
  }

  async function handleSaveAndAuthenticate() {
    await onSaveAndAuthenticate()
  }

  async function handleTestConnection() {
    try {
      const result = await onTestConnection()
      setTestResult({ ...result, tone: 'success' })
    } catch (error) {
      setTestResult({ tone: 'error', message: error.message || t('errors.testDestinationConnection') })
    }
  }

  return (
    <ModalDialog
      isDirty={isDirty}
      onClose={onClose}
      size="wide"
      title={t(configured ? 'destination.editDialogTitle' : 'destination.addDialogTitle')}
      unsavedChangesMessage={t('common.unsavedChangesConfirm')}
    >
      <p className="section-copy">{t('destination.dialogCopy')}</p>
      <form className="settings-grid destination-dialog-form" onSubmit={handleSave}>
        <FormField helpText={t('destination.providerHelp')} label={t('destination.provider')}>
          <select aria-label={t('destination.provider')} value={provider} onChange={(event) => updateProvider(event.target.value)}>
            {DESTINATION_PROVIDER_PRESETS.map((preset) => (
              <option key={preset.id} value={preset.id}>{t(preset.labelKey)}</option>
            ))}
          </select>
        </FormField>
        <div className="full muted-box">{t(providerPreset.descriptionKey)}</div>

        {isGmailProvider ? (
          <div className="full muted-box">
            <strong>{t('destination.gmailPendingTitle')}</strong><br />
            {t('destination.gmailPendingBody')}
          </div>
        ) : (
          <>
            <FormField helpText={t('destination.hostHelp')} label={t('destination.host')}>
              <input aria-label={t('destination.host')} value={resolvedDestinationConfig.host} onChange={(event) => setDestinationConfig((current) => ({ ...current, host: event.target.value }))} />
            </FormField>
            <FormField helpText={t('destination.portHelp')} label={t('destination.port')}>
              <input aria-label={t('destination.port')} type="number" value={resolvedDestinationConfig.port} onChange={(event) => setDestinationConfig((current) => ({ ...current, port: Number(event.target.value) || '' }))} />
            </FormField>
            <FormField helpText={t('emailAccounts.usernameHelp')} label={t('emailAccounts.username')}>
              <input aria-label={t('emailAccounts.username')} value={resolvedDestinationConfig.username} onChange={(event) => setDestinationConfig((current) => ({ ...current, username: event.target.value }))} />
            </FormField>
            {usesPassword ? (
              <PasswordField
                helpText={t('emailAccounts.passwordHelp')}
                hideLabel={t('common.hideField', { label: t('emailAccounts.password') })}
                label={t('emailAccounts.password')}
                placeholder={configured && savedProvider === provider && destinationMeta?.passwordConfigured ? t('emailAccounts.keepExisting') : ''}
                showLabel={t('common.showField', { label: t('emailAccounts.password') })}
                value={resolvedDestinationConfig.password}
                onChange={(event) => setDestinationConfig((current) => ({ ...current, password: event.target.value }))}
              />
            ) : (
              <div className="full muted-box">{t('destination.microsoftOauthHelp')}</div>
            )}
            <div className="destination-folder-control full">
              <label>
                <span className="field-label-row">
                  <span>{t('emailAccounts.folder')}</span>
                  <InfoHint text={t('emailAccounts.folderHelp')} />
                </span>
                {detectedFolders.length > 0 && !manualFolderEntry ? (
                  <select aria-label={t('emailAccounts.folder')} value={currentFolderDetected ? currentFolder : defaultDetectedFolder} onChange={(event) => setDestinationConfig((current) => ({ ...current, folder: event.target.value }))}>
                    {detectedFolders.map((folder) => (
                      <option key={folder} value={folder}>{folder}</option>
                    ))}
                  </select>
                ) : (
                  <input aria-label={t('emailAccounts.folder')} value={resolvedDestinationConfig.folder} onChange={(event) => setDestinationConfig((current) => ({ ...current, folder: event.target.value }))} />
                )}
              </label>
              <div className="destination-folder-actions">
                {destinationFoldersLoading && savedProvider === provider && destinationMeta?.linked ? (
                  <span className="destination-folder-status">{t('common.refreshingSection')}</span>
                ) : null}
                {detectedFolders.length > 0 ? (
                  <button className="destination-folder-toggle" onClick={manualFolderEntry ? useDetectedFolderOptions : () => setManualFolderEntry(true)} type="button">
                    {t(manualFolderEntry ? 'destination.folderUseDetected' : 'destination.folderUseManual')}
                  </button>
                ) : null}
              </div>
            </div>
            <label className="checkbox-row full destination-dialog-checkbox">
              <input checked={true} disabled readOnly type="checkbox" />
              <span className="field-label-row">
                <span>{t('emailAccounts.tlsOnly')}</span>
                <InfoHint text={t('emailAccounts.tlsOnlyHelp')} />
              </span>
            </label>
          </>
        )}

        <div className="full action-row">
          {!isGmailProvider ? (
            <LoadingButton className="secondary" disabled={!canTestConnection} isLoading={testConnectionLoading} loadingLabel={t('emailAccounts.testConnectionLoading')} onClick={handleTestConnection} type="button">
              {t('emailAccounts.testConnection')}
            </LoadingButton>
          ) : null}
          {canLaunchOAuth ? (
            <LoadingButton className="primary" disabled={!canSaveAndAuthenticate} isLoading={oauthLoading || saveLoading} loadingLabel={t('destination.saveAndAuthenticateLoading')} onClick={handleSaveAndAuthenticate} type="button">
              {t('destination.saveAndAuthenticate')}
            </LoadingButton>
          ) : null}
          {showUnlinkButton ? (
            <LoadingButton className="secondary" isLoading={unlinkLoading} loadingLabel={t('destination.unlinkLoading')} onClick={onUnlink} type="button">
              {t('destination.unlink')}
            </LoadingButton>
          ) : null}
          {canPlainSave ? (
            <LoadingButton className="primary" disabled={!isDirty} isLoading={saveLoading} loadingLabel={t('destination.saveLoading')} type="submit">
              {t('destination.save')}
            </LoadingButton>
          ) : null}
          <button className="secondary" onClick={onClose} type="button">
            {t('common.cancel')}
          </button>
        </div>

        {testResult ? (
          <div className={`full ${testResult.tone === 'error' ? 'banner-error' : 'muted-box'} fetcher-test-result`}>
            <strong>{testResult.message}</strong>
            {testResult.tone !== 'error' && testResult.protocol ? (
              <dl className="fetcher-test-result-grid">
                <div><dt>{t('emailAccounts.testProtocol')}</dt><dd>{testResult.protocol}</dd></div>
                <div><dt>{t('emailAccounts.testEndpoint')}</dt><dd>{testResult.host}:{testResult.port}</dd></div>
                <div><dt>{t('emailAccounts.testTls')}</dt><dd>{testResult.tls ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('emailAccounts.testAuth')}</dt><dd>{t(`authMethod.${String(testResult.authMethod || '').toLowerCase()}`)}{testResult.oauthProvider && testResult.oauthProvider !== 'NONE' ? ` / ${t(`oauthProvider.${String(testResult.oauthProvider).toLowerCase()}`)}` : ''}</dd></div>
                <div><dt>{t('emailAccounts.testAuthenticated')}</dt><dd>{testResult.authenticated ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('emailAccounts.testFolder')}</dt><dd>{testResult.folder || 'INBOX'}</dd></div>
                <div><dt>{t('emailAccounts.testFolderAccessible')}</dt><dd>{testResult.folderAccessible ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('emailAccounts.testVisibleMessages')}</dt><dd>{testResult.visibleMessageCount ?? t('common.unavailable')}</dd></div>
              </dl>
            ) : null}
          </div>
        ) : null}
      </form>
    </ModalDialog>
  )
}

export default DestinationMailboxDialog
