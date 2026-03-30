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
          emailAccountCount: 0
        },
        destinationConfig: {
          provider: 'GMAIL_API',
          deliveryMode: 'GMAIL_API',
          linked: true,
          host: '',
          port: null,
          authMethod: 'OAUTH2',
          username: '',
          folder: ''
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
        emailAccounts: []
      }}
      selectedUserLoading={false}
      updatingPasskeysResetUserId={null}
      updatingUserId={null}
      users={[{ id: 7, username: 'admin', role: 'ADMIN', approved: true, active: true, emailAccountCount: 0 }]}
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

    expect(screen.getByText('Modo de implementação')).toBeInTheDocument()
    expect(screen.getByText('Estão ativadas várias contas de utilizador para esta implementação.')).toBeInTheDocument()
    expect(screen.getByText('Configuração do utilizador')).toBeInTheDocument()
    const gmailSectionTitle = screen.getByText('Caixa de destino')
    expect(screen.getByText('Contas de email de origem')).toBeInTheDocument()
    expect(gmailSectionTitle.parentElement).toHaveTextContent('Provider: GMAIL_API')
  })

  it('shows a refresh indicator while the users section reloads', () => {
    renderUi({ sectionLoading: true })

    expect(screen.getAllByText('Refreshing section…').length).toBeGreaterThan(0)
  })

  it('renders expanded user details even when the selected config payload is partial', () => {
    renderUi({
      selectedUserConfig: {
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
          emailAccountCount: 1
        },
        destinationConfig: null,
        pollingSettings: {
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 25
        },
        emailAccounts: [{ emailAccountId: 'outlook-main', protocol: 'IMAP', authMethod: 'OAUTH2', oauthProvider: 'MICROSOFT', host: 'outlook.office365.com', port: 993, tokenStorageMode: 'DATABASE', effectivePollInterval: '5m', effectiveFetchWindow: 25 }],
        passkeys: null
      }
    })

    expect(screen.getByText('Destination Mailbox')).toBeInTheDocument()
    expect(screen.getByText(/Provider: Not set/)).toBeInTheDocument()
    expect(screen.getByText('outlook-main')).toBeInTheDocument()
    expect(screen.getByText('No passkeys registered for this user.')).toBeInTheDocument()
  })

  it('renders partial email-account entries without blanking the expanded user pane', () => {
    renderUi({
      selectedUserConfig: {
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
          emailAccountCount: 2
        },
        destinationConfig: null,
        pollingSettings: null,
        emailAccounts: [null, { emailAccountId: 'outlook-main' }],
        passkeys: null
      }
    })

    expect(screen.getByText('Destination Mailbox')).toBeInTheDocument()
    expect(screen.getByText('outlook-main')).toBeInTheDocument()
    expect(screen.getByText(/Not set:Not set/)).toBeInTheDocument()
  })

  it('sorts users by username before rendering the list', () => {
    renderUi({
      expandedUserId: null,
      users: [
        { id: 2, username: 'zoe', role: 'USER', approved: true, active: true, emailAccountCount: 0 },
        { id: 1, username: 'alice', role: 'ADMIN', approved: true, active: true, emailAccountCount: 0 },
        { id: 3, username: 'John2', role: 'USER', approved: true, active: true, emailAccountCount: 0 },
        { id: 4, username: 'john10', role: 'USER', approved: true, active: true, emailAccountCount: 0 }
      ]
    })

    const usernames = ['alice', 'John2', 'john10', 'zoe']
      .map((username) => screen.getByText(username))
      .map((element) => element.closest('.user-list-entry')?.querySelector('strong')?.textContent)

    expect(usernames).toEqual(['alice', 'John2', 'john10', 'zoe'])
  })
})
