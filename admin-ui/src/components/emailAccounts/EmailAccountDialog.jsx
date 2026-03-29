import { useEffect, useMemo, useRef, useState } from 'react'
import InfoHint from '../common/InfoHint'
import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'
import PasswordField from '../common/PasswordField'
import { EMAIL_PROVIDER_PRESETS, findEmailProviderPreset, inferEmailProviderPresetId, isOutlookSourceConfig } from '../../lib/emailProviderPresets'
import './EmailAccountDialog.css'

function LabeledField({ children, helpText, label }) {
  return (
    <label>
      <span className="field-label-row">
        <span>{label}</span>
        <InfoHint text={helpText} />
      </span>
      {children}
    </label>
  )
}

function EmailAccountDialog({
  availableOAuthProviders = [],
  emailAccountForm,
  duplicateIdError = '',
  microsoftOAuthAvailable = true,
  onApplyPreset,
  onEmailAccountFormChange,
  onClose,
  onSave,
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
  useEffect(() => {
    setSelectedPreset(inferredPresetId)
  }, [inferredPresetId])
  const effectivePresetId = availablePresets.some((option) => option.id === selectedPreset) ? selectedPreset : 'custom'
  const preset = useMemo(() => findEmailProviderPreset(effectivePresetId), [effectivePresetId])
  const requiresMicrosoftOAuth = effectivePresetId === 'outlook' || isOutlookSourceConfig(emailAccountForm)
  const usingPassword = !requiresMicrosoftOAuth && emailAccountForm.authMethod === 'PASSWORD'
  const canUseOAuth = resolvedOAuthProviders.length > 0
  const effectiveOauthProvider = requiresMicrosoftOAuth ? 'MICROSOFT' : emailAccountForm.oauthProvider
  const canLaunchProviderOAuth = !usingPassword && effectiveOauthProvider !== 'NONE' && resolvedOAuthProviders.includes(effectiveOauthProvider)
  const providerLabel = effectiveOauthProvider === 'GOOGLE' ? t('oauthProvider.google') : t('oauthProvider.microsoft')
  const dialogTitle = emailAccountForm.emailAccountId ? t('emailAccounts.editDialogTitle', { bridgeId: emailAccountForm.emailAccountId }) : t('emailAccounts.addDialogTitle')
  const initialSnapshotRef = useRef(JSON.stringify(emailAccountForm))
  const isDirty = initialSnapshotRef.current !== JSON.stringify(emailAccountForm)

  function applyPreset(presetId) {
    setSelectedPreset(presetId)
    if (presetId !== 'custom') {
      onApplyPreset(presetId)
    }
  }

  return (
    <ModalDialog
      isDirty={isDirty}
      onClose={onClose}
      size="wide"
      title={dialogTitle}
      unsavedChangesMessage={t('common.unsavedChangesConfirm')}
    >
      <p className="section-copy">{t('emailAccounts.dialogCopy')}</p>
      <form className="settings-grid fetcher-dialog-form" onSubmit={onSave}>
        <LabeledField helpText={t('emailAccounts.providerPresetHelp')} label={t('emailAccounts.providerPreset')}>
          <select value={selectedPreset} onChange={(event) => applyPreset(event.target.value)}>
            {availablePresets.map((option) => (
              <option key={option.id} value={option.id}>{t(`preset.${option.id}.label`)}</option>
            ))}
          </select>
        </LabeledField>
        <div className="muted-box full">{t(`preset.${preset.id}.description`)}</div>

        <LabeledField helpText={t('emailAccounts.bridgeIdHelp')} label={t('emailAccounts.bridgeId')}>
          <input value={emailAccountForm.emailAccountId} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, emailAccountId: event.target.value }))} />
        </LabeledField>
        {duplicateIdError ? <div className="banner-error full">{duplicateIdError}</div> : null}
        <LabeledField helpText={t('emailAccounts.hostHelp')} label={t('emailAccounts.host')}>
          <input value={emailAccountForm.host} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, host: event.target.value }))} />
        </LabeledField>
        <LabeledField helpText={t('emailAccounts.protocolHelp')} label={t('emailAccounts.protocol')}>
          <select value={emailAccountForm.protocol} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, protocol: event.target.value, port: event.target.value === 'IMAP' ? 993 : 995 }))}>
            <option value="IMAP">{t('protocol.imap')}</option>
            <option value="POP3">{t('protocol.pop3')}</option>
          </select>
        </LabeledField>
        <LabeledField helpText={t('emailAccounts.portHelp')} label={t('emailAccounts.port')}>
          <input type="number" value={emailAccountForm.port} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, port: Number(event.target.value) }))} />
        </LabeledField>
        <LabeledField helpText={t('emailAccounts.authMethodHelp')} label={t('emailAccounts.authMethod')}>
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
        </LabeledField>
        {!usingPassword ? (
          <LabeledField helpText={t('emailAccounts.oauthProviderHelp')} label={t('emailAccounts.oauthProvider')}>
            {requiresMicrosoftOAuth ? (
              <input disabled value={t('oauthProvider.microsoft')} />
            ) : (
              <select value={emailAccountForm.oauthProvider} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, oauthProvider: event.target.value }))}>
                {resolvedOAuthProviders.includes('GOOGLE') || emailAccountForm.oauthProvider === 'GOOGLE' ? <option value="GOOGLE">{t('oauthProvider.google')}</option> : null}
                {resolvedOAuthProviders.includes('MICROSOFT') || emailAccountForm.oauthProvider === 'MICROSOFT' ? <option value="MICROSOFT">{t('oauthProvider.microsoft')}</option> : null}
              </select>
            )}
          </LabeledField>
        ) : null}
        <LabeledField helpText={t('emailAccounts.usernameHelp')} label={t('emailAccounts.username')}>
          <input value={emailAccountForm.username} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, username: event.target.value }))} />
        </LabeledField>
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
        ) : null}
        {!usingPassword ? (
          <PasswordField
            helpText={t('emailAccounts.oauthRefreshTokenHelp')}
            hideLabel={t('common.hideField', { label: t('emailAccounts.oauthRefreshToken') })}
            label={t('emailAccounts.oauthRefreshToken')}
            value={emailAccountForm.oauthRefreshToken}
            onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, oauthRefreshToken: event.target.value }))}
            placeholder={t('emailAccounts.optionalManualToken')}
            showLabel={t('common.showField', { label: t('emailAccounts.oauthRefreshToken') })}
          />
        ) : null}
        {canLaunchProviderOAuth ? (
          <div className="muted-box full fetcher-oauth-setup-box">
            <strong>{t('emailAccounts.oauthSetupTitle')}</strong><br />
            {t('emailAccounts.oauthSetupBody', { provider: providerLabel })}
          </div>
        ) : null}
        <LabeledField helpText={t('emailAccounts.folderHelp')} label={t('emailAccounts.folder')}>
          <input value={emailAccountForm.folder} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, folder: event.target.value }))} />
        </LabeledField>
        <LabeledField helpText={t('emailAccounts.customLabelHelp')} label={t('emailAccounts.customLabel')}>
          <input value={emailAccountForm.customLabel} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, customLabel: event.target.value }))} />
        </LabeledField>
        <label className="checkbox-row">
          <input type="checkbox" checked={emailAccountForm.enabled} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, enabled: event.target.checked }))} />
          <span className="field-label-row">
            <span>{t('emailAccounts.enabled')}</span>
            <InfoHint text={t('emailAccounts.enabledHelp')} />
          </span>
        </label>
        <label className="checkbox-row">
          <input type="checkbox" checked={emailAccountForm.tls} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, tls: event.target.checked }))} />
          <span className="field-label-row">
            <span>{t('emailAccounts.tlsOnly')}</span>
            <InfoHint text={t('emailAccounts.tlsOnlyHelp')} />
          </span>
        </label>
        <label className="checkbox-row">
          <input type="checkbox" checked={emailAccountForm.unreadOnly} onChange={(event) => onEmailAccountFormChange((current) => ({ ...current, unreadOnly: event.target.checked }))} />
          <span className="field-label-row">
            <span>{t('emailAccounts.unreadOnly')}</span>
            <InfoHint text={t('emailAccounts.unreadOnlyHelp')} />
          </span>
        </label>
        <div className="full action-row">
          <LoadingButton className="secondary" isLoading={testConnectionLoading} loadingLabel={t('emailAccounts.testConnectionLoading')} onClick={onTestEmailAccountConnection} type="button">
            {t('emailAccounts.testConnection')}
          </LoadingButton>
          {canLaunchProviderOAuth ? (
            <LoadingButton
              className="secondary"
              isLoading={saveAndConnectLoading}
              loadingLabel={t('emailAccounts.saveAndConnectOAuthLoading', { provider: providerLabel })}
              onClick={onSaveAndConnectOAuth}
              type="button"
            >
              {t('emailAccounts.saveAndConnectOAuth', { provider: providerLabel })}
            </LoadingButton>
          ) : null}
          <LoadingButton className="primary" isLoading={saveLoading} loadingLabel={t('emailAccounts.saveLoading')} type="submit">
            {emailAccountForm.emailAccountId ? t('emailAccounts.save') : t('emailAccounts.add')}
          </LoadingButton>
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
                <div><dt>{t('emailAccounts.testAuth')}</dt><dd>{t(`authMethod.${testResult.authMethod.toLowerCase()}`)}{testResult.oauthProvider && testResult.oauthProvider !== 'NONE' ? ` / ${t(`oauthProvider.${testResult.oauthProvider.toLowerCase()}`)}` : ''}</dd></div>
                <div><dt>{t('emailAccounts.testAuthenticated')}</dt><dd>{testResult.authenticated ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('emailAccounts.testFolder')}</dt><dd>{testResult.folder || 'INBOX'}</dd></div>
                <div><dt>{t('emailAccounts.testFolderAccessible')}</dt><dd>{testResult.folderAccessible ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('emailAccounts.testUnreadFilterRequested')}</dt><dd>{testResult.unreadFilterRequested ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('emailAccounts.testUnreadFilterSupported')}</dt><dd>{testResult.unreadFilterSupported === null ? t('common.unavailable') : testResult.unreadFilterSupported ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('emailAccounts.testUnreadFilterValidated')}</dt><dd>{testResult.unreadFilterValidated === null ? t('common.unavailable') : testResult.unreadFilterValidated ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('emailAccounts.testVisibleMessages')}</dt><dd>{testResult.visibleMessageCount ?? t('common.unavailable')}</dd></div>
                <div><dt>{t('emailAccounts.testUnreadMessages')}</dt><dd>{testResult.unreadMessageCount ?? t('common.unavailable')}</dd></div>
                <div><dt>{t('emailAccounts.testSampleAvailable')}</dt><dd>{testResult.sampleMessageAvailable === null ? t('common.unavailable') : testResult.sampleMessageAvailable ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('emailAccounts.testSampleMaterialized')}</dt><dd>{testResult.sampleMessageMaterialized === null ? t('common.unavailable') : testResult.sampleMessageMaterialized ? t('common.yes') : t('common.no')}</dd></div>
              </dl>
            ) : null}
          </div>
        ) : null}
      </form>
    </ModalDialog>
  )
}

export default EmailAccountDialog
