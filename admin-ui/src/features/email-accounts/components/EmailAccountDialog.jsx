import { useEffect, useMemo, useRef, useState } from 'react'
import FormField from '@/shared/components/FormField'
import InfoHint from '@/shared/components/InfoHint'
import LoadingButton from '@/shared/components/LoadingButton'
import ModalDialog from '@/shared/components/ModalDialog'
import PasswordField from '@/shared/components/PasswordField'
import PillboxInput from '@/shared/components/PillboxInput'
import { EMAIL_PROVIDER_PRESETS, findEmailProviderPreset, inferEmailProviderPresetId, isOutlookSourceConfig } from '@/lib/emailProviderPresets'
import './EmailAccountDialog.css'

function parseFolderSelection(value) {
  if (!value || !value.trim()) {
    return []
  }
  return value
    .split(/[,\n\r]+/)
    .map((folder) => folder.trim())
    .filter(Boolean)
    .filter((folder, index, folders) => folders.findIndex((candidate) => candidate.toLowerCase() === folder.toLowerCase()) === index)
}

function serializeFolderSelection(folders) {
  return folders.join(', ')
}

function EmailAccountDialog({
  availableOAuthProviders = [],
  destinationConfig = null,
  destinationMeta = null,
  emailAccountForm,
  emailAccountFolders = [],
  emailAccountFoldersLoading = false,
  emailAccountFolderLoadError = '',
  duplicateIdError = '',
  microsoftOAuthAvailable = true,
  onApplyPreset,
  onEmailAccountFormChange,
  onFolderInputActivity,
  onFolderInputFocus,
  onClose,
  onSave,
  onSaveWithoutValidation,
  onSaveAndConnectOAuth,
  onTestEmailAccountConnection,
  saveLoading,
  saveAndConnectLoading = false,
  t,
  testConnectionLoading = false,
  testResult = null
}) {
  const resolvedOAuthProviders = availableOAuthProviders.length
    ? availableOAuthProviders
    : (microsoftOAuthAvailable ? ['MICROSOFT'] : [])
  const inferredPresetId = useMemo(() => inferEmailProviderPresetId(emailAccountForm), [emailAccountForm])
  const [selectedPreset, setSelectedPreset] = useState(inferredPresetId)
  const availablePresets = useMemo(
    () => EMAIL_PROVIDER_PRESETS,
    []
  )
  const effectivePresetId = availablePresets.some((option) => option.id === selectedPreset) ? selectedPreset : 'custom'
  const preset = useMemo(() => findEmailProviderPreset(effectivePresetId), [effectivePresetId])
  const requiresMicrosoftOAuth = effectivePresetId === 'outlook' || isOutlookSourceConfig(emailAccountForm)
  const usingPassword = !requiresMicrosoftOAuth && emailAccountForm.authMethod === 'PASSWORD'
  const canUseOAuth = resolvedOAuthProviders.length > 0
  const effectiveOauthProvider = requiresMicrosoftOAuth ? 'MICROSOFT' : emailAccountForm.oauthProvider
  const canLaunchProviderOAuth = !usingPassword && effectiveOauthProvider !== 'NONE' && resolvedOAuthProviders.includes(effectiveOauthProvider)
  const editingExistingAccount = Boolean(emailAccountForm.originalEmailAccountId)
  const supportsFolder = emailAccountForm.protocol === 'IMAP'
  const destinationProvider = destinationMeta?.provider || destinationConfig?.provider || 'GMAIL_API'
  const destinationSupportsLabels = destinationProvider === 'GMAIL_API'
  const supportsCustomLabel = destinationSupportsLabels
  const supportsPostPollActions = emailAccountForm.protocol === 'IMAP'
  const detectedFolders = supportsFolder ? emailAccountFolders : []
  const currentFolder = emailAccountForm.folder || ''
  const selectedFolderValues = useMemo(() => parseFolderSelection(currentFolder), [currentFolder])
  const connectionValidated = testResult?.tone === 'success'
  const tlsLockedToSecure = Boolean(testResult?.tlsRecommended && emailAccountForm.tls)
  const folderSuggestionsLoaded = supportsFolder && detectedFolders.length > 0
  const folderSuggestionsValidated = supportsFolder && connectionValidated && folderSuggestionsLoaded
  const invalidDetectedFolders = useMemo(
    () => folderSuggestionsLoaded
      ? selectedFolderValues.filter((folder) => !detectedFolders.some((candidate) => candidate.toLowerCase() === folder.toLowerCase()))
      : [],
    [detectedFolders, folderSuggestionsLoaded, selectedFolderValues]
  )
  const currentFolderDetected = selectedFolderValues.length > 0
    && selectedFolderValues.every((folder) => detectedFolders.some((candidate) => candidate === folder))
  const defaultDetectedFolder = detectedFolders.find((folder) => folder.toUpperCase() === 'INBOX') || detectedFolders[0] || ''
  const currentPostPollAction = emailAccountForm.postPollAction || 'NONE'
  const currentPostPollTargetFolder = emailAccountForm.postPollTargetFolder || ''
  const currentPostPollTargetDetected = detectedFolders.includes(currentPostPollTargetFolder)
  const [manualPostPollTargetEntry, setManualPostPollTargetEntry] = useState(detectedFolders.length === 0)
  const [showUntestedSaveDialog, setShowUntestedSaveDialog] = useState(false)
  const [showFolderErrorState, setShowFolderErrorState] = useState(false)
  const testResultRef = useRef(null)
  const canSaveWithoutOAuth = !requiresMicrosoftOAuth || editingExistingAccount
  const canPersistEnabledSource = connectionValidated && invalidDetectedFolders.length === 0
  const folderValidationError = supportsFolder
    && folderSuggestionsValidated
    && invalidDetectedFolders.length > 0
  const folderSaveError = showFolderErrorState && folderValidationError
  const hasRequiredConnectionFields = Boolean(
    emailAccountForm.emailAccountId?.trim()
    && emailAccountForm.host?.trim()
    && emailAccountForm.username?.trim()
    && emailAccountForm.port
    && (!supportsFolder || emailAccountForm.folder?.trim())
    && (currentPostPollAction !== 'MOVE' || emailAccountForm.postPollTargetFolder?.trim())
    && (!usingPassword || emailAccountForm.password?.trim() || editingExistingAccount)
  )
  const providerLabel = effectiveOauthProvider === 'GOOGLE' ? t('oauthProvider.google') : t('oauthProvider.microsoft')
  const dialogTitle = emailAccountForm.emailAccountId ? t('emailAccounts.editDialogTitle', { emailAccountId: emailAccountForm.emailAccountId }) : t('emailAccounts.addDialogTitle')
  const initialSnapshotRef = useRef(JSON.stringify(emailAccountForm))
  const isDirty = initialSnapshotRef.current !== JSON.stringify(emailAccountForm)

  useEffect(() => {
    setSelectedPreset(inferredPresetId)
  }, [inferredPresetId])
  useEffect(() => {
    if (!folderValidationError) {
      setShowFolderErrorState(false)
    }
  }, [folderValidationError])
  useEffect(() => {
    if (!detectedFolders.length) {
      setManualPostPollTargetEntry(true)
    } else if (currentPostPollTargetFolder && !currentPostPollTargetDetected) {
      setManualPostPollTargetEntry(true)
    } else {
      setManualPostPollTargetEntry(false)
    }
  }, [currentFolder, currentFolderDetected, currentPostPollTargetDetected, currentPostPollTargetFolder, detectedFolders])
  useEffect(() => {
    if (!detectedFolders.length || currentFolder || !defaultDetectedFolder) {
      return
    }
    onEmailAccountFormChange((current) => {
      if ((current.folder || '') === defaultDetectedFolder) {
        return current
      }
      return { ...current, folder: defaultDetectedFolder }
    })
  }, [currentFolder, defaultDetectedFolder, detectedFolders, onEmailAccountFormChange])
  useEffect(() => {
    if (!testResultRef.current || !testResult || typeof testResultRef.current.scrollIntoView !== 'function') {
      return
    }
    testResultRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [testResult])

  function applyPreset(presetId) {
    setSelectedPreset(presetId)
    if (presetId !== 'custom') {
      onApplyPreset(presetId)
    }
  }

  function updateDetectedFolders(nextFolders) {
    setShowFolderErrorState(false)
    onEmailAccountFormChange((current) => ({
      ...current,
      folder: serializeFolderSelection(nextFolders)
    }))
  }

  function useDetectedPostPollTargetOptions() {
    if (!defaultDetectedFolder) {
      return
    }
    onEmailAccountFormChange((current) => ({
      ...current,
      postPollTargetFolder: currentPostPollTargetDetected ? current.postPollTargetFolder : defaultDetectedFolder
    }))
    setManualPostPollTargetEntry(false)
  }

  function renderTestResultRow(labelKey, value, helpKey) {
    return (
      <div>
        <dt>
          <span className="field-label-row">
            <span>{t(labelKey)}</span>
            <InfoHint text={t(helpKey)} />
          </span>
        </dt>
        <dd>{value}</dd>
      </div>
    )
  }

  function handleSaveSubmit(event) {
    event.preventDefault()
    if (!emailAccountForm.enabled) {
      onSave(event)
      return
    }
    if (folderValidationError) {
      setShowFolderErrorState(true)
      return
    }
    if (canPersistEnabledSource) {
      onSave(event)
      return
    }
    setShowUntestedSaveDialog(true)
  }

  function handleSaveDisabledWithoutTesting() {
    setShowUntestedSaveDialog(false)
    onSaveWithoutValidation?.()
  }

  function handleTestBeforeSaving() {
    setShowUntestedSaveDialog(false)
    onTestEmailAccountConnection?.()
  }

  return (
    <>
      <ModalDialog
        isDirty={isDirty}
        onClose={onClose}
        size="wide"
        title={dialogTitle}
        unsavedChangesMessage={t('common.unsavedChangesConfirm')}
      >
        <p className="section-copy">{t('emailAccounts.dialogCopy')}</p>
        <form className="settings-grid fetcher-dialog-form" onSubmit={handleSaveSubmit}>
        <FormField helpText={t('emailAccounts.providerPresetHelp')} label={t('emailAccounts.providerPreset')}>
          <select disabled={editingExistingAccount} value={selectedPreset} onChange={(event) => applyPreset(event.target.value)}>
            {availablePresets.map((option) => (
              <option key={option.id} value={option.id}>{t(`preset.${option.id}.label`)}</option>
            ))}
          </select>
        </FormField>
        <div className="muted-box full">{t(`preset.${preset.id}.description`)}</div>

        <FormField helpText={t('emailAccounts.emailAccountIdHelp')} label={t('emailAccounts.emailAccountId')}>
          <input value={emailAccountForm.emailAccountId} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, emailAccountId: event.target.value }))} />
        </FormField>
        {duplicateIdError ? <div className="banner-error full">{duplicateIdError}</div> : null}
        <FormField helpText={t('emailAccounts.hostHelp')} label={t('emailAccounts.host')}>
          <input value={emailAccountForm.host} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, host: event.target.value }))} />
        </FormField>
        <FormField helpText={t('emailAccounts.protocolHelp')} label={t('emailAccounts.protocol')}>
          <select value={emailAccountForm.protocol} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, protocol: event.target.value, port: event.target.value === 'IMAP' ? 993 : 995 }))}>
            <option value="IMAP">{t('protocol.imap')}</option>
            <option value="POP3">{t('protocol.pop3')}</option>
          </select>
        </FormField>
        <FormField helpText={t('emailAccounts.portHelp')} label={t('emailAccounts.port')}>
          <input type="number" value={emailAccountForm.port} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, port: Number(event.target.value) }))} />
        </FormField>
        <FormField helpText={t('emailAccounts.authMethodHelp')} label={t('emailAccounts.authMethod')}>
          {requiresMicrosoftOAuth ? (
            <input disabled value={t('authMethod.oauth2')} />
          ) : (
            <select value={emailAccountForm.authMethod} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, authMethod: event.target.value }))}>
              <option value="PASSWORD">{t('authMethod.password')}</option>
              {(canUseOAuth || emailAccountForm.authMethod === 'OAUTH2') ? (
                <option value="OAUTH2">{t('authMethod.oauth2')}</option>
              ) : null}
            </select>
          )}
        </FormField>
        {!usingPassword ? (
          <FormField helpText={t('emailAccounts.oauthProviderHelp')} label={t('emailAccounts.oauthProvider')}>
            {requiresMicrosoftOAuth ? (
              <input disabled value={t('oauthProvider.microsoft')} />
            ) : (
              <select value={emailAccountForm.oauthProvider} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, oauthProvider: event.target.value }))}>
                {resolvedOAuthProviders.includes('GOOGLE') || emailAccountForm.oauthProvider === 'GOOGLE' ? <option value="GOOGLE">{t('oauthProvider.google')}</option> : null}
                {resolvedOAuthProviders.includes('MICROSOFT') || emailAccountForm.oauthProvider === 'MICROSOFT' ? <option value="MICROSOFT">{t('oauthProvider.microsoft')}</option> : null}
              </select>
            )}
          </FormField>
        ) : null}
        <div className="form-field-pair full">
          <FormField helpText={t('emailAccounts.usernameHelp')} label={t('emailAccounts.username')}>
            <input value={emailAccountForm.username} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, username: event.target.value }))} />
          </FormField>
          {usingPassword ? (
            <PasswordField
              helpText={t('emailAccounts.passwordHelp')}
              hideLabel={t('common.hideField', { label: t('emailAccounts.password') })}
              label={t('emailAccounts.password')}
              value={emailAccountForm.password}
              onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, password: event.target.value }))}
              placeholder={t('emailAccounts.keepExisting')}
              showLabel={t('common.showField', { label: t('emailAccounts.password') })}
            />
          ) : (
            <PasswordField
              helpText={t('emailAccounts.oauthRefreshTokenHelp')}
              hideLabel={t('common.hideField', { label: t('emailAccounts.oauthRefreshToken') })}
              label={t('emailAccounts.oauthRefreshToken')}
              value={emailAccountForm.oauthRefreshToken}
              onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, oauthRefreshToken: event.target.value }))}
              placeholder={t('emailAccounts.optionalManualToken')}
              showLabel={t('common.showField', { label: t('emailAccounts.oauthRefreshToken') })}
            />
          )}
        </div>
        {canLaunchProviderOAuth ? (
          <div className="muted-box full fetcher-oauth-setup-box">
            <strong>{t('emailAccounts.oauthSetupTitle')}</strong><br />
            {t('emailAccounts.oauthSetupBody', { provider: providerLabel })}
          </div>
        ) : null}
        {supportsFolder ? (
          <div className="fetcher-folder-control full">
            <label className="field-label-row" htmlFor="email-account-folder-pillbox">
              <span>{t('emailAccounts.folder')}</span>
              <InfoHint text={t('emailAccounts.folderHelp')} />
            </label>
            <PillboxInput
              allowCustomValues={!folderSuggestionsLoaded}
              helperText={emailAccountFoldersLoading
                ? t('common.retrievingFoldersFromServer')
                : folderSuggestionsValidated
                  ? t('emailAccounts.folderValidationReady')
                  : ''}
              inputAriaLabel={t('emailAccounts.folder')}
              inputId="email-account-folder-pillbox"
              loading={emailAccountFoldersLoading}
              onChange={updateDetectedFolders}
            onInputActivity={onFolderInputActivity}
            onInputFocus={onFolderInputFocus}
            invalid={folderSaveError}
            options={detectedFolders}
            placeholder={t('emailAccounts.folderAutocompletePlaceholder')}
              removeLabel={(folder) => t('emailAccounts.folderRemoveSelected', { folder })}
              validationActive={folderSuggestionsLoaded}
              valueTone={(folder) => detectedFolders.some((candidate) => candidate.toLowerCase() === folder.toLowerCase()) ? 'success' : 'error'}
              valueValidationLabel={(folder, tone) => tone === 'success'
                ? t('emailAccounts.folderExists', { folder })
                : t('emailAccounts.folderMissing', { folder })}
              values={selectedFolderValues}
            />
            {emailAccountFolderLoadError ? (
              <div className="muted-box fetcher-transport-warning folder-load-error-card">{emailAccountFolderLoadError}</div>
            ) : null}
          </div>
        ) : null}
        {supportsCustomLabel ? (
          <FormField helpText={t('emailAccounts.customLabelHelp')} label={t('emailAccounts.customLabel')}>
            <input value={emailAccountForm.customLabel} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, customLabel: event.target.value }))} />
          </FormField>
        ) : null}
        {supportsPostPollActions ? (
          <>
            <div className="form-field-pair full fetcher-post-poll-row">
              <FormField helpText={t('emailAccounts.postPollActionHelp')} label={t('emailAccounts.postPollAction')}>
                <select value={currentPostPollAction} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, postPollAction: event.target.value, postPollTargetFolder: event.target.value === 'MOVE' ? current.postPollTargetFolder : '' }))}>
                  <option value="NONE">{t('emailAccounts.postPollAction.none')}</option>
                  <option value="FORWARDED">{t('emailAccounts.postPollAction.forwarded')}</option>
                  <option value="DELETE">{t('emailAccounts.postPollAction.delete')}</option>
                  <option value="MOVE">{t('emailAccounts.postPollAction.move')}</option>
                </select>
              </FormField>
              <label className="checkbox-row fetcher-inline-checkbox">
                <input type="checkbox" checked={Boolean(emailAccountForm.markReadAfterPoll)} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, markReadAfterPoll: event.target.checked }))} />
                <span className="field-label-row">
                  <span>{t('emailAccounts.markReadAfterPoll')}</span>
                  <InfoHint text={t('emailAccounts.markReadAfterPollHelp')} />
                </span>
              </label>
            </div>
            {currentPostPollAction === 'MOVE' ? (
              <div className="fetcher-folder-control full">
                <label>
                  <span className="field-label-row">
                    <span>{t('emailAccounts.postPollTargetFolder')}</span>
                    <InfoHint text={t('emailAccounts.postPollTargetFolderHelp')} />
                  </span>
                  {detectedFolders.length > 0 && !manualPostPollTargetEntry ? (
                    <select value={currentPostPollTargetDetected ? currentPostPollTargetFolder : defaultDetectedFolder} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, postPollTargetFolder: event.target.value }))}>
                      {detectedFolders.map((folder) => (
                        <option key={folder} value={folder}>{folder}</option>
                      ))}
                    </select>
                  ) : (
                    <input value={emailAccountForm.postPollTargetFolder} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, postPollTargetFolder: event.target.value }))} />
                  )}
                </label>
                <div className="fetcher-folder-actions">
                  {detectedFolders.length > 0 ? (
                    <button className="fetcher-folder-toggle" onClick={manualPostPollTargetEntry ? useDetectedPostPollTargetOptions : () => setManualPostPollTargetEntry(true)} type="button">
                      {t(manualPostPollTargetEntry ? 'destination.folderUseDetected' : 'destination.folderUseManual')}
                    </button>
                  ) : null}
                </div>
              </div>
            ) : null}
          </>
        ) : null}
        {supportsFolder ? (
          <FormField helpText={t('emailAccounts.fetchModeHelp')} label={t('emailAccounts.fetchMode')}>
            <select value={emailAccountForm.fetchMode || 'POLLING'} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, fetchMode: event.target.value }))}>
              <option value="POLLING">{t('emailAccounts.fetchMode.polling')}</option>
              <option value="IDLE">{t('emailAccounts.fetchMode.idle')}</option>
            </select>
          </FormField>
        ) : null}
        {!emailAccountForm.tls ? (
          <div className="muted-box full fetcher-transport-warning">
            <strong>{t('emailAccounts.insecureTransportWarningTitle')}</strong><br />
            {t('emailAccounts.insecureTransportWarningBody')}
          </div>
        ) : null}
        {tlsLockedToSecure ? (
          <div className="muted-box full">
            <strong>{t('emailAccounts.tlsLockedTitle')}</strong><br />
            {t('emailAccounts.tlsLockedBody', { port: testResult?.recommendedTlsPort || emailAccountForm.port })}
          </div>
        ) : null}
        <div className="form-field-pair full fetcher-checkbox-pair">
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={emailAccountForm.tls}
              disabled={tlsLockedToSecure}
              onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, tls: event.target.checked }))}
            />
            <span className="field-label-row">
              <span>{t('emailAccounts.tlsOnly')}</span>
              <InfoHint text={t(tlsLockedToSecure ? 'emailAccounts.tlsOnlyLockedHelp' : 'emailAccounts.tlsOnlyHelp')} />
            </span>
          </label>
          <label className="checkbox-row">
            <input type="checkbox" checked={emailAccountForm.unreadOnly} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, unreadOnly: event.target.checked }))} />
            <span className="field-label-row">
              <span>{t('emailAccounts.unreadOnly')}</span>
              <InfoHint text={t('emailAccounts.unreadOnlyHelp')} />
            </span>
          </label>
        </div>
        <label className="checkbox-row full">
          <input type="checkbox" checked={emailAccountForm.enabled} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, enabled: event.target.checked }))} />
          <span className="field-label-row">
            <span>{t('emailAccounts.enabled')}</span>
            <InfoHint text={t('emailAccounts.enabledHelp')} />
          </span>
        </label>
        <div className="full action-row">
          <LoadingButton className="secondary" disabled={!hasRequiredConnectionFields} isLoading={testConnectionLoading} loadingLabel={t('emailAccounts.testConnectionLoading')} onClick={onTestEmailAccountConnection} type="button">
            {t('emailAccounts.testConnection')}
          </LoadingButton>
          {canLaunchProviderOAuth ? (
            <LoadingButton
              className={canSaveWithoutOAuth ? 'secondary' : 'primary'}
              disabled={!hasRequiredConnectionFields}
              isLoading={saveAndConnectLoading}
              loadingLabel={t('emailAccounts.saveAndConnectOAuthLoading', { provider: providerLabel })}
              onClick={onSaveAndConnectOAuth}
              type="button"
            >
              {t('emailAccounts.saveAndConnectOAuth', { provider: providerLabel })}
            </LoadingButton>
          ) : null}
          {canSaveWithoutOAuth ? (
            <LoadingButton
              className="primary"
              disabled={!hasRequiredConnectionFields}
              isLoading={saveLoading}
              loadingLabel={t('emailAccounts.saveLoading')}
              type="submit"
            >
              {editingExistingAccount ? t('emailAccounts.save') : t('emailAccounts.addAction')}
            </LoadingButton>
          ) : null}
          <button className="secondary" onClick={onClose} type="button">
            {t('common.cancel')}
          </button>
        </div>
        {testResult ? (
          <div ref={testResultRef} className={`full ${testResult.tone === 'error' ? 'banner-error' : 'muted-box'} fetcher-test-result`}>
            <strong>{testResult.message}</strong>
            {testResult.tone !== 'error' && testResult.protocol ? (
              <dl className="fetcher-test-result-grid">
                {renderTestResultRow('emailAccounts.testProtocol', testResult.protocol, 'emailAccounts.testProtocolHelp')}
                {renderTestResultRow('emailAccounts.testEndpoint', `${testResult.host}:${testResult.port}`, 'emailAccounts.testEndpointHelp')}
                {renderTestResultRow('emailAccounts.testTls', testResult.tls ? t('common.yes') : t('common.no'), 'emailAccounts.testTlsHelp')}
                {renderTestResultRow('emailAccounts.testTlsAvailable', testResult.tlsAvailable === null ? t('common.unavailable') : testResult.tlsAvailable ? t('common.yes') : t('common.no'), 'emailAccounts.testTlsAvailableHelp')}
                {renderTestResultRow('emailAccounts.testTlsRecommended', testResult.tlsRecommended === null ? t('common.unavailable') : testResult.tlsRecommended ? t('common.yes') : t('common.no'), 'emailAccounts.testTlsRecommendedHelp')}
                {testResult.recommendedTlsPort ? (
                  renderTestResultRow('emailAccounts.testRecommendedTlsPort', testResult.recommendedTlsPort, 'emailAccounts.testRecommendedTlsPortHelp')
                ) : null}
                {renderTestResultRow(
                  'emailAccounts.testAuth',
                  `${t(`authMethod.${testResult.authMethod.toLowerCase()}`)}${testResult.oauthProvider && testResult.oauthProvider !== 'NONE' ? ` / ${t(`oauthProvider.${testResult.oauthProvider.toLowerCase()}`)}` : ''}`,
                  'emailAccounts.testAuthHelp'
                )}
                {renderTestResultRow('emailAccounts.testAuthenticated', testResult.authenticated ? t('common.yes') : t('common.no'), 'emailAccounts.testAuthenticatedHelp')}
                {renderTestResultRow('emailAccounts.testFolder', testResult.folder || 'INBOX', 'emailAccounts.testFolderHelp')}
                {renderTestResultRow('emailAccounts.testFolderAccessible', testResult.folderAccessible ? t('common.yes') : t('common.no'), 'emailAccounts.testFolderAccessibleHelp')}
                {renderTestResultRow('emailAccounts.testUnreadFilterRequested', testResult.unreadFilterRequested ? t('common.yes') : t('common.no'), 'emailAccounts.testUnreadFilterRequestedHelp')}
                {renderTestResultRow('emailAccounts.testUnreadFilterSupported', testResult.unreadFilterSupported === null ? t('common.unavailable') : testResult.unreadFilterSupported ? t('common.yes') : t('common.no'), 'emailAccounts.testUnreadFilterSupportedHelp')}
                {renderTestResultRow('emailAccounts.testUnreadFilterValidated', testResult.unreadFilterValidated === null ? t('common.unavailable') : testResult.unreadFilterValidated ? t('common.yes') : t('common.no'), 'emailAccounts.testUnreadFilterValidatedHelp')}
                {renderTestResultRow('emailAccounts.testVisibleMessages', testResult.visibleMessageCount ?? t('common.unavailable'), 'emailAccounts.testVisibleMessagesHelp')}
                {renderTestResultRow('emailAccounts.testUnreadMessages', testResult.unreadMessageCount ?? t('common.unavailable'), 'emailAccounts.testUnreadMessagesHelp')}
                {renderTestResultRow('emailAccounts.testSampleAvailable', testResult.sampleMessageAvailable === null ? t('common.unavailable') : testResult.sampleMessageAvailable ? t('common.yes') : t('common.no'), 'emailAccounts.testSampleAvailableHelp')}
                {renderTestResultRow('emailAccounts.testSampleMaterialized', testResult.sampleMessageMaterialized === null ? t('common.unavailable') : testResult.sampleMessageMaterialized ? t('common.yes') : t('common.no'), 'emailAccounts.testSampleMaterializedHelp')}
                {testResult.protocol === 'IMAP' ? (
                  renderTestResultRow(
                    'emailAccounts.testForwardedMarkerSupported',
                    testResult.forwardedMarkerSupported === null ? t('common.unavailable') : testResult.forwardedMarkerSupported ? t('common.yes') : t('common.no'),
                    'emailAccounts.testForwardedMarkerSupportedHelp'
                  )
                ) : null}
              </dl>
            ) : null}
          </div>
        ) : null}
        </form>
      </ModalDialog>
      {showUntestedSaveDialog ? (
        <ModalDialog onClose={() => setShowUntestedSaveDialog(false)} title={t('emailAccounts.untestedSaveConfirmTitle')}>
          <p className="section-copy">{t('emailAccounts.untestedSaveConfirmBody')}</p>
          <p className="section-copy">{t('emailAccounts.untestedSaveDisabledBody')}</p>
          <div className="action-row">
            <LoadingButton
              className="primary"
              isLoading={testConnectionLoading}
              loadingLabel={t('emailAccounts.testConnectionLoading')}
              onClick={handleTestBeforeSaving}
              type="button"
            >
              {t('emailAccounts.testConnectionNow')}
            </LoadingButton>
            <LoadingButton
              className="secondary"
              isLoading={saveLoading}
              loadingLabel={t('emailAccounts.saveLoading')}
              onClick={handleSaveDisabledWithoutTesting}
              type="button"
            >
              {t('emailAccounts.saveDisabledWithoutTesting')}
            </LoadingButton>
            <button className="secondary" onClick={() => setShowUntestedSaveDialog(false)} type="button">
              {t('common.cancel')}
            </button>
          </div>
        </ModalDialog>
      ) : null}
    </>
  )
}

export default EmailAccountDialog
