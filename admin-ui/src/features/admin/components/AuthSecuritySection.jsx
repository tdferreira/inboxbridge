import LoadingButton from '@/shared/components/LoadingButton'
import CollapsibleSection from '@/shared/components/CollapsibleSection'
import DurationValue from '@/shared/components/DurationValue'
import { captchaProviderLabel } from '@/lib/captchaProviders'
import { parseProviderList, providerLabel } from '@/lib/geoIpProviders'

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
    <CollapsibleSection
      actions={
        <LoadingButton className="secondary" onClick={onOpenEditor} type="button">
          {t('authSecurity.edit')}
        </LoadingButton>
      }
      className="system-dashboard-section"
      collapsed={collapsed}
      collapseLoading={collapseLoading}
      copy={t('authSecurity.sectionCopy')}
      id="auth-security-section"
      onCollapseToggle={onCollapseToggle}
      sectionLoading={sectionLoading}
      t={t}
      title={t('authSecurity.sectionTitle')}
    >
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
              <div><span>{t('authSecurity.registrationChallengeProvider')}</span><strong>{authSecuritySettings?.effectiveRegistrationChallengeProvider ? captchaProviderLabel(authSecuritySettings.effectiveRegistrationChallengeProvider) : t('common.unavailable')}</strong></div>
            </div>
          </article>

          <article className="surface-card polling-statistics-card">
            <div className="polling-statistics-card-title">{t('authSecurity.geoIpProtectionTitle')}</div>
            <div className="polling-statistics-breakdown">
              <div><span>{t('authSecurity.geoIpMode')}</span><strong>{t(authSecuritySettings?.effectiveGeoIpEnabled ? 'common.enabled' : 'common.disabled')}</strong></div>
              <div><span>{t('authSecurity.geoIpPrimaryProvider')}</span><strong>{authSecuritySettings?.effectiveGeoIpPrimaryProvider ? providerLabel(authSecuritySettings.effectiveGeoIpPrimaryProvider) : t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.geoIpFallbackProviders')}</span><strong>{authSecuritySettings?.effectiveGeoIpFallbackProviders ? parseProviderList(authSecuritySettings.effectiveGeoIpFallbackProviders).map(providerLabel).join(', ') : t('common.unavailable')}</strong></div>
            </div>
          </article>

          <article className="surface-card polling-statistics-card">
            <div className="polling-statistics-card-title">{t('authSecurity.runtimeSectionTitle')}</div>
            <p className="section-copy">{t('authSecurity.summaryHelp')}</p>
          </article>
        </div>
    </CollapsibleSection>
  )
}

export default AuthSecuritySection
