import {
  DESTINATION_PROVIDER_PRESETS,
  EMAIL_PROVIDER_PRESETS,
  findDestinationProviderPreset,
  findEmailProviderPreset,
  inferEmailProviderPresetId,
  normalizeDestinationProviderConfig
} from './emailProviderPresets'

describe('emailProviderPresets', () => {
  it('returns the matching source email account preset', () => {
    expect(findEmailProviderPreset('gmail')).toEqual(expect.objectContaining({
      id: 'gmail',
      values: expect.objectContaining({
        host: 'imap.gmail.com',
        oauthProvider: 'NONE'
      })
    }))
  })

  it('falls back to the custom source preset for unknown ids', () => {
    expect(findEmailProviderPreset('missing-preset')).toBe(EMAIL_PROVIDER_PRESETS[0])
  })

  it('returns the matching destination preset and keeps Gmail API value-less', () => {
    const preset = findDestinationProviderPreset('GMAIL_API')

    expect(preset).toEqual(expect.objectContaining({
      id: 'GMAIL_API',
      labelKey: 'bridge.providerGmail',
      descriptionKey: 'destinationPreset.gmail.description'
    }))
    expect(preset).not.toHaveProperty('values')
  })

  it('falls back to the first destination preset for unknown ids', () => {
    expect(findDestinationProviderPreset('missing-target')).toBe(DESTINATION_PROVIDER_PRESETS[0])
  })

  it('infers the outlook preset from a stored outlook host', () => {
    expect(inferEmailProviderPresetId({
      protocol: 'IMAP',
      host: 'outlook.office365.com',
      port: 993
    })).toBe('outlook')
  })

  it('forces the outlook destination provider into Microsoft OAuth mode', () => {
    expect(normalizeDestinationProviderConfig({
      provider: 'OUTLOOK_IMAP',
      host: 'outlook.office365.com',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: 'owner@example.com',
      password: 'secret',
      folder: 'Inbox/Subfolder'
    })).toEqual(expect.objectContaining({
      provider: 'OUTLOOK_IMAP',
      authMethod: 'OAUTH2',
      oauthProvider: 'MICROSOFT',
      password: '',
      folder: 'Inbox/Subfolder'
    }))
  })
})
