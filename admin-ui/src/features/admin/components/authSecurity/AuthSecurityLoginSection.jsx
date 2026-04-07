import FormField from '@/shared/components/FormField'

function AuthSecurityLoginSection({
  authSecuritySettings,
  authSecuritySettingsForm,
  locale,
  onAuthSecurityFormChange,
  formatDurationHint,
  t
}) {
  return (
    <section className="surface-card auth-security-settings-section">
      <div className="auth-security-section-header">
        <div className="auth-security-section-title">{t('authSecurity.loginProtectionTitle')}</div>
        <p className="auth-security-section-copy">{t('authSecurity.loginProtectionCopy')}</p>
      </div>
      <div className="settings-grid auth-security-grid">
        <FormField helpText={t('authSecurity.failedAttemptsHelp')} label={t('authSecurity.failedAttempts')}>
          <input
            max="50"
            min="1"
            placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultLoginFailureThreshold })}
            type="number"
            value={authSecuritySettingsForm.loginFailureThresholdOverride}
            onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, loginFailureThresholdOverride: event.target.value }))}
          />
        </FormField>
        <div className="form-field-pair full">
          <FormField helpText={t('authSecurity.initialBlockHelp')} label={t('authSecurity.initialBlock')}>
            <input
              placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultLoginInitialBlock })}
              title={formatDurationHint(authSecuritySettingsForm.loginInitialBlockOverride || authSecuritySettings.defaultLoginInitialBlock, locale) || undefined}
              value={authSecuritySettingsForm.loginInitialBlockOverride}
              onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, loginInitialBlockOverride: event.target.value }))}
            />
          </FormField>
          <FormField helpText={t('authSecurity.maxBlockHelp')} label={t('authSecurity.maxBlock')}>
            <input
              placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultLoginMaxBlock })}
              title={formatDurationHint(authSecuritySettingsForm.loginMaxBlockOverride || authSecuritySettings.defaultLoginMaxBlock, locale) || undefined}
              value={authSecuritySettingsForm.loginMaxBlockOverride}
              onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, loginMaxBlockOverride: event.target.value }))}
            />
          </FormField>
        </div>
      </div>
    </section>
  )
}

export default AuthSecurityLoginSection
