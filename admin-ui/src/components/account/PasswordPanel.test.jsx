import { fireEvent, render, screen } from '@testing-library/react'
import PasswordPanel from './PasswordPanel'

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
          passwordForm={passwordForm}
          passwordLoading={false}
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
          passwordForm={passwordForm}
          passwordLoading={false}
        />
      )
    }
  })
})
