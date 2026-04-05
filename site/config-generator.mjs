const DEFAULTS = {
  publicHostname: 'localhost',
  publicPort: '3000',
  jdbcUrl: 'jdbc:postgresql://postgres:5432/inboxbridge',
  jdbcUsername: 'inboxbridge',
  jdbcPassword: 'inboxbridge',
  encryptionKeyId: 'v1',
  multiUserEnabled: true,
  pollInterval: '5m',
  fetchWindow: '50',
  sourceEnabled: true,
  sourceProtocol: 'IMAP',
  sourceTls: true,
  sourceAuthMethod: 'PASSWORD',
  sourceOauthProvider: 'NONE',
  sourceUnreadOnly: false,
  sourceFolder: 'INBOX',
  sourceFetchMode: 'POLLING',
  googleRedirectPath: '/api/google-oauth/callback',
  microsoftRedirectPath: '/api/microsoft-oauth/callback'
}

function normalizeBoolean(value, fallback) {
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'string') {
    return value.toLowerCase() === 'true'
  }
  return fallback
}

function normalizeString(value, fallback = '') {
  if (value === undefined || value === null) {
    return fallback
  }
  return String(value).trim()
}

function resolveHostname(publicBaseUrl) {
  try {
    return new URL(publicBaseUrl).hostname || 'localhost'
  } catch {
    return 'localhost'
  }
}

function resolvePort(publicBaseUrl) {
  try {
    const url = new URL(publicBaseUrl)
    if (url.port) {
      return url.port
    }
    return url.protocol === 'https:' ? '443' : '80'
  } catch {
    return DEFAULTS.publicPort
  }
}

function resolvePublicBaseUrl(hostname, port) {
  const normalizedHostname = withDefault(hostname, DEFAULTS.publicHostname)
  const normalizedPort = withDefault(port, DEFAULTS.publicPort)
  return `https://${normalizedHostname}:${normalizedPort}`
}

function withDefault(value, fallback) {
  const normalized = normalizeString(value)
  return normalized || fallback
}

export function bytesToBase64(bytes) {
  if (typeof Buffer !== 'undefined') {
    return Buffer.from(bytes).toString('base64')
  }

  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary)
}

export function generateEncryptionKey(randomBytes) {
  if (!randomBytes || randomBytes.length !== 32) {
    throw new Error('InboxBridge encryption keys must be generated from exactly 32 random bytes')
  }
  return bytesToBase64(randomBytes)
}

export function buildEnvConfig(input = {}) {
  const explicitPublicBaseUrl = normalizeString(input.publicBaseUrl)
  const publicBaseUrl = explicitPublicBaseUrl || resolvePublicBaseUrl(input.publicHostname, input.publicPort)
  const hostname = explicitPublicBaseUrl ? resolveHostname(publicBaseUrl) : withDefault(input.publicHostname, DEFAULTS.publicHostname)
  const publicPort = explicitPublicBaseUrl ? resolvePort(publicBaseUrl) : withDefault(input.publicPort, DEFAULTS.publicPort)
  const sourceProtocol = withDefault(input.sourceProtocol, DEFAULTS.sourceProtocol).toUpperCase()
  const sourceAuthMethod = withDefault(input.sourceAuthMethod, DEFAULTS.sourceAuthMethod).toUpperCase()
  const sourceOauthProvider = withDefault(input.sourceOauthProvider, DEFAULTS.sourceOauthProvider).toUpperCase()
  const sourceFolder = sourceProtocol === 'IMAP'
    ? withDefault(input.sourceFolder, DEFAULTS.sourceFolder)
    : ''
  const sourceFetchMode = sourceProtocol === 'IMAP'
    ? withDefault(input.sourceFetchMode, DEFAULTS.sourceFetchMode).toUpperCase()
    : 'POLLING'

  return {
    publicBaseUrl,
    hostname,
    publicHostname: hostname,
    publicPort,
    jdbcUrl: withDefault(input.jdbcUrl, DEFAULTS.jdbcUrl),
    jdbcUsername: withDefault(input.jdbcUsername, DEFAULTS.jdbcUsername),
    jdbcPassword: withDefault(input.jdbcPassword, DEFAULTS.jdbcPassword),
    encryptionKey: normalizeString(input.encryptionKey),
    encryptionKeyId: withDefault(input.encryptionKeyId, DEFAULTS.encryptionKeyId),
    multiUserEnabled: normalizeBoolean(input.multiUserEnabled, DEFAULTS.multiUserEnabled),
    pollInterval: withDefault(input.pollInterval, DEFAULTS.pollInterval),
    fetchWindow: withDefault(input.fetchWindow, DEFAULTS.fetchWindow),
    googleClientId: normalizeString(input.googleClientId),
    googleClientSecret: normalizeString(input.googleClientSecret),
    googleRedirectUri: normalizeString(input.googleRedirectUri) || `${publicBaseUrl}${DEFAULTS.googleRedirectPath}`,
    microsoftTenant: withDefault(input.microsoftTenant, 'consumers'),
    microsoftClientId: normalizeString(input.microsoftClientId),
    microsoftClientSecret: normalizeString(input.microsoftClientSecret),
    microsoftRedirectUri: normalizeString(input.microsoftRedirectUri) || `${publicBaseUrl}${DEFAULTS.microsoftRedirectPath}`,
    sourceId: normalizeString(input.sourceId),
    sourceEnabled: normalizeBoolean(input.sourceEnabled, DEFAULTS.sourceEnabled),
    sourceProtocol,
    sourceHost: normalizeString(input.sourceHost),
    sourcePort: normalizeString(input.sourcePort),
    sourceTls: normalizeBoolean(input.sourceTls, DEFAULTS.sourceTls),
    sourceAuthMethod,
    sourceOauthProvider,
    sourceUsername: normalizeString(input.sourceUsername),
    sourcePassword: normalizeString(input.sourcePassword),
    sourceOauthRefreshToken: normalizeString(input.sourceOauthRefreshToken),
    sourceFolder,
    sourceUnreadOnly: normalizeBoolean(input.sourceUnreadOnly, DEFAULTS.sourceUnreadOnly),
    sourceFetchMode,
    sourceCustomLabel: normalizeString(input.sourceCustomLabel)
  }
}

export function generateEnvText(rawInput = {}) {
  const config = buildEnvConfig(rawInput)
  const lines = [
    '# Generated by the InboxBridge GitHub Pages setup helper',
    '# Review carefully before using in production.',
    '',
    `JDBC_URL=${config.jdbcUrl}`,
    `JDBC_USERNAME=${config.jdbcUsername}`,
    `JDBC_PASSWORD=${config.jdbcPassword}`,
    `PUBLIC_HOSTNAME=${config.publicHostname}`,
    `PUBLIC_PORT=${config.publicPort}`,
    `SECURITY_TOKEN_ENCRYPTION_KEY=${config.encryptionKey}`,
    `SECURITY_TOKEN_ENCRYPTION_KEY_ID=${config.encryptionKeyId}`,
    `SECURITY_PASSKEY_RP_ID=${config.hostname}`
  ]

  if (normalizeString(rawInput.publicBaseUrl)) {
    lines.push(`PUBLIC_BASE_URL=${config.publicBaseUrl}`)
    lines.push(`SECURITY_PASSKEY_ORIGINS=${config.publicBaseUrl}`)
  }

  lines.push(
    `MULTI_USER_ENABLED=${config.multiUserEnabled}`,
    `POLL_INTERVAL=${config.pollInterval}`,
    `FETCH_WINDOW=${config.fetchWindow}`
  )

  if (config.googleClientId || config.googleClientSecret) {
    lines.push(
      '',
      `GOOGLE_CLIENT_ID=${config.googleClientId}`,
      `GOOGLE_CLIENT_SECRET=${config.googleClientSecret}`,
      `GOOGLE_REDIRECT_URI=${config.googleRedirectUri}`
    )
  }

  if (config.microsoftClientId || config.microsoftClientSecret) {
    lines.push(
      '',
      `MICROSOFT_TENANT=${config.microsoftTenant}`,
      `MICROSOFT_CLIENT_ID=${config.microsoftClientId}`,
      `MICROSOFT_CLIENT_SECRET=${config.microsoftClientSecret}`,
      `MICROSOFT_REDIRECT_URI=${config.microsoftRedirectUri}`
    )
  }

  if (config.sourceId) {
    lines.push(
      '',
      `MAIL_ACCOUNT_0__ID=${config.sourceId}`,
      `MAIL_ACCOUNT_0__ENABLED=${config.sourceEnabled}`,
      `MAIL_ACCOUNT_0__PROTOCOL=${config.sourceProtocol}`,
      `MAIL_ACCOUNT_0__HOST=${config.sourceHost}`,
      `MAIL_ACCOUNT_0__PORT=${config.sourcePort}`,
      `MAIL_ACCOUNT_0__TLS=${config.sourceTls}`,
      `MAIL_ACCOUNT_0__AUTH_METHOD=${config.sourceAuthMethod}`,
      `MAIL_ACCOUNT_0__OAUTH_PROVIDER=${config.sourceOauthProvider}`,
      `MAIL_ACCOUNT_0__USERNAME=${config.sourceUsername}`
    )

    if (config.sourceAuthMethod === 'PASSWORD') {
      lines.push(`MAIL_ACCOUNT_0__PASSWORD=${config.sourcePassword}`)
    } else {
      lines.push(`MAIL_ACCOUNT_0__OAUTH_REFRESH_TOKEN=${config.sourceOauthRefreshToken}`)
    }

    if (config.sourceProtocol === 'IMAP') {
      lines.push(
        `MAIL_ACCOUNT_0__FOLDER=${config.sourceFolder}`,
        `MAIL_ACCOUNT_0__UNREAD_ONLY=${config.sourceUnreadOnly}`,
        `MAIL_ACCOUNT_0__FETCH_MODE=${config.sourceFetchMode}`
      )
    }

    if (config.sourceCustomLabel) {
      lines.push(`MAIL_ACCOUNT_0__CUSTOM_LABEL=${config.sourceCustomLabel}`)
    }
  }

  return `${lines.join('\n')}\n`
}
