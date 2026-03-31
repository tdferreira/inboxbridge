import { fireEvent, render, screen } from '@testing-library/react'
import AuthSecuritySection from './AuthSecuritySection'
import { translate } from '../../lib/i18n'

describe('AuthSecuritySection', () => {
  it('renders the effective authentication protection summary and opens the editor', () => {
    const onOpenEditor = vi.fn()

    render(
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
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Authentication Security')).toBeInTheDocument()
    expect(screen.getByText('Login protection')).toBeInTheDocument()
    expect(screen.getByText('Registration protection')).toBeInTheDocument()
    expect(screen.getByText('Geo-IP session visibility')).toBeInTheDocument()
    expect(screen.getByText('PT5M')).toBeInTheDocument()
    expect(screen.getByText('PT1H')).toBeInTheDocument()
    expect(screen.getAllByText('Enabled')).toHaveLength(2)
    expect(screen.getByText('IPwho.is')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit Authentication Security' }))

    expect(onOpenEditor).toHaveBeenCalled()
  })
})
