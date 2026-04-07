import LoadingButton from '@/shared/components/LoadingButton'
import PasswordField from '@/shared/components/PasswordField'
import SectionCard from '@/shared/components/SectionCard'
import { buildPasswordChecks, canSubmitPasswordChange } from '@/lib/passwordPolicy'
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
  const canRemovePassword = passwordConfigured && passkeyCount > 0 && passwordForm.currentPassword.trim().length > 0

  return (
    <SectionCard
      actions={passwordConfigured ? (
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
      className="password-panel"
      copy={passwordConfigured
        ? t('password.configuredCopy')
        : t('password.passwordlessCopy')}
      id="password-panel-section"
      title={t('password.title')}
    >
      {!passwordConfigured ? (
        <div className="muted-box">
          {t('password.passwordlessNotice')}
        </div>
      ) : null}
      {passwordConfigured && !canRemovePassword ? (
        <div className="muted-box">
          {passkeyCount > 0 ? t('password.enterCurrentToRemove') : t('password.registerPasskeyFirst')}
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
    </SectionCard>
  )
}

export default PasswordPanel
