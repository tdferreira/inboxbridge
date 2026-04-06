import { authMethodLabel, DATE_FORMAT_DMY_12, DATE_FORMAT_DMY_24, DATE_FORMAT_MDY_12, DATE_FORMAT_MDY_24, DATE_FORMAT_YMD_12, DATE_FORMAT_YMD_24, dateFormatPreferenceToPattern, describeAutomaticDateFormat, effectiveEmailAccountStatus, formatBytes, formatDate, formatDurationHint, formatDurationMeaning, formatImportedSizeSummary, formatPollError, formatPollExecutionSummary, formatRemoteImportedSizeSummary, getLocalizedCustomDateFormatTokens, isCustomDateFormatPreference, isOauthRevokedError, localizeCustomDateFormatPattern, normalizeCustomDateFormat, normalizeLocalizedCustomDateFormat, oauthProviderLabel, protocolLabel, resetCurrentFormattingDateFormat, roleLabel, setCurrentFormattingDateFormat, statusLabel, statusTone, tokenStorageLabel, triggerLabel, validateCustomDateFormat, validateLocalizedCustomDateFormat } from './formatters'

describe('formatters', () => {
  afterEach(() => {
    resetCurrentFormattingDateFormat()
  })

  it('formats missing dates as Never', () => {
    expect(formatDate('')).toBe('Never')
  })

  it('maps statuses to tone classes', () => {
    expect(statusTone('DISABLED')).toBe('tone-neutral')
    expect(statusTone('SUCCESS')).toBe('tone-success')
    expect(statusTone('ERROR')).toBe('tone-error')
    expect(statusTone('PENDING')).toBe('tone-neutral')
  })

  it('renders translated labels for common enums', () => {
    expect(statusLabel('DISABLED')).toBe('Disabled')
    expect(statusLabel('SUCCESS')).toBe('Success')
    expect(roleLabel('ADMIN')).toBe('Admin')
    expect(protocolLabel('IMAP')).toBe('IMAP')
    expect(authMethodLabel('PASSWORD')).toBe('Password')
    expect(authMethodLabel('PASSWORD_PLUS_PASSKEY')).toBe('Password + passkey')
    expect(oauthProviderLabel('MICROSOFT')).toBe('Microsoft')
    expect(triggerLabel('scheduler')).toBe('scheduler')
  })

  it('prefers the disabled status for disabled email accounts', () => {
    expect(effectiveEmailAccountStatus({
      enabled: false,
      lastEvent: { status: 'ERROR' }
    })).toBe('DISABLED')
    expect(effectiveEmailAccountStatus({
      enabled: true,
      lastEvent: { status: 'SUCCESS' }
    })).toBe('SUCCESS')
  })

  it('turns ISO-8601 durations into human-readable meanings', () => {
    expect(formatDurationMeaning('PT0.25S', 'en')).toBe('250 milliseconds')
    expect(formatDurationMeaning('PT1H30M', 'en')).toBe('1 hour and 30 minutes')
    expect(formatDurationHint('PT5M', 'en')).toBe('PT5M = 5 minutes')
  })

  it('formats byte counts into human-readable sizes', () => {
    expect(formatBytes(999, 'en')).toBe('999 B')
    expect(formatBytes(1536 * 1024, 'en')).toBe('1.5 MB')
  })

  it('formats imported size summaries for bridge and remote views', () => {
    expect(formatImportedSizeSummary({ importedBytes: 2048 }, 'en')).toBe('Imported size: 2 KB')
    expect(formatRemoteImportedSizeSummary(3 * 1024 * 1024, 'pt-PT')).toBe('Tamanho importado: 3 MB')
  })

  it('renders token storage labels for known modes', () => {
    expect(tokenStorageLabel('DATABASE')).toBe('Encrypted storage')
    expect(tokenStorageLabel('PASSWORD')).toBe('Password auth')
    expect(tokenStorageLabel('SOMETHING_ELSE')).toBe('SOMETHING_ELSE')
  })

  it('formats cooldown poll errors with a localized date', () => {
    expect(formatPollError('Source outlook-main is cooling down until 2026-03-28T06:22:31.605711Z.', 'en'))
      .toContain('Source outlook-main is cooling down until')
    expect(formatPollError('Source outlook-main is cooling down until 2026-03-28T06:22:31.605711Z.', 'en'))
      .not.toContain('2026-03-28T06:22:31.605711Z')
  })

  it('formats microsecond timestamps without showing Invalid Date', () => {
    expect(formatDate('2026-03-28T06:22:31.605711Z', 'en')).not.toBe('Unavailable')
  })

  it('formats dates in the requested timezone', () => {
    expect(formatDate('2026-04-04T09:15:00Z', 'en', 'Europe/Lisbon')).toContain('10:')
    expect(formatDate('2026-04-04T09:15:00Z', 'en', 'UTC')).toContain('9:')
  })

  it('uses locale-specific automatic date patterns while timezone only changes the rendered local time', () => {
    expect(formatDate('2026-04-04T09:15:00Z', 'en-US', 'UTC')).toContain('9:15:00 AM')
    expect(formatDate('2026-04-04T09:15:00Z', 'en-GB', 'UTC')).toContain('09:15:00')
    expect(formatDate('2026-04-04T09:15:00Z', 'en-US', 'Europe/Lisbon')).toContain('10:15:00 AM')
  })

  it('describes the automatic date pattern from the selected locale', () => {
    expect(describeAutomaticDateFormat('en-US')).toContain('MM')
    expect(describeAutomaticDateFormat('en-US')).toContain('hh')
    expect(describeAutomaticDateFormat('en-US')).toContain('A')
    expect(describeAutomaticDateFormat('en-GB')).toContain('DD')
    expect(describeAutomaticDateFormat('en-GB')).toContain('HH')
  })

  it('derives editable custom-pattern drafts from presets and automatic mode', () => {
    expect(dateFormatPreferenceToPattern(DATE_FORMAT_DMY_12, 'en')).toBe('DD/MM/YYYY hh:mm:ss A')
    expect(dateFormatPreferenceToPattern(DATE_FORMAT_YMD_24, 'en')).toBe('YYYY-MM-DD HH:mm:ss')
    expect(dateFormatPreferenceToPattern('AUTO', 'en-US')).toBe(describeAutomaticDateFormat('en-US'))
    expect(dateFormatPreferenceToPattern('ddd, MMM DD YY h:M:S A', 'en')).toBe('ddd, MMM DD YY h:M:S A')
  })

  it('formats dates with a selected manual preset', () => {
    expect(formatDate('2026-04-04T09:15:00Z', 'en', 'UTC', DATE_FORMAT_DMY_24)).toBe('04/04/2026 09:15:00')
    expect(formatDate('2026-04-04T21:15:00Z', 'en', 'UTC', DATE_FORMAT_DMY_12)).toBe('04/04/2026 09:15:00 PM')
    expect(formatDate('2026-04-04T09:15:00Z', 'en', 'UTC', DATE_FORMAT_MDY_24)).toBe('04/04/2026 09:15:00')
    expect(formatDate('2026-04-04T21:15:00Z', 'en', 'UTC', DATE_FORMAT_MDY_12)).toBe('04/04/2026 09:15:00 PM')
    expect(formatDate('2026-04-04T09:15:00Z', 'en', 'UTC', DATE_FORMAT_YMD_24)).toBe('2026-04-04 09:15:00')
    expect(formatDate('2026-04-04T21:15:00Z', 'en', 'UTC', DATE_FORMAT_YMD_12)).toBe('2026-04-04 09:15:00 PM')
  })

  it('formats dates with a valid custom pattern', () => {
    expect(formatDate('2026-04-04T21:15:00Z', 'en', 'UTC', 'DD/MM/YYYY hh:mm:ss A')).toBe('04/04/2026 09:15:00 PM')
    expect(formatDate('2026-04-06T21:05:06Z', 'en', 'UTC', 'ddd, MMM DD YY h:M:S A')).toBe('Mon, Apr 06 26 9:5:6 PM')
    expect(formatDate('2026-04-06T09:05:06Z', 'en', 'UTC', 'dddd, MMMM DD YYYY H:M:S')).toBe('Monday, April 06 2026 9:5:6')
  })

  it('uses the active global date-format preference when no explicit format is passed', () => {
    setCurrentFormattingDateFormat(DATE_FORMAT_YMD_24)
    expect(formatDate('2026-04-04T09:15:00Z', 'en', 'UTC')).toBe('2026-04-04 09:15:00')
  })

  it('uses the active global custom date-format preference when the mode is custom', () => {
    setCurrentFormattingDateFormat('DD/MM/YYYY HH:mm:ss')
    expect(formatDate('2026-04-04T09:15:00Z', 'en', 'UTC')).toBe('04/04/2026 09:15:00')
  })

  it('validates and normalizes custom date-format patterns', () => {
    expect(validateCustomDateFormat('DD/MM/YYYY HH:mm:ss').valid).toBe(true)
    expect(validateCustomDateFormat('ddd, MMM DD YY h:M:S A').valid).toBe(true)
    expect(validateCustomDateFormat('weekday DD/MM/YYYY').valid).toBe(false)
    expect(normalizeCustomDateFormat(' DD/MM/YYYY HH:mm:ss ')).toBe('DD/MM/YYYY HH:mm:ss')
    expect(normalizeCustomDateFormat('weekday DD/MM/YYYY')).toBe('')
    expect(isCustomDateFormatPreference('DD/MM/YYYY HH:mm:ss')).toBe(true)
    expect(isCustomDateFormatPreference(DATE_FORMAT_YMD_24)).toBe(false)
  })

  it('localizes the editable token aliases without changing canonical storage', () => {
    expect(localizeCustomDateFormatPattern('DD/MM/YYYY HH:mm:ss', 'pt-PT')).toBe('DD/MM/AAAA HH:mm:ss')
    expect(normalizeLocalizedCustomDateFormat('DD/MM/AAAA HH:mm:ss', 'pt-PT')).toBe('DD/MM/YYYY HH:mm:ss')
    expect(validateLocalizedCustomDateFormat('DD/MM/AAAA HH:mm:ss', 'pt-PT')).toEqual({
      valid: true,
      value: 'DD/MM/YYYY HH:mm:ss'
    })
    expect(getLocalizedCustomDateFormatTokens('pt-PT').find((entry) => entry.canonical === 'YYYY')?.alias).toBe('AAAA')
  })

  it('renders cooldown errors in portuguese', () => {
    expect(formatPollError('Source outlook-main is cooling down until 2026-03-28T06:22:31.605711Z.', 'pt-PT'))
      .toContain('A fonte outlook-main está em pausa até')
  })

  it('renders structured polling errors through translations', () => {
    expect(formatPollError({
      code: 'gmail_access_revoked',
      sourceId: 'outlook-main',
      message: 'Source outlook-main failed: The linked Gmail account no longer grants InboxBridge access.'
    }, 'pt-PT')).toBe('A fonte outlook-main deixou de ter acesso ao Gmail. Volte a ligá-la em A minha caixa de destino.')
  })

  it('translates known backend gmail access errors from persisted text', () => {
    expect(formatPollError(
      'Source outlook-main failed: The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Gmail Account.',
      'pt-PT'
    )).toBe('A fonte outlook-main deixou de ter acesso ao Gmail. Volte a ligá-la em A minha caixa de destino.')
  })

  it('translates legacy google invalid_grant refresh-token errors from persisted text', () => {
    expect(formatPollError(
      'Source outlook-main failed: Google token request failed with status 400: { "error": "invalid_grant", "error_description": "Token has been expired or revoked." }',
      'pt-PT'
    )).toBe('A fonte outlook-main deixou de ter acesso ao Gmail. Volte a ligá-la em A minha caixa de destino.')
  })

  it('translates structured microsoft access revoked errors', () => {
    expect(formatPollError({
      code: 'microsoft_access_revoked',
      sourceId: 'outlook-main',
      message: 'Source outlook-main failed: The linked Microsoft account no longer grants InboxBridge access.'
    }, 'pt-PT')).toBe('A fonte outlook-main deixou de ter acesso ao Microsoft OAuth. Volte a ligá-la nesta conta de email.')
  })

  it('translates missing OAuth refresh-token errors from persisted text', () => {
    expect(formatPollError(
      'Source outlook-main failed: Source outlook-main is configured for OAuth2 but has no refresh token',
      'pt-PT'
    )).toBe('A fonte outlook-main está configurada para OAuth2 mas não tem refresh token.')
  })

  it('detects revoked oauth errors from strings and structured payloads', () => {
    expect(isOauthRevokedError('Source x failed: The linked Microsoft account no longer grants InboxBridge access.')).toBe(true)
    expect(isOauthRevokedError({ code: 'gmail_access_revoked' })).toBe(true)
    expect(isOauthRevokedError('Source x is cooling down until 2026-03-28T06:22:31.605711Z.')).toBe(false)
  })

  it('formats richer poll execution summaries from persisted event context', () => {
    expect(formatPollExecutionSummary({
      finishedAt: '2026-04-02T08:24:28Z',
      trigger: 'app-fetcher',
      actorUsername: 'alice',
      executionSurface: 'MY_INBOXBRIDGE'
    }, 'en', 'alice')).toContain('Executed at')
    expect(formatPollExecutionSummary({
      finishedAt: '2026-04-02T08:24:28Z',
      trigger: 'admin-fetcher',
      actorUsername: 'admin',
      executionSurface: 'ADMINISTRATION'
    }, 'en', 'alice')).toContain('by admin via Administration')
    expect(formatPollExecutionSummary({
      finishedAt: '2026-04-02T08:24:28Z',
      trigger: 'scheduler',
      executionSurface: 'AUTOMATIC'
    }, 'en', 'alice')).toContain('Executed automatically')
  })
})
