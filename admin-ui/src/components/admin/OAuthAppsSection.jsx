import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'

function OAuthAppsSection({
  collapsed,
  collapseLoading,
  oauthSettings,
  onCollapseToggle,
  onEditGoogle,
  onEditMicrosoft,
  sectionLoading = false,
  t
}) {
  return (
    <section className="surface-card system-dashboard-section section-with-corner-toggle" id="oauth-apps-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('system.oauthAppsSectionTitle')}</div>
          <p className="section-copy">{t('system.oauthAppsSectionCopy')}</p>
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
        <div className="polling-statistics-grid">
          <article className="surface-card polling-statistics-card">
            <div className="polling-statistics-card-title">{t('system.oauthGoogleTitle')}</div>
            <div className="polling-statistics-breakdown">
              <div><span>{t('system.googleClientId')}</span><strong>{t(oauthSettings?.googleClientId ? 'common.yes' : 'common.no')}</strong></div>
              <div><span>{t('system.googleRefreshToken')}</span><strong>{t(oauthSettings?.googleRefreshTokenConfigured ? 'common.yes' : 'common.no')}</strong></div>
              <div><span>{t('system.googleDestinationUser')}</span><strong>{oauthSettings?.googleDestinationUser || t('common.unavailable')}</strong></div>
            </div>
            <div className="action-row">
              <LoadingButton className="secondary" onClick={onEditGoogle} type="button">
                {t('system.oauthGoogleEdit')}
              </LoadingButton>
            </div>
          </article>

          <article className="surface-card polling-statistics-card">
            <div className="polling-statistics-card-title">{t('system.oauthMicrosoftTitle')}</div>
            <div className="polling-statistics-breakdown">
              <div><span>{t('system.microsoftClientId')}</span><strong>{t(oauthSettings?.microsoftClientId ? 'common.yes' : 'common.no')}</strong></div>
              <div><span>{t('system.microsoftRedirectUri')}</span><strong>{oauthSettings?.microsoftRedirectUri || t('common.unavailable')}</strong></div>
            </div>
            <div className="action-row">
              <LoadingButton className="secondary" onClick={onEditMicrosoft} type="button">
                {t('system.oauthMicrosoftEdit')}
              </LoadingButton>
            </div>
          </article>
        </div>
      ) : null}
    </section>
  )
}

export default OAuthAppsSection
