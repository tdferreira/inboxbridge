import { formatDurationHint } from '@/lib/formatters'

function DurationValue({ className = '', locale = 'en', value }) {
  const hint = formatDurationHint(value, locale)
  const resolvedClassName = `duration-value${className ? ` ${className}` : ''}`
  return (
    <span className={resolvedClassName} title={hint || undefined}>
      {value}
    </span>
  )
}

export default DurationValue
