import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import AuthSecuritySettingsDialog from './AuthSecuritySettingsDialog'
import { translate } from '../../lib/i18n'

describe('AuthSecuritySettingsDialog', () => {
  it('renders the editor and forwards changes', () => {
    const initialForm = {
      loginFailureThresholdOverride: '',
      loginInitialBlockOverride: '',
      loginMaxBlockOverride: '',
      registrationChallengeMode: 'DEFAULT',
      registrationChallengeTtlOverride: '',
      registrationChallengeProviderOverride: '',
      registrationTurnstileSiteKeyOverride: '',
      registrationTurnstileSecret: '',
      registrationHcaptchaSiteKeyOverride: '',
      registrationHcaptchaSecret: '',
      geoIpMode: 'DEFAULT',
      geoIpPrimaryProviderOverride: '',
      geoIpFallbackProvidersOverride: '',
      geoIpCacheTtlOverride: '',
      geoIpProviderCooldownOverride: '',
      geoIpRequestTimeoutOverride: '',
      geoIpIpinfoToken: ''
    }

    let latestForm = initialForm

    function Harness() {
      const [form, setForm] = useState(initialForm)
      latestForm = form
      return (
        <AuthSecuritySettingsDialog
          authSecuritySettings={{
            defaultLoginFailureThreshold: 5,
            effectiveLoginFailureThreshold: 5,
            defaultLoginInitialBlock: 'PT5M',
            effectiveLoginInitialBlock: 'PT5M',
            defaultLoginMaxBlock: 'PT1H',
            effectiveLoginMaxBlock: 'PT1H',
            defaultRegistrationChallengeEnabled: true,
            effectiveRegistrationChallengeEnabled: true,
            defaultRegistrationChallengeTtl: 'PT10M',
            effectiveRegistrationChallengeTtl: 'PT10M',
            defaultRegistrationChallengeProvider: 'ALTCHA',
            effectiveRegistrationChallengeProvider: 'ALTCHA',
            availableRegistrationCaptchaProviders: 'ALTCHA, TURNSTILE, HCAPTCHA',
            defaultRegistrationTurnstileSiteKey: '',
            registrationTurnstileConfigured: false,
            defaultRegistrationHcaptchaSiteKey: '',
            registrationHcaptchaConfigured: false,
            defaultGeoIpEnabled: false,
            effectiveGeoIpEnabled: false,
            defaultGeoIpPrimaryProvider: 'IPWHOIS',
            effectiveGeoIpPrimaryProvider: 'IPWHOIS',
            defaultGeoIpFallbackProviders: 'IPAPI_CO,IP_API,IPINFO_LITE',
            effectiveGeoIpFallbackProviders: 'IPAPI_CO,IP_API,IPINFO_LITE',
            defaultGeoIpCacheTtl: 'PT720H',
            effectiveGeoIpCacheTtl: 'PT720H',
            defaultGeoIpProviderCooldown: 'PT5M',
            effectiveGeoIpProviderCooldown: 'PT5M',
            defaultGeoIpRequestTimeout: 'PT3S',
            effectiveGeoIpRequestTimeout: 'PT3S',
            availableGeoIpProviders: 'IPWHOIS, IPAPI_CO, IP_API, IPINFO_LITE',
            geoIpIpinfoTokenConfigured: true,
            secureStorageConfigured: true
          }}
          authSecuritySettingsForm={form}
          authSecuritySettingsLoading={false}
          isDirty={false}
          onAuthSecurityFormChange={(updater) => {
            setForm((current) => typeof updater === 'function' ? updater(current) : updater)
          }}
          onClose={vi.fn()}
          onResetAuthSecuritySettings={vi.fn()}
          onSaveAuthSecuritySettings={vi.fn((event) => event.preventDefault())}
          t={(key, params) => translate('en', key, params)}
        />
      )
    }

    render(<Harness />)

    fireEvent.change(screen.getByLabelText(/Failed sign-ins before lockout/), { target: { value: '7' } })
    fireEvent.change(screen.getByLabelText(/Initial lockout duration/), { target: { value: '10m' } })
    fireEvent.change(screen.getByLabelText(/Maximum lockout duration/), { target: { value: '2h' } })
    fireEvent.change(screen.getByLabelText(/Registration anti-robot check/), { target: { value: 'DISABLED' } })
    fireEvent.change(screen.getByLabelText(/Registration challenge lifetime/), { target: { value: '20m' } })
    fireEvent.change(screen.getByLabelText(/Registration CAPTCHA provider/), { target: { value: 'ALTCHA' } })
    fireEvent.change(screen.getByLabelText(/Turnstile site key/), { target: { value: 'site-key-1' } })
    fireEvent.change(screen.getByPlaceholderText(/Paste a new Turnstile secret/i), { target: { value: 'secret-1' } })
    fireEvent.change(screen.getByLabelText(/Geo-IP mode/), { target: { value: 'ENABLED' } })
    fireEvent.change(screen.getByLabelText(/Primary Geo-IP provider/), { target: { value: 'IPAPI_CO' } })
    const fallbackInput = screen.getByLabelText(/Fallback Geo-IP providers/i)
    fireEvent.change(fallbackInput, { target: { value: 'IP_API' } })
    fireEvent.keyDown(fallbackInput, { key: 'Enter', code: 'Enter' })
    fireEvent.change(screen.getByPlaceholderText(/A secret is already configured|Paste a new IPinfo Lite token/i), { target: { value: 'token-123' } })
    fireEvent.change(fallbackInput, { target: { value: 'IPINFO_LITE' } })
    fireEvent.click(screen.getByRole('button', { name: /Add Provider/i }))
    fireEvent.change(screen.getByLabelText(/Geo-IP cache lifetime/), { target: { value: '48h' } })
    fireEvent.change(screen.getByLabelText(/Provider cooldown after retryable failure/), { target: { value: '10m' } })
    fireEvent.change(screen.getByLabelText(/Geo-IP request timeout/), { target: { value: '5s' } })

    expect(latestForm.loginFailureThresholdOverride).toBe('7')
    expect(latestForm.loginInitialBlockOverride).toBe('10m')
    expect(latestForm.loginMaxBlockOverride).toBe('2h')
    expect(latestForm.registrationChallengeMode).toBe('DISABLED')
    expect(latestForm.registrationChallengeTtlOverride).toBe('20m')
    expect(latestForm.registrationChallengeProviderOverride).toBe('ALTCHA')
    expect(latestForm.registrationTurnstileSiteKeyOverride).toBe('site-key-1')
    expect(latestForm.registrationTurnstileSecret).toBe('secret-1')
    expect(latestForm.geoIpMode).toBe('ENABLED')
    expect(latestForm.geoIpPrimaryProviderOverride).toBe('IPAPI_CO')
    expect(latestForm.geoIpFallbackProvidersOverride).toBe('IP_API,IPINFO_LITE')
    expect(latestForm.geoIpCacheTtlOverride).toBe('48h')
    expect(latestForm.geoIpProviderCooldownOverride).toBe('10m')
    expect(latestForm.geoIpRequestTimeoutOverride).toBe('5s')
    expect(latestForm.geoIpIpinfoToken).toBe('token-123')
  })

  it('shows the effective summary and translated help copy', () => {
    render(
      <AuthSecuritySettingsDialog
        authSecuritySettings={{
          defaultLoginFailureThreshold: 5,
          effectiveLoginFailureThreshold: 8,
          defaultLoginInitialBlock: 'PT5M',
          effectiveLoginInitialBlock: 'PT10M',
          defaultLoginMaxBlock: 'PT1H',
          effectiveLoginMaxBlock: 'PT2H',
          defaultRegistrationChallengeEnabled: false,
          effectiveRegistrationChallengeEnabled: true,
          defaultRegistrationChallengeTtl: 'PT10M',
          effectiveRegistrationChallengeTtl: 'PT20M',
          defaultRegistrationChallengeProvider: 'ALTCHA',
          effectiveRegistrationChallengeProvider: 'TURNSTILE',
          availableRegistrationCaptchaProviders: 'ALTCHA, TURNSTILE, HCAPTCHA',
          defaultRegistrationTurnstileSiteKey: '',
          registrationTurnstileConfigured: true,
          defaultRegistrationHcaptchaSiteKey: '',
          registrationHcaptchaConfigured: false,
          defaultGeoIpEnabled: false,
          effectiveGeoIpEnabled: true,
          defaultGeoIpPrimaryProvider: 'IPWHOIS',
          effectiveGeoIpPrimaryProvider: 'IPAPI_CO',
          defaultGeoIpFallbackProviders: 'IPAPI_CO,IP_API,IPINFO_LITE',
          effectiveGeoIpFallbackProviders: 'IP_API,IPINFO_LITE',
          defaultGeoIpCacheTtl: 'PT720H',
          effectiveGeoIpCacheTtl: 'PT240H',
          defaultGeoIpProviderCooldown: 'PT5M',
          effectiveGeoIpProviderCooldown: 'PT10M',
          defaultGeoIpRequestTimeout: 'PT3S',
          effectiveGeoIpRequestTimeout: 'PT5S',
          availableGeoIpProviders: 'IPWHOIS, IPAPI_CO, IP_API, IPINFO_LITE',
          geoIpIpinfoTokenConfigured: true,
          secureStorageConfigured: true
        }}
        authSecuritySettingsForm={{
          loginFailureThresholdOverride: '',
          loginInitialBlockOverride: '',
          loginMaxBlockOverride: '',
          registrationChallengeMode: 'DEFAULT',
          registrationChallengeTtlOverride: '',
          registrationChallengeProviderOverride: '',
          registrationTurnstileSiteKeyOverride: '',
          registrationTurnstileSecret: '',
          registrationHcaptchaSiteKeyOverride: '',
          registrationHcaptchaSecret: '',
          geoIpMode: 'DEFAULT',
          geoIpPrimaryProviderOverride: '',
          geoIpFallbackProvidersOverride: '',
          geoIpCacheTtlOverride: '',
          geoIpProviderCooldownOverride: '',
          geoIpRequestTimeoutOverride: '',
          geoIpIpinfoToken: ''
        }}
        authSecuritySettingsLoading={false}
        isDirty={false}
        onAuthSecurityFormChange={vi.fn()}
        onClose={vi.fn()}
        onResetAuthSecuritySettings={vi.fn()}
        onSaveAuthSecuritySettings={vi.fn((event) => event.preventDefault())}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText(/InboxBridge tracks repeated sign-in failures per client address/i)).toBeInTheDocument()
    expect(screen.getByText('CAPTCHA provider')).toBeInTheDocument()
    expect(screen.getByText('Provider chain')).toBeInTheDocument()
    expect(screen.getByText('Cache and retry timing')).toBeInTheDocument()
    expect(screen.getByText('CAPTCHA provider configuration')).toBeInTheDocument()
    expect(screen.getByText(/Effective failed sign-ins before lockout: 8/)).toBeInTheDocument()
    expect(screen.getByText(/Effective registration anti-robot check: Enabled/)).toBeInTheDocument()
    expect(screen.getByText(/Effective registration CAPTCHA provider: Cloudflare Turnstile/i)).toBeInTheDocument()
    expect(screen.getByText(/Effective Geo-IP mode: Enabled/)).toBeInTheDocument()
    expect(screen.getByText(/Effective primary Geo-IP provider: ipapi\.co/i)).toBeInTheDocument()
    expect(screen.getAllByText(/Provider docs/i).length).toBeGreaterThan(0)
    expect(screen.getByText('PT20M')).toBeInTheDocument()
    expect(screen.getByTitle('PT20M = 20 minutes')).toBeInTheDocument()
    expect(screen.queryByText(/PostgreSQL/i)).not.toBeInTheDocument()
  })
})
