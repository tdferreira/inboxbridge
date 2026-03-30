import InfoHint from '../common/InfoHint'
import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'
import DurationValue from '../common/DurationValue'
import { formatDurationHint } from '../../lib/formatters'

function AuthSecuritySettingsDialog({
  authSecuritySettings,
  authSecuritySettingsForm,
  authSecuritySettingsLoading,
  isDirty,
  locale = 'en',
  onAuthSecurityFormChange,
  onClose,
  onResetAuthSecuritySettings,
  onSaveAuthSecuritySettings,
  t
}) {
  return (
    <ModalDialog
      closeLabel={t('common.closeDialog')}
      isDirty={isDirty}
      onClose={onClose}
      title={t('authSecurity.editTitle')}
      unsavedChangesMessage={t('dialogs.unsavedChanges')}
    >
      <form className="settings-grid system-polling-grid" onSubmit={onSaveAuthSecuritySettings}>
        <div className="muted-box full">
          <strong>{t('authSecurity.protectionSectionTitle')}</strong><br />
          {t('authSecurity.protectionSectionCopy')}<br />
          {t('authSecurity.protectionExplanation')}
        </div>
        <label>
          <span className="field-label-row">
            <span>{t('authSecurity.failedAttempts')}</span>
            <InfoHint text={t('authSecurity.failedAttemptsHelp')} />
          </span>
            <input
              min="1"
              max="50"
              placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultLoginFailureThreshold })}
            type="number"
            value={authSecuritySettingsForm.loginFailureThresholdOverride}
            onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, loginFailureThresholdOverride: event.target.value }))}
          />
        </label>
        <div className="form-field-pair full">
          <label>
            <span className="field-label-row">
              <span>{t('authSecurity.initialBlock')}</span>
              <InfoHint text={t('authSecurity.initialBlockHelp')} />
            </span>
            <input
              placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultLoginInitialBlock })}
              title={formatDurationHint(authSecuritySettingsForm.loginInitialBlockOverride || authSecuritySettings.defaultLoginInitialBlock, locale) || undefined}
              value={authSecuritySettingsForm.loginInitialBlockOverride}
              onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, loginInitialBlockOverride: event.target.value }))}
            />
          </label>
          <label>
            <span className="field-label-row">
              <span>{t('authSecurity.maxBlock')}</span>
              <InfoHint text={t('authSecurity.maxBlockHelp')} />
            </span>
            <input
              placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultLoginMaxBlock })}
              title={formatDurationHint(authSecuritySettingsForm.loginMaxBlockOverride || authSecuritySettings.defaultLoginMaxBlock, locale) || undefined}
              value={authSecuritySettingsForm.loginMaxBlockOverride}
              onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, loginMaxBlockOverride: event.target.value }))}
            />
          </label>
        </div>
        <label>
          <span className="field-label-row">
            <span>{t('authSecurity.registrationChallengeMode')}</span>
            <InfoHint text={t('authSecurity.registrationChallengeModeHelp')} />
          </span>
          <select
            value={authSecuritySettingsForm.registrationChallengeMode}
            onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationChallengeMode: event.target.value }))}
          >
            <option value="DEFAULT">{authSecuritySettings.defaultRegistrationChallengeEnabled ? t('authSecurity.challengeDefaultEnabled') : t('authSecurity.challengeDefaultDisabled')}</option>
            <option value="ENABLED">{t('authSecurity.challengeEnabledOverride')}</option>
            <option value="DISABLED">{t('authSecurity.challengeDisabledOverride')}</option>
          </select>
        </label>
        <label>
          <span className="field-label-row">
            <span>{t('authSecurity.registrationChallengeTtl')}</span>
            <InfoHint text={t('authSecurity.registrationChallengeTtlHelp')} />
          </span>
          <input
            placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultRegistrationChallengeTtl })}
            title={formatDurationHint(authSecuritySettingsForm.registrationChallengeTtlOverride || authSecuritySettings.defaultRegistrationChallengeTtl, locale) || undefined}
            value={authSecuritySettingsForm.registrationChallengeTtlOverride}
            onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationChallengeTtlOverride: event.target.value }))}
          />
        </label>
        <div className="muted-box full">
          {t('authSecurity.effectiveFailedAttempts', { value: authSecuritySettings.effectiveLoginFailureThreshold })}<br />
          {t('authSecurity.effectiveInitialBlock', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveLoginInitialBlock} /><br />
          {t('authSecurity.effectiveMaxBlock', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveLoginMaxBlock} /><br />
          {t('authSecurity.effectiveRegistrationChallenge', { value: t(authSecuritySettings.effectiveRegistrationChallengeEnabled ? 'common.enabled' : 'common.disabled') })}<br />
          {t('authSecurity.effectiveRegistrationChallengeTtl', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveRegistrationChallengeTtl} />
        </div>
        <div className="action-row full">
          <LoadingButton className="primary" isLoading={authSecuritySettingsLoading} loadingLabel={t('authSecurity.saveLoading')} type="submit">
            {t('authSecurity.save')}
          </LoadingButton>
          <LoadingButton className="secondary" isLoading={authSecuritySettingsLoading} loadingLabel={t('authSecurity.resetLoading')} onClick={onResetAuthSecuritySettings} type="button">
            {t('authSecurity.useEnvDefaults')}
          </LoadingButton>
        </div>
      </form>
    </ModalDialog>
  )
}

export default AuthSecuritySettingsDialog
