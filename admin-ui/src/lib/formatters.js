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

function parseDurationToMilliseconds(value) {
  if (value == null) return null
  const text = String(value).trim()
  if (!text) return null

  const shorthandMatch = text.match(/^(\d+(?:\.\d+)?)(ms|s|m|h|d)$/i)
  if (shorthandMatch) {
    const amount = Number(shorthandMatch[1])
    const unit = shorthandMatch[2].toLowerCase()
    if (Number.isNaN(amount)) return null
    const multipliers = {
      ms: 1,
      s: 1000,
      m: 60_000,
      h: 3_600_000,
      d: 86_400_000
    }
    return amount * multipliers[unit]
  }

  const isoMatch = text.match(/^P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$/i)
  if (!isoMatch) return null
  const days = Number(isoMatch[1] || 0)
  const hours = Number(isoMatch[2] || 0)
  const minutes = Number(isoMatch[3] || 0)
  const seconds = Number(isoMatch[4] || 0)
  return (days * 86_400_000) + (hours * 3_600_000) + (minutes * 60_000) + (seconds * 1000)
}

export function formatDurationMeaning(value, locale = 'en') {
  const milliseconds = parseDurationToMilliseconds(value)
  if (milliseconds == null) return ''

  const unitFormatter = (unit, amount) => new Intl.NumberFormat(locale, {
    style: 'unit',
    unit,
    unitDisplay: 'long',
    maximumFractionDigits: unit === 'second' && amount % 1 !== 0 ? 2 : 0
  }).format(amount)
  const parts = []
  let remainder = milliseconds

  const days = Math.floor(remainder / 86_400_000)
  if (days > 0) {
    parts.push(unitFormatter('day', days))
    remainder -= days * 86_400_000
  }
  const hours = Math.floor(remainder / 3_600_000)
  if (hours > 0) {
    parts.push(unitFormatter('hour', hours))
    remainder -= hours * 3_600_000
  }
  const minutes = Math.floor(remainder / 60_000)
  if (minutes > 0) {
    parts.push(unitFormatter('minute', minutes))
    remainder -= minutes * 60_000
  }
  const seconds = Math.floor(remainder / 1000)
  if (seconds > 0) {
    parts.push(unitFormatter('second', seconds))
    remainder -= seconds * 1000
  }
  if (remainder > 0 || parts.length === 0) {
    if (parts.length === 0 && milliseconds >= 1000 && milliseconds % 1000 !== 0) {
      parts.push(unitFormatter('second', milliseconds / 1000))
    } else if (remainder > 0 || milliseconds < 1000) {
      parts.push(unitFormatter('millisecond', remainder || milliseconds))
    }
  }

  if (parts.length === 1) return parts[0]
  return new Intl.ListFormat(locale, { style: 'long', type: 'conjunction' }).format(parts)
}

export function formatDurationHint(value, locale = 'en') {
  const meaning = formatDurationMeaning(value, locale)
  if (!meaning) return ''
  return `${value} = ${meaning}`
}

export function formatBytes(value, locale = 'en') {
  if (!Number.isFinite(value) || value < 0) return translate(locale, 'common.unavailable')
  if (value < 1024) return `${Math.round(value)} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let scaled = value / 1024
  let unitIndex = 0
  while (scaled >= 1024 && unitIndex < units.length - 1) {
    scaled /= 1024
    unitIndex += 1
  }
  const maximumFractionDigits = scaled >= 10 ? 1 : 2
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits }).format(scaled)} ${units[unitIndex]}`
}

export function formatImportedSizeSummary(event, locale = 'en') {
  if (!event || !Number.isFinite(event.importedBytes) || event.importedBytes <= 0) return ''
  return translate(locale, 'bridge.importedSize', {
    size: formatBytes(event.importedBytes, locale)
  })
}

export function formatRemoteImportedSizeSummary(value, locale = 'en') {
  if (!Number.isFinite(value) || value <= 0) return ''
  return translate(locale, 'remote.importedSize', {
    size: formatBytes(value, locale)
  })
}

export function formatPollError(message, locale = 'en') {
  if (message && typeof message === 'object') {
    const code = String(message.code || '').trim()
    const emailAccountId = message.sourceId || ''
    switch (code) {
      case 'source_cooling_down':
        return translate(locale, 'common.cooldownError', {
          emailAccountId,
          value: formatDate(message.value, locale)
        })
      case 'source_waiting_next_window':
        return translate(locale, 'common.nextPollWindowError', {
          emailAccountId,
          value: formatDate(message.value, locale)
        })
      case 'source_disabled':
        return translate(locale, 'common.sourceDisabledError', { emailAccountId })
      case 'source_polling_disabled':
        return translate(locale, 'common.sourcePollingDisabledError', { emailAccountId })
      case 'gmail_account_not_linked':
        return translate(locale, 'common.gmailAccountNotLinkedError', { emailAccountId })
      case 'gmail_access_revoked':
        return translate(locale, 'common.gmailAccessRevokedError', { emailAccountId })
      case 'microsoft_access_revoked':
        return translate(locale, 'common.microsoftAccessRevokedError', { emailAccountId })
      case 'google_source_access_revoked':
        return translate(locale, 'common.googleSourceAccessRevokedError', { emailAccountId })
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
    const [, emailAccountId, until] = cooldownMatch
    return translate(locale, 'common.cooldownError', {
      emailAccountId,
      value: formatDate(until, locale)
    })
  }

  const nextWindowMatch = text.match(/^Source (.+?) is waiting for its next poll window at (.+)\.$/)
  if (nextWindowMatch) {
    const [, emailAccountId, at] = nextWindowMatch
    return translate(locale, 'common.nextPollWindowError', {
      emailAccountId,
      value: formatDate(at, locale)
    })
  }

  const sourceDisabledMatch = text.match(/^Source (.+?) is disabled\.$/)
  if (sourceDisabledMatch) {
    return translate(locale, 'common.sourceDisabledError', { emailAccountId: sourceDisabledMatch[1] })
  }

  const sourcePollingDisabledMatch = text.match(/^Source (.+?) is skipped because polling is disabled for this fetcher\.$/)
  if (sourcePollingDisabledMatch) {
    return translate(locale, 'common.sourcePollingDisabledError', { emailAccountId: sourcePollingDisabledMatch[1] })
  }

  const gmailAccountNotLinkedMatch = text.match(/^Source (.+?) failed: The Gmail account is not linked for this destination\..+$/)
  if (gmailAccountNotLinkedMatch) {
    return translate(locale, 'common.gmailAccountNotLinkedError', { emailAccountId: gmailAccountNotLinkedMatch[1] })
  }

  const gmailAccessRevokedMatch = text.match(/^Source (.+?) failed: The linked Gmail account no longer grants InboxBridge access\..+$/)
  if (gmailAccessRevokedMatch) {
    return translate(locale, 'common.gmailAccessRevokedError', { emailAccountId: gmailAccessRevokedMatch[1] })
  }

  const gmailRefreshRevokedMatch = text.match(/^Source (.+?) failed: Google token request failed with status 400:[\s\S]*invalid_grant[\s\S]*expired or revoked[\s\S]*$/i)
  if (gmailRefreshRevokedMatch) {
    return translate(locale, 'common.gmailAccessRevokedError', { emailAccountId: gmailRefreshRevokedMatch[1] })
  }

  const microsoftAccessRevokedMatch = text.match(/^Source (.+?) failed: The linked Microsoft account no longer grants InboxBridge access\..+$/)
  if (microsoftAccessRevokedMatch) {
    return translate(locale, 'common.microsoftAccessRevokedError', { emailAccountId: microsoftAccessRevokedMatch[1] })
  }

  const googleSourceAccessRevokedMatch = text.match(/^Source (.+?) failed: The linked Google account no longer grants InboxBridge access\..+$/)
  if (googleSourceAccessRevokedMatch) {
    return translate(locale, 'common.googleSourceAccessRevokedError', { emailAccountId: googleSourceAccessRevokedMatch[1] })
  }

  const oauthRefreshTokenMissingMatch = text.match(/^(?:Source (.+?) failed:\s*)?Source (.+?) is configured for OAuth2 but has no refresh token\.?$/)
  if (oauthRefreshTokenMissingMatch) {
    return translate(locale, 'common.oauthRefreshTokenMissingError', {
      emailAccountId: oauthRefreshTokenMissingMatch[1] || oauthRefreshTokenMissingMatch[2]
    })
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
    case 'DISABLED':
      return 'tone-neutral'
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
    case 'DISABLED':
      return translate(locale, 'common.disabled')
    case 'SUCCESS':
      return translate(locale, 'status.success')
    case 'ERROR':
      return translate(locale, 'status.error')
    default:
      return translate(locale, 'status.notRun')
  }
}

export function effectiveEmailAccountStatus(emailAccount) {
  if (emailAccount?.enabled === false) {
    return 'DISABLED'
  }
  return emailAccount?.lastEvent?.status || null
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
    case 'PASSKEY':
      return translate(locale, 'authMethod.passkey')
    case 'PASSWORD_PLUS_PASSKEY':
      return translate(locale, 'authMethod.passwordPlusPasskey')
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

export function executionSurfaceLabel(surface, locale = 'en') {
  switch (String(surface || '').toUpperCase()) {
    case 'MY_INBOXBRIDGE':
      return translate(locale, 'bridge.surface.myInboxBridge')
    case 'ADMINISTRATION':
      return translate(locale, 'bridge.surface.administration')
    case 'INBOXBRIDGE_GO':
      return translate(locale, 'bridge.surface.inboxBridgeGo')
    case 'API':
      return translate(locale, 'bridge.surface.api')
    default:
      return ''
  }
}

export function formatPollExecutionSummary(event, locale = 'en', viewerUsername = null) {
  if (!event) return ''

  const time = formatDate(event.finishedAt || event.startedAt, locale)
  const surface = executionSurfaceLabel(event.executionSurface, locale)
  const actorUsername = event.actorUsername || null
  const shouldShowActor = actorUsername && viewerUsername && actorUsername !== viewerUsername

  if (String(event.executionSurface || '').toUpperCase() === 'AUTOMATIC' || String(event.trigger || '').toLowerCase() === 'scheduler') {
    return translate(locale, 'bridge.executedAutomaticallyAt', { time })
  }
  if (shouldShowActor && surface) {
    return translate(locale, 'bridge.executedAtByVia', { time, username: actorUsername, surface })
  }
  if (surface) {
    return translate(locale, 'bridge.executedAtVia', { time, surface })
  }
  if (shouldShowActor) {
    return translate(locale, 'bridge.executedAtBy', { time, username: actorUsername })
  }
  return translate(locale, 'bridge.executedAt', { time })
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
