import InfoHint from '../common/InfoHint'
import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'

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
            <label>
              <span className="field-label-row">
                <span>{t('system.googleRedirectUri')}</span>
                <InfoHint text={t('system.googleRedirectUriHelp')} />
              </span>
              <input
                aria-label={t('system.googleRedirectUri')}
                value={oauthSettings.googleRedirectUri}
                onChange={(event) => onOauthSettingsChange((current) => ({ ...current, googleRedirectUri: event.target.value }))}
              />
            </label>
            <label>
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
            <label>
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
            <div className="muted-box full">
              {t('system.googleClientUsageHelp')}
            </div>
          </>
        ) : (
          <>
            <label>
              <span className="field-label-row">
                <span>{t('system.microsoftRedirectUri')}</span>
                <InfoHint text={t('system.microsoftRedirectUriHelp')} />
              </span>
              <input aria-label={t('system.microsoftRedirectUri')} readOnly value={oauthSettings.microsoftRedirectUri} />
            </label>
            <label>
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
