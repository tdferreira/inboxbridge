import { fireEvent, render, screen } from '@testing-library/react'
import CreateUserDialog from './CreateUserDialog'
import { translate } from '../../lib/i18n'

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
})
