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
    description: 'Prefills Gmail IMAP settings. InboxBridge source-side Gmail OAuth is not available yet, so this preset keeps password auth.',
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

export function findEmailProviderPreset(presetId) {
  return EMAIL_PROVIDER_PRESETS.find((preset) => preset.id === presetId) || EMAIL_PROVIDER_PRESETS[0]
}
