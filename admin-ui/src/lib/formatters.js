import { translate } from './i18n'
import { getCurrentFormattingTimeZone } from './timeZonePreferences'

export const DATE_FORMAT_AUTO = 'AUTO'
export const DATE_FORMAT_YMD_24 = 'YMD_24'
export const DATE_FORMAT_YMD_12 = 'YMD_12'
export const DATE_FORMAT_DMY_24 = 'DMY_24'
export const DATE_FORMAT_DMY_12 = 'DMY_12'
export const DATE_FORMAT_MDY_24 = 'MDY_24'
export const DATE_FORMAT_MDY_12 = 'MDY_12'
export const DATE_FORMAT_CUSTOM = 'CUSTOM'
export const DATE_FORMAT_OPTIONS = [DATE_FORMAT_AUTO, DATE_FORMAT_DMY_24, DATE_FORMAT_DMY_12, DATE_FORMAT_MDY_24, DATE_FORMAT_MDY_12, DATE_FORMAT_YMD_24, DATE_FORMAT_YMD_12]
const CUSTOM_DATE_FORMAT_TOKEN_PATTERN = /(YYYY|YY|MMMM|MMM|MM|DD|dddd|ddd|HH|H|hh|h|mm|M|ss|S|A|[^A-Za-z]+)/g
const AUTOMATIC_DATE_FORMAT_REFERENCE = new Date('2026-04-06T13:24:56Z')
const CUSTOM_DATE_FORMAT_TOKEN_DEFINITIONS = [
  { canonical: 'YYYY', aliasByLocale: { de: 'JJJJ', es: 'AAAA', fr: 'AAAA', pt: 'AAAA' }, descriptionKey: 'preferences.customDateFormatToken.year4', id: 'year4', sampleKey: 'preferences.customDateFormatTokenSample.year4' },
  { canonical: 'YY', aliasByLocale: { de: 'JJ', es: 'AA', fr: 'AA', pt: 'AA' }, descriptionKey: 'preferences.customDateFormatToken.year2', id: 'year2', sampleKey: 'preferences.customDateFormatTokenSample.year2' },
  { canonical: 'MMMM', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.monthLong', id: 'monthLong', sampleKey: 'preferences.customDateFormatTokenSample.monthLong' },
  { canonical: 'MMM', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.monthShort', id: 'monthShort', sampleKey: 'preferences.customDateFormatTokenSample.monthShort' },
  { canonical: 'MM', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.monthNumber', id: 'monthNumber', sampleKey: 'preferences.customDateFormatTokenSample.monthNumber' },
  { canonical: 'DD', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.dayNumber', id: 'dayNumber', sampleKey: 'preferences.customDateFormatTokenSample.dayNumber' },
  { canonical: 'dddd', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.weekdayLong', id: 'weekdayLong', sampleKey: 'preferences.customDateFormatTokenSample.weekdayLong' },
  { canonical: 'ddd', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.weekdayShort', id: 'weekdayShort', sampleKey: 'preferences.customDateFormatTokenSample.weekdayShort' },
  { canonical: 'HH', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.hour24Padded', id: 'hour24Padded', sampleKey: 'preferences.customDateFormatTokenSample.hour24Padded' },
  { canonical: 'H', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.hour24', id: 'hour24', sampleKey: 'preferences.customDateFormatTokenSample.hour24' },
  { canonical: 'hh', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.hour12Padded', id: 'hour12Padded', sampleKey: 'preferences.customDateFormatTokenSample.hour12Padded' },
  { canonical: 'h', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.hour12', id: 'hour12', sampleKey: 'preferences.customDateFormatTokenSample.hour12' },
  { canonical: 'mm', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.minutePadded', id: 'minutePadded', sampleKey: 'preferences.customDateFormatTokenSample.minutePadded' },
  { canonical: 'M', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.minute', id: 'minute', sampleKey: 'preferences.customDateFormatTokenSample.minute' },
  { canonical: 'ss', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.secondPadded', id: 'secondPadded', sampleKey: 'preferences.customDateFormatTokenSample.secondPadded' },
  { canonical: 'S', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.second', id: 'second', sampleKey: 'preferences.customDateFormatTokenSample.second' },
  { canonical: 'A', aliasByLocale: {}, descriptionKey: 'preferences.customDateFormatToken.meridiem', id: 'meridiem', sampleKey: 'preferences.customDateFormatTokenSample.meridiem' }
]

let currentFormattingDateFormat = DATE_FORMAT_AUTO

function resolveCustomDateFormatLocaleKey(locale = 'en') {
  const normalized = String(locale || 'en').toLowerCase()
  if (normalized.startsWith('pt')) return 'pt'
  if (normalized.startsWith('fr')) return 'fr'
  if (normalized.startsWith('es')) return 'es'
  if (normalized.startsWith('de')) return 'de'
  return 'en'
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function getLocalizedCustomDateFormatTokens(locale = 'en', translateFn = translate) {
  const localeKey = resolveCustomDateFormatLocaleKey(locale)
  return CUSTOM_DATE_FORMAT_TOKEN_DEFINITIONS.map((definition) => ({
    alias: definition.aliasByLocale[localeKey] || definition.canonical,
    canonical: definition.canonical,
    description: translateFn(locale, definition.descriptionKey),
    id: definition.id,
    sample: translateFn(locale, definition.sampleKey)
  }))
}

function buildLocalizedCustomTokenPattern(locale = 'en') {
  const tokens = getLocalizedCustomDateFormatTokens(locale)
  const variants = Array.from(new Set(tokens.flatMap((token) => [token.alias, token.canonical])))
    .sort((left, right) => right.length - left.length)
    .map(escapeRegExp)
  return new RegExp(`(${variants.join('|')}|[^A-Za-z]+)`, 'g')
}

export function normalizeLocalizedCustomDateFormat(value, locale = 'en') {
  if (value == null || String(value).trim() === '') {
    return ''
  }
  const normalized = String(value).trim()
  const tokenLookup = new Map(
    getLocalizedCustomDateFormatTokens(locale).flatMap((token) => [
      [token.alias, token.canonical],
      [token.canonical, token.canonical]
    ])
  )
  const matches = normalized.match(buildLocalizedCustomTokenPattern(locale))
  if (!matches || matches.join('') !== normalized) {
    return ''
  }
  let hasToken = false
  const rebuilt = matches.map((part) => {
    const canonical = tokenLookup.get(part)
    if (canonical) {
      hasToken = true
      return canonical
    }
    return part
  }).join('')
  if (!hasToken) {
    return ''
  }
  return normalizeCustomDateFormat(rebuilt)
}

export function validateLocalizedCustomDateFormat(value, locale = 'en') {
  if (value == null || String(value).trim() === '') {
    return { valid: false, reason: 'empty' }
  }
  if (String(value).trim().length > 64) {
    return { valid: false, reason: 'tooLong' }
  }
  const normalized = normalizeLocalizedCustomDateFormat(value, locale)
  if (!normalized) {
    return { valid: false, reason: 'invalidToken' }
  }
  return { valid: true, value: normalized }
}

export function localizeCustomDateFormatPattern(value, locale = 'en') {
  const normalized = normalizeCustomDateFormat(value)
  if (!normalized) {
    return ''
  }
  const tokenLookup = new Map(getLocalizedCustomDateFormatTokens(locale).map((token) => [token.canonical, token.alias]))
  return normalized.replace(/YYYY|YY|MMMM|MMM|MM|DD|dddd|ddd|HH|H|hh|h|mm|M|ss|S|A/g, (token) => tokenLookup.get(token) || token)
}

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

export function normalizeDateFormatPreference(value) {
  if (DATE_FORMAT_OPTIONS.includes(value)) {
    return value
  }
  return normalizeCustomDateFormat(value) || DATE_FORMAT_AUTO
}

export function setCurrentFormattingDateFormat(value) {
  currentFormattingDateFormat = normalizeDateFormatPreference(value)
}

export function resetCurrentFormattingDateFormat() {
  currentFormattingDateFormat = DATE_FORMAT_AUTO
}

export function getCurrentFormattingDateFormat() {
  return normalizeDateFormatPreference(currentFormattingDateFormat)
}

export function validateCustomDateFormat(value) {
  if (value == null || String(value).trim() === '') {
    return { valid: false, reason: 'empty' }
  }
  const normalized = String(value).trim()
  if (normalized.length > 64) {
    return { valid: false, reason: 'tooLong' }
  }
  const matches = normalized.match(CUSTOM_DATE_FORMAT_TOKEN_PATTERN)
  if (!matches || matches.join('') !== normalized) {
    return { valid: false, reason: 'invalidToken' }
  }
  if (!matches.some((part) => /^(YYYY|YY|MMMM|MMM|MM|DD|dddd|ddd|HH|H|hh|h|mm|M|ss|S|A)$/.test(part))) {
    return { valid: false, reason: 'missingToken' }
  }
  return { valid: true, value: normalized }
}

export function normalizeCustomDateFormat(value) {
  const validation = validateCustomDateFormat(value)
  return validation.valid ? validation.value : ''
}

function padPart(value) {
  return String(value).padStart(2, '0')
}

function readDateTimeParts(date, timeZone, hour12) {
  const formatter = new Intl.DateTimeFormat('en-US-u-nu-latn', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12,
    ...(timeZone ? { timeZone } : {})
  })
  const parts = formatter.formatToParts(date)
  const lookup = Object.fromEntries(parts.filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]))
  const year = lookup.year || ''
  const month = lookup.month || ''
  const day = lookup.day || ''
  const minute = lookup.minute || ''
  const second = lookup.second || ''
  const dayPeriod = (lookup.dayPeriod || '').toUpperCase()
  const rawHour = Number(lookup.hour || 0)
  const monthNumber = Number(month || 0)
  const dayNumber = Number(day || 0)
  const minuteNumber = Number(minute || 0)
  const secondNumber = Number(second || 0)
  const hour24 = hour12
    ? padPart(rawHour === 12 ? 12 : (dayPeriod === 'PM' ? rawHour + 12 : rawHour))
    : padPart(rawHour)
  const hour12Value = hour12
    ? padPart(rawHour === 0 ? 12 : rawHour)
    : padPart(((rawHour + 11) % 12) + 1)
  return {
    year,
    month,
    monthNumber,
    day,
    dayNumber,
    hour24,
    hour24Number: Number(hour24 || 0),
    hour12: hour12Value,
    hour12Number: Number(hour12Value || 0),
    minute,
    minuteNumber,
    second,
    secondNumber,
    dayPeriod
  }
}

function readCalendarNameParts(date, locale, timeZone) {
  const formatter = new Intl.DateTimeFormat(locale, {
    weekday: 'long',
    month: 'long',
    timeZone: timeZone || undefined
  })
  const parts = formatter.formatToParts(date)
  const lookup = Object.fromEntries(parts.filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]))
  const fullMonth = lookup.month || ''
  const fullWeekday = lookup.weekday || ''
  const shortMonth = fullMonth ? new Intl.DateTimeFormat(locale, { month: 'short', timeZone: timeZone || undefined }).format(date) : ''
  const shortWeekday = fullWeekday ? new Intl.DateTimeFormat(locale, { weekday: 'short', timeZone: timeZone || undefined }).format(date) : ''
  return { fullMonth, shortMonth, fullWeekday, shortWeekday }
}

function formatDateByPreference(date, dateFormat, locale, timeZone) {
  switch (normalizeDateFormatPreference(dateFormat)) {
    case DATE_FORMAT_YMD_24: {
      const { year, month, day, hour24, minute, second } = readDateTimeParts(date, timeZone, false)
      return `${year}-${month}-${day} ${hour24}:${minute}:${second}`
    }
    case DATE_FORMAT_YMD_12: {
      const { year, month, day, hour12, minute, second, dayPeriod } = readDateTimeParts(date, timeZone, true)
      return `${year}-${month}-${day} ${hour12}:${minute}:${second} ${dayPeriod}`
    }
    case DATE_FORMAT_DMY_24: {
      const { year, month, day, hour24, minute, second } = readDateTimeParts(date, timeZone, false)
      return `${day}/${month}/${year} ${hour24}:${minute}:${second}`
    }
    case DATE_FORMAT_DMY_12: {
      const { year, month, day, hour12, minute, second, dayPeriod } = readDateTimeParts(date, timeZone, true)
      return `${day}/${month}/${year} ${hour12}:${minute}:${second} ${dayPeriod}`
    }
    case DATE_FORMAT_MDY_24: {
      const { year, month, day, hour24, minute, second } = readDateTimeParts(date, timeZone, false)
      return `${month}/${day}/${year} ${hour24}:${minute}:${second}`
    }
    case DATE_FORMAT_MDY_12: {
      const { year, month, day, hour12, minute, second, dayPeriod } = readDateTimeParts(date, timeZone, true)
      return `${month}/${day}/${year} ${hour12}:${minute}:${second} ${dayPeriod}`
    }
    default:
      return date.toLocaleString(locale, timeZone ? { timeZone } : undefined)
  }
}

export function describeAutomaticDateFormat(locale = 'en') {
  try {
    const formatter = new Intl.DateTimeFormat(locale, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      timeZone: 'UTC'
    })
    const parts = formatter.formatToParts(AUTOMATIC_DATE_FORMAT_REFERENCE)
    const usesDayPeriod = parts.some((part) => part.type === 'dayPeriod')
    return parts
      .map((part) => {
        switch (part.type) {
          case 'year':
            return 'YYYY'
          case 'month':
            return 'MM'
          case 'day':
            return 'DD'
          case 'hour':
            return usesDayPeriod ? 'hh' : 'HH'
          case 'minute':
            return 'mm'
          case 'second':
            return 'ss'
          case 'dayPeriod':
            return 'A'
          case 'literal':
            return part.value
          default:
            return ''
        }
      })
      .join('')
      .trim()
  } catch {
    return 'YYYY-MM-DD HH:mm:ss'
  }
}

export function dateFormatPreferenceToPattern(dateFormat, locale = 'en') {
  switch (normalizeDateFormatPreference(dateFormat)) {
    case DATE_FORMAT_DMY_24:
      return 'DD/MM/YYYY HH:mm:ss'
    case DATE_FORMAT_DMY_12:
      return 'DD/MM/YYYY hh:mm:ss A'
    case DATE_FORMAT_MDY_24:
      return 'MM/DD/YYYY HH:mm:ss'
    case DATE_FORMAT_MDY_12:
      return 'MM/DD/YYYY hh:mm:ss A'
    case DATE_FORMAT_YMD_24:
      return 'YYYY-MM-DD HH:mm:ss'
    case DATE_FORMAT_YMD_12:
      return 'YYYY-MM-DD hh:mm:ss A'
    case DATE_FORMAT_AUTO:
      return describeAutomaticDateFormat(locale)
    default:
      return normalizeCustomDateFormat(dateFormat) || 'YYYY-MM-DD HH:mm:ss'
  }
}

function formatDateByCustomPattern(date, customPattern, locale, timeZone) {
  const pattern = normalizeCustomDateFormat(customPattern)
  if (!pattern) {
    return ''
  }
  const twentyFourHourParts = readDateTimeParts(date, timeZone, false)
  const twelveHourParts = readDateTimeParts(date, timeZone, true)
  const calendarNameParts = readCalendarNameParts(date, locale, timeZone)
  return pattern.replace(/YYYY|YY|MMMM|MMM|MM|DD|dddd|ddd|HH|H|hh|h|mm|M|ss|S|A/g, (token) => {
    switch (token) {
      case 'YYYY':
        return twentyFourHourParts.year
      case 'YY':
        return twentyFourHourParts.year.slice(-2)
      case 'MMMM':
        return calendarNameParts.fullMonth
      case 'MMM':
        return calendarNameParts.shortMonth
      case 'MM':
        return twentyFourHourParts.month
      case 'DD':
        return twentyFourHourParts.day
      case 'dddd':
        return calendarNameParts.fullWeekday
      case 'ddd':
        return calendarNameParts.shortWeekday
      case 'HH':
        return twentyFourHourParts.hour24
      case 'H':
        return String(twentyFourHourParts.hour24Number)
      case 'hh':
        return twelveHourParts.hour12
      case 'h':
        return String(twelveHourParts.hour12Number)
      case 'mm':
        return twentyFourHourParts.minute
      case 'M':
        return String(twentyFourHourParts.minuteNumber)
      case 'ss':
        return twentyFourHourParts.second
      case 'S':
        return String(twentyFourHourParts.secondNumber)
      case 'A':
        return twelveHourParts.dayPeriod
      default:
        return token
    }
  })
}

export function isCustomDateFormatPreference(value) {
  const normalized = normalizeDateFormatPreference(value)
  return normalized !== DATE_FORMAT_AUTO
    && normalized !== DATE_FORMAT_YMD_24
    && normalized !== DATE_FORMAT_YMD_12
    && normalized !== DATE_FORMAT_DMY_24
    && normalized !== DATE_FORMAT_DMY_12
    && normalized !== DATE_FORMAT_MDY_24
    && normalized !== DATE_FORMAT_MDY_12
}

export function formatDate(value, locale = 'en', timeZone = getCurrentFormattingTimeZone(), dateFormat = getCurrentFormattingDateFormat()) {
  if (!value) return translate(locale, 'common.never')
  const date = normalizeDateValue(value)
  if (!date) return translate(locale, 'common.unavailable')
  const normalizedDateFormat = normalizeDateFormatPreference(dateFormat)
  const fallbackDateFormat = isCustomDateFormatPreference(normalizedDateFormat) ? DATE_FORMAT_AUTO : normalizedDateFormat
  try {
    if (isCustomDateFormatPreference(normalizedDateFormat)) {
      const customFormatted = formatDateByCustomPattern(date, normalizedDateFormat, locale, timeZone)
      if (customFormatted) {
        return customFormatted
      }
    }
    return formatDateByPreference(date, fallbackDateFormat, locale, timeZone)
  } catch {
    try {
      if (isCustomDateFormatPreference(normalizedDateFormat)) {
        const customFormatted = formatDateByCustomPattern(date, normalizedDateFormat, locale, null)
        if (customFormatted) {
          return customFormatted
        }
      }
      return formatDateByPreference(date, fallbackDateFormat, locale, null)
    } catch {
      return date.toLocaleString(locale)
    }
  }
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

export function formatPollError(message, locale = 'en', timeZone = getCurrentFormattingTimeZone()) {
  if (message && typeof message === 'object') {
    const code = String(message.code || '').trim()
    const emailAccountId = message.sourceId || ''
    switch (code) {
      case 'source_cooling_down':
        return translate(locale, 'common.cooldownError', {
          emailAccountId,
          value: formatDate(message.value, locale, timeZone)
        })
      case 'source_waiting_next_window':
        return translate(locale, 'common.nextPollWindowError', {
          emailAccountId,
          value: formatDate(message.value, locale, timeZone)
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
          value: formatDate(message.value, locale, timeZone)
        })
      default:
        return formatPollError(message.message, locale, timeZone)
    }
  }

  const text = String(message || '').trim()
  if (!text) return text
  const cooldownMatch = text.match(/^Source (.+?) is cooling down until (.+)\.$/)
  if (cooldownMatch) {
    const [, emailAccountId, until] = cooldownMatch
    return translate(locale, 'common.cooldownError', {
      emailAccountId,
      value: formatDate(until, locale, timeZone)
    })
  }

  const nextWindowMatch = text.match(/^Source (.+?) is waiting for its next poll window at (.+)\.$/)
  if (nextWindowMatch) {
    const [, emailAccountId, at] = nextWindowMatch
    return translate(locale, 'common.nextPollWindowError', {
      emailAccountId,
      value: formatDate(at, locale, timeZone)
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
      value: formatDate(manualRateLimitMatch[1], locale, timeZone)
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
