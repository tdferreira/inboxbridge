import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'
import './PasskeyRegistrationDialog.css'

/**
 * Focused modal used to register a new passkey without stretching the security
 * panel layout.
 */
function PasskeyRegistrationDialog({
  isLoading,
  onClose,
  onPasskeyLabelChange,
  onSubmit,
  passkeyLabel,
  t
}) {
  const isDirty = passkeyLabel.trim().length > 0

  function handleCancel() {
    if (isDirty && !window.confirm(t('common.unsavedChangesConfirm'))) {
      return
    }
    onClose()
  }

  return (
    <ModalDialog
      closeLabel={t('passkey.closeDialog')}
      isDirty={isDirty}
      onClose={onClose}
      title={t('passkey.dialogTitle')}
      unsavedChangesMessage={t('common.unsavedChangesConfirm')}
    >
      <form className="passkey-registration-dialog" onSubmit={onSubmit}>
        <p className="section-copy">{t('passkey.dialogCopy')}</p>
        <label>
          <span>{t('passkey.label')}</span>
          <input
            autoFocus
            placeholder={t('passkey.placeholder')}
            value={passkeyLabel}
            onChange={(event) => onPasskeyLabelChange(event.target.value)}
          />
        </label>
        <div className="action-row">
          <LoadingButton className="primary" isLoading={isLoading} loadingLabel={t('passkey.registerLoading')} type="submit">
            {t('passkey.register')}
          </LoadingButton>
          <button className="secondary" onClick={handleCancel} type="button">
            {t('common.cancel')}
          </button>
        </div>
      </form>
    </ModalDialog>
  )
}

export default PasskeyRegistrationDialog
