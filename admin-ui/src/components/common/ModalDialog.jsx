import { useEffect, useRef } from 'react'

import './ModalDialog.css'

/**
 * Lightweight modal shell used for focused admin workflows without introducing
 * another UI framework dependency. Supports Escape-to-close and optional
 * unsaved-changes confirmation for form dialogs.
 */
function ModalDialog({
  children,
  className = '',
  closeDisabled = false,
  closeLabel,
  isDirty = false,
  onClose,
  size = 'default',
  title,
  unsavedChangesMessage = ''
}) {
  const resolvedCloseLabel = closeLabel || `Close ${title}`
  const sizeClassName = size === 'wide' ? 'modal-surface-wide' : ''
  const dialogRef = useRef(null)

  function requestClose() {
    if (closeDisabled) {
      return
    }
    if (isDirty && unsavedChangesMessage && !window.confirm(unsavedChangesMessage)) {
      return
    }
    onClose()
  }

  useEffect(() => {
    function handleKeyDown(event) {
      const currentDialog = dialogRef.current
      const dialogs = Array.from(document.querySelectorAll('[role="dialog"][aria-modal="true"]'))
      const topmostDialog = dialogs.at(-1)
      if (!currentDialog || currentDialog !== topmostDialog) {
        return
      }
      if (event.key === 'Escape') {
        event.preventDefault()
        requestClose()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  })

  return (
    <div className="modal-backdrop" role="presentation">
      <section aria-label={title} aria-modal="true" className={`modal-surface surface-card ${sizeClassName} ${className}`.trim()} ref={dialogRef} role="dialog">
        <div className="modal-header">
          <div className="section-title">{title}</div>
          <button aria-label={resolvedCloseLabel} className="modal-close-button" disabled={closeDisabled} onClick={requestClose} title={resolvedCloseLabel} type="button">
            ×
          </button>
        </div>
        {children}
      </section>
    </div>
  )
}

export default ModalDialog
