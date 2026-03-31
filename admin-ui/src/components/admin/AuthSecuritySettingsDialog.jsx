import { useMemo, useState } from 'react'
import InfoHint from '../common/InfoHint'
import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'
import DurationValue from '../common/DurationValue'
import { formatDurationHint } from '../../lib/formatters'
import { geoIpProviderCatalog, parseProviderList, providerLabel } from '../../lib/geoIpProviders'
import { captchaProviderLabel, parseCaptchaProviderList, registrationCaptchaProviderCatalog } from '../../lib/captchaProviders'
import './AuthSecuritySettingsDialog.css'

function AuthSecuritySettingsDialog({
  authSecuritySettings,
  authSecuritySettingsForm,
  authSecuritySettingsLoading,
  isDirty,
  locale = 'en',
  onAuthSecurityFormChange,
  onClose,
  onResetAuthSecuritySettings,
  onSaveAuthSecuritySettings,
  t
}) {
  const [fallbackInput, setFallbackInput] = useState('')
  const availableProviders = useMemo(
    () => parseProviderList(authSecuritySettings.availableGeoIpProviders),
    [authSecuritySettings.availableGeoIpProviders]
  )
  const selectedFallbackProviders = useMemo(
    () => parseProviderList(authSecuritySettingsForm.geoIpFallbackProvidersOverride),
    [authSecuritySettingsForm.geoIpFallbackProvidersOverride]
  )
  const availableRegistrationCaptchaProviders = useMemo(
    () => parseCaptchaProviderList(authSecuritySettings.availableRegistrationCaptchaProviders),
    [authSecuritySettings.availableRegistrationCaptchaProviders]
  )
  const currentPrimaryProvider = authSecuritySettingsForm.geoIpPrimaryProviderOverride || authSecuritySettings.defaultGeoIpPrimaryProvider
  const ipinfoConfigured = authSecuritySettings.geoIpIpinfoTokenConfigured || Boolean(authSecuritySettingsForm.geoIpIpinfoToken?.trim())
  const turnstileConfigured = Boolean(
    (authSecuritySettings.registrationTurnstileConfigured || authSecuritySettingsForm.registrationTurnstileSecret?.trim())
    && (authSecuritySettingsForm.registrationTurnstileSiteKeyOverride?.trim() || authSecuritySettings.registrationTurnstileSiteKeyOverride || authSecuritySettings.defaultRegistrationTurnstileSiteKey)
  )
  const hcaptchaConfigured = Boolean(
    (authSecuritySettings.registrationHcaptchaConfigured || authSecuritySettingsForm.registrationHcaptchaSecret?.trim())
    && (authSecuritySettingsForm.registrationHcaptchaSiteKeyOverride?.trim() || authSecuritySettings.registrationHcaptchaSiteKeyOverride || authSecuritySettings.defaultRegistrationHcaptchaSiteKey)
  )
  const canAddFallback = Boolean(
    fallbackInput?.trim()
    && fallbackInput.trim().toUpperCase() !== currentPrimaryProvider
    && !selectedFallbackProviders.includes(fallbackInput.trim().toUpperCase())
    && providerIsSelectable(fallbackInput.trim().toUpperCase())
  )

  function providerIsSelectable(providerId) {
    const provider = geoIpProviderCatalog[providerId]
    if (!provider) {
      return false
    }
    if (!provider.requiresToken) {
      return true
    }
    return ipinfoConfigured
  }

  function captchaProviderIsSelectable(providerId) {
    const provider = registrationCaptchaProviderCatalog[providerId]
    if (!provider) {
      return false
    }
    if (providerId === 'ALTCHA') {
      return true
    }
    if (providerId === 'TURNSTILE') {
      return turnstileConfigured
    }
    if (providerId === 'HCAPTCHA') {
      return hcaptchaConfigured
    }
    return false
  }

  function addFallbackProvider(providerId) {
    const normalized = providerId?.trim().toUpperCase()
    if (!normalized || normalized === currentPrimaryProvider || selectedFallbackProviders.includes(normalized) || !providerIsSelectable(normalized)) {
      return
    }
    const nextProviders = [...selectedFallbackProviders, normalized]
    onAuthSecurityFormChange((current) => ({
      ...current,
      geoIpFallbackProvidersOverride: nextProviders.join(',')
    }))
    setFallbackInput('')
  }

  function handlePrimaryProviderChange(providerId) {
    onAuthSecurityFormChange((current) => ({
      ...current,
      geoIpPrimaryProviderOverride: providerId,
      geoIpFallbackProvidersOverride: selectedFallbackProviders
        .filter((item) => item !== providerId)
        .join(',')
    }))
  }

  function removeFallbackProvider(providerId) {
    onAuthSecurityFormChange((current) => ({
      ...current,
      geoIpFallbackProvidersOverride: selectedFallbackProviders.filter((item) => item !== providerId).join(',')
    }))
  }

  function handleFallbackKeyDown(event) {
    if (event.key !== 'Enter' && event.key !== ',') {
      return
    }
    event.preventDefault()
    addFallbackProvider(fallbackInput)
  }

  function helpLink(labelKey, href) {
    return (
      <div className="auth-security-provider-links">
        <a href={href} rel="noreferrer" target="_blank">{t(labelKey)}</a>
      </div>
    )
  }

  return (
    <ModalDialog
      closeLabel={t('common.closeDialog')}
      isDirty={isDirty}
      onClose={onClose}
      size="wide"
      title={t('authSecurity.editTitle')}
      unsavedChangesMessage={t('dialogs.unsavedChanges')}
    >
      <form className="auth-security-form" onSubmit={onSaveAuthSecuritySettings}>
        <div className="auth-security-sections">
          <section className="surface-card auth-security-settings-section">
            <div className="auth-security-section-header">
              <div className="auth-security-section-title">{t('authSecurity.runtimeSectionTitle')}</div>
              <p className="auth-security-section-copy">{t('authSecurity.summaryHelp')}</p>
            </div>
            <div className="muted-box">
              <strong>{t('authSecurity.protectionSectionTitle')}</strong><br />
              {t('authSecurity.protectionSectionCopy')}<br />
              {t('authSecurity.protectionExplanation')}
            </div>
          </section>

          <section className="surface-card auth-security-settings-section">
            <div className="auth-security-section-header">
              <div className="auth-security-section-title">{t('authSecurity.loginProtectionTitle')}</div>
              <p className="auth-security-section-copy">{t('authSecurity.loginProtectionCopy')}</p>
            </div>
            <div className="settings-grid auth-security-grid">
              <label>
                <span className="field-label-row">
                  <span>{t('authSecurity.failedAttempts')}</span>
                  <InfoHint text={t('authSecurity.failedAttemptsHelp')} />
                </span>
                <input
                  max="50"
                  min="1"
                  placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultLoginFailureThreshold })}
                  type="number"
                  value={authSecuritySettingsForm.loginFailureThresholdOverride}
                  onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, loginFailureThresholdOverride: event.target.value }))}
                />
              </label>
              <div className="form-field-pair full">
                <label>
                  <span className="field-label-row">
                    <span>{t('authSecurity.initialBlock')}</span>
                    <InfoHint text={t('authSecurity.initialBlockHelp')} />
                  </span>
                  <input
                    placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultLoginInitialBlock })}
                    title={formatDurationHint(authSecuritySettingsForm.loginInitialBlockOverride || authSecuritySettings.defaultLoginInitialBlock, locale) || undefined}
                    value={authSecuritySettingsForm.loginInitialBlockOverride}
                    onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, loginInitialBlockOverride: event.target.value }))}
                  />
                </label>
                <label>
                  <span className="field-label-row">
                    <span>{t('authSecurity.maxBlock')}</span>
                    <InfoHint text={t('authSecurity.maxBlockHelp')} />
                  </span>
                  <input
                    placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultLoginMaxBlock })}
                    title={formatDurationHint(authSecuritySettingsForm.loginMaxBlockOverride || authSecuritySettings.defaultLoginMaxBlock, locale) || undefined}
                    value={authSecuritySettingsForm.loginMaxBlockOverride}
                    onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, loginMaxBlockOverride: event.target.value }))}
                  />
                </label>
              </div>
            </div>
          </section>

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
                  <label>
                    <span className="field-label-row">
                      <span>{t('authSecurity.registrationChallengeMode')}</span>
                      <InfoHint text={t('authSecurity.registrationChallengeModeHelp')} />
                    </span>
                    <select
                      value={authSecuritySettingsForm.registrationChallengeMode}
                      onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationChallengeMode: event.target.value }))}
                    >
                      <option value="DEFAULT">{authSecuritySettings.defaultRegistrationChallengeEnabled ? t('authSecurity.challengeDefaultEnabled') : t('authSecurity.challengeDefaultDisabled')}</option>
                      <option value="ENABLED">{t('authSecurity.challengeEnabledOverride')}</option>
                      <option value="DISABLED">{t('authSecurity.challengeDisabledOverride')}</option>
                    </select>
                  </label>
                  <label>
                    <span className="field-label-row">
                      <span>{t('authSecurity.registrationChallengeTtl')}</span>
                      <InfoHint text={t('authSecurity.registrationChallengeTtlHelp')} />
                    </span>
                    <input
                      placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultRegistrationChallengeTtl })}
                      title={formatDurationHint(authSecuritySettingsForm.registrationChallengeTtlOverride || authSecuritySettings.defaultRegistrationChallengeTtl, locale) || undefined}
                      value={authSecuritySettingsForm.registrationChallengeTtlOverride}
                      onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationChallengeTtlOverride: event.target.value }))}
                    />
                  </label>
                </div>
              </section>

              <section className="surface-card auth-security-nested-card">
                <div className="auth-security-section-header">
                  <div className="auth-security-section-title auth-security-section-title-small">{t('authSecurity.registrationProviderTitle')}</div>
                  <p className="auth-security-section-copy">{t('authSecurity.registrationProviderCopy')}</p>
                </div>
                <div className="settings-grid auth-security-grid">
                  <label>
                    <span className="field-label-row">
                      <span>{t('authSecurity.registrationChallengeProvider')}</span>
                      <InfoHint text={t('authSecurity.registrationChallengeProviderHelp')} />
                    </span>
                    <select
                      value={authSecuritySettingsForm.registrationChallengeProviderOverride}
                      onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationChallengeProviderOverride: event.target.value }))}
                    >
                      <option value="">{t('system.leaveBlank', { value: captchaProviderLabel(authSecuritySettings.defaultRegistrationChallengeProvider) })}</option>
                      {availableRegistrationCaptchaProviders.map((providerId) => (
                        <option disabled={!captchaProviderIsSelectable(providerId)} key={providerId} value={providerId}>
                          {captchaProviderLabel(providerId)}
                        </option>
                      ))}
                    </select>
                  </label>
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
                          <label>
                            <span className="field-label-row">
                              <span>{t('authSecurity.turnstileSiteKey')}</span>
                              <InfoHint text={t('authSecurity.turnstileSiteKeyHelp')} />
                            </span>
                            <input
                              placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultRegistrationTurnstileSiteKey || t('common.unavailable') })}
                              value={authSecuritySettingsForm.registrationTurnstileSiteKeyOverride}
                              onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationTurnstileSiteKeyOverride: event.target.value }))}
                            />
                          </label>
                          <label>
                            <span className="field-label-row">
                              <span>{t('authSecurity.turnstileSecret')}</span>
                              <InfoHint text={t('authSecurity.turnstileSecretHelp')} />
                            </span>
                            <input
                              disabled={!authSecuritySettings.secureStorageConfigured}
                              placeholder={authSecuritySettings.registrationTurnstileConfigured ? t('authSecurity.secretAlreadyConfigured') : t('authSecurity.turnstileSecretPlaceholder')}
                              type="password"
                              value={authSecuritySettingsForm.registrationTurnstileSecret}
                              onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationTurnstileSecret: event.target.value }))}
                            />
                            {!authSecuritySettings.secureStorageConfigured ? <div className="field-help-text">{t('authSecurity.secureStorageRequiredForProviderSecrets')}</div> : null}
                          </label>
                        </>
                      ) : null}
                      {providerId === 'HCAPTCHA' ? (
                        <>
                          <label>
                            <span className="field-label-row">
                              <span>{t('authSecurity.hcaptchaSiteKey')}</span>
                              <InfoHint text={t('authSecurity.hcaptchaSiteKeyHelp')} />
                            </span>
                            <input
                              placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultRegistrationHcaptchaSiteKey || t('common.unavailable') })}
                              value={authSecuritySettingsForm.registrationHcaptchaSiteKeyOverride}
                              onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationHcaptchaSiteKeyOverride: event.target.value }))}
                            />
                          </label>
                          <label>
                            <span className="field-label-row">
                              <span>{t('authSecurity.hcaptchaSecret')}</span>
                              <InfoHint text={t('authSecurity.hcaptchaSecretHelp')} />
                            </span>
                            <input
                              disabled={!authSecuritySettings.secureStorageConfigured}
                              placeholder={authSecuritySettings.registrationHcaptchaConfigured ? t('authSecurity.secretAlreadyConfigured') : t('authSecurity.hcaptchaSecretPlaceholder')}
                              type="password"
                              value={authSecuritySettingsForm.registrationHcaptchaSecret}
                              onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, registrationHcaptchaSecret: event.target.value }))}
                            />
                            {!authSecuritySettings.secureStorageConfigured ? <div className="field-help-text">{t('authSecurity.secureStorageRequiredForProviderSecrets')}</div> : null}
                          </label>
                        </>
                      ) : null}
                      {helpLink('authSecurity.providerDocsLink', provider.docsUrl)}
                      {helpLink('authSecurity.providerTermsLink', provider.termsUrl)}
                    </article>
                  )
                })}
              </div>
            </section>
          </section>

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
                  <label>
                    <span className="field-label-row">
                      <span>{t('authSecurity.geoIpMode')}</span>
                      <InfoHint text={t('authSecurity.geoIpModeHelp')} />
                    </span>
                    <select
                      value={authSecuritySettingsForm.geoIpMode}
                      onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpMode: event.target.value }))}
                    >
                      <option value="DEFAULT">{authSecuritySettings.defaultGeoIpEnabled ? t('authSecurity.geoIpDefaultEnabled') : t('authSecurity.geoIpDefaultDisabled')}</option>
                      <option value="ENABLED">{t('authSecurity.geoIpEnabledOverride')}</option>
                      <option value="DISABLED">{t('authSecurity.geoIpDisabledOverride')}</option>
                    </select>
                  </label>
                  <label>
                    <span className="field-label-row">
                      <span>{t('authSecurity.geoIpPrimaryProvider')}</span>
                      <InfoHint text={t('authSecurity.geoIpPrimaryProviderHelp', { providers: authSecuritySettings.availableGeoIpProviders })} />
                    </span>
                    <select
                      value={authSecuritySettingsForm.geoIpPrimaryProviderOverride}
                      onChange={(event) => handlePrimaryProviderChange(event.target.value)}
                    >
                      <option value="">{t('system.leaveBlank', { value: providerLabel(authSecuritySettings.defaultGeoIpPrimaryProvider) })}</option>
                      {availableProviders.map((providerId) => (
                        <option disabled={!providerIsSelectable(providerId)} key={providerId} value={providerId}>
                          {providerLabel(providerId)}
                        </option>
                      ))}
                    </select>
                  </label>
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
                            <button aria-label={t('authSecurity.removeFallbackProvider', { provider: providerLabel(providerId) })} onClick={() => removeFallbackProvider(providerId)} type="button">×</button>
                          </span>
                        ))}
                        <input
                          aria-label={t('authSecurity.geoIpFallbackProviders')}
                          list="geo-ip-provider-options"
                          placeholder={selectedFallbackProviders.length ? t('authSecurity.geoIpFallbackProvidersPlaceholder') : t('system.leaveBlank', { value: authSecuritySettings.defaultGeoIpFallbackProviders })}
                          value={fallbackInput}
                          onChange={(event) => setFallbackInput(event.target.value)}
                          onKeyDown={handleFallbackKeyDown}
                        />
                      </div>
                      <div className="auth-security-inline-actions">
                        <button className="secondary auth-security-add-provider-button" disabled={!canAddFallback} onClick={() => addFallbackProvider(fallbackInput)} type="button">
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
                    <label>
                      <span className="field-label-row">
                        <span>{t('authSecurity.geoIpCacheTtl')}</span>
                        <InfoHint text={t('authSecurity.geoIpCacheTtlHelp')} />
                      </span>
                      <input
                        placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultGeoIpCacheTtl })}
                        title={formatDurationHint(authSecuritySettingsForm.geoIpCacheTtlOverride || authSecuritySettings.defaultGeoIpCacheTtl, locale) || undefined}
                        value={authSecuritySettingsForm.geoIpCacheTtlOverride}
                        onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpCacheTtlOverride: event.target.value }))}
                      />
                    </label>
                    <label>
                      <span className="field-label-row">
                        <span>{t('authSecurity.geoIpProviderCooldown')}</span>
                        <InfoHint text={t('authSecurity.geoIpProviderCooldownHelp')} />
                      </span>
                      <input
                        placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultGeoIpProviderCooldown })}
                        title={formatDurationHint(authSecuritySettingsForm.geoIpProviderCooldownOverride || authSecuritySettings.defaultGeoIpProviderCooldown, locale) || undefined}
                        value={authSecuritySettingsForm.geoIpProviderCooldownOverride}
                        onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpProviderCooldownOverride: event.target.value }))}
                      />
                    </label>
                  </div>
                  <label>
                    <span className="field-label-row">
                      <span>{t('authSecurity.geoIpRequestTimeout')}</span>
                      <InfoHint text={t('authSecurity.geoIpRequestTimeoutHelp')} />
                    </span>
                    <input
                      placeholder={t('system.leaveBlank', { value: authSecuritySettings.defaultGeoIpRequestTimeout })}
                      title={formatDurationHint(authSecuritySettingsForm.geoIpRequestTimeoutOverride || authSecuritySettings.defaultGeoIpRequestTimeout, locale) || undefined}
                      value={authSecuritySettingsForm.geoIpRequestTimeoutOverride}
                      onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpRequestTimeoutOverride: event.target.value }))}
                    />
                  </label>
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
                const configured = provider.requiresToken ? ipinfoConfigured : true
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
                      <label>
                        <span className="field-label-row">
                          <span>{t('authSecurity.ipinfoLiteToken')}</span>
                          <InfoHint text={t('authSecurity.ipinfoLiteTokenHelp')} />
                        </span>
                        <input
                          disabled={!authSecuritySettings.secureStorageConfigured}
                          placeholder={authSecuritySettings.geoIpIpinfoTokenConfigured ? t('authSecurity.secretAlreadyConfigured') : t('authSecurity.ipinfoLiteTokenPlaceholder')}
                          type="password"
                          value={authSecuritySettingsForm.geoIpIpinfoToken}
                          onChange={(event) => onAuthSecurityFormChange((current) => ({ ...current, geoIpIpinfoToken: event.target.value }))}
                        />
                        {!authSecuritySettings.secureStorageConfigured ? <div className="field-help-text">{t('authSecurity.secureStorageRequiredForProviderSecrets')}</div> : null}
                      </label>
                    ) : null}
                    {helpLink('authSecurity.providerDocsLink', provider.docsUrl)}
                    {helpLink('authSecurity.providerTermsLink', provider.termsUrl)}
                  </article>
                )
              })}
              </div>
            </section>
          </section>

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
              {t('authSecurity.effectiveRegistrationChallengeProvider', { value: captchaProviderLabel(authSecuritySettings.effectiveRegistrationChallengeProvider) })}<br />
              {t('authSecurity.effectiveGeoIpMode', { value: t(authSecuritySettings.effectiveGeoIpEnabled ? 'common.enabled' : 'common.disabled') })}<br />
              {t('authSecurity.effectiveGeoIpPrimaryProvider', { value: providerLabel(authSecuritySettings.effectiveGeoIpPrimaryProvider) })}<br />
              {t('authSecurity.effectiveGeoIpFallbackProviders', { value: parseProviderList(authSecuritySettings.effectiveGeoIpFallbackProviders).map(providerLabel).join(', ') || t('common.unavailable') })}<br />
              {t('authSecurity.effectiveGeoIpCacheTtl', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveGeoIpCacheTtl} /><br />
              {t('authSecurity.effectiveGeoIpProviderCooldown', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveGeoIpProviderCooldown} /><br />
              {t('authSecurity.effectiveGeoIpRequestTimeout', { value: '' })}<DurationValue locale={locale} value={authSecuritySettings.effectiveGeoIpRequestTimeout} />
            </div>
          </section>
        </div>
        <div className="action-row full">
          <LoadingButton className="primary" isLoading={authSecuritySettingsLoading} loadingLabel={t('authSecurity.saveLoading')} type="submit">
            {t('authSecurity.save')}
          </LoadingButton>
          <LoadingButton className="secondary" isLoading={authSecuritySettingsLoading} loadingLabel={t('authSecurity.resetLoading')} onClick={onResetAuthSecuritySettings} type="button">
            {t('authSecurity.useEnvDefaults')}
          </LoadingButton>
        </div>
      </form>
    </ModalDialog>
  )
}

export default AuthSecuritySettingsDialog
