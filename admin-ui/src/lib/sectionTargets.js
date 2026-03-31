const SOURCE_EMAIL_ACCOUNT_TARGET_PREFIX = 'source-email-account-'
const RECENT_SESSION_TARGET_PREFIX = 'recent-session-'

export function buildSourceEmailAccountTargetId(emailAccountId) {
  return `${SOURCE_EMAIL_ACCOUNT_TARGET_PREFIX}${encodeURIComponent(String(emailAccountId || ''))}`
}

export function isSourceEmailAccountTargetId(targetId) {
  return typeof targetId === 'string' && targetId.startsWith(SOURCE_EMAIL_ACCOUNT_TARGET_PREFIX)
}

export function buildRecentSessionTargetId(sessionType, sessionId) {
  if (sessionId == null) {
    return `${RECENT_SESSION_TARGET_PREFIX}${encodeURIComponent(String(sessionType || ''))}`
  }
  return `${RECENT_SESSION_TARGET_PREFIX}${encodeURIComponent(String(sessionType || 'BROWSER'))}-${encodeURIComponent(String(sessionId || ''))}`
}

export function isRecentSessionTargetId(targetId) {
  return typeof targetId === 'string' && targetId.startsWith(RECENT_SESSION_TARGET_PREFIX)
}

export function extractSourceEmailAccountId(errorLike) {
  if (!errorLike) {
    return ''
  }
  if (typeof errorLike === 'object') {
    return String(errorLike.sourceId || '')
  }
  const match = String(errorLike).match(/^Source (.+?) (?:failed: |is |cannot )/i)
  return match ? match[1] : ''
}
