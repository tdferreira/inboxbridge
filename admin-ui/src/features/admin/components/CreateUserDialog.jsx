import LoadingButton from '@/shared/components/LoadingButton'
import ModalDialog from '@/shared/components/ModalDialog'
import PasswordField from '@/shared/components/PasswordField'
import InfoHint from '@/shared/components/InfoHint'
import { buildPasswordChecks, canSubmitPasswordChange } from '@/lib/passwordPolicy'
import './PasswordResetDialog.css'

function CreateUserDialog({
  createUserForm,
  createUserLoading,
  duplicateUsername,
  onClose,
  onFormChange,
  onSubmit,
  roleLabel,
  t
}) {
  const validations = buildPasswordChecks('', createUserForm.password, createUserForm.confirmPassword, t, {
    requireDifferent: false
  })
  const canSubmit = createUserForm.username.trim() !== ''
    && !duplicateUsername
    && canSubmitPasswordChange('', createUserForm.password, createUserForm.confirmPassword, {
      requireCurrentPassword: false,
      requireDifferent: false
    })
  const isDirty = createUserForm.username.trim() !== ''
    || createUserForm.password !== ''
    || createUserForm.confirmPassword !== ''
    || createUserForm.role !== 'USER'

  function updateField(field, value) {
    onFormChange((current) => ({ ...current, [field]: value }))
  }

  return (
    <ModalDialog
      isDirty={isDirty}
      onClose={onClose}
      title={t('users.createDialogTitle')}
      unsavedChangesMessage={t('common.unsavedChangesConfirm')}
    >
      <p className="section-copy">{t('users.createDialogCopy')}</p>
      <form className="stack-form" onSubmit={onSubmit}>
        <label>
          <span>{t('auth.username')}</span>
          <input
            autoFocus
            value={createUserForm.username}
            onChange={(event) => updateField('username', event.target.value)}
          />
        </label>
        {duplicateUsername ? <div className="banner-error">{t('users.duplicateUsername', { username: createUserForm.username.trim() })}</div> : null}
        <PasswordField
          hideLabel={t('common.hideField', { label: t('users.initialPassword') })}
          label={t('users.initialPassword')}
          value={createUserForm.password}
          onChange={(event) => updateField('password', event.target.value)}
          showLabel={t('common.showField', { label: t('users.initialPassword') })}
        />
        <PasswordField
          hideLabel={t('common.hideField', { label: t('password.repeatNew') })}
          label={t('password.repeatNew')}
          value={createUserForm.confirmPassword}
          onChange={(event) => updateField('confirmPassword', event.target.value)}
          showLabel={t('common.showField', { label: t('password.repeatNew') })}
        />
        <div className="password-requirements">
          {validations.map((item) => (
            <div key={item.label} className={`password-requirement ${item.valid ? 'valid' : 'invalid'}`}>
              {item.label}
            </div>
          ))}
        </div>
        <label>
          <span className="field-label-row">
            <span>{t('users.role')}</span>
            <InfoHint text={t('users.roleHelp')} />
          </span>
          <select value={createUserForm.role} onChange={(event) => updateField('role', event.target.value)}>
            <option value="USER">{roleLabel('USER')}</option>
            <option value="ADMIN">{roleLabel('ADMIN')}</option>
          </select>
        </label>
        <div className="action-row">
          <LoadingButton className="primary" disabled={!canSubmit} isLoading={createUserLoading} loadingLabel={t('users.createLoading')} type="submit">
            {t('users.create')}
          </LoadingButton>
          <button className="secondary" onClick={onClose} type="button">
            {t('common.cancel')}
          </button>
        </div>
      </form>
    </ModalDialog>
  )
}

export default CreateUserDialog
