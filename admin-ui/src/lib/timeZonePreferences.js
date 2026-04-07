export const TIMEZONE_MODE_AUTO = 'AUTO'
export const TIMEZONE_MODE_MANUAL = 'MANUAL'

let currentFormattingTimeZone = null

/**
 * Central timezone normalization and storage helpers used by both app shells
 * and by formatter utilities that need a stable current formatting zone.
 */
function normalizeTimeZoneText(value) {
  if (value == null) {
    return ''
  }
  return String(value).trim()
}

export function detectBrowserTimeZone() {
  try {
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone
    return normalizeTimeZoneText(timeZone) || 'UTC'
  } catch {
    return 'UTC'
  }
}

export function isValidTimeZone(value) {
  const timeZone = normalizeTimeZoneText(value)
  if (!timeZone) {
    return false
  }
  try {
    Intl.DateTimeFormat(undefined, { timeZone }).format(new Date())
    return true
  } catch {
    return false
  }
}

export function normalizeTimeZoneMode(value) {
  return value === TIMEZONE_MODE_MANUAL ? TIMEZONE_MODE_MANUAL : TIMEZONE_MODE_AUTO
}

export function normalizeManualTimeZone(value) {
  const timeZone = normalizeTimeZoneText(value)
  return isValidTimeZone(timeZone) ? timeZone : ''
}

export function resolveEffectiveTimeZone(timezoneMode, timezone) {
  if (normalizeTimeZoneMode(timezoneMode) === TIMEZONE_MODE_MANUAL) {
    return normalizeManualTimeZone(timezone) || detectBrowserTimeZone()
  }
  return detectBrowserTimeZone()
}

export function listSupportedTimeZones() {
  try {
    if (typeof Intl.supportedValuesOf === 'function') {
      const values = Intl.supportedValuesOf('timeZone').filter(Boolean)
      if (values.length) {
        return values
      }
    }
  } catch {
    // Fall through to the manual fallback list.
  }
  return Array.from(new Set(['UTC', detectBrowserTimeZone()])).filter(Boolean)
}

export function setCurrentFormattingTimeZone(timeZone) {
  currentFormattingTimeZone = normalizeManualTimeZone(timeZone) || null
}

export function resetCurrentFormattingTimeZone() {
  currentFormattingTimeZone = null
}

export function getCurrentFormattingTimeZone() {
  return currentFormattingTimeZone || detectBrowserTimeZone()
}

function storageKey(userId) {
  return `inboxbridge.timezonePreference.${userId}`
}

export function readStoredTimeZonePreference(userId) {
  if (!userId) {
    return null
  }
  try {
    const rawValue = window.localStorage.getItem(storageKey(userId))
    if (!rawValue) {
      return null
    }
    const parsed = JSON.parse(rawValue)
    return {
      timezoneMode: normalizeTimeZoneMode(parsed?.timezoneMode),
      timezone: normalizeManualTimeZone(parsed?.timezone)
    }
  } catch {
    return null
  }
}

export function writeStoredTimeZonePreference(userId, preference) {
  if (!userId) {
    return
  }
  try {
    window.localStorage.setItem(storageKey(userId), JSON.stringify({
      timezoneMode: normalizeTimeZoneMode(preference?.timezoneMode),
      timezone: normalizeManualTimeZone(preference?.timezone)
    }))
  } catch {
    // Ignore storage write failures.
  }
}
