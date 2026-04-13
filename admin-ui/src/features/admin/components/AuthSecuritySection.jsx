import LoadingButton from '@/shared/components/LoadingButton'
import CollapsibleSection from '@/shared/components/CollapsibleSection'
import DurationValue from '@/shared/components/DurationValue'
import { captchaProviderLabel } from '@/lib/captchaProviders'
import { parseProviderList, providerLabel } from '@/lib/geoIpProviders'

function formatKeyIds(keyIds, t) {
  return Array.isArray(keyIds) && keyIds.length > 0 ? keyIds.join(', ') : t('authSecurity.secretManagementNoLegacyKeys')
}

function formatKeyUsageAreas(areas, t) {
  return String(areas || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
    .map((value) => {
      const translationKey = `authSecurity.secretArea.${value}`
      const translated = t(translationKey)
      return translated === translationKey ? value : translated
    })
    .join(', ')
}

function AuthSecuritySection({
  authSecuritySettings,
  collapsed,
  collapseLoading,
  onCollapseToggle,
  onOpenEditor,
  onReencryptStoredSecrets,
  reencryptionLoading = false,
  sectionLoading = false,
  secretManagementStatus,
  locale = 'en',
  t
}) {
  const keyUsage = Array.isArray(secretManagementStatus?.keyUsage) ? secretManagementStatus.keyUsage : []

  return (
    <CollapsibleSection
      actions={
        <div className="action-row">
          <LoadingButton className="secondary" onClick={onOpenEditor} type="button">
            {t('authSecurity.edit')}
          </LoadingButton>
        </div>
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

          <article className="surface-card polling-statistics-card">
            <div className="polling-statistics-card-title">{t('authSecurity.secretManagementTitle')}</div>
            <p className="section-copy">{t('authSecurity.secretManagementCopy')}</p>
            <div className="polling-statistics-breakdown">
              <div><span>{t('authSecurity.secretManagementMode')}</span><strong>{secretManagementStatus?.mode || t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.secretManagementProvider')}</span><strong>{secretManagementStatus?.providerId || t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.secretManagementActiveKey')}</span><strong>{secretManagementStatus?.activeKeyId || secretManagementStatus?.activeKeyVersion || t('common.unavailable')}</strong></div>
              <div><span>{t('authSecurity.secretManagementProtectedRecords')}</span><strong>{secretManagementStatus?.protectedRecordCount ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementNonActiveRecords')}</span><strong>{secretManagementStatus?.nonActiveKeyRecordCount ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementUnavailableRecords')}</span><strong>{secretManagementStatus?.unavailableKeyRecordCount ?? 0}</strong></div>
              <div><span>{t('authSecurity.secretManagementLegacyKeys')}</span><strong>{formatKeyIds(secretManagementStatus?.configuredLegacyKeyIds, t)}</strong></div>
              <div><span>{t('authSecurity.secretManagementRetirementStatus')}</span><strong>{t(secretManagementStatus?.safeToRetireLegacyKeys ? 'authSecurity.secretManagementSafeToRetire' : 'authSecurity.secretManagementKeepLegacyKeys')}</strong></div>
            </div>
            <p className="section-copy">{t('authSecurity.secretManagementReencryptHelp')}</p>
            <LoadingButton
              className="secondary"
              disabled={!secretManagementStatus?.secureStorageConfigured}
              onClick={onReencryptStoredSecrets}
              type="button"
            >
              {reencryptionLoading ? t('authSecurity.secretManagementReencryptLoading') : t('authSecurity.secretManagementReencrypt')}
            </LoadingButton>
          </article>

          <article className="surface-card polling-statistics-card">
            <div className="polling-statistics-card-title">{t('authSecurity.secretManagementKeyUsageTitle')}</div>
            {keyUsage.length === 0 ? (
              <p className="section-copy">{t('authSecurity.secretManagementKeyUsageEmpty')}</p>
            ) : (
              <div className="polling-statistics-breakdown">
                {keyUsage.map((usage) => (
                  <div key={usage.keyVersion}>
                    <span>
                      {usage.keyVersion}
                      {' · '}
                      {formatKeyUsageAreas(usage.areas, t)}
                    </span>
                    <strong>
                      {t(usage.availableForDecryption ? 'authSecurity.secretManagementKeyAvailable' : 'authSecurity.secretManagementKeyUnavailable')}
                      {' · '}
                      {t('authSecurity.secretManagementKeyRecordCount', { count: usage.recordCount })}
                    </strong>
                  </div>
                ))}
              </div>
            )}
          </article>
        </div>
    </CollapsibleSection>
  )
}

export default AuthSecuritySection
