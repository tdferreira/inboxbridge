import { fireEvent, render, screen } from '@testing-library/react'
import UserManagementSection from './UserManagementSection'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('UserManagementSection', () => {
  it('disables self-demotion and forwards sensitive user actions through callbacks', () => {
    const onResetUserPasskeys = vi.fn()
    const onOpenResetPasswordDialog = vi.fn()
    const onToggleUserActive = vi.fn()
    const onForcePasswordChange = vi.fn()

    renderUi()

    expect(screen.getByRole('button', { name: 'Make regular user' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Reset passkeys' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Suspend user' }))
    fireEvent.click(screen.getByRole('button', { name: 'Force password change' }))
    fireEvent.click(screen.getByRole('button', { name: 'Reset password' }))
    fireEvent.click(screen.getByRole('button', { name: 'Reset passkeys' }))

    expect(onToggleUserActive).toHaveBeenCalledWith(expect.objectContaining({ username: 'admin' }))
    expect(onForcePasswordChange).toHaveBeenCalledWith(expect.objectContaining({ username: 'admin' }))
    expect(onResetUserPasskeys).not.toHaveBeenCalled()
    expect(onOpenResetPasswordDialog).toHaveBeenCalled()

    function renderUi() {
      return render(
        <UserManagementSection
          collapsed={false}
          collapseLoading={false}
          createUserForm={{ username: '', password: '', role: 'USER' }}
          createUserLoading={false}
          onCollapseToggle={vi.fn()}
          onCreateUser={vi.fn()}
          onCreateUserFormChange={vi.fn()}
          onForcePasswordChange={onForcePasswordChange}
          onOpenResetPasswordDialog={onOpenResetPasswordDialog}
          onResetUserPasskeys={onResetUserPasskeys}
          onSelectUser={vi.fn()}
          onToggleUserActive={onToggleUserActive}
          onUpdateUser={vi.fn()}
          session={{ id: 7, role: 'ADMIN' }}
          selectedUserConfig={{
            user: {
              id: 7,
              username: 'admin',
              role: 'ADMIN',
              approved: true,
              active: true,
              gmailConfigured: true,
              passwordConfigured: true,
              mustChangePassword: false,
              passkeyCount: 1
            },
            gmailConfig: {
              redirectUri: 'https://localhost:3000/api/google-oauth/callback',
              sharedClientConfigured: true,
              clientIdConfigured: false,
              clientSecretConfigured: false,
              refreshTokenConfigured: true
            },
            passkeys: [],
            bridges: []
          }}
          selectedUserId={7}
          selectedUserLoading={false}
          updatingPasskeysResetUserId={null}
          updatingUserId={null}
          users={[{ id: 7, username: 'admin', role: 'ADMIN', approved: true, active: true, bridgeCount: 0 }]}
          locale="en"
          t={t}
        />
      )
    }
  })
})
