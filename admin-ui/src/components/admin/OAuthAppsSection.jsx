import LoadingButton from '../common/LoadingButton'
import CollapsibleSection from '../common/CollapsibleSection'

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
    <CollapsibleSection
      className="system-dashboard-section"
      collapsed={collapsed}
      collapseLoading={collapseLoading}
      copy={t('system.oauthAppsSectionCopy')}
      id="oauth-apps-section"
      onCollapseToggle={onCollapseToggle}
      sectionLoading={sectionLoading}
      t={t}
      title={t('system.oauthAppsSectionTitle')}
    >
        <div className="polling-statistics-grid">
          <article className="surface-card polling-statistics-card">
            <div className="polling-statistics-card-title">{t('system.oauthGoogleTitle')}</div>
            <div className="polling-statistics-breakdown">
              <div><span>{t('system.googleClientId')}</span><strong>{t(oauthSettings?.googleClientId ? 'common.yes' : 'common.no')}</strong></div>
              <div><span>{t('system.googleClientSecret')}</span><strong>{t(oauthSettings?.googleClientSecretConfigured ? 'common.yes' : 'common.no')}</strong></div>
              <div><span>{t('system.googleRedirectUri')}</span><strong>{oauthSettings?.googleRedirectUri || t('common.unavailable')}</strong></div>
            </div>
            <p className="section-copy">{t('system.googleClientUsageHelp')}</p>
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
    </CollapsibleSection>
  )
}

export default OAuthAppsSection
