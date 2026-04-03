import { useMemo, useState } from 'react'
import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'
import { formatDurationHint } from '../../lib/formatters'
import { geoIpProviderCatalog, parseProviderList, providerLabel } from '../../lib/geoIpProviders'
import { captchaProviderLabel, parseCaptchaProviderList, registrationCaptchaProviderCatalog } from '../../lib/captchaProviders'
import AuthSecurityGeoIpSection from './authSecurity/AuthSecurityGeoIpSection'
import AuthSecurityLoginSection from './authSecurity/AuthSecurityLoginSection'
import AuthSecurityRegistrationSection from './authSecurity/AuthSecurityRegistrationSection'
import AuthSecuritySummarySection from './authSecurity/AuthSecuritySummarySection'
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
  const defaultRegistrationChallengeProviderLabel = captchaProviderLabel(authSecuritySettings.defaultRegistrationChallengeProvider)
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

          <AuthSecurityLoginSection
            authSecuritySettings={authSecuritySettings}
            authSecuritySettingsForm={authSecuritySettingsForm}
            formatDurationHint={formatDurationHint}
            locale={locale}
            onAuthSecurityFormChange={onAuthSecurityFormChange}
            t={t}
          />

          <AuthSecurityRegistrationSection
            authSecuritySettings={{
              ...authSecuritySettings,
              defaultRegistrationChallengeProviderLabel
            }}
            authSecuritySettingsForm={authSecuritySettingsForm}
            availableRegistrationCaptchaProviders={availableRegistrationCaptchaProviders}
            captchaProviderIsSelectable={captchaProviderIsSelectable}
            formatDurationHint={formatDurationHint}
            locale={locale}
            onAuthSecurityFormChange={onAuthSecurityFormChange}
            registrationCaptchaProviderCatalog={registrationCaptchaProviderCatalog}
            t={t}
          />

          <AuthSecurityGeoIpSection
            authSecuritySettings={authSecuritySettings}
            authSecuritySettingsForm={authSecuritySettingsForm}
            availableProviders={availableProviders}
            canAddFallback={canAddFallback}
            currentPrimaryProvider={currentPrimaryProvider}
            fallbackInput={fallbackInput}
            formatDurationHint={formatDurationHint}
            geoIpProviderCatalog={geoIpProviderCatalog}
            locale={locale}
            onAddFallbackProvider={addFallbackProvider}
            onAuthSecurityFormChange={onAuthSecurityFormChange}
            onFallbackInputChange={setFallbackInput}
            onFallbackKeyDown={handleFallbackKeyDown}
            onPrimaryProviderChange={handlePrimaryProviderChange}
            onRemoveFallbackProvider={removeFallbackProvider}
            providerIsSelectable={providerIsSelectable}
            providerLabel={providerLabel}
            selectedFallbackProviders={selectedFallbackProviders}
            t={t}
          />

          <AuthSecuritySummarySection
            authSecuritySettings={{
              ...authSecuritySettings,
              effectiveRegistrationChallengeProviderLabel: captchaProviderLabel(authSecuritySettings.effectiveRegistrationChallengeProvider)
            }}
            locale={locale}
            parseProviderList={parseProviderList}
            providerLabel={providerLabel}
            t={t}
          />
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
