import { authMethodLabel, effectiveEmailAccountStatus, formatDate, formatDurationHint, formatDurationMeaning, formatPollError, formatPollExecutionSummary, isOauthRevokedError, oauthProviderLabel, protocolLabel, roleLabel, statusLabel, statusTone, tokenStorageLabel, triggerLabel } from './formatters'

describe('formatters', () => {
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
