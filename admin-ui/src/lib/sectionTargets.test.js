import {
  buildRecentSessionTargetId,
  buildSourceEmailAccountTargetId,
  extractSourceEmailAccountId,
  isRecentSessionTargetId,
  isSourceEmailAccountTargetId
} from '@/lib/sectionTargets'

describe('sectionTargets', () => {
  it('builds stable target ids for source email accounts', () => {
    expect(buildSourceEmailAccountTargetId('outlook-main')).toBe('source-email-account-outlook-main')
    expect(buildSourceEmailAccountTargetId('gmail/source')).toBe('source-email-account-gmail%2Fsource')
  })

  it('detects source email account target ids', () => {
    expect(isSourceEmailAccountTargetId('source-email-account-outlook-main')).toBe(true)
    expect(isSourceEmailAccountTargetId('destination-mailbox-section')).toBe(false)
  })

  it('builds and detects recent session target ids', () => {
    expect(buildRecentSessionTargetId('BROWSER', 42)).toBe('recent-session-BROWSER-42')
    expect(buildRecentSessionTargetId('REMOTE', 42)).toBe('recent-session-REMOTE-42')
    expect(isRecentSessionTargetId('recent-session-BROWSER-42')).toBe(true)
    expect(isRecentSessionTargetId('security-sessions-panel-section')).toBe(false)
  })

  it('extracts the source email account id from structured or raw errors', () => {
    expect(extractSourceEmailAccountId({ sourceId: 'outlook-main' })).toBe('outlook-main')
    expect(extractSourceEmailAccountId('Source outlook-main is configured for OAuth2 but has no refresh token.')).toBe('outlook-main')
    expect(extractSourceEmailAccountId('A polling run is already in progress.')).toBe('')
  })
})
