import { fireEvent, render, screen, within } from '@testing-library/react'
import UserManagementSection from './UserManagementSection'
import { translate } from '../../lib/i18n'

function renderUi(overrides = {}) {
  const locale = overrides.locale || 'en'
  return render(
    <UserManagementSection
      collapsed={false}
      collapseLoading={false}
      createUserDialogOpen={false}
      createUserForm={{ username: '', password: '', confirmPassword: '', role: 'USER' }}
      createUserLoading={false}
      duplicateUsername={false}
      expandedUserId={7}
      onCloseCreateUserDialog={vi.fn()}
      onCollapseToggle={vi.fn()}
      onCreateUser={vi.fn()}
      onCreateUserFormChange={vi.fn()}
      onForcePasswordChange={vi.fn()}
      onOpenCreateUserDialog={vi.fn()}
      onOpenResetPasswordDialog={vi.fn()}
      onDeleteUser={vi.fn()}
      onResetUserPasskeys={vi.fn()}
      onToggleExpandUser={vi.fn()}
      onToggleUserActive={vi.fn()}
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
          passkeyCount: 0,
          bridgeCount: 0
        },
        gmailConfig: {
          destinationUser: 'me',
          redirectUri: 'https://localhost:3000/api/google-oauth/callback',
          sharedClientConfigured: true,
          clientIdConfigured: false,
          clientSecretConfigured: false,
          refreshTokenConfigured: true
        },
        pollingSettings: {
          defaultPollEnabled: true,
          pollEnabledOverride: null,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          pollIntervalOverride: null,
          effectivePollInterval: '5m',
          defaultFetchWindow: 50,
          fetchWindowOverride: null,
          effectiveFetchWindow: 50
        },
        passkeys: [],
        bridges: []
      }}
      selectedUserLoading={false}
      updatingPasskeysResetUserId={null}
      updatingUserId={null}
      users={[{ id: 7, username: 'admin', role: 'ADMIN', approved: true, active: true, bridgeCount: 0 }]}
      locale={locale}
      t={(key, params) => translate(locale, key, params)}
      {...overrides}
    />
  )
}

describe('UserManagementSection', () => {
  it('opens the create-user modal instead of showing the form inline', () => {
    const onOpenCreateUserDialog = vi.fn()
    renderUi({ onOpenCreateUserDialog })

    expect(screen.queryByText('Create a fully approved account and assign its initial role. The temporary password must follow the same policy as every other account.')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Create User' }))
    expect(onOpenCreateUserDialog).toHaveBeenCalled()
  })

  it('routes contextual menu actions for the expanded user', () => {
    const onToggleUserActive = vi.fn()
    const onForcePasswordChange = vi.fn()
    const onOpenResetPasswordDialog = vi.fn()
    const onResetUserPasskeys = vi.fn()
    const onDeleteUser = vi.fn()

    renderUi({
      onToggleUserActive,
      onForcePasswordChange,
      onOpenResetPasswordDialog,
      onResetUserPasskeys,
      onDeleteUser
    })

    fireEvent.click(screen.getByRole('button', { name: 'User actions' }))
    const menu = screen.getByRole('button', { name: 'User actions' }).parentElement
    const menuQueries = within(menu)

    expect(menuQueries.queryByRole('button', { name: 'Expand section' })).not.toBeInTheDocument()
    expect(menuQueries.queryByRole('button', { name: 'Collapse section' })).not.toBeInTheDocument()
    expect(menuQueries.getByRole('button', { name: 'Make regular user' })).toBeDisabled()
    expect(menuQueries.getByRole('button', { name: 'Reset passkeys' })).toBeDisabled()
    expect(menuQueries.getByRole('button', { name: 'Delete user' })).toBeDisabled()

    fireEvent.click(menuQueries.getByRole('button', { name: 'Suspend user' }))
    fireEvent.click(screen.getByRole('button', { name: 'User actions' }))
    fireEvent.click(within(screen.getByRole('button', { name: 'User actions' }).parentElement).getByRole('button', { name: 'Force password change' }))
    fireEvent.click(screen.getByRole('button', { name: 'User actions' }))
    fireEvent.click(within(screen.getByRole('button', { name: 'User actions' }).parentElement).getByRole('button', { name: 'Reset password' }))
    fireEvent.click(screen.getByRole('button', { name: 'User actions' }))
    fireEvent.click(within(screen.getByRole('button', { name: 'User actions' }).parentElement).getByRole('button', { name: 'Reset passkeys' }))

    expect(onToggleUserActive).toHaveBeenCalledWith(expect.objectContaining({ username: 'admin' }))
    expect(onForcePasswordChange).toHaveBeenCalledWith(expect.objectContaining({ username: 'admin' }))
    expect(onOpenResetPasswordDialog).toHaveBeenCalledWith(expect.objectContaining({ username: 'admin' }))
    expect(onResetUserPasskeys).not.toHaveBeenCalled()
    expect(onDeleteUser).not.toHaveBeenCalled()
  })

  it('opens the user contextual menu when the actions button is clicked', () => {
    renderUi()

    fireEvent.click(screen.getByRole('button', { name: 'User actions' }))

    const menu = screen.getByRole('button', { name: 'User actions' }).parentElement

    expect(within(menu).getByRole('button', { name: 'Suspend user' })).toBeInTheDocument()
    expect(within(menu).getByRole('button', { name: 'Force password change' })).toBeInTheDocument()
    expect(within(menu).getByRole('button', { name: 'Reset password' })).toBeInTheDocument()
  })

  it('renders translated subsection titles inside the expanded user pane', () => {
    renderUi({ locale: 'pt-PT' })

    expect(screen.getByText('Configuração do utilizador')).toBeInTheDocument()
    const gmailSectionTitle = screen.getByText('Destino Gmail')
    expect(screen.getByText('Contas de email de origem')).toBeInTheDocument()
    expect(gmailSectionTitle.parentElement).toHaveTextContent('Utilizador da API Gmail: me')
  })

  it('shows a refresh indicator while the users section reloads', () => {
    renderUi({ sectionLoading: true })

    expect(screen.getAllByText('Refreshing section…').length).toBeGreaterThan(0)
  })

  it('sorts users by username before rendering the list', () => {
    renderUi({
      expandedUserId: null,
      users: [
        { id: 2, username: 'zoe', role: 'USER', approved: true, active: true, bridgeCount: 0 },
        { id: 1, username: 'alice', role: 'ADMIN', approved: true, active: true, bridgeCount: 0 },
        { id: 3, username: 'John2', role: 'USER', approved: true, active: true, bridgeCount: 0 },
        { id: 4, username: 'john10', role: 'USER', approved: true, active: true, bridgeCount: 0 }
      ]
    })

    const usernames = ['alice', 'John2', 'john10', 'zoe']
      .map((username) => screen.getByText(username))
      .map((element) => element.closest('.user-list-entry')?.querySelector('strong')?.textContent)

    expect(usernames).toEqual(['alice', 'John2', 'john10', 'zoe'])
  })
})
