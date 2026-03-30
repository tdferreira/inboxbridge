import { fireEvent, render, screen } from '@testing-library/react'
import AuthSecuritySettingsDialog from './AuthSecuritySettingsDialog'
import { translate } from '../../lib/i18n'

describe('AuthSecuritySettingsDialog', () => {
  it('renders the editor and forwards changes', () => {
    let form = {
      loginFailureThresholdOverride: '',
      loginInitialBlockOverride: '',
      loginMaxBlockOverride: '',
      registrationChallengeMode: 'DEFAULT',
      registrationChallengeTtlOverride: ''
    }

    render(
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
          effectiveRegistrationChallengeTtl: 'PT10M'
        }}
        authSecuritySettingsForm={form}
        authSecuritySettingsLoading={false}
        isDirty={false}
        onAuthSecurityFormChange={(updater) => {
          form = typeof updater === 'function' ? updater(form) : updater
        }}
        onClose={vi.fn()}
        onResetAuthSecuritySettings={vi.fn()}
        onSaveAuthSecuritySettings={vi.fn((event) => event.preventDefault())}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.change(screen.getByLabelText(/Failed sign-ins before lockout/), { target: { value: '7' } })
    fireEvent.change(screen.getByLabelText(/Initial lockout duration/), { target: { value: '10m' } })
    fireEvent.change(screen.getByLabelText(/Maximum lockout duration/), { target: { value: '2h' } })
    fireEvent.change(screen.getByLabelText(/Registration anti-robot check/), { target: { value: 'DISABLED' } })
    fireEvent.change(screen.getByLabelText(/Registration challenge lifetime/), { target: { value: '20m' } })

    expect(form.loginFailureThresholdOverride).toBe('7')
    expect(form.loginInitialBlockOverride).toBe('10m')
    expect(form.loginMaxBlockOverride).toBe('2h')
    expect(form.registrationChallengeMode).toBe('DISABLED')
    expect(form.registrationChallengeTtlOverride).toBe('20m')
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
          effectiveRegistrationChallengeTtl: 'PT20M'
        }}
        authSecuritySettingsForm={{
          loginFailureThresholdOverride: '',
          loginInitialBlockOverride: '',
          loginMaxBlockOverride: '',
          registrationChallengeMode: 'DEFAULT',
          registrationChallengeTtlOverride: ''
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
    expect(screen.getByText(/Effective failed sign-ins before lockout: 8/)).toBeInTheDocument()
    expect(screen.getByText(/Effective registration anti-robot check: Enabled/)).toBeInTheDocument()
    expect(screen.getByText('PT20M')).toBeInTheDocument()
    expect(screen.getByTitle('PT20M = 20 minutes')).toBeInTheDocument()
  })
})
