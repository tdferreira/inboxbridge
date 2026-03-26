import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'
import PasswordField from '../common/PasswordField'
import { buildPasswordChecks, canSubmitPasswordChange } from '../../lib/passwordPolicy'
import './PasswordResetDialog.css'

function PasswordResetDialog({
  onClose,
  onFormChange,
  onSubmit,
  passwordLoading,
  resetPasswordForm,
  t,
  username
}) {
  const validations = buildPasswordChecks('', resetPasswordForm.newPassword, resetPasswordForm.confirmNewPassword, t, {
    requireDifferent: false
  })
  const canSubmit = canSubmitPasswordChange('', resetPasswordForm.newPassword, resetPasswordForm.confirmNewPassword, {
    requireCurrentPassword: false,
    requireDifferent: false
  })

  return (
    <ModalDialog onClose={onClose} title={t('security.resetPasswordTitle', { username })}>
      <p className="section-copy">
        {t('security.resetPasswordCopy')}
      </p>
      <form className="stack-form" onSubmit={onSubmit}>
        <PasswordField
          hideLabel={t('common.hideField', { label: t('users.initialPassword') })}
          label={t('users.initialPassword')}
          value={resetPasswordForm.newPassword}
          onChange={(event) => onFormChange((current) => ({ ...current, newPassword: event.target.value }))}
          showLabel={t('common.showField', { label: t('users.initialPassword') })}
        />
        <PasswordField
          hideLabel={t('common.hideField', { label: t('password.repeatNew') })}
          label={t('password.repeatNew')}
          value={resetPasswordForm.confirmNewPassword}
          onChange={(event) => onFormChange((current) => ({ ...current, confirmNewPassword: event.target.value }))}
          showLabel={t('common.showField', { label: t('password.repeatNew') })}
        />
        <div className="password-requirements">
          {validations.map((item) => (
            <div key={item.label} className={`password-requirement ${item.valid ? 'valid' : 'invalid'}`}>
              {item.label}
            </div>
          ))}
        </div>
        <div className="action-row">
          <LoadingButton className="primary" disabled={!canSubmit} hint={t('users.resetPasswordHint')} isLoading={passwordLoading} loadingLabel={t('security.resetPasswordLoading')} type="submit">
            {t('users.resetPassword')}
          </LoadingButton>
          <button className="secondary" onClick={onClose} title={t('common.cancel')} type="button">
            {t('common.cancel')}
          </button>
        </div>
      </form>
    </ModalDialog>
  )
}

export default PasswordResetDialog
