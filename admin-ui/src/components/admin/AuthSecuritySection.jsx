import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
import DurationValue from '../common/DurationValue'

function AuthSecuritySection({
  authSecuritySettings,
  collapsed,
  collapseLoading,
  onCollapseToggle,
  onOpenEditor,
  sectionLoading = false,
  locale = 'en',
  t
}) {
  return (
    <section className="surface-card system-dashboard-section section-with-corner-toggle" id="auth-security-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('authSecurity.sectionTitle')}</div>
          <p className="section-copy">{t('authSecurity.sectionCopy')}</p>
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
            <div className="polling-statistics-card-title">{t('authSecurity.loginProtectionTitle')}</div>
            <div className="polling-statistics-breakdown">
              <div><span>{t('authSecurity.failedAttempts')}</span><strong>{authSecuritySettings?.effectiveLoginFailureThreshold ?? t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.initialBlock')}</span><strong>{authSecuritySettings?.effectiveLoginInitialBlock ? <DurationValue locale={locale} value={authSecuritySettings.effectiveLoginInitialBlock} /> : t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.maxBlock')}</span><strong>{authSecuritySettings?.effectiveLoginMaxBlock ? <DurationValue locale={locale} value={authSecuritySettings.effectiveLoginMaxBlock} /> : t('common.unavailable')}</strong></div>
            </div>
          </article>

          <article className="surface-card polling-statistics-card">
            <div className="polling-statistics-card-title">{t('authSecurity.registrationProtectionTitle')}</div>
            <div className="polling-statistics-breakdown">
              <div><span>{t('authSecurity.registrationChallengeMode')}</span><strong>{t(authSecuritySettings?.effectiveRegistrationChallengeEnabled ? 'common.enabled' : 'common.disabled')}</strong></div>
              <div><span>{t('authSecurity.registrationChallengeTtl')}</span><strong>{authSecuritySettings?.effectiveRegistrationChallengeTtl ? <DurationValue locale={locale} value={authSecuritySettings.effectiveRegistrationChallengeTtl} /> : t('common.unavailable')}</strong></div>
            </div>
            <p className="section-copy">{t('authSecurity.summaryHelp')}</p>
            <div className="action-row">
              <LoadingButton className="secondary" onClick={onOpenEditor} type="button">
                {t('authSecurity.edit')}
              </LoadingButton>
            </div>
          </article>
        </div>
      ) : null}
    </section>
  )
}

export default AuthSecuritySection
