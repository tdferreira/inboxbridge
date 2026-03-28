import { useMemo, useRef, useState } from 'react'
import InfoHint from '../common/InfoHint'
import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'
import PasswordField from '../common/PasswordField'
import { EMAIL_PROVIDER_PRESETS, findEmailProviderPreset } from '../../lib/emailProviderPresets'
import './FetcherDialog.css'

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

function FetcherDialog({
  bridgeForm,
  duplicateIdError = '',
  microsoftOAuthAvailable = true,
  onApplyPreset,
  onBridgeFormChange,
  onClose,
  onSave,
  onTestConnection,
  saveLoading,
  t,
  testConnectionLoading = false,
  testResult = null
}) {
  const [selectedPreset, setSelectedPreset] = useState('custom')
  const availablePresets = useMemo(
    () => EMAIL_PROVIDER_PRESETS.filter((option) => microsoftOAuthAvailable || option.id !== 'outlook'),
    [microsoftOAuthAvailable]
  )
  const effectivePresetId = availablePresets.some((option) => option.id === selectedPreset) ? selectedPreset : 'custom'
  const preset = useMemo(() => findEmailProviderPreset(effectivePresetId), [effectivePresetId])
  const usingPassword = bridgeForm.authMethod === 'PASSWORD'
  const dialogTitle = bridgeForm.bridgeId ? t('bridges.editDialogTitle', { bridgeId: bridgeForm.bridgeId }) : t('bridges.addDialogTitle')
  const initialSnapshotRef = useRef(JSON.stringify(bridgeForm))
  const isDirty = initialSnapshotRef.current !== JSON.stringify(bridgeForm)

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
      <p className="section-copy">{t('bridges.dialogCopy')}</p>
      <form className="settings-grid fetcher-dialog-form" onSubmit={onSave}>
        <LabeledField helpText={t('bridges.providerPresetHelp')} label={t('bridges.providerPreset')}>
          <select value={selectedPreset} onChange={(event) => applyPreset(event.target.value)}>
            {availablePresets.map((option) => (
              <option key={option.id} value={option.id}>{t(`preset.${option.id}.label`)}</option>
            ))}
          </select>
        </LabeledField>
        <div className="muted-box full">{t(`preset.${preset.id}.description`)}</div>

        <LabeledField helpText={t('bridges.bridgeIdHelp')} label={t('bridges.bridgeId')}>
          <input value={bridgeForm.bridgeId} onChange={(event) => onBridgeFormChange((current) => ({ ...current, bridgeId: event.target.value }))} />
        </LabeledField>
        {duplicateIdError ? <div className="banner-error full">{duplicateIdError}</div> : null}
        <LabeledField helpText={t('bridges.hostHelp')} label={t('bridges.host')}>
          <input value={bridgeForm.host} onChange={(event) => onBridgeFormChange((current) => ({ ...current, host: event.target.value }))} />
        </LabeledField>
        <LabeledField helpText={t('bridges.protocolHelp')} label={t('bridges.protocol')}>
          <select value={bridgeForm.protocol} onChange={(event) => onBridgeFormChange((current) => ({ ...current, protocol: event.target.value, port: event.target.value === 'IMAP' ? 993 : 995 }))}>
            <option value="IMAP">{t('protocol.imap')}</option>
            <option value="POP3">{t('protocol.pop3')}</option>
          </select>
        </LabeledField>
        <LabeledField helpText={t('bridges.portHelp')} label={t('bridges.port')}>
          <input type="number" value={bridgeForm.port} onChange={(event) => onBridgeFormChange((current) => ({ ...current, port: Number(event.target.value) }))} />
        </LabeledField>
        <LabeledField helpText={t('bridges.authMethodHelp')} label={t('bridges.authMethod')}>
          <select value={bridgeForm.authMethod} onChange={(event) => onBridgeFormChange((current) => ({ ...current, authMethod: event.target.value }))}>
            <option value="PASSWORD">{t('authMethod.password')}</option>
            {(microsoftOAuthAvailable || bridgeForm.authMethod === 'OAUTH2') ? (
              <option value="OAUTH2">{t('authMethod.oauth2')}</option>
            ) : null}
          </select>
        </LabeledField>
        {!usingPassword ? (
          <LabeledField helpText={t('bridges.oauthProviderHelp')} label={t('bridges.oauthProvider')}>
            <select value={bridgeForm.oauthProvider} onChange={(event) => onBridgeFormChange((current) => ({ ...current, oauthProvider: event.target.value }))}>
              <option value="MICROSOFT">{t('oauthProvider.microsoft')}</option>
            </select>
          </LabeledField>
        ) : null}
        <LabeledField helpText={t('bridges.usernameHelp')} label={t('bridges.username')}>
          <input value={bridgeForm.username} onChange={(event) => onBridgeFormChange((current) => ({ ...current, username: event.target.value }))} />
        </LabeledField>
        {usingPassword ? (
          <PasswordField
            helpText={t('bridges.passwordHelp')}
            hideLabel={t('common.hideField', { label: t('bridges.password') })}
            label={t('bridges.password')}
            value={bridgeForm.password}
            onChange={(event) => onBridgeFormChange((current) => ({ ...current, password: event.target.value }))}
            placeholder={t('bridges.keepExisting')}
            showLabel={t('common.showField', { label: t('bridges.password') })}
          />
        ) : null}
        {!usingPassword ? (
          <PasswordField
            helpText={t('bridges.oauthRefreshTokenHelp')}
            hideLabel={t('common.hideField', { label: t('bridges.oauthRefreshToken') })}
            label={t('bridges.oauthRefreshToken')}
            value={bridgeForm.oauthRefreshToken}
            onChange={(event) => onBridgeFormChange((current) => ({ ...current, oauthRefreshToken: event.target.value }))}
            placeholder={t('bridges.optionalManualToken')}
            showLabel={t('common.showField', { label: t('bridges.oauthRefreshToken') })}
          />
        ) : null}
        <LabeledField helpText={t('bridges.folderHelp')} label={t('bridges.folder')}>
          <input value={bridgeForm.folder} onChange={(event) => onBridgeFormChange((current) => ({ ...current, folder: event.target.value }))} />
        </LabeledField>
        <LabeledField helpText={t('bridges.customLabelHelp')} label={t('bridges.customLabel')}>
          <input value={bridgeForm.customLabel} onChange={(event) => onBridgeFormChange((current) => ({ ...current, customLabel: event.target.value }))} />
        </LabeledField>
        <label className="checkbox-row">
          <input type="checkbox" checked={bridgeForm.enabled} onChange={(event) => onBridgeFormChange((current) => ({ ...current, enabled: event.target.checked }))} />
          <span className="field-label-row">
            <span>{t('bridges.enabled')}</span>
            <InfoHint text={t('bridges.enabledHelp')} />
          </span>
        </label>
        <label className="checkbox-row">
          <input type="checkbox" checked={bridgeForm.tls} onChange={(event) => onBridgeFormChange((current) => ({ ...current, tls: event.target.checked }))} />
          <span className="field-label-row">
            <span>{t('bridges.tlsOnly')}</span>
            <InfoHint text={t('bridges.tlsOnlyHelp')} />
          </span>
        </label>
        <label className="checkbox-row">
          <input type="checkbox" checked={bridgeForm.unreadOnly} onChange={(event) => onBridgeFormChange((current) => ({ ...current, unreadOnly: event.target.checked }))} />
          <span className="field-label-row">
            <span>{t('bridges.unreadOnly')}</span>
            <InfoHint text={t('bridges.unreadOnlyHelp')} />
          </span>
        </label>
        <div className="full action-row">
          <LoadingButton className="secondary" isLoading={testConnectionLoading} loadingLabel={t('bridges.testConnectionLoading')} onClick={onTestConnection} type="button">
            {t('bridges.testConnection')}
          </LoadingButton>
          <LoadingButton className="primary" isLoading={saveLoading} loadingLabel={t('bridges.saveLoading')} type="submit">
            {bridgeForm.bridgeId ? t('bridges.save') : t('bridges.add')}
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
                <div><dt>{t('bridges.testProtocol')}</dt><dd>{testResult.protocol}</dd></div>
                <div><dt>{t('bridges.testEndpoint')}</dt><dd>{testResult.host}:{testResult.port}</dd></div>
                <div><dt>{t('bridges.testTls')}</dt><dd>{testResult.tls ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('bridges.testAuth')}</dt><dd>{t(`authMethod.${testResult.authMethod.toLowerCase()}`)}{testResult.oauthProvider && testResult.oauthProvider !== 'NONE' ? ` / ${t(`oauthProvider.${testResult.oauthProvider.toLowerCase()}`)}` : ''}</dd></div>
                <div><dt>{t('bridges.testAuthenticated')}</dt><dd>{testResult.authenticated ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('bridges.testFolder')}</dt><dd>{testResult.folder || 'INBOX'}</dd></div>
                <div><dt>{t('bridges.testFolderAccessible')}</dt><dd>{testResult.folderAccessible ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('bridges.testUnreadFilterRequested')}</dt><dd>{testResult.unreadFilterRequested ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('bridges.testUnreadFilterSupported')}</dt><dd>{testResult.unreadFilterSupported === null ? t('common.unavailable') : testResult.unreadFilterSupported ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('bridges.testUnreadFilterValidated')}</dt><dd>{testResult.unreadFilterValidated === null ? t('common.unavailable') : testResult.unreadFilterValidated ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('bridges.testVisibleMessages')}</dt><dd>{testResult.visibleMessageCount ?? t('common.unavailable')}</dd></div>
                <div><dt>{t('bridges.testUnreadMessages')}</dt><dd>{testResult.unreadMessageCount ?? t('common.unavailable')}</dd></div>
                <div><dt>{t('bridges.testSampleAvailable')}</dt><dd>{testResult.sampleMessageAvailable === null ? t('common.unavailable') : testResult.sampleMessageAvailable ? t('common.yes') : t('common.no')}</dd></div>
                <div><dt>{t('bridges.testSampleMaterialized')}</dt><dd>{testResult.sampleMessageMaterialized === null ? t('common.unavailable') : testResult.sampleMessageMaterialized ? t('common.yes') : t('common.no')}</dd></div>
              </dl>
            ) : null}
          </div>
        ) : null}
      </form>
    </ModalDialog>
  )
}

export default FetcherDialog
