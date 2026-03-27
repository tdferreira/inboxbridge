import InfoHint from '../common/InfoHint'
import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
import GoogleSetupPanel from './GoogleSetupPanel'
import './GmailAccountSection.css'

function LabeledHint({ helpText, label }) {
  return (
    <span className="field-label-row">
      <span>{label}</span>
      <InfoHint text={helpText} />
    </span>
  )
}

/**
 * Renders the Gmail account area in two modes:
 * admins can edit advanced overrides, while regular users only see connection
 * status plus a connect/reconnect OAuth action.
 */
function GmailAccountSection({
  collapsed,
  collapseLoading,
  gmailConfig,
  gmailMeta,
  isAdmin,
  oauthLoading,
  unlinkLoading,
  locale,
  onCollapseToggle,
  onConnectOAuth,
  onUnlinkOAuth,
  onSave,
  saveLoading,
  sectionLoading = false,
  setGmailConfig,
  t
}) {
  const isConnected = Boolean(gmailMeta?.refreshTokenConfigured)
  const connectLabel = isConnected ? t('gmail.reconnect') : t('gmail.connect')
  const connectLoadingLabel = isConnected ? t('gmail.reconnectLoading') : t('gmail.connectLoading')
  const hasSidebar = !collapsed && isAdmin

  return (
    <section className={`app-columns ${hasSidebar ? '' : 'app-columns-single'}`.trim()}>
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
            {isConnected ? (
              <LoadingButton className="secondary" isLoading={unlinkLoading} loadingLabel={t('gmail.unlinkLoading')} onClick={onUnlinkOAuth} type="button">
                {t('gmail.unlink')}
              </LoadingButton>
            ) : null}
          </div>
        </div>
        <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={collapseLoading} expandLabel={t('common.expandSection')} isLoading={collapseLoading} onClick={onCollapseToggle} />
        {sectionLoading ? (
          <div className="section-refresh-indicator" role="status">
            <span aria-hidden="true" className="section-refresh-spinner" />
            {t('common.refreshingSection')}
          </div>
        ) : null}
        {!collapsed ? (
          <>
            {isAdmin ? (
              <div className="gmail-credential-status muted-box">
                <strong>{t('gmail.savedStatus')}</strong><br />
                {t('gmail.connectionStatus', { value: t(isConnected ? 'common.yes' : 'common.no') })}<br />
                {t('gmail.refreshTokenStored', { value: t(gmailMeta?.refreshTokenConfigured ? 'common.yes' : 'common.no') })}<br />
                {t('gmail.redirectEffective', { value: gmailConfig.redirectUri || gmailMeta?.defaultRedirectUri || `${window.location.origin}/api/google-oauth/callback` })}
              </div>
            ) : null}

            {isAdmin ? (
              <form className="settings-grid" onSubmit={onSave}>
                <label>
                  <LabeledHint helpText={t('gmail.destinationUserHelp')} label={t('gmail.destinationUser')} />
                  <input
                    aria-label={t('gmail.destinationUser')}
                    value={gmailConfig.destinationUser}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, destinationUser: event.target.value }))}
                  />
                </label>
                <label>
                  <LabeledHint helpText={t('gmail.redirectUriHelp')} label={t('gmail.redirectUri')} />
                  <input
                    aria-label={t('gmail.redirectUri')}
                    value={gmailConfig.redirectUri}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, redirectUri: event.target.value }))}
                  />
                </label>
                <label>
                  <LabeledHint helpText={t('gmail.clientIdHelp')} label={t('gmail.clientId')} />
                  <input
                    aria-label={t('gmail.clientId')}
                    value={gmailConfig.clientId}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, clientId: event.target.value }))}
                    placeholder={gmailMeta?.clientIdConfigured ? t('gmail.storedSecurely') : gmailMeta?.sharedClientConfigured ? t('gmail.usingSharedClient') : ''}
                  />
                </label>
                <label>
                  <LabeledHint helpText={t('gmail.clientSecretHelp')} label={t('gmail.clientSecret')} />
                  <input
                    aria-label={t('gmail.clientSecret')}
                    type="password"
                    value={gmailConfig.clientSecret}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, clientSecret: event.target.value }))}
                    placeholder={gmailMeta?.clientSecretConfigured ? t('gmail.storedSecurely') : gmailMeta?.sharedClientConfigured ? t('gmail.usingSharedClient') : ''}
                  />
                </label>
                <label className="full">
                  <LabeledHint helpText={t('gmail.refreshTokenHelp')} label={t('gmail.refreshToken')} />
                  <input
                    aria-label={t('gmail.refreshToken')}
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
                  <LabeledHint helpText={t('gmail.createMissingLabelsHelp')} label={t('gmail.createMissingLabels')} />
                </label>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={gmailConfig.neverMarkSpam}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, neverMarkSpam: event.target.checked }))}
                  />
                  <LabeledHint helpText={t('gmail.neverMarkSpamHelp')} label={t('gmail.neverMarkSpam')} />
                </label>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={gmailConfig.processForCalendar}
                    onChange={(event) => setGmailConfig((current) => ({ ...current, processForCalendar: event.target.checked }))}
                  />
                  <LabeledHint helpText={t('gmail.processForCalendarHelp')} label={t('gmail.processForCalendar')} />
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

      {hasSidebar ? (
        <aside className="sidebar-stack">
          <GoogleSetupPanel gmailMeta={gmailMeta} locale={locale} t={t} />
        </aside>
      ) : null}
    </section>
  )
}

export default GmailAccountSection
