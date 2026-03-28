import { translate } from './i18n'

function normalizeDateValue(value) {
  if (!value) return value
  if (value instanceof Date) return value
  const text = String(value)
  const exactDate = new Date(text)
  if (!Number.isNaN(exactDate.getTime())) {
    return exactDate
  }
  const normalizedText = text.replace(/\.(\d{3})\d+(Z|[+-]\d{2}:\d{2})$/, '.$1$2')
  const normalizedDate = new Date(normalizedText)
  if (!Number.isNaN(normalizedDate.getTime())) {
    return normalizedDate
  }
  return null
}

export function formatDate(value, locale = 'en') {
  if (!value) return translate(locale, 'common.never')
  const date = normalizeDateValue(value)
  if (!date) return translate(locale, 'common.unavailable')
  return date.toLocaleString(locale)
}

export function formatPollError(message, locale = 'en') {
  if (message && typeof message === 'object') {
    const code = String(message.code || '').trim()
    const bridgeId = message.sourceId || ''
    switch (code) {
      case 'source_cooling_down':
        return translate(locale, 'common.cooldownError', {
          bridgeId,
          value: formatDate(message.value, locale)
        })
      case 'source_waiting_next_window':
        return translate(locale, 'common.nextPollWindowError', {
          bridgeId,
          value: formatDate(message.value, locale)
        })
      case 'source_disabled':
        return translate(locale, 'common.sourceDisabledError', { bridgeId })
      case 'source_polling_disabled':
        return translate(locale, 'common.sourcePollingDisabledError', { bridgeId })
      case 'gmail_account_not_linked':
        return translate(locale, 'common.gmailAccountNotLinkedError', { bridgeId })
      case 'gmail_access_revoked':
        return translate(locale, 'common.gmailAccessRevokedError', { bridgeId })
      case 'microsoft_access_revoked':
        return translate(locale, 'common.microsoftAccessRevokedError', { bridgeId })
      case 'google_source_access_revoked':
        return translate(locale, 'common.googleSourceAccessRevokedError', { bridgeId })
      case 'poll_busy':
        return translate(locale, 'common.pollBusyError')
      case 'manual_poll_rate_limited':
        return translate(locale, 'common.manualPollRateLimitedError', {
          value: formatDate(message.value, locale)
        })
      default:
        return formatPollError(message.message, locale)
    }
  }

  const text = String(message || '').trim()
  if (!text) return text
  const cooldownMatch = text.match(/^Source (.+?) is cooling down until (.+)\.$/)
  if (cooldownMatch) {
    const [, bridgeId, until] = cooldownMatch
    return translate(locale, 'common.cooldownError', {
      bridgeId,
      value: formatDate(until, locale)
    })
  }

  const nextWindowMatch = text.match(/^Source (.+?) is waiting for its next poll window at (.+)\.$/)
  if (nextWindowMatch) {
    const [, bridgeId, at] = nextWindowMatch
    return translate(locale, 'common.nextPollWindowError', {
      bridgeId,
      value: formatDate(at, locale)
    })
  }

  const sourceDisabledMatch = text.match(/^Source (.+?) is disabled\.$/)
  if (sourceDisabledMatch) {
    return translate(locale, 'common.sourceDisabledError', { bridgeId: sourceDisabledMatch[1] })
  }

  const sourcePollingDisabledMatch = text.match(/^Source (.+?) is skipped because polling is disabled for this fetcher\.$/)
  if (sourcePollingDisabledMatch) {
    return translate(locale, 'common.sourcePollingDisabledError', { bridgeId: sourcePollingDisabledMatch[1] })
  }

  const gmailAccountNotLinkedMatch = text.match(/^Source (.+?) failed: The Gmail account is not linked for this destination\..+$/)
  if (gmailAccountNotLinkedMatch) {
    return translate(locale, 'common.gmailAccountNotLinkedError', { bridgeId: gmailAccountNotLinkedMatch[1] })
  }

  const gmailAccessRevokedMatch = text.match(/^Source (.+?) failed: The linked Gmail account no longer grants InboxBridge access\..+$/)
  if (gmailAccessRevokedMatch) {
    return translate(locale, 'common.gmailAccessRevokedError', { bridgeId: gmailAccessRevokedMatch[1] })
  }

  const gmailRefreshRevokedMatch = text.match(/^Source (.+?) failed: Google token request failed with status 400:[\s\S]*invalid_grant[\s\S]*expired or revoked[\s\S]*$/i)
  if (gmailRefreshRevokedMatch) {
    return translate(locale, 'common.gmailAccessRevokedError', { bridgeId: gmailRefreshRevokedMatch[1] })
  }

  const microsoftAccessRevokedMatch = text.match(/^Source (.+?) failed: The linked Microsoft account no longer grants InboxBridge access\..+$/)
  if (microsoftAccessRevokedMatch) {
    return translate(locale, 'common.microsoftAccessRevokedError', { bridgeId: microsoftAccessRevokedMatch[1] })
  }

  const googleSourceAccessRevokedMatch = text.match(/^Source (.+?) failed: The linked Google account no longer grants InboxBridge access\..+$/)
  if (googleSourceAccessRevokedMatch) {
    return translate(locale, 'common.googleSourceAccessRevokedError', { bridgeId: googleSourceAccessRevokedMatch[1] })
  }

  if (text.startsWith('A poll is already running')) {
    return translate(locale, 'common.pollBusyError')
  }

  const manualRateLimitMatch = text.match(/^Manual polling is temporarily rate limited until (.+)\.$/)
  if (manualRateLimitMatch) {
    return translate(locale, 'common.manualPollRateLimitedError', {
      value: formatDate(manualRateLimitMatch[1], locale)
    })
  }

  return text
}

export function isOauthRevokedError(message) {
  if (!message) return false
  if (typeof message === 'object') {
    const code = String(message.code || '').trim()
    return code === 'gmail_access_revoked' || code === 'microsoft_access_revoked'
  }
  const text = String(message).trim()
  return text.includes('The linked Gmail account no longer grants InboxBridge access.')
    || text.includes('The linked Microsoft account no longer grants InboxBridge access.')
    || text.includes('The linked Google account no longer grants InboxBridge access.')
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
    case 'GOOGLE':
      return translate(locale, 'oauthProvider.google')
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
