import SectionCard from '../common/SectionCard'
import LoadingButton from '../common/LoadingButton'
import DeviceLocationValue from './DeviceLocationValue'
import { authMethodLabel, formatDate } from '../../lib/formatters'
import { buildRecentSessionTargetId } from '../../lib/sectionTargets'
import './SessionsPanel.css'

function SessionsPanel({
  activeSessions,
  currentSessionCanRequestDeviceLocation = false,
  currentSessionDeviceLocationError = '',
  geoIpConfigured = false,
  locale,
  onRequestCurrentDeviceLocation,
  onRevokeOtherSessions,
  onRevokeSession,
  recentLogins,
  revokeLoadingId,
  revokeOthersLoading,
  requestCurrentDeviceLocationLoading = false,
  t
}) {
  const sessionKindLabel = (session) => t(session.sessionType === 'REMOTE' ? 'sessions.kindRemote' : 'sessions.kindBrowser')
  const canRequestCurrentDeviceLocation = (session) => (
    session.current && session.sessionType === 'BROWSER' && !session.deviceLocationCapturedAt && currentSessionCanRequestDeviceLocation
  )
  const deviceLocationFallback = (session) => (
    canRequestCurrentDeviceLocation(session) ? t('sessions.deviceLocationPending') : t('sessions.deviceLocationUnavailable')
  )

  return (
    <SectionCard
      actions={
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
      }
      className="sessions-panel"
      copy={t('sessions.copy')}
      id="security-sessions-panel-section"
      title={t('sessions.title')}
    >

      <div className="detail-stack">
        {!geoIpConfigured ? (
          <div className="muted-box">
            {t('sessions.locationNotice')}
          </div>
        ) : null}

        <div className="sessions-group">
          <div className="section-title sessions-group-title">{t('sessions.activeTitle')}</div>
          {activeSessions.length > 0 ? activeSessions.map((session) => (
            <div key={session.id} className={`passkey-card session-card ${session.current ? 'session-card-current' : ''}`.trim()}>
              <div>
                <strong>
                  {session.current ? t('sessions.currentSession') : t('sessions.activeSession')}
                </strong><br />
                {t('sessions.sessionKind', { value: sessionKindLabel(session) })}<br />
                {t('sessions.browser', { value: session.browserLabel || t('common.unavailable') })}<br />
                {t('sessions.deviceType', { value: session.deviceLabel || t('common.unavailable') })}<br />
                {t('sessions.loginMethod', { value: authMethodLabel(session.loginMethod, locale) })}<br />
                {t('sessions.ipAddress', { value: session.ipAddress || t('common.unavailable') })}<br />
                {t('sessions.location', { value: session.locationLabel || t('sessions.locationUnavailable') })}<br />
                {t('sessions.deviceLocationLabel')}{' '}
                <DeviceLocationValue
                  fallbackLabel={session.deviceLocationLabel}
                  latitude={session.deviceLatitude}
                  locale={locale}
                  longitude={session.deviceLongitude}
                  placeholderLabel={deviceLocationFallback(session)}
                  t={t}
                /><br />
                {t('sessions.deviceLocationCapturedAt', { value: session.deviceLocationCapturedAt ? formatDate(session.deviceLocationCapturedAt, locale) : deviceLocationFallback(session) })}<br />
                {t('sessions.lastSeen', { value: formatDate(session.lastSeenAt, locale) })}<br />
                {t('sessions.expiresAt', { value: formatDate(session.expiresAt, locale) })}
                {canRequestCurrentDeviceLocation(session) ? (
                  <>
                    <br />
                    <LoadingButton
                      className="secondary session-device-location-action"
                      isLoading={requestCurrentDeviceLocationLoading}
                      loadingLabel={t('sessions.captureDeviceLocationLoading')}
                      onClick={onRequestCurrentDeviceLocation}
                      type="button"
                    >
                      {t('sessions.captureDeviceLocation')}
                    </LoadingButton>
                  </>
                ) : null}
                {canRequestCurrentDeviceLocation(session) && currentSessionDeviceLocationError ? (
                  <div className="muted-box utility-prompt-error session-device-location-feedback">{currentSessionDeviceLocationError}</div>
                ) : null}
              </div>
              {!session.current ? (
                <LoadingButton
                  className="secondary"
                  isLoading={revokeLoadingId === `${session.sessionType || 'BROWSER'}:${session.id}`}
                  loadingLabel={t('sessions.revokeLoading')}
                  onClick={() => onRevokeSession(session)}
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
            <div id={buildRecentSessionTargetId(session.sessionType, session.id)} key={`recent-${session.sessionType || 'BROWSER'}-${session.id}`} className="passkey-card session-card" tabIndex="-1">
              <div>
                <strong>{formatDate(session.createdAt, locale)}</strong><br />
                {t('sessions.sessionKind', { value: sessionKindLabel(session) })}<br />
                {t('sessions.browser', { value: session.browserLabel || t('common.unavailable') })}<br />
                {t('sessions.deviceType', { value: session.deviceLabel || t('common.unavailable') })}<br />
                {t('sessions.loginMethod', { value: authMethodLabel(session.loginMethod, locale) })}<br />
                {t('sessions.ipAddress', { value: session.ipAddress || t('common.unavailable') })}<br />
                {t('sessions.location', { value: session.locationLabel || t('sessions.locationUnavailable') })}<br />
                {t('sessions.deviceLocationLabel')}{' '}
                <DeviceLocationValue
                  fallbackLabel={session.deviceLocationLabel}
                  latitude={session.deviceLatitude}
                  locale={locale}
                  longitude={session.deviceLongitude}
                  placeholderLabel={deviceLocationFallback(session)}
                  t={t}
                /><br />
                {t('sessions.deviceLocationCapturedAt', { value: session.deviceLocationCapturedAt ? formatDate(session.deviceLocationCapturedAt, locale) : deviceLocationFallback(session) })}<br />
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
    </SectionCard>
  )
}

export default SessionsPanel
