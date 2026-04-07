import FormField from '@/shared/components/FormField'
import InfoHint from '@/shared/components/InfoHint'
import AuthSecurityHelpLinks from './AuthSecurityHelpLinks'

function AuthSecurityGeoIpSection({
  authSecuritySettings,
  authSecuritySettingsForm,
  availableProviders,
  canAddFallback,
  currentPrimaryProvider,
  fallbackInput,
  formatDurationHint,
  geoIpProviderCatalog,
  locale,
  onAddFallbackProvider,
  onAuthSecurityFormChange,
  onFallbackInputChange,
  onFallbackKeyDown,
  onPrimaryProviderChange,
  onRemoveFallbackProvider,
  providerIsSelectable,
  providerLabel,
  selectedFallbackProviders,
  t
}) {
  return (
    <section className="surface-card auth-security-settings-section">
      <div className="auth-security-section-header">
        <div className="auth-security-section-title">{t('authSecurity.geoIpProtectionTitle')}</div>
        <p className="auth-security-section-copy">{t('authSecurity.geoIpProtectionCopy')}</p>
      </div>
      <div className="muted-box">
        {t('authSecurity.geoIpProtectionExplanation')}
      </div>
      <div className="auth-security-nested-grid">
        <section className="surface-card auth-security-nested-card">
          <div className="auth-security-section-header">
            <div className="auth-security-section-title auth-security-section-title-small">{t('authSecurity.geoIpProviderChainTitle')}</div>
            <p className="auth-security-section-copy">{t('authSecurity.geoIpProviderChainCopy')}</p>
          </div>
          <div className="settings-grid auth-security-grid">
            <FormField helpText={t('authSecurity.geoIpModeHelp')} label={t('authSecurity.geoIpMode')}>
              <select
                value={authSecuritySettingsForm.geoIpMode}
                onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpMode: event.target.value }))}
              >
                <option value="DEFAULT">{authSecuritySettings.defaultGeoIpEnabled ? t('authSecurity.geoIpDefaultEnabled') : t('authSecurity.geoIpDefaultDisabled')}</option>
                <option value="ENABLED">{t('authSecurity.geoIpEnabledOverride')}</option>
                <option value="DISABLED">{t('authSecurity.geoIpDisabledOverride')}</option>
              </select>
            </FormField>
            <FormField
              helpText={t('authSecurity.geoIpPrimaryProviderHelp', { providers: authSecuritySettings.availableGeoIpProviders })}
              label={t('authSecurity.geoIpPrimaryProvider')}
            >
              <select
                value={authSecuritySettingsForm.geoIpPrimaryProviderOverride}
                onChange={(event) => onPrimaryProviderChange(event.target.value)}
              >
                <option value="">{t('system.leaveBlank', { value: providerLabel(authSecuritySettings.defaultGeoIpPrimaryProvider) })}</option>
                {availableProviders.map((providerId) => (
                  <option disabled={!providerIsSelectable(providerId)} key={providerId} value={providerId}>
                    {providerLabel(providerId)}
                  </option>
                ))}
              </select>
            </FormField>
            <div className="full">
              <span className="field-label-row">
                <span>{t('authSecurity.geoIpFallbackProviders')}</span>
                <InfoHint text={t('authSecurity.geoIpFallbackProvidersHelp', { providers: authSecuritySettings.availableGeoIpProviders })} />
              </span>
              <div className="provider-chip-input">
                <div className="provider-chip-list">
                  {selectedFallbackProviders.map((providerId) => (
                    <span className="provider-chip" key={providerId}>
                      {providerLabel(providerId)}
                      <button
                        aria-label={t('authSecurity.removeFallbackProvider', { provider: providerLabel(providerId) })}
                        onClick={() => onRemoveFallbackProvider(providerId)}
                        type="button"
                      >
                        ×
                      </button>
                    </span>
                  ))}
                  <input
                    aria-label={t('authSecurity.geoIpFallbackProviders')}
                    list="geo-ip-provider-options"
                    placeholder={selectedFallbackProviders.length ? t('authSecurity.geoIpFallbackProvidersPlaceholder') : t('system.leaveBlank', { value: authSecuritySettings.defaultGeoIpFallbackProviders })}
                    value={fallbackInput}
                    onChange={(event) => onFallbackInputChange(event.target.value)}
                    onKeyDown={onFallbackKeyDown}
                  />
                </div>
                <div className="auth-security-inline-actions">
                  <button className="secondary auth-security-add-provider-button" disabled={!canAddFallback} onClick={() => onAddFallbackProvider(fallbackInput)} type="button">
                    {t('authSecurity.addFallbackProvider')}
                  </button>
                </div>
                <datalist id="geo-ip-provider-options">
                  {availableProviders
                    .filter((providerId) => providerId !== currentPrimaryProvider && !selectedFallbackProviders.includes(providerId) && providerIsSelectable(providerId))
                    .map((providerId) => <option key={providerId} value={providerId}>{providerLabel(providerId)}</option>)}
                </datalist>
                <div className="field-help-text">{t('authSecurity.geoIpFallbackProvidersInputHelp')}</div>
              </div>
            </div>
          </div>
        </section>

        <section className="surface-card auth-security-nested-card">
          <div className="auth-security-section-header">
            <div className="auth-security-section-title auth-security-section-title-small">{t('authSecurity.geoIpTimingTitle')}</div>
            <p className="auth-security-section-copy">{t('authSecurity.geoIpTimingCopy')}</p>
          </div>
          <div className="settings-grid auth-security-grid">
            <div className="form-field-pair full">
              <FormField helpText={t('authSecurity.geoIpCacheTtlHelp')} label={t('authSecurity.geoIpCacheTtl')}>
                <input
                  placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultGeoIpCacheTtl })}
                  title={formatDurationHint(authSecuritySettingsForm.geoIpCacheTtlOverride || authSecuritySettings.defaultGeoIpCacheTtl, locale) || undefined}
                  value={authSecuritySettingsForm.geoIpCacheTtlOverride}
                  onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpCacheTtlOverride: event.target.value }))}
                />
              </FormField>
              <FormField helpText={t('authSecurity.geoIpProviderCooldownHelp')} label={t('authSecurity.geoIpProviderCooldown')}>
                <input
                  placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultGeoIpProviderCooldown })}
                  title={formatDurationHint(authSecuritySettingsForm.geoIpProviderCooldownOverride || authSecuritySettings.defaultGeoIpProviderCooldown, locale) || undefined}
                  value={authSecuritySettingsForm.geoIpProviderCooldownOverride}
                  onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpProviderCooldownOverride: event.target.value }))}
                />
              </FormField>
            </div>
            <FormField helpText={t('authSecurity.geoIpRequestTimeoutHelp')} label={t('authSecurity.geoIpRequestTimeout')}>
              <input
                placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultGeoIpRequestTimeout })}
                title={formatDurationHint(authSecuritySettingsForm.geoIpRequestTimeoutOverride || authSecuritySettings.defaultGeoIpRequestTimeout, locale) || undefined}
                value={authSecuritySettingsForm.geoIpRequestTimeoutOverride}
                onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpRequestTimeoutOverride: event.target.value }))}
              />
            </FormField>
          </div>
        </section>
      </div>

      <section className="surface-card auth-security-nested-card auth-security-provider-config-card">
        <div className="auth-security-section-header">
          <div className="auth-security-section-title auth-security-section-title-small">{t('authSecurity.geoIpProviderConfigurationTitle')}</div>
          <p className="auth-security-section-copy">{t('authSecurity.geoIpProviderConfigurationCopy')}</p>
        </div>
        <div className="auth-security-provider-grid">
          {availableProviders.map((providerId) => {
            const provider = geoIpProviderCatalog[providerId]
            const configured = providerIsSelectable(providerId)

            return (
              <article className="surface-card auth-security-provider-card" key={providerId}>
                <div className="auth-security-provider-card-header">
                  <div>
                    <div className="auth-security-provider-title">{provider.label}</div>
                    <p className="auth-security-provider-copy">
                      {provider.requiresToken ? t('authSecurity.providerRequiresToken') : t('authSecurity.providerNoConfigurationRequired')}
                    </p>
                  </div>
                  <span className={`status-pill ${configured ? 'status-ok' : 'status-warn'}`}>
                    {configured ? t('authSecurity.providerReady') : t('authSecurity.providerNeedsConfiguration')}
                  </span>
                </div>
                {provider.id === 'IPINFO_LITE' ? (
                  <FormField helpText={t('authSecurity.ipinfoLiteTokenHelp')} label={t('authSecurity.ipinfoLiteToken')}>
                    <>
                      <input
                        disabled={!authSecuritySettings.secureStorageConfigured}
                        placeholder={authSecuritySettings.geoIpIpinfoTokenConfigured ? t('authSecurity.secretAlreadyConfigured') : t('authSecurity.ipinfoLiteTokenPlaceholder')}
                        type="password"
                        value={authSecuritySettingsForm.geoIpIpinfoToken}
                        onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpIpinfoToken: event.target.value }))}
                      />
                      {!authSecuritySettings.secureStorageConfigured ? <div className="field-help-text">{t('authSecurity.secureStorageRequiredForProviderSecrets')}</div> : null}
                    </>
                  </FormField>
                ) : null}
                <AuthSecurityHelpLinks docsUrl={provider.docsUrl} termsUrl={provider.termsUrl} t={t} />
              </article>
            )
          })}
        </div>
      </section>
    </section>
  )
}

export default AuthSecurityGeoIpSection
