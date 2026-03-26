import LoadingButton from '../common/LoadingButton'
import GoogleSetupPanel from './GoogleSetupPanel'
import './GmailDestinationSection.css'

/**
 * Groups the user Gmail destination form with the related password and Google
 * OAuth guidance panels.
 */
function GmailDestinationSection({
  collapsed,
  collapseLoading,
  gmailConfig,
  gmailMeta,
  oauthLoading,
  onCollapseToggle,
  onConnectOAuth,
  onSave,
  saveLoading,
  setGmailConfig
}) {
  return (
    <section className="app-columns">
      <section className="surface-card gmail-destination-panel" id="gmail-destination-section" tabIndex="-1">
        <div className="panel-header">
          <div>
            <div className="section-title">My Gmail Destination</div>
            <p className="section-copy">Client credentials and refresh token for the Gmail mailbox that receives this user’s imported mail.</p>
          </div>
          <div className="action-row">
            <LoadingButton className="primary" isLoading={oauthLoading} loadingLabel="Starting Gmail OAuth…" onClick={onConnectOAuth} type="button">
              Connect My Gmail OAuth
            </LoadingButton>
            <LoadingButton className="secondary" isLoading={collapseLoading} loadingLabel={collapsed ? 'Expanding…' : 'Collapsing…'} onClick={onCollapseToggle} type="button">
              {collapsed ? 'Expand' : 'Collapse'}
            </LoadingButton>
          </div>
        </div>
        {!collapsed ? (
          <>
            <div className="gmail-credential-status muted-box">
              <strong>Saved credential status</strong><br />
              Shared Google client available: {gmailMeta?.sharedClientConfigured ? 'Yes' : 'No'}<br />
              Client ID stored for this user: {gmailMeta?.clientIdConfigured ? 'Yes' : 'No'}<br />
              Client secret stored for this user: {gmailMeta?.clientSecretConfigured ? 'Yes' : 'No'}<br />
              Refresh token stored for this user: {gmailMeta?.refreshTokenConfigured ? 'Yes' : 'No'}<br />
              Effective redirect URI: {gmailConfig.redirectUri || gmailMeta?.defaultRedirectUri || `${window.location.origin}/api/google-oauth/callback`}
            </div>

            <form className="settings-grid" onSubmit={onSave}>
              <label>
                <span>Destination User</span>
                <input
                  value={gmailConfig.destinationUser}
                  onChange={(event) => setGmailConfig((current) => ({ ...current, destinationUser: event.target.value }))}
                />
              </label>
              <label>
                <span>Redirect URI</span>
                <input
                  value={gmailConfig.redirectUri}
                  onChange={(event) => setGmailConfig((current) => ({ ...current, redirectUri: event.target.value }))}
                />
              </label>
              <label>
                <span>Google Client ID</span>
                <input
                  value={gmailConfig.clientId}
                  onChange={(event) => setGmailConfig((current) => ({ ...current, clientId: event.target.value }))}
                  placeholder={gmailMeta?.clientIdConfigured ? 'Stored securely' : ''}
                />
              </label>
              <label>
                <span>Google Client Secret</span>
                <input
                  type="password"
                  value={gmailConfig.clientSecret}
                  onChange={(event) => setGmailConfig((current) => ({ ...current, clientSecret: event.target.value }))}
                  placeholder={gmailMeta?.clientSecretConfigured ? 'Stored securely' : ''}
                />
              </label>
              <label className="full">
                <span>Refresh Token</span>
                <input
                  type="password"
                  value={gmailConfig.refreshToken}
                  onChange={(event) => setGmailConfig((current) => ({ ...current, refreshToken: event.target.value }))}
                  placeholder={gmailMeta?.refreshTokenConfigured ? 'Stored securely' : ''}
                />
              </label>
              <label className="checkbox-row">
                <input
                  type="checkbox"
                  checked={gmailConfig.createMissingLabels}
                  onChange={(event) => setGmailConfig((current) => ({ ...current, createMissingLabels: event.target.checked }))}
                />
                <span>Create missing labels</span>
              </label>
              <label className="checkbox-row">
                <input
                  type="checkbox"
                  checked={gmailConfig.neverMarkSpam}
                  onChange={(event) => setGmailConfig((current) => ({ ...current, neverMarkSpam: event.target.checked }))}
                />
                <span>Never mark spam</span>
              </label>
              <label className="checkbox-row">
                <input
                  type="checkbox"
                  checked={gmailConfig.processForCalendar}
                  onChange={(event) => setGmailConfig((current) => ({ ...current, processForCalendar: event.target.checked }))}
                />
                <span>Process for calendar</span>
              </label>
              <div className="full action-row">
                <LoadingButton className="primary" isLoading={saveLoading} loadingLabel="Saving Gmail Settings…" type="submit">
                  Save Gmail Settings
                </LoadingButton>
              </div>
            </form>
          </>
        ) : null}
      </section>

      {!collapsed ? (
        <aside className="sidebar-stack">
          <GoogleSetupPanel gmailMeta={gmailMeta} />
        </aside>
      ) : null}
    </section>
  )
}

export default GmailDestinationSection
