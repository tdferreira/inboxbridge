import { findEmailProviderPreset, isOutlookSourceConfig } from './emailProviderPresets'

export const DEFAULT_EMAIL_ACCOUNT_FORM = {
  originalEmailAccountId: '',
  emailAccountId: '',
  enabled: true,
  protocol: 'IMAP',
  host: '',
  port: 993,
  tls: true,
  authMethod: 'PASSWORD',
  oauthProvider: 'NONE',
  username: '',
  password: '',
  oauthRefreshToken: '',
  folder: 'INBOX',
  unreadOnly: false,
  fetchMode: 'POLLING',
  customLabel: '',
  markReadAfterPoll: false,
  postPollAction: 'NONE',
  postPollTargetFolder: ''
}

export function normalizeEmailAccountForm(nextEmailAccountForm, authOptions = {}) {
  const next = { ...nextEmailAccountForm }
  const availableSourceProviders = Array.isArray(authOptions.sourceOAuthProviders) ? authOptions.sourceOAuthProviders : []
  const requiresMicrosoftOAuth = isOutlookSourceConfig(next)

  if (requiresMicrosoftOAuth) {
    next.authMethod = 'OAUTH2'
    next.oauthProvider = 'MICROSOFT'
    next.password = ''
  }

  if (next.authMethod === 'PASSWORD') {
    next.oauthProvider = 'NONE'
    next.oauthRefreshToken = ''
  }

  if (next.authMethod === 'OAUTH2') {
    next.password = ''
    if (requiresMicrosoftOAuth) {
      next.oauthProvider = 'MICROSOFT'
    } else if (!next.oauthProvider || next.oauthProvider === 'NONE') {
      next.oauthProvider = availableSourceProviders[0] || 'NONE'
    }
  }

  if (next.authMethod === 'OAUTH2' && availableSourceProviders.length === 0 && !requiresMicrosoftOAuth) {
    next.authMethod = 'PASSWORD'
    next.oauthProvider = 'NONE'
    next.oauthRefreshToken = ''
  }

  if (next.protocol !== 'IMAP') {
    next.fetchMode = 'POLLING'
    next.markReadAfterPoll = false
    next.postPollAction = 'NONE'
    next.postPollTargetFolder = ''
  }

  if (next.postPollAction !== 'MOVE') {
    next.postPollTargetFolder = ''
  }

  return next
}

export function applyEmailAccountPreset(currentForm, presetId, authOptions = {}) {
  const preset = findEmailProviderPreset(presetId)
  if (!preset.values) {
    return currentForm
  }

  const next = {
    ...currentForm,
    ...preset.values,
    ...(presetId === 'gmail' && authOptions.sourceOAuthProviders?.includes('GOOGLE')
      ? { authMethod: 'OAUTH2', oauthProvider: 'GOOGLE' }
      : {})
  }

  return normalizeEmailAccountForm(next, authOptions)
}
