import InfoHint from './InfoHint'

function FormField({
  children,
  className = '',
  helpText,
  inputId,
  label,
  wrapWithLabel = true
}) {
  if (!wrapWithLabel) {
    return (
      <div className={className}>
        <label className="field-label-row" htmlFor={inputId}>
          <span>{label}</span>
          {helpText ? <InfoHint text={helpText} /> : null}
        </label>
        {children}
      </div>
    )
  }

  return (
    <label className={className}>
      <span className="field-label-row">
        <span>{label}</span>
        {helpText ? <InfoHint text={helpText} /> : null}
      </span>
      {children}
    </label>
  )
}

export default FormField
