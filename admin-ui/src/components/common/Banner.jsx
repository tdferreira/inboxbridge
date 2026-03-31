import CopyButton from './CopyButton'
import './Banner.css'

/**
 * Reusable inline status message with severity-specific styling.
 */
function Banner({
  children,
  copyLabel = 'Copy Error',
  copiedLabel = 'Copied',
  copyText,
  dismissLabel = 'Dismiss notification',
  focusLabel = 'Focus the related section',
  onDismiss,
  onFocus,
  title,
  tone = 'success'
}) {
  function handleFocusActivate() {
    onFocus?.()
  }

  function handleKeyDown(event) {
    if (!onFocus) {
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      handleFocusActivate()
    }
  }

  const toneClass = tone === 'error'
    ? 'banner-error'
    : tone === 'warning'
      ? 'banner-warning'
      : 'banner-success'

  return (
    <section
      aria-label={onFocus ? focusLabel : undefined}
      className={`app-banner ${toneClass} ${onFocus ? 'app-banner-actionable' : ''}`.trim()}
      onClick={onFocus ? handleFocusActivate : undefined}
      onKeyDown={handleKeyDown}
      role={onFocus ? 'button' : undefined}
      tabIndex={onFocus ? 0 : undefined}
      title={title}
    >
      <div className="app-banner-content">
        {children}
      </div>
      <div className="app-banner-actions" onClick={(event) => event.stopPropagation()} onKeyDown={(event) => event.stopPropagation()}>
        {copyText ? <CopyButton copiedLabel={copiedLabel} label={copyLabel} text={copyText} /> : null}
        {onDismiss ? (
          <button
            aria-label={dismissLabel}
            className="banner-dismiss-button"
            onClick={onDismiss}
            title={dismissLabel}
            type="button"
          >
            ×
          </button>
        ) : null}
      </div>
    </section>
  )
}

export default Banner
