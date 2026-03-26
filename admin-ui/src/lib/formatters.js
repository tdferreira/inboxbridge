import { translate } from './i18n'

export function formatDate(value, locale = 'en') {
  if (!value) return translate(locale, 'common.never')
  return new Date(value).toLocaleString(locale)
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

export function tokenStorageLabel(mode, locale = 'en') {
  switch (mode) {
    case 'DATABASE':
      return translate(locale, 'tokenStorage.database')
    case 'ENVIRONMENT':
      return translate(locale, 'tokenStorage.environment')
    case 'CONFIGURED_BUT_EMPTY':
      return translate(locale, 'tokenStorage.configuredEmpty')
    case 'NOT_CONFIGURED':
      return translate(locale, 'tokenStorage.notConfigured')
    case 'PASSWORD':
      return translate(locale, 'tokenStorage.password')
    default:
      return mode || translate(locale, 'tokenStorage.unknown')
  }
}
