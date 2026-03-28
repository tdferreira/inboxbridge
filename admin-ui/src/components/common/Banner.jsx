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
  const toneClass = tone === 'error'
    ? 'banner-error'
    : tone === 'warning'
      ? 'banner-warning'
      : 'banner-success'

  return (
    <section className={`app-banner ${toneClass}`} title={title}>
      <div className="app-banner-content">
        {onFocus ? (
          <button aria-label={focusLabel} className="banner-focus-button" onClick={onFocus} title={focusLabel} type="button">
            {children}
          </button>
        ) : children}
      </div>
      <div className="app-banner-actions">
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
