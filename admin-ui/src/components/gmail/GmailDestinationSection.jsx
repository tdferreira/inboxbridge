import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
import GoogleSetupPanel from './GoogleSetupPanel'
import './GmailDestinationSection.css'

/**
 * Renders the Gmail destination area in two modes:
 * admins can edit advanced overrides, while regular users only see connection
 * status plus a connect/reconnect OAuth action.
 */
function GmailDestinationSection({
  collapsed,
  collapseLoading,
  gmailConfig,
  gmailMeta,
  isAdmin,
  oauthLoading,
  locale,
  onCollapseToggle,
  onConnectOAuth,
  onSave,
  saveLoading,
  setGmailConfig,
  t
}) {
  const isConnected = Boolean(gmailMeta?.refreshTokenConfigured)
  const effectiveRedirectUri = gmailConfig.redirectUri || gmailMeta?.defaultRedirectUri || `${window.location.origin}/api/google-oauth/callback`
  const connectLabel = isConnected ? t('gmail.reconnect') : t('gmail.connect')
  const connectLoadingLabel = isConnected ? t('gmail.reconnectLoading') : t('gmail.connectLoading')

  return (
    <section className="app-columns">
      <section className="surface-card gmail-destination-panel section-with-corner-toggle" id="gmail-destination-section" tabIndex="-1">
        <div className="panel-header">
          <div>
            <div className="section-title">{t('gmail.title')}</div>
            <p className="section-copy">{t(isAdmin ? 'gmail.copy' : 'gmail.nonAdminCopy')}</p>
          </div>
          <div className="panel-header-actions">
            <LoadingButton className="primary" isLoading={oauthLoading} loadingLabel={connectLoadingLabel} onClick={onConnectOAuth} type="button">
              {connectLabel}
            </LoadingButton>
          </div>
        </div>
        <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={collapseLoading} expandLabel={t('common.expandSection')} isLoading={collapseLoading} onClick={onCollapseToggle} />
        {!collapsed ? (
          <>
            <div className="gmail-credential-status muted-box">
              <strong>{t('gmail.savedStatus')}</strong><br />
              {t('gmail.connectionStatus', { value: t(isConnected ? 'common.yes' : 'common.no') })}<br />
              {t('gmail.refreshTokenStored', { value: t(gmailMeta?.refreshTokenConfigured ? 'common.yes' : 'common.no') })}<br />
              {t('gmail.redirectEffective', { value: effectiveRedirectUri })}
              {!isAdmin ? (
                <>
                  <br />
                  {t('gmail.sharedClient', { value: t(gmailMeta?.sharedClientConfigured ? 'common.yes' : 'common.no') })}
                </>
              ) : null}
            </div>

            {isAdmin ? (
              <form className="settings-grid" onSubmit={onSave}>
                <label>
                  <span>{t('gmail.destinationUser')}</span>
                  <input
                    value={gmailConfig.destinationUser}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, destinationUser: event.target.value }))}
                  />
                </label>
                <label>
                  <span>{t('gmail.redirectUri')}</span>
                  <input
                    value={gmailConfig.redirectUri}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, redirectUri: event.target.value }))}
                  />
                </label>
                <label>
                  <span>{t('gmail.clientId')}</span>
                  <input
                    value={gmailConfig.clientId}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, clientId: event.target.value }))}
                    placeholder={gmailMeta?.clientIdConfigured ? t('gmail.storedSecurely') : gmailMeta?.sharedClientConfigured ? t('gmail.usingSharedClient') : ''}
                  />
                </label>
                <label>
                  <span>{t('gmail.clientSecret')}</span>
                  <input
                    type="password"
                    value={gmailConfig.clientSecret}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, clientSecret: event.target.value }))}
                    placeholder={gmailMeta?.clientSecretConfigured ? t('gmail.storedSecurely') : gmailMeta?.sharedClientConfigured ? t('gmail.usingSharedClient') : ''}
                  />
                </label>
                <label className="full">
                  <span>{t('gmail.refreshToken')}</span>
                  <input
                    type="password"
                    value={gmailConfig.refreshToken}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, refreshToken: event.target.value }))}
                    placeholder={gmailMeta?.refreshTokenConfigured ? t('gmail.storedSecurely') : ''}
                  />
                </label>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={gmailConfig.createMissingLabels}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, createMissingLabels: event.target.checked }))}
                  />
                  <span>{t('gmail.createMissingLabels')}</span>
                </label>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={gmailConfig.neverMarkSpam}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, neverMarkSpam: event.target.checked }))}
                  />
                  <span>{t('gmail.neverMarkSpam')}</span>
                </label>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={gmailConfig.processForCalendar}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, processForCalendar: event.target.checked }))}
                  />
                  <span>{t('gmail.processForCalendar')}</span>
                </label>
                <div className="full action-row">
                  <LoadingButton className="primary" isLoading={saveLoading} loadingLabel={t('gmail.saveLoading')} type="submit">
                    {t('gmail.save')}
                  </LoadingButton>
                </div>
              </form>
            ) : (
              <div className="muted-box gmail-simplified-status">
                <strong>{t(isConnected ? 'gmail.nonAdminReadyTitle' : 'gmail.nonAdminPendingTitle')}</strong><br />
                {t(isConnected ? 'gmail.nonAdminReadyBody' : 'gmail.nonAdminPendingBody')}
                {!gmailMeta?.sharedClientConfigured ? (
                  <>
                    <br />
                    {t('gmail.nonAdminAdminRequired')}
                  </>
                ) : null}
              </div>
            )}
          </>
        ) : null}
      </section>

      {!collapsed && isAdmin ? (
        <aside className="sidebar-stack">
          <GoogleSetupPanel gmailMeta={gmailMeta} locale={locale} t={t} />
        </aside>
      ) : null}
    </section>
  )
}

export default GmailDestinationSection
