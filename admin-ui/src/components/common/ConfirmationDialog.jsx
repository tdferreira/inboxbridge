import LoadingButton from './LoadingButton'
import ModalDialog from './ModalDialog'

/**
 * Shared confirmation modal for destructive or access-impacting actions.
 */
function ConfirmationDialog({
  body,
  cancelLabel,
  closeDisabled = false,
  confirmLabel,
  confirmLoading = false,
  confirmLoadingLabel,
  confirmTone = 'danger',
  onCancel,
  onConfirm,
  title
}) {
  return (
    <ModalDialog closeDisabled={closeDisabled} onClose={onCancel} title={title}>
      <p className="section-copy">{body}</p>
      <div className="action-row">
        <LoadingButton
          className={confirmTone}
          disabled={false}
          isLoading={confirmLoading}
          loadingLabel={confirmLoadingLabel}
          onClick={onConfirm}
          type="button"
        >
          {confirmLabel}
        </LoadingButton>
        <button className="secondary" disabled={closeDisabled} onClick={onCancel} type="button">
          {cancelLabel}
        </button>
      </div>
    </ModalDialog>
  )
}

export default ConfirmationDialog
