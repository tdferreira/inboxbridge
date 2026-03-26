import './ModalDialog.css'

/**
 * Lightweight modal shell used for focused admin workflows without introducing
 * another UI framework dependency.
 */
function ModalDialog({ children, className = '', closeDisabled = false, closeLabel, onClose, size = 'default', title }) {
  const resolvedCloseLabel = closeLabel || `Close ${title}`
  const sizeClassName = size === 'wide' ? 'modal-surface-wide' : ''
  return (
    <div className="modal-backdrop" role="presentation">
      <section aria-label={title} aria-modal="true" className={`modal-surface surface-card ${sizeClassName} ${className}`.trim()} role="dialog">
        <div className="modal-header">
          <div className="section-title">{title}</div>
          <button aria-label={resolvedCloseLabel} className="modal-close-button" disabled={closeDisabled} onClick={onClose} title={resolvedCloseLabel} type="button">
            ×
          </button>
        </div>
        {children}
      </section>
    </div>
  )
}

export default ModalDialog
