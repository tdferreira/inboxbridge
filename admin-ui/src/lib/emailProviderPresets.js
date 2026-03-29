export const EMAIL_PROVIDER_PRESETS = [
  {
    id: 'custom',
    label: 'Custom',
    description: 'Start from a blank fetcher and fill in the mail server settings manually.'
  },
  {
    id: 'outlook',
    label: 'Outlook / Hotmail / Live',
    description: 'Prefills the Outlook IMAP server and Microsoft OAuth defaults supported by InboxBridge.',
    values: {
      protocol: 'IMAP',
      host: 'outlook.office365.com',
      port: 993,
      tls: true,
      authMethod: 'OAUTH2',
      oauthProvider: 'MICROSOFT',
      folder: 'INBOX'
    }
  },
  {
    id: 'gmail',
    label: 'Gmail',
    description: 'Prefills Gmail IMAP settings. When the shared Google OAuth app is configured, you can use OAuth2 for this source account.',
    values: {
      protocol: 'IMAP',
      host: 'imap.gmail.com',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      folder: 'INBOX'
    }
  },
  {
    id: 'yahoo',
    label: 'Yahoo Mail',
    description: 'Prefills Yahoo IMAP settings for password or app-password based access.',
    values: {
      protocol: 'IMAP',
      host: 'imap.mail.yahoo.com',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      folder: 'INBOX'
    }
  },
  {
    id: 'proton-bridge',
    label: 'Proton Mail Bridge',
    description: 'Prefills the local Proton Mail Bridge IMAP endpoint. This assumes Proton Mail Bridge is running on the same machine.',
    values: {
      protocol: 'IMAP',
      host: '127.0.0.1',
      port: 1143,
      tls: false,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      folder: 'INBOX'
    }
  }
]

export const DESTINATION_PROVIDER_PRESETS = [
  {
    id: 'GMAIL_API',
    label: 'Gmail',
    description: 'Imports through the Gmail API into the mailbox that completes Google OAuth.'
  },
  {
    id: 'OUTLOOK_IMAP',
    label: 'Outlook / Hotmail / Live',
    description: 'Uses IMAP APPEND against Outlook with Microsoft OAuth2 by default.',
    values: {
      provider: 'OUTLOOK_IMAP',
      host: 'outlook.office365.com',
      port: 993,
      tls: true,
      authMethod: 'OAUTH2',
      oauthProvider: 'MICROSOFT',
      username: '',
      password: '',
      folder: 'INBOX'
    }
  },
  {
    id: 'YAHOO_IMAP',
    label: 'Yahoo Mail',
    description: 'Uses IMAP APPEND with password or app-password authentication.',
    values: {
      provider: 'YAHOO_IMAP',
      host: 'imap.mail.yahoo.com',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: '',
      password: '',
      folder: 'INBOX'
    }
  },
  {
    id: 'PROTON_BRIDGE_IMAP',
    label: 'Proton Mail Bridge',
    description: 'Uses the local Proton Mail Bridge IMAP endpoint for APPEND delivery.',
    values: {
      provider: 'PROTON_BRIDGE_IMAP',
      host: '127.0.0.1',
      port: 1143,
      tls: false,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: '',
      password: '',
      folder: 'INBOX'
    }
  },
  {
    id: 'CUSTOM_IMAP',
    label: 'Generic IMAP',
    description: 'Bring your own IMAP APPEND target and fill in the server settings manually.',
    values: {
      provider: 'CUSTOM_IMAP',
      host: '',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: '',
      password: '',
      folder: 'INBOX'
    }
  }
]

const OUTLOOK_SOURCE_HOSTS = new Set([
  'outlook.office365.com',
  'imap-mail.outlook.com',
  'pop-mail.outlook.com'
])

export function findEmailProviderPreset(presetId) {
  return EMAIL_PROVIDER_PRESETS.find((preset) => preset.id === presetId) || EMAIL_PROVIDER_PRESETS[0]
}

export function findDestinationProviderPreset(presetId) {
  return DESTINATION_PROVIDER_PRESETS.find((preset) => preset.id === presetId) || DESTINATION_PROVIDER_PRESETS[0]
}

export function isOutlookSourceConfig(config = {}) {
  return OUTLOOK_SOURCE_HOSTS.has(String(config.host || '').trim().toLowerCase())
}

export function inferEmailProviderPresetId(config = {}) {
  if (isOutlookSourceConfig(config)) {
    return 'outlook'
  }

  const protocol = config.protocol || 'IMAP'
  const host = String(config.host || '').trim().toLowerCase()
  const port = Number(config.port || 0)

  const matchedPreset = EMAIL_PROVIDER_PRESETS.find((preset) => (
    preset.values
      && String(preset.values.host || '').trim().toLowerCase() === host
      && (preset.values.protocol || 'IMAP') === protocol
      && Number(preset.values.port || 0) === port
  ))

  return matchedPreset?.id || 'custom'
}

export function normalizeDestinationProviderConfig(config = {}) {
  const provider = config.provider || 'GMAIL_API'
  if (provider === 'OUTLOOK_IMAP') {
    const preset = findDestinationProviderPreset(provider)
    return {
      ...config,
      provider,
      host: config.host || preset.values.host,
      port: config.port === '' || config.port == null ? preset.values.port : config.port,
      tls: config.tls ?? preset.values.tls,
      authMethod: 'OAUTH2',
      oauthProvider: 'MICROSOFT',
      password: '',
      folder: config.folder || preset.values.folder
    }
  }

  if (provider === 'GMAIL_API') {
    return {
      ...config,
      provider,
      authMethod: 'OAUTH2',
      oauthProvider: 'GOOGLE',
      password: ''
    }
  }

  return {
    ...config,
    provider
  }
}
