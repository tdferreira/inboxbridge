import LoadingButton from '../common/LoadingButton'
import './PasswordPanel.css'

function PasswordPanel({ onPasswordChange, onPasswordFormChange, passwordForm, passwordLoading }) {
  const validations = [
    { label: 'At least 8 characters', valid: passwordForm.newPassword.length >= 8 },
    { label: 'Uppercase letter', valid: /[A-Z]/.test(passwordForm.newPassword) },
    { label: 'Lowercase letter', valid: /[a-z]/.test(passwordForm.newPassword) },
    { label: 'Number', valid: /\d/.test(passwordForm.newPassword) },
    { label: 'Special character', valid: /[^A-Za-z0-9]/.test(passwordForm.newPassword) },
    { label: 'Different from current password', valid: passwordForm.newPassword !== '' && passwordForm.newPassword !== passwordForm.currentPassword },
    { label: 'Repeat password matches', valid: passwordForm.newPassword !== '' && passwordForm.newPassword === passwordForm.confirmNewPassword }
  ]
  const canSubmit = validations.every((item) => item.valid) && passwordForm.currentPassword !== ''

  return (
    <section className="surface-card password-panel" id="password-panel-section" tabIndex="-1">
      <div className="section-title">Password</div>
      <form className="stack-form" onSubmit={onPasswordChange}>
        <label>
          <span>Current Password</span>
          <input
            type="password"
            value={passwordForm.currentPassword}
            onChange={(event) => onPasswordFormChange((current) => ({ ...current, currentPassword: event.target.value }))}
          />
        </label>
        <label>
          <span>New Password</span>
          <input
            type="password"
            value={passwordForm.newPassword}
            onChange={(event) => onPasswordFormChange((current) => ({ ...current, newPassword: event.target.value }))}
          />
        </label>
        <label>
          <span>Repeat New Password</span>
          <input
            type="password"
            value={passwordForm.confirmNewPassword}
            onChange={(event) => onPasswordFormChange((current) => ({ ...current, confirmNewPassword: event.target.value }))}
          />
        </label>
        <div className="password-requirements">
          {validations.map((item) => (
            <div key={item.label} className={`password-requirement ${item.valid ? 'valid' : 'invalid'}`}>
              {item.label}
            </div>
          ))}
        </div>
        <LoadingButton className="secondary" disabled={!canSubmit} isLoading={passwordLoading} loadingLabel="Changing Password…" type="submit">
          Change Password
        </LoadingButton>
      </form>
    </section>
  )
}

export default PasswordPanel
