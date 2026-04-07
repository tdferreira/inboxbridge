import { fireEvent, render, screen } from '@testing-library/react'
import PasswordPanel from './PasswordPanel'
import { translate } from '@/lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('PasswordPanel', () => {
  it('blocks submit until the new password satisfies the policy and matches confirmation', () => {
    let passwordForm = {
      currentPassword: '',
      newPassword: '',
      confirmNewPassword: ''
    }

    const { rerender } = renderPanel()

    expect(screen.getByRole('button', { name: 'Change Password' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Current Password'), { target: { value: 'Oldpass#123' } })
    fireEvent.change(screen.getByLabelText('New Password'), { target: { value: 'Newpass#123' } })
    fireEvent.change(screen.getByLabelText('Repeat New Password'), { target: { value: 'Newpass#123' } })

    expect(screen.getByRole('button', { name: 'Change Password' })).toBeEnabled()

    function renderPanel() {
      return render(
        <PasswordPanel
          onPasswordChange={vi.fn()}
          onPasswordFormChange={(updater) => {
            passwordForm = typeof updater === 'function' ? updater(passwordForm) : updater
            rerenderPanel()
          }}
          onPasswordRemove={vi.fn()}
          passkeyCount={1}
          passwordConfigured
          passwordForm={passwordForm}
          passwordLoading={false}
          passwordRemoveLoading={false}
          t={t}
        />
      )
    }

    function rerenderPanel() {
      rerender(
        <PasswordPanel
          onPasswordChange={vi.fn()}
          onPasswordFormChange={(updater) => {
            passwordForm = typeof updater === 'function' ? updater(passwordForm) : updater
            rerenderPanel()
          }}
          onPasswordRemove={vi.fn()}
          passkeyCount={1}
          passwordConfigured
          passwordForm={passwordForm}
          passwordLoading={false}
          passwordRemoveLoading={false}
          t={t}
        />
      )
    }
  })

  it('allows passwordless accounts to set a password without entering a current password', () => {
    render(
      <PasswordPanel
        onPasswordChange={vi.fn()}
        onPasswordFormChange={vi.fn()}
        onPasswordRemove={vi.fn()}
        passkeyCount={1}
        passwordConfigured={false}
        passwordForm={{
          currentPassword: '',
          newPassword: 'Newpass#123',
          confirmNewPassword: 'Newpass#123'
        }}
        passwordLoading={false}
        passwordRemoveLoading={false}
        t={t}
      />
    )

    expect(screen.queryByLabelText('Current Password')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Set Password' })).toBeEnabled()
  })

  it('requires the current password before enabling remove password', () => {
    let passwordForm = {
      currentPassword: '',
      newPassword: '',
      confirmNewPassword: ''
    }

    const { rerender } = render(
      <PasswordPanel
        onPasswordChange={vi.fn()}
        onPasswordFormChange={(updater) => {
          passwordForm = typeof updater === 'function' ? updater(passwordForm) : updater
          rerenderPanel()
        }}
        onPasswordRemove={vi.fn()}
        passkeyCount={1}
        passwordConfigured
        passwordForm={passwordForm}
        passwordLoading={false}
        passwordRemoveLoading={false}
        t={t}
      />
    )

    expect(screen.getByRole('button', { name: 'Remove Password' })).toBeDisabled()
    expect(screen.getByText('Enter the current password above before removing it.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Current Password'), { target: { value: 'Oldpass#123' } })

    expect(screen.getByRole('button', { name: 'Remove Password' })).toBeEnabled()

    function rerenderPanel() {
      rerender(
        <PasswordPanel
          onPasswordChange={vi.fn()}
          onPasswordFormChange={(updater) => {
            passwordForm = typeof updater === 'function' ? updater(passwordForm) : updater
            rerenderPanel()
          }}
          onPasswordRemove={vi.fn()}
          passkeyCount={1}
          passwordConfigured
          passwordForm={passwordForm}
          passwordLoading={false}
          passwordRemoveLoading={false}
          t={t}
        />
      )
    }
  })

  it('renders translated password copy in portuguese', () => {
    render(
      <PasswordPanel
        onPasswordChange={vi.fn()}
        onPasswordFormChange={vi.fn()}
        onPasswordRemove={vi.fn()}
        passkeyCount={1}
        passwordConfigured
        passwordForm={{
          currentPassword: '',
          newPassword: '',
          confirmNewPassword: ''
        }}
        passwordLoading={false}
        passwordRemoveLoading={false}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('Palavra-passe')).toBeInTheDocument()
    expect(screen.getByText('Use uma palavra-passe forte, a menos que queira deliberadamente que esta conta dependa apenas de passkeys.')).toBeInTheDocument()
    expect(screen.getByText('Pelo menos 8 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Diferente da palavra-passe atual')).toBeInTheDocument()
  })
})
