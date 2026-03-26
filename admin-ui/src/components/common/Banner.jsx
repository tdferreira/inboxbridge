import CopyButton from './CopyButton'
import './Banner.css'

/**
 * Reusable inline status message with severity-specific styling.
 */
function Banner({ children, copyText, tone = 'success' }) {
  const toneClass = tone === 'error'
    ? 'banner-error'
    : tone === 'warning'
      ? 'banner-warning'
      : 'banner-success'

  return (
    <section className={`app-banner ${toneClass}`}>
      <div className="app-banner-content">{children}</div>
      {copyText ? <CopyButton label="Copy Error" text={copyText} /> : null}
    </section>
  )
}

export default Banner
