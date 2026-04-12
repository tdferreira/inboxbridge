import { useState } from 'react'
import CopyButton from '@/shared/components/CopyButton'
import LoadingButton from '@/shared/components/LoadingButton'
import SectionCard from '@/shared/components/SectionCard'
import { formatDate } from '@/lib/formatters'
import './ExtensionSessionsPanel.css'

/**
 * Manages browser-extension API tokens from the same security workspace that
 * already handles browser and remote sessions. Tokens are shown only once at
 * creation time, so the panel keeps the latest created token visible until the
 * user closes it or revokes that token.
 */
function ExtensionSessionsPanel({
  createLoading = false,
  latestCreatedSession = null,
  locale,
  onClearLatestCreatedSession,
  onCreateSession,
  onRevokeSession,
  revokeLoadingId = null,
  sessions,
  t
}) {
  const [label, setLabel] = useState('')

  function detectBrowserFamily() {
    const userAgent = String(window.navigator?.userAgent || '').toLowerCase()
    if (userAgent.includes('firefox')) {
      return 'firefox'
    }
    if (userAgent.includes('edg/')) {
      return 'edge'
    }
    if (userAgent.includes('opr/') || userAgent.includes('opera')) {
      return 'opera'
    }
    if (userAgent.includes('safari') && !userAgent.includes('chrome')) {
      return 'safari'
    }
    if (userAgent.includes('chrome') || userAgent.includes('chromium')) {
      return 'chromium'
    }
    return 'unknown'
  }

  async function handleSubmit(event) {
    event.preventDefault()
    await onCreateSession({
      browserFamily: detectBrowserFamily(),
      extensionVersion: 'manual-bootstrap',
      label
    })
    setLabel('')
  }

  return (
    <SectionCard
      className="extension-sessions-panel"
      copy={t('extensionSessions.copy')}
      id="security-extension-sessions-panel-section"
      title={t('extensionSessions.title')}
    >
      <div className="detail-stack">
        <div className="muted-box">
          {t('extensionSessions.instructions')}
        </div>

        {latestCreatedSession?.token ? (
          <div className="passkey-card extension-session-token-card">
            <div>
              <strong>{t('extensionSessions.latestTokenTitle')}</strong><br />
              {t('extensionSessions.latestTokenCopy')}<br />
              <code className="extension-session-token-value">{latestCreatedSession.token}</code>
            </div>
            <div className="extension-session-token-actions">
              <CopyButton copiedLabel={t('common.copied')} label={t('common.copy')} text={latestCreatedSession.token} />
              <button className="secondary" onClick={onClearLatestCreatedSession} type="button">
                {t('common.dismissNotification')}
              </button>
            </div>
          </div>
        ) : null}

        <form className="extension-session-create-form" onSubmit={handleSubmit}>
          <label className="field-label" htmlFor="extension-session-label">{t('extensionSessions.label')}</label>
          <input
            className="text-input"
            id="extension-session-label"
            maxLength={120}
            onChange={(event) => setLabel(event.target.value)}
            placeholder={t('extensionSessions.labelPlaceholder')}
            type="text"
            value={label}
          />
          <div className="field-hint">{t('extensionSessions.labelHelp')}</div>
          <LoadingButton
            className="primary"
            isLoading={createLoading}
            loadingLabel={t('extensionSessions.createLoading')}
            type="submit"
          >
            {t('extensionSessions.create')}
          </LoadingButton>
        </form>

        <div className="sessions-group">
          <div className="section-title sessions-group-title">{t('extensionSessions.activeTitle')}</div>
          {sessions.length > 0 ? sessions.map((session) => (
            <div key={session.id} className="passkey-card session-card extension-session-card">
              <div>
                <strong>{session.label || t('extensionSessions.defaultLabel')}</strong><br />
                {t('extensionSessions.browserFamily', { value: session.browserFamily || t('common.unavailable') })}<br />
                {t('extensionSessions.extensionVersion', { value: session.extensionVersion || t('common.unavailable') })}<br />
                {t('extensionSessions.tokenPrefix', { value: session.tokenPrefix || t('common.unavailable') })}<br />
                {t('extensionSessions.createdAt', { value: formatDate(session.createdAt, locale) })}<br />
                {t('extensionSessions.lastUsedAt', { value: session.lastUsedAt ? formatDate(session.lastUsedAt, locale) : t('common.never') })}<br />
                {t('extensionSessions.status', { value: session.revokedAt ? t('extensionSessions.statusRevoked') : t('extensionSessions.statusActive') })}<br />
                {session.revokedAt ? t('extensionSessions.revokedAt', { value: formatDate(session.revokedAt, locale) }) : t('extensionSessions.activeHint')}
              </div>
              {!session.revokedAt ? (
                <LoadingButton
                  className="secondary"
                  isLoading={revokeLoadingId === session.id}
                  loadingLabel={t('extensionSessions.revokeLoading')}
                  onClick={() => onRevokeSession(session)}
                  type="button"
                >
                  {t('extensionSessions.revoke')}
                </LoadingButton>
              ) : (
                <div className="status-pill">{t('extensionSessions.statusRevoked')}</div>
              )}
            </div>
          )) : (
            <div className="muted-box">{t('extensionSessions.none')}</div>
          )}
        </div>
      </div>
    </SectionCard>
  )
}

export default ExtensionSessionsPanel
