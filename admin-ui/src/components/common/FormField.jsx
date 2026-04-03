import InfoHint from './InfoHint'

function FormField({
  children,
  className = '',
  helpText,
  label
}) {
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
