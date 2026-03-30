import LoadingButton from '../common/LoadingButton'
import { authMethodLabel, formatDate } from '../../lib/formatters'
import { buildRecentSessionTargetId } from '../../lib/sectionTargets'
import './SessionsPanel.css'

function SessionsPanel({
  activeSessions,
  locale,
  onRevokeOtherSessions,
  onRevokeSession,
  recentLogins,
  revokeLoadingId,
  revokeOthersLoading,
  t
}) {
  return (
    <section className="surface-card sessions-panel" id="security-sessions-panel-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('sessions.title')}</div>
          <p className="section-copy">{t('sessions.copy')}</p>
        </div>
        <LoadingButton
          className="secondary"
          disabled={activeSessions.filter((session) => !session.current).length === 0}
          isLoading={revokeOthersLoading}
          loadingLabel={t('sessions.revokeOthersLoading')}
          onClick={onRevokeOtherSessions}
          type="button"
        >
          {t('sessions.revokeOthers')}
        </LoadingButton>
      </div>

      <div className="detail-stack">
        <div className="muted-box">
          {t('sessions.locationNotice')}
        </div>

        <div className="sessions-group">
          <div className="section-title sessions-group-title">{t('sessions.activeTitle')}</div>
          {activeSessions.length > 0 ? activeSessions.map((session) => (
            <div key={session.id} className={`passkey-card session-card ${session.current ? 'session-card-current' : ''}`.trim()}>
              <div>
                <strong>
                  {session.current ? t('sessions.currentSession') : t('sessions.activeSession')}
                </strong><br />
                {t('sessions.loginMethod', { value: authMethodLabel(session.loginMethod, locale) })}<br />
                {t('sessions.ipAddress', { value: session.ipAddress || t('common.unavailable') })}<br />
                {t('sessions.location', { value: session.locationLabel || t('sessions.locationUnavailable') })}<br />
                {t('sessions.lastSeen', { value: formatDate(session.lastSeenAt, locale) })}<br />
                {t('sessions.expiresAt', { value: formatDate(session.expiresAt, locale) })}
              </div>
              {!session.current ? (
                <LoadingButton
                  className="secondary"
                  isLoading={revokeLoadingId === session.id}
                  loadingLabel={t('sessions.revokeLoading')}
                  onClick={() => onRevokeSession(session.id)}
                  type="button"
                >
                  {t('sessions.revoke')}
                </LoadingButton>
              ) : (
                <div className="status-pill tone-success">{t('sessions.currentBadge')}</div>
              )}
            </div>
          )) : (
            <div className="muted-box">{t('sessions.noneActive')}</div>
          )}
        </div>

        <div className="sessions-group">
          <div className="section-title sessions-group-title">{t('sessions.recentTitle')}</div>
          {recentLogins.length > 0 ? recentLogins.map((session) => (
            <div id={buildRecentSessionTargetId(session.id)} key={`recent-${session.id}`} className="passkey-card session-card" tabIndex="-1">
              <div>
                <strong>{formatDate(session.createdAt, locale)}</strong><br />
                {t('sessions.loginMethod', { value: authMethodLabel(session.loginMethod, locale) })}<br />
                {t('sessions.ipAddress', { value: session.ipAddress || t('common.unavailable') })}<br />
                {t('sessions.location', { value: session.locationLabel || t('sessions.locationUnavailable') })}<br />
                {t('sessions.sessionStatus', { value: t(session.active ? 'sessions.statusActive' : 'sessions.statusClosed') })}<br />
                {t('sessions.lastSeen', { value: formatDate(session.lastSeenAt, locale) })}
              </div>
              {session.current ? <div className="status-pill tone-success">{t('sessions.currentBadge')}</div> : null}
            </div>
          )) : (
            <div className="muted-box">{t('sessions.noneRecent')}</div>
          )}
        </div>
      </div>
    </section>
  )
}

export default SessionsPanel
