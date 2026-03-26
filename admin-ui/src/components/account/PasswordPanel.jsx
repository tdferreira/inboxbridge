import LoadingButton from '../common/LoadingButton'
import PasswordField from '../common/PasswordField'
import { buildPasswordChecks, canSubmitPasswordChange } from '../../lib/passwordPolicy'
import './PasswordPanel.css'

function PasswordPanel({
  onPasswordChange,
  onPasswordFormChange,
  onPasswordRemove,
  passkeyCount,
  passwordConfigured,
  passwordLoading,
  passwordRemoveLoading,
  passwordForm,
  t
}) {
  const validations = buildPasswordChecks(passwordForm.currentPassword, passwordForm.newPassword, passwordForm.confirmNewPassword, t, {
    requireDifferent: passwordConfigured
  })
  const canSubmit = canSubmitPasswordChange(passwordForm.currentPassword, passwordForm.newPassword, passwordForm.confirmNewPassword, {
    requireCurrentPassword: passwordConfigured,
    requireDifferent: passwordConfigured
  })
  const canRemovePassword = passwordConfigured && passkeyCount > 0

  return (
    <section className="surface-card password-panel" id="password-panel-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('password.title')}</div>
          <p className="section-copy">
            {passwordConfigured
              ? t('password.configuredCopy')
              : t('password.passwordlessCopy')}
          </p>
        </div>
        {passwordConfigured ? (
          <LoadingButton
            className="secondary"
            disabled={!canRemovePassword}
            isLoading={passwordRemoveLoading}
            loadingLabel={t('password.removeLoading')}
            onClick={onPasswordRemove}
            type="button"
          >
            {t('password.remove')}
          </LoadingButton>
        ) : null}
      </div>
      {!passwordConfigured ? (
        <div className="muted-box">
          {t('password.passwordlessNotice')}
        </div>
      ) : null}
      {passwordConfigured && !canRemovePassword ? (
        <div className="muted-box">
          {t('password.registerPasskeyFirst')}
        </div>
      ) : null}
      <form className="stack-form" onSubmit={onPasswordChange}>
        {passwordConfigured ? (
          <PasswordField
            hideLabel={t('common.hideField', { label: t('password.current') })}
            label={t('password.current')}
            value={passwordForm.currentPassword}
            onChange={(event) => onPasswordFormChange((current) => ({ ...current, currentPassword: event.target.value }))}
            showLabel={t('common.showField', { label: t('password.current') })}
          />
        ) : null}
        <PasswordField
          hideLabel={t('common.hideField', { label: t('password.new') })}
          label={t('password.new')}
          value={passwordForm.newPassword}
          onChange={(event) => onPasswordFormChange((current) => ({ ...current, newPassword: event.target.value }))}
          showLabel={t('common.showField', { label: t('password.new') })}
        />
        <PasswordField
          hideLabel={t('common.hideField', { label: t('password.repeatNew') })}
          label={t('password.repeatNew')}
          value={passwordForm.confirmNewPassword}
          onChange={(event) => onPasswordFormChange((current) => ({ ...current, confirmNewPassword: event.target.value }))}
          showLabel={t('common.showField', { label: t('password.repeatNew') })}
        />
        <div className="password-requirements">
          {validations.map((item) => (
            <div key={item.label} className={`password-requirement ${item.valid ? 'valid' : 'invalid'}`}>
              {item.label}
            </div>
          ))}
        </div>
        <LoadingButton className="secondary" disabled={!canSubmit} isLoading={passwordLoading} loadingLabel={t('password.changeLoading')} type="submit">
          {passwordConfigured ? t('password.change') : t('password.set')}
        </LoadingButton>
      </form>
    </section>
  )
}

export default PasswordPanel
