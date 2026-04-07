import {
  TIMEZONE_MODE_AUTO,
  TIMEZONE_MODE_MANUAL,
  detectBrowserTimeZone,
  getCurrentFormattingTimeZone,
  listSupportedTimeZones,
  normalizeManualTimeZone,
  normalizeTimeZoneMode,
  readStoredTimeZonePreference,
  resetCurrentFormattingTimeZone,
  resolveEffectiveTimeZone,
  setCurrentFormattingTimeZone,
  writeStoredTimeZonePreference
} from './timeZonePreferences'

describe('timeZonePreferences', () => {
  beforeEach(() => {
    window.localStorage.clear()
    resetCurrentFormattingTimeZone()
  })

  it('normalizes timezone mode and manual timezone values', () => {
    expect(normalizeTimeZoneMode('OTHER')).toBe(TIMEZONE_MODE_AUTO)
    expect(normalizeTimeZoneMode(TIMEZONE_MODE_MANUAL)).toBe(TIMEZONE_MODE_MANUAL)
    expect(normalizeManualTimeZone('Europe/Lisbon')).toBe('Europe/Lisbon')
    expect(normalizeManualTimeZone('Invalid/Zone')).toBe('')
  })

  it('resolves the effective timezone and keeps the formatting override', () => {
    const browserTimeZone = detectBrowserTimeZone()

    expect(browserTimeZone).toBeTruthy()
    expect(resolveEffectiveTimeZone(TIMEZONE_MODE_MANUAL, 'America/New_York')).toBe('America/New_York')
    expect(resolveEffectiveTimeZone(TIMEZONE_MODE_MANUAL, 'Invalid/Zone')).toBe(browserTimeZone)

    setCurrentFormattingTimeZone('America/New_York')
    expect(getCurrentFormattingTimeZone()).toBe('America/New_York')

    resetCurrentFormattingTimeZone()
    expect(getCurrentFormattingTimeZone()).toBe(browserTimeZone)
  })

  it('reads and writes persisted preferences safely', () => {
    writeStoredTimeZonePreference('user-1', {
      timezoneMode: TIMEZONE_MODE_MANUAL,
      timezone: 'Europe/Lisbon'
    })

    expect(readStoredTimeZonePreference('user-1')).toEqual({
      timezoneMode: TIMEZONE_MODE_MANUAL,
      timezone: 'Europe/Lisbon'
    })
  })

  it('falls back to a small safe list when supportedValuesOf is unavailable', () => {
    const supportedValuesOf = Intl.supportedValuesOf
    const browserTimeZoneSpy = vi.spyOn(Intl, 'DateTimeFormat').mockImplementation(() => ({
      resolvedOptions: () => ({ timeZone: 'Europe/Lisbon' })
    }))
    Intl.supportedValuesOf = undefined

    expect(listSupportedTimeZones()).toEqual(['UTC', 'Europe/Lisbon'])

    Intl.supportedValuesOf = supportedValuesOf
    browserTimeZoneSpy.mockRestore()
  })
})
