import FormField from '../../common/FormField'
import AuthSecurityHelpLinks from './AuthSecurityHelpLinks'

function AuthSecurityRegistrationSection({
  authSecuritySettings,
  authSecuritySettingsForm,
  availableRegistrationCaptchaProviders,
  captchaProviderIsSelectable,
  locale,
  onAuthSecurityFormChange,
  formatDurationHint,
  registrationCaptchaProviderCatalog,
  t
}) {
  return (
    <section className="surface-card auth-security-settings-section">
      <div className="auth-security-section-header">
        <div className="auth-security-section-title">{t('authSecurity.registrationProtectionTitle')}</div>
        <p className="auth-security-section-copy">{t('authSecurity.registrationProtectionCopy')}</p>
      </div>
      <div className="auth-security-nested-grid">
        <section className="surface-card auth-security-nested-card">
          <div className="auth-security-section-header">
            <div className="auth-security-section-title auth-security-section-title-small">{t('authSecurity.registrationRuntimeTitle')}</div>
            <p className="auth-security-section-copy">{t('authSecurity.registrationRuntimeCopy')}</p>
          </div>
          <div className="settings-grid auth-security-grid">
            <FormField helpText={t('authSecurity.registrationChallengeModeHelp')} label={t('authSecurity.registrationChallengeMode')}>
              <select
                value={authSecuritySettingsForm.registrationChallengeMode}
                onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationChallengeMode: event.target.value }))}
              >
                <option value="DEFAULT">{authSecuritySettings.defaultRegistrationChallengeEnabled ? t('authSecurity.challengeDefaultEnabled') : t('authSecurity.challengeDefaultDisabled')}</option>
                <option value="ENABLED">{t('authSecurity.challengeEnabledOverride')}</option>
                <option value="DISABLED">{t('authSecurity.challengeDisabledOverride')}</option>
              </select>
            </FormField>
            <FormField helpText={t('authSecurity.registrationChallengeTtlHelp')} label={t('authSecurity.registrationChallengeTtl')}>
              <input
                placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultRegistrationChallengeTtl })}
                title={formatDurationHint(authSecuritySettingsForm.registrationChallengeTtlOverride || authSecuritySettings.defaultRegistrationChallengeTtl, locale) || undefined}
                value={authSecuritySettingsForm.registrationChallengeTtlOverride}
                onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationChallengeTtlOverride: event.target.value }))}
              />
            </FormField>
          </div>
        </section>

        <section className="surface-card auth-security-nested-card">
          <div className="auth-security-section-header">
            <div className="auth-security-section-title auth-security-section-title-small">{t('authSecurity.registrationProviderTitle')}</div>
            <p className="auth-security-section-copy">{t('authSecurity.registrationProviderCopy')}</p>
          </div>
          <div className="settings-grid auth-security-grid">
            <FormField helpText={t('authSecurity.registrationChallengeProviderHelp')} label={t('authSecurity.registrationChallengeProvider')}>
              <select
                value={authSecuritySettingsForm.registrationChallengeProviderOverride}
                onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationChallengeProviderOverride: event.target.value }))}
              >
                <option value="">{t('system.leaveBlank', { value: authSecuritySettings.defaultRegistrationChallengeProviderLabel })}</option>
                {availableRegistrationCaptchaProviders.map((providerId) => (
                  <option disabled={!captchaProviderIsSelectable(providerId)} key={providerId} value={providerId}>
                    {registrationCaptchaProviderCatalog[providerId].label}
                  </option>
                ))}
              </select>
            </FormField>
          </div>
        </section>
      </div>

      <section className="surface-card auth-security-nested-card auth-security-provider-config-card">
        <div className="auth-security-section-header">
          <div className="auth-security-section-title auth-security-section-title-small">{t('authSecurity.registrationProviderConfigurationTitle')}</div>
          <p className="auth-security-section-copy">{t('authSecurity.registrationProviderConfigurationCopy')}</p>
        </div>
        <div className="auth-security-provider-grid">
          {availableRegistrationCaptchaProviders.map((providerId) => {
            const provider = registrationCaptchaProviderCatalog[providerId]
            const configured = captchaProviderIsSelectable(providerId)

            return (
              <article className="surface-card auth-security-provider-card" key={providerId}>
                <div className="auth-security-provider-card-header">
                  <div>
                    <div className="auth-security-provider-title">{provider.label}</div>
                    <p className="auth-security-provider-copy">
                      {providerId === 'ALTCHA'
                        ? t('authSecurity.registrationProviderNoConfigurationRequired')
                        : t('authSecurity.registrationProviderRequiresConfiguration')}
                    </p>
                  </div>
                  <span className={`status-pill ${configured ? 'status-ok' : 'status-warn'}`}>
                    {configured ? t('authSecurity.providerReady') : t('authSecurity.providerNeedsConfiguration')}
                  </span>
                </div>
                {providerId === 'TURNSTILE' ? (
                  <>
                    <FormField helpText={t('authSecurity.turnstileSiteKeyHelp')} label={t('authSecurity.turnstileSiteKey')}>
                      <input
                        placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultRegistrationTurnstileSiteKey || t('common.unavailable') })}
                        value={authSecuritySettingsForm.registrationTurnstileSiteKeyOverride}
                        onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationTurnstileSiteKeyOverride: event.target.value }))}
                      />
                    </FormField>
                    <FormField helpText={t('authSecurity.turnstileSecretHelp')} label={t('authSecurity.turnstileSecret')}>
                      <>
                        <input
                          disabled={!authSecuritySettings.secureStorageConfigured}
                          placeholder={authSecuritySettings.registrationTurnstileConfigured ? t('authSecurity.secretAlreadyConfigured') : t('authSecurity.turnstileSecretPlaceholder')}
                          type="password"
                          value={authSecuritySettingsForm.registrationTurnstileSecret}
                          onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationTurnstileSecret: event.target.value }))}
                        />
                        {!authSecuritySettings.secureStorageConfigured ? <div className="field-help-text">{t('authSecurity.secureStorageRequiredForProviderSecrets')}</div> : null}
                      </>
                    </FormField>
                  </>
                ) : null}
                {providerId === 'HCAPTCHA' ? (
                  <>
                    <FormField helpText={t('authSecurity.hcaptchaSiteKeyHelp')} label={t('authSecurity.hcaptchaSiteKey')}>
                      <input
                        placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultRegistrationHcaptchaSiteKey || t('common.unavailable') })}
                        value={authSecuritySettingsForm.registrationHcaptchaSiteKeyOverride}
                        onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationHcaptchaSiteKeyOverride: event.target.value }))}
                      />
                    </FormField>
                    <FormField helpText={t('authSecurity.hcaptchaSecretHelp')} label={t('authSecurity.hcaptchaSecret')}>
                      <>
                        <input
                          disabled={!authSecuritySettings.secureStorageConfigured}
                          placeholder={authSecuritySettings.registrationHcaptchaConfigured ? t('authSecurity.secretAlreadyConfigured') : t('authSecurity.hcaptchaSecretPlaceholder')}
                          type="password"
                          value={authSecuritySettingsForm.registrationHcaptchaSecret}
                          onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationHcaptchaSecret: event.target.value }))}
                        />
                        {!authSecuritySettings.secureStorageConfigured ? <div className="field-help-text">{t('authSecurity.secureStorageRequiredForProviderSecrets')}</div> : null}
                      </>
                    </FormField>
                  </>
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

export default AuthSecurityRegistrationSection
