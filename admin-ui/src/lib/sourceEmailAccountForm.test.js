import { applyEmailAccountPreset, DEFAULT_EMAIL_ACCOUNT_FORM, normalizeEmailAccountForm } from '@/lib/sourceEmailAccountForm'

describe('sourceEmailAccountForm', () => {
  it('clears OAuth fields when password auth is selected', () => {
    const normalized = normalizeEmailAccountForm({
      ...DEFAULT_EMAIL_ACCOUNT_FORM,
      authMethod: 'PASSWORD',
      oauthProvider: 'GOOGLE',
      oauthRefreshToken: 'refresh-token'
    }, {
      sourceOAuthProviders: ['GOOGLE']
    })

    expect(normalized.oauthProvider).toBe('NONE')
    expect(normalized.oauthRefreshToken).toBe('')
  })

  it('defaults OAuth2 to the first available provider and clears password secrets', () => {
    const normalized = normalizeEmailAccountForm({
      ...DEFAULT_EMAIL_ACCOUNT_FORM,
      authMethod: 'OAUTH2',
      oauthProvider: 'NONE',
      password: 'secret-password'
    }, {
      sourceOAuthProviders: ['MICROSOFT', 'GOOGLE']
    })

    expect(normalized.authMethod).toBe('OAUTH2')
    expect(normalized.oauthProvider).toBe('MICROSOFT')
    expect(normalized.password).toBe('')
  })

  it('falls back to password auth when OAuth2 is unavailable', () => {
    const normalized = normalizeEmailAccountForm({
      ...DEFAULT_EMAIL_ACCOUNT_FORM,
      authMethod: 'OAUTH2',
      oauthProvider: 'GOOGLE',
      oauthRefreshToken: 'refresh-token'
    }, {
      sourceOAuthProviders: []
    })

    expect(normalized.authMethod).toBe('PASSWORD')
    expect(normalized.oauthProvider).toBe('NONE')
    expect(normalized.oauthRefreshToken).toBe('')
  })

  it('promotes the Gmail preset to Google OAuth when that provider is available', () => {
    const preset = applyEmailAccountPreset(DEFAULT_EMAIL_ACCOUNT_FORM, 'gmail', {
      sourceOAuthProviders: ['GOOGLE']
    })

    expect(preset.host).toBe('imap.gmail.com')
    expect(preset.authMethod).toBe('OAUTH2')
    expect(preset.oauthProvider).toBe('GOOGLE')
  })

  it('forces Outlook hosts to stay on Microsoft OAuth', () => {
    const normalized = normalizeEmailAccountForm({
      ...DEFAULT_EMAIL_ACCOUNT_FORM,
      host: 'outlook.office365.com',
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      password: 'secret-password'
    }, {
      sourceOAuthProviders: []
    })

    expect(normalized.authMethod).toBe('OAUTH2')
    expect(normalized.oauthProvider).toBe('MICROSOFT')
    expect(normalized.password).toBe('')
  })
})
