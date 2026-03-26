import { useId, useState } from 'react'
import InfoHint from './InfoHint'
import './PasswordField.css'

/**
 * Shared password input with an inline show/hide toggle so authentication and
 * account-security flows behave consistently across the admin UI.
 */
function PasswordField({ className = '', helpText, hideLabel, id, label, showLabel, ...inputProps }) {
  const generatedId = useId()
  const inputId = id || generatedId
  const [visible, setVisible] = useState(false)
  const resolvedShowLabel = showLabel || `Show ${label}`
  const resolvedHideLabel = hideLabel || `Hide ${label}`

  return (
    <label className={`password-field ${className}`.trim()}>
      <span className="field-label-row">
        <span>{label}</span>
        {helpText ? <InfoHint text={helpText} /> : null}
      </span>
      <span className="password-field-input-wrap">
        <input
          {...inputProps}
          id={inputId}
          type={visible ? 'text' : 'password'}
        />
        <button
          aria-label={visible ? resolvedHideLabel : resolvedShowLabel}
          className="password-visibility-toggle"
          onClick={() => setVisible((current) => !current)}
          title={visible ? resolvedHideLabel : resolvedShowLabel}
          type="button"
        >
          <svg aria-hidden="true" className="password-visibility-icon" viewBox="0 0 24 24">
            {visible ? (
              <>
                <path d="M3 5L21 19" />
                <path d="M10.6 10.7a2 2 0 0 0 2.7 2.7" />
                <path d="M9.4 5.8A10.7 10.7 0 0 1 12 5.5c4.6 0 8.5 2.6 10 6.5a11.4 11.4 0 0 1-4 5.1" />
                <path d="M6.5 8.2A11.2 11.2 0 0 0 2 12c1.5 3.9 5.4 6.5 10 6.5 1 0 2-.1 3-.4" />
              </>
            ) : (
              <>
                <path d="M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12Z" />
                <circle cx="12" cy="12" r="3" />
              </>
            )}
          </svg>
        </button>
      </span>
    </label>
  )
}

export default PasswordField
