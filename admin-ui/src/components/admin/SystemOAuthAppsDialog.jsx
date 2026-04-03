import CopyButton from '../common/CopyButton'
import InfoHint from '../common/InfoHint'
import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'
import ButtonLink from '../common/ButtonLink'

function RedirectUriInstruction({ intro, redirectUri, t }) {
  return (
    <div className="oauth-setup-step-block">
      <div>{intro}</div>
      <div className="oauth-setup-redirect-shell">
        <code className="oauth-setup-redirect-value">{redirectUri}</code>
        <CopyButton copiedLabel={t('common.copied')} label={t('common.copy')} text={redirectUri} />
      </div>
    </div>
  )
}

function SetupInstructions({ title, linkHref, linkLabel, children }) {
  return (
    <div className="muted-box full oauth-setup-box">
      <div className="oauth-setup-header">
        <strong>{title}</strong>
        <ButtonLink className="oauth-setup-link-button" href={linkHref} rel="noreferrer" target="_blank" tone="secondary">
          {linkLabel}
        </ButtonLink>
      </div>
      <ol className="oauth-setup-steps">
        {children}
      </ol>
    </div>
  )
}

function SystemOAuthAppsDialog({
  isDirty,
  oauthSettings,
  oauthSettingsLoading,
  onClose,
  onOauthSettingsChange,
  onSave,
  provider,
  t
}) {
  const isGoogle = provider === 'google'

  return (
    <ModalDialog
      closeLabel={t('common.closeDialog')}
      isDirty={isDirty}
      onClose={onClose}
      title={isGoogle ? t('system.googleDialogTitle') : t('system.microsoftDialogTitle')}
      unsavedChangesMessage={t('dialogs.unsavedChanges')}
    >
      <form className="settings-grid system-polling-grid" onSubmit={onSave}>
        {isGoogle ? (
          <>
            <label className="full">
              <span className="field-label-row">
                <span>{t('system.googleClientId')}</span>
                <InfoHint text={t('system.googleClientIdHelp')} />
              </span>
              <input
                aria-label={t('system.googleClientId')}
                value={oauthSettings.googleClientId}
                onChange={(event) => onOauthSettingsChange((current) => ({ ...current, googleClientId: event.target.value }))}
              />
            </label>
            <label className="full">
              <span className="field-label-row">
                <span>{t('system.googleClientSecret')}</span>
                <InfoHint text={t('system.googleClientSecretHelp')} />
              </span>
              <input
                aria-label={t('system.googleClientSecret')}
                placeholder={oauthSettings.googleClientSecretConfigured ? t('gmail.storedSecurely') : ''}
                type="password"
                value={oauthSettings.googleClientSecret}
                onChange={(event) => onOauthSettingsChange((current) => ({ ...current, googleClientSecret: event.target.value }))}
              />
            </label>
            <div className="full oauth-redirect-field">
              <span className="field-label-row">
                <span>{t('system.googleRedirectUri')}</span>
                <InfoHint text={t('system.googleRedirectUriHelp')} />
              </span>
              <div className="oauth-setup-redirect-shell">
                <input
                  aria-label={t('system.googleRedirectUri')}
                  readOnly
                  value={oauthSettings.googleRedirectUri}
                />
                <CopyButton copiedLabel={t('common.copied')} label={t('common.copy')} text={oauthSettings.googleRedirectUri} />
              </div>
            </div>
            <div className="muted-box full">
              {t('system.googleClientUsageHelp')}
            </div>
            <SetupInstructions
              linkHref="https://console.cloud.google.com/"
              linkLabel={t('system.googleSetupConsoleLink')}
              title={t('system.googleSetupTitle')}
            >
              <li>{t('system.googleSetupStep1')}</li>
              <li>{t('system.googleSetupStep2')}</li>
              <li>
                <RedirectUriInstruction
                  intro={t('system.googleSetupStep3Intro')}
                  redirectUri={oauthSettings.googleRedirectUri}
                  t={t}
                />
              </li>
              <li>{t('system.googleSetupStep4')}</li>
              <li>{t('system.googleSetupStep5')}</li>
            </SetupInstructions>
          </>
        ) : (
          <>
            <label className="full">
              <span className="field-label-row">
                <span>{t('system.microsoftClientId')}</span>
                <InfoHint text={t('system.microsoftClientIdHelp')} />
              </span>
              <input
                aria-label={t('system.microsoftClientId')}
                value={oauthSettings.microsoftClientId}
                onChange={(event) => onOauthSettingsChange((current) => ({ ...current, microsoftClientId: event.target.value }))}
              />
            </label>
            <label className="full">
              <span className="field-label-row">
                <span>{t('system.microsoftClientSecret')}</span>
                <InfoHint text={t('system.microsoftClientSecretHelp')} />
              </span>
              <input
                aria-label={t('system.microsoftClientSecret')}
                placeholder={oauthSettings.microsoftClientSecretConfigured ? t('gmail.storedSecurely') : ''}
                type="password"
                value={oauthSettings.microsoftClientSecret}
                onChange={(event) => onOauthSettingsChange((current) => ({ ...current, microsoftClientSecret: event.target.value }))}
              />
            </label>
            <div className="full oauth-redirect-field">
              <span className="field-label-row">
                <span>{t('system.microsoftRedirectUri')}</span>
                <InfoHint text={t('system.microsoftRedirectUriHelp')} />
              </span>
              <div className="oauth-setup-redirect-shell">
                <input aria-label={t('system.microsoftRedirectUri')} readOnly value={oauthSettings.microsoftRedirectUri} />
                <CopyButton copiedLabel={t('common.copied')} label={t('common.copy')} text={oauthSettings.microsoftRedirectUri} />
              </div>
            </div>
            <SetupInstructions
              linkHref="https://entra.microsoft.com/"
              linkLabel={t('system.microsoftSetupConsoleLink')}
              title={t('system.microsoftSetupTitle')}
            >
              <li>{t('system.microsoftSetupStep1')}</li>
              <li>{t('system.microsoftSetupStep2')}</li>
              <li>
                {t('system.microsoftSetupStep3Prefix')} <strong>{t('system.microsoftSupportedAccountTypes')}</strong>.
              </li>
              <li>
                <RedirectUriInstruction
                  intro={t('system.microsoftSetupStep4Intro')}
                  redirectUri={oauthSettings.microsoftRedirectUri}
                  t={t}
                />
              </li>
              <li>{t('system.microsoftSetupStep5')}</li>
              <li>{t('system.microsoftSetupStep6')}</li>
            </SetupInstructions>
          </>
        )}
        {!oauthSettings.secureStorageConfigured ? (
          <div className="muted-box full">
            {t('system.oauthAppsSecureStorageRequired')}
          </div>
        ) : null}
        <div className="action-row full">
          <LoadingButton
            className="primary"
            isLoading={oauthSettingsLoading}
            loadingLabel={t('system.oauthAppsSaveLoading')}
            type="submit"
          >
            {t('system.oauthAppsSave')}
          </LoadingButton>
        </div>
      </form>
    </ModalDialog>
  )
}

export default SystemOAuthAppsDialog
