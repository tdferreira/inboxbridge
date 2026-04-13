import { fireEvent, render, screen, within } from '@testing-library/react'
import AuthSecuritySection from './AuthSecuritySection'
import { translate } from '@/lib/i18n'

describe('AuthSecuritySection', () => {
  it('renders the effective authentication protection summary and opens the editor', () => {
    const onOpenEditor = vi.fn()
    const onReencryptStoredSecrets = vi.fn()

    const { container } = render(
      <AuthSecuritySection
        authSecuritySettings={{
          effectiveLoginFailureThreshold: 5,
          effectiveLoginInitialBlock: 'PT5M',
          effectiveLoginMaxBlock: 'PT1H',
          effectiveRegistrationChallengeEnabled: true,
          effectiveRegistrationChallengeTtl: 'PT10M',
          effectiveGeoIpEnabled: true,
          effectiveGeoIpPrimaryProvider: 'IPWHOIS',
          effectiveGeoIpFallbackProviders: 'IPAPI_CO,IP_API'
        }}
        collapsed={false}
        collapseLoading={false}
        onCollapseToggle={vi.fn()}
        onOpenEditor={onOpenEditor}
        onReencryptStoredSecrets={onReencryptStoredSecrets}
        secretManagementStatus={{
          secureStorageConfigured: true,
          mode: 'LOCAL',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2',
          activeKeyId: 'LOCAL:v2',
          configuredLegacyKeyIds: ['LOCAL:v1'],
          protectedRecordCount: 14,
          nonActiveKeyRecordCount: 3,
          unavailableKeyRecordCount: 1,
          safeToRetireLegacyKeys: false,
          keyUsage: [
            {
              keyVersion: 'LOCAL:v2',
              recordCount: 11,
              areas: 'oauth-credentials,source-mailboxes',
              active: true,
              availableForDecryption: true
            },
            {
              keyVersion: 'LOCAL:v1',
              recordCount: 3,
              areas: 'destination-mailboxes',
              active: false,
              availableForDecryption: false
            }
          ]
        }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Authentication Security')).toBeInTheDocument()
    expect(screen.getByText('Login protection')).toBeInTheDocument()
    expect(screen.getByText('Registration protection')).toBeInTheDocument()
    expect(screen.getByText('Geo-IP session visibility')).toBeInTheDocument()
    expect(screen.getByText('Secret management')).toBeInTheDocument()
    expect(screen.getByText('LOCAL:v2')).toBeInTheDocument()
    expect(screen.getAllByText('LOCAL:v1').length).toBeGreaterThan(0)
    expect(screen.getByText((content) => content.includes('Available') && content.includes('11 records'))).toBeInTheDocument()
    expect(screen.getByText((content) => content.includes('Unavailable for decryption') && content.includes('3 records'))).toBeInTheDocument()
    expect(screen.getByText('PT5M')).toBeInTheDocument()
    expect(screen.getByText('PT1H')).toBeInTheDocument()
    expect(screen.getAllByText('Enabled')).toHaveLength(2)
    expect(screen.getByText('IPwho.is')).toBeInTheDocument()
    expect(within(container.querySelector('.panel-header-actions')).getByRole('button', { name: 'Edit Authentication Security' })).toBeInTheDocument()
    expect(within(screen.getByText('Runtime protection summary').closest('article')).queryByRole('button', { name: 'Edit Authentication Security' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit Authentication Security' }))
    fireEvent.click(screen.getByRole('button', { name: 'Re-encrypt stored secrets' }))

    expect(onOpenEditor).toHaveBeenCalled()
    expect(onReencryptStoredSecrets).toHaveBeenCalled()
  })
})
