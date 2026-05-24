import { useState } from 'react'
import CollapsibleSection from '@/shared/components/CollapsibleSection'
import LoadingButton from '@/shared/components/LoadingButton'
import { formatDate } from '@/lib/formatters'
import './ExtensionSessionsPanel.css'

/**
 * Lets the signed-in user inspect and revoke browser-extension sessions from
 * the same security workspace that already handles browser and remote sessions.
 */
function ExtensionSessionsPanel({
  locale,
  onRevokeAllSessions,
  onRevokeSession,
  revokeAllLoading = false,
  revokeLoadingId = null,
  sessions,
  t
}) {
  const [collapsed, setCollapsed] = useState(false)
  const activeSessions = sessions.filter((session) => !session.revokedAt)
  const revokedSessions = sessions.filter((session) => session.revokedAt)

  function renderSessionCard(session) {
    const isRevoked = Boolean(session.revokedAt)

    return (
      <div key={session.id} className="passkey-card session-card extension-session-card">
        <div>
          <strong>{session.label || t('extensionSessions.defaultLabel')}</strong><br />
          {t('extensionSessions.browserFamily', { value: session.browserFamily || t('common.unavailable') })}<br />
          {t('extensionSessions.extensionVersion', { value: session.extensionVersion || t('common.unavailable') })}<br />
          {t('extensionSessions.createdAt', { value: formatDate(session.createdAt, locale) })}<br />
          {t('extensionSessions.lastUsedAt', { value: session.lastUsedAt ? formatDate(session.lastUsedAt, locale) : t('common.never') })}<br />
          {t('extensionSessions.status', { value: isRevoked ? t('extensionSessions.statusRevoked') : t('extensionSessions.statusActive') })}<br />
          {isRevoked ? t('extensionSessions.revokedAt', { value: formatDate(session.revokedAt, locale) }) : t('extensionSessions.activeHint')}
        </div>
        {!isRevoked ? (
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
    )
  }

  return (
    <CollapsibleSection
      actions={
        <LoadingButton
          className="secondary"
          disabled={activeSessions.length === 0}
          isLoading={revokeAllLoading}
          loadingLabel={t('extensionSessions.revokeAllLoading')}
          onClick={onRevokeAllSessions}
          type="button"
        >
          {t('extensionSessions.revokeAll')}
        </LoadingButton>
      }
      className="extension-sessions-panel"
      collapsed={collapsed}
      copy={t('extensionSessions.copy')}
      id="security-extension-sessions-panel-section"
      onCollapseToggle={() => setCollapsed((current) => !current)}
      t={t}
      title={t('extensionSessions.title')}
    >
      <div className="detail-stack">
        <div className="muted-box">
          {t('extensionSessions.instructions')}
        </div>

        <div className="sessions-group">
          <div className="section-title sessions-group-title">{t('extensionSessions.activeTitle')}</div>
          {activeSessions.length > 0 ? activeSessions.map(renderSessionCard) : (
            <div className="muted-box">{sessions.length > 0 ? t('extensionSessions.noneActive') : t('extensionSessions.none')}</div>
          )}
        </div>
        {revokedSessions.length > 0 ? (
          <div className="sessions-group">
            <div className="section-title sessions-group-title">{t('extensionSessions.recentRevokedTitle')}</div>
            {revokedSessions.map(renderSessionCard)}
          </div>
        ) : null}
      </div>
    </CollapsibleSection>
  )
}

export default ExtensionSessionsPanel
