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

export function statusLabel(status, locale = 'en') {
  switch (status) {
    case 'SUCCESS':
      return translate(locale, 'status.success')
    case 'ERROR':
      return translate(locale, 'status.error')
    default:
      return translate(locale, 'status.notRun')
  }
}

export function roleLabel(role, locale = 'en') {
  switch (role) {
    case 'ADMIN':
      return translate(locale, 'role.admin')
    case 'USER':
      return translate(locale, 'role.user')
    default:
      return role || translate(locale, 'tokenStorage.unknown')
  }
}

export function protocolLabel(protocol, locale = 'en') {
  switch (protocol) {
    case 'IMAP':
      return translate(locale, 'protocol.imap')
    case 'POP3':
      return translate(locale, 'protocol.pop3')
    default:
      return protocol || translate(locale, 'tokenStorage.unknown')
  }
}

export function authMethodLabel(method, locale = 'en') {
  switch (method) {
    case 'PASSWORD':
      return translate(locale, 'authMethod.password')
    case 'OAUTH2':
      return translate(locale, 'authMethod.oauth2')
    default:
      return method || translate(locale, 'tokenStorage.unknown')
  }
}

export function oauthProviderLabel(provider, locale = 'en') {
  switch (provider) {
    case 'MICROSOFT':
      return translate(locale, 'oauthProvider.microsoft')
    case 'NONE':
      return translate(locale, 'oauthProvider.none')
    default:
      return provider || translate(locale, 'tokenStorage.unknown')
  }
}

export function triggerLabel(trigger, locale = 'en') {
  switch (String(trigger || '').toLowerCase()) {
    case 'scheduler':
      return translate(locale, 'trigger.scheduler')
    case 'manual':
      return translate(locale, 'trigger.manual')
    default:
      return trigger || translate(locale, 'trigger.unknown')
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
