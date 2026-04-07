import { fireEvent, render, screen } from '@testing-library/react'
import CreateUserDialog from './CreateUserDialog'
import { translate } from '@/lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('CreateUserDialog', () => {
  it('shows duplicate username feedback and blocks submit when password rules are not satisfied', () => {
    render(
      <CreateUserDialog
        createUserForm={{ username: 'alice', password: 'weak', confirmPassword: 'weak', role: 'USER' }}
        createUserLoading={false}
        duplicateUsername
        onClose={vi.fn()}
        onFormChange={vi.fn()}
        onSubmit={vi.fn()}
        roleLabel={(role) => role}
        t={t}
      />
    )

    expect(screen.getByText('A user named alice already exists.')).toBeInTheDocument()
    expect(screen.getByText('At least 8 characters')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create User' })).toBeDisabled()
  })

  it('allows submit when the username is unique and the password rules pass', () => {
    const onSubmit = vi.fn((event) => event.preventDefault())

    render(
      <CreateUserDialog
        createUserForm={{ username: 'alice', password: 'Secret#123', confirmPassword: 'Secret#123', role: 'USER' }}
        createUserLoading={false}
        duplicateUsername={false}
        onClose={vi.fn()}
        onFormChange={vi.fn()}
        onSubmit={onSubmit}
        roleLabel={(role) => role}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Create User' }))
    expect(onSubmit).toHaveBeenCalled()
  })

  it('renders translated labels in portuguese', () => {
    render(
      <CreateUserDialog
        createUserForm={{ username: 'alice', password: 'Secret#123', confirmPassword: 'Secret#123', role: 'USER' }}
        createUserLoading={false}
        duplicateUsername={false}
        onClose={vi.fn()}
        onFormChange={vi.fn()}
        onSubmit={vi.fn()}
        roleLabel={(role) => translate('pt-PT', role === 'ADMIN' ? 'role.admin' : 'role.user')}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByRole('dialog', { name: 'Criar utilizador' })).toBeInTheDocument()
    expect(screen.getByText('Crie uma conta totalmente aprovada e atribua o papel inicial. A palavra-passe temporária tem de respeitar a mesma política de todas as outras contas.')).toBeInTheDocument()
    expect(screen.getByLabelText('Palavra-passe inicial')).toBeInTheDocument()
    expect(screen.getByLabelText('Utilizador só pode gerir a própria caixa de destino, as próprias contas de email de origem e as preferências pessoais. Admin também pode aceder à área de Administração, gerir utilizadores, aplicações OAuth partilhadas e definições globais de polling.')).toBeInTheDocument()
  })

  it('forwards form field updates and cancel actions', () => {
    const onClose = vi.fn()
    const onFormChange = vi.fn()

    render(
      <CreateUserDialog
        createUserForm={{ username: '', password: '', confirmPassword: '', role: 'USER' }}
        createUserLoading={false}
        duplicateUsername={false}
        onClose={onClose}
        onFormChange={onFormChange}
        onSubmit={vi.fn()}
        roleLabel={(role) => role}
        t={t}
      />
    )

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'alice' } })
    fireEvent.change(screen.getByLabelText('Initial Password'), { target: { value: 'Secret#123' } })
    fireEvent.change(screen.getByLabelText('Repeat New Password'), { target: { value: 'Secret#123' } })
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ADMIN' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onFormChange).toHaveBeenCalledTimes(4)

    const usernameUpdate = onFormChange.mock.calls[0][0]
    expect(usernameUpdate({ username: '', password: '', confirmPassword: '', role: 'USER' })).toEqual({
      username: 'alice',
      password: '',
      confirmPassword: '',
      role: 'USER'
    })

    const passwordUpdate = onFormChange.mock.calls[1][0]
    expect(passwordUpdate({ username: 'alice', password: '', confirmPassword: '', role: 'USER' })).toEqual({
      username: 'alice',
      password: 'Secret#123',
      confirmPassword: '',
      role: 'USER'
    })

    const confirmPasswordUpdate = onFormChange.mock.calls[2][0]
    expect(confirmPasswordUpdate({ username: 'alice', password: 'Secret#123', confirmPassword: '', role: 'USER' })).toEqual({
      username: 'alice',
      password: 'Secret#123',
      confirmPassword: 'Secret#123',
      role: 'USER'
    })

    const roleUpdate = onFormChange.mock.calls[3][0]
    expect(roleUpdate({ username: 'alice', password: 'Secret#123', confirmPassword: 'Secret#123', role: 'USER' })).toEqual({
      username: 'alice',
      password: 'Secret#123',
      confirmPassword: 'Secret#123',
      role: 'ADMIN'
    })

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('shows the loading submit state while user creation is in progress', () => {
    render(
      <CreateUserDialog
        createUserForm={{ username: 'alice', password: 'Secret#123', confirmPassword: 'Secret#123', role: 'USER' }}
        createUserLoading
        duplicateUsername={false}
        onClose={vi.fn()}
        onFormChange={vi.fn()}
        onSubmit={vi.fn()}
        roleLabel={(role) => role}
        t={t}
      />
    )

    expect(screen.getByRole('button', { name: 'Creating User…' })).toBeDisabled()
  })
})
