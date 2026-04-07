import DurationValue from '@/shared/components/DurationValue'

function AuthSecuritySummarySection({
  authSecuritySettings,
  locale,
  parseProviderList,
  providerLabel,
  t
}) {
  return (
    <section className="surface-card auth-security-settings-section auth-security-summary-card">
      <div className="auth-security-section-header">
        <div className="auth-security-section-title">{t('authSecurity.effectiveSectionTitle')}</div>
        <p className="auth-security-section-copy">{t('authSecurity.effectiveSectionCopy')}</p>
      </div>
      <div className="muted-box">
        {t('authSecurity.effectiveFailedAttempts', { value: authSecuritySettings.effectiveLoginFailureThreshold })}<br />
        {t('authSecurity.effectiveInitialBlock', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveLoginInitialBlock} /><br />
        {t('authSecurity.effectiveMaxBlock', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveLoginMaxBlock} /><br />
        {t('authSecurity.effectiveRegistrationChallenge', { value: t(authSecuritySettings.effectiveRegistrationChallengeEnabled ? 'common.enabled' : 'common.disabled') })}<br />
        {t('authSecurity.effectiveRegistrationChallengeTtl', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveRegistrationChallengeTtl} /><br />
        {t('authSecurity.effectiveRegistrationChallengeProvider', { value: authSecuritySettings.effectiveRegistrationChallengeProviderLabel })}<br />
        {t('authSecurity.effectiveGeoIpMode', { value: t(authSecuritySettings.effectiveGeoIpEnabled ? 'common.enabled' : 'common.disabled') })}<br />
        {t('authSecurity.effectiveGeoIpPrimaryProvider', { value: providerLabel(authSecuritySettings.effectiveGeoIpPrimaryProvider) })}<br />
        {t('authSecurity.effectiveGeoIpFallbackProviders', { value: parseProviderList(authSecuritySettings.effectiveGeoIpFallbackProviders).map(providerLabel).join(', ') || t('common.unavailable') })}<br />
        {t('authSecurity.effectiveGeoIpCacheTtl', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveGeoIpCacheTtl} /><br />
        {t('authSecurity.effectiveGeoIpProviderCooldown', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveGeoIpProviderCooldown} /><br />
        {t('authSecurity.effectiveGeoIpRequestTimeout', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveGeoIpRequestTimeout} />
      </div>
    </section>
  )
}

export default AuthSecuritySummarySection
