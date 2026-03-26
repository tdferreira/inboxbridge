export function formatDate(value) {
  if (!value) return 'Never'
  return new Date(value).toLocaleString()
}

export function statusTone(status) {
  switch (status) {
    case 'SUCCESS':
      return 'tone-success'
    case 'ERROR':
      return 'tone-error'
    default:
      return 'tone-neutral'
  }
}

export function tokenStorageLabel(mode) {
  switch (mode) {
    case 'DATABASE':
      return 'Encrypted DB'
    case 'ENVIRONMENT':
      return '.env fallback'
    case 'CONFIGURED_BUT_EMPTY':
      return 'Ready for OAuth'
    case 'NOT_CONFIGURED':
      return 'Not configured'
    case 'PASSWORD':
      return 'Password auth'
    default:
      return mode || 'Unknown'
  }
}
