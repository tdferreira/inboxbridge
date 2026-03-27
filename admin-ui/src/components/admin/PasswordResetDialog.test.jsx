import { fireEvent, render, screen } from '@testing-library/react'
import PasswordResetDialog from './PasswordResetDialog'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('PasswordResetDialog', () => {
  it('shows password rules and requires a valid matching temporary password before enabling reset', () => {
    let resetPasswordForm = { newPassword: '', confirmNewPassword: '' }
    const { rerender } = renderUi()

    expect(screen.getByRole('button', { name: 'Reset password' })).toBeDisabled()
    expect(screen.getByText('At least 8 characters')).toBeInTheDocument()
    expect(screen.getByText('Uppercase letter')).toBeInTheDocument()
    expect(screen.getByText('Lowercase letter')).toBeInTheDocument()
    expect(screen.getByText('Number')).toBeInTheDocument()
    expect(screen.getByText('Special character')).toBeInTheDocument()
    expect(screen.getByText('Repeat password matches')).toBeInTheDocument()
    expect(screen.queryByText('Different from current password')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Initial Password'), { target: { value: 'TempPass#123' } })
    fireEvent.change(screen.getByLabelText('Repeat New Password'), { target: { value: 'TempPass#123' } })

    expect(screen.getByRole('button', { name: 'Reset password' })).toBeEnabled()

    function renderUi() {
      return render(
        <PasswordResetDialog
          onClose={vi.fn()}
          onFormChange={(updater) => {
            resetPasswordForm = typeof updater === 'function' ? updater(resetPasswordForm) : updater
            rerenderUi()
          }}
          onSubmit={vi.fn((event) => event.preventDefault())}
          passwordLoading={false}
          resetPasswordForm={resetPasswordForm}
          t={t}
          username="alice"
        />
      )
    }

    function rerenderUi() {
      rerender(
        <PasswordResetDialog
          onClose={vi.fn()}
          onFormChange={(updater) => {
            resetPasswordForm = typeof updater === 'function' ? updater(resetPasswordForm) : updater
            rerenderUi()
          }}
          onSubmit={vi.fn((event) => event.preventDefault())}
          passwordLoading={false}
          resetPasswordForm={resetPasswordForm}
          t={t}
          username="alice"
        />
      )
    }
  })

  it('renders translated reset-password copy in portuguese', () => {
    render(
      <PasswordResetDialog
        onClose={vi.fn()}
        onFormChange={vi.fn()}
        onSubmit={vi.fn()}
        passwordLoading={false}
        resetPasswordForm={{ newPassword: '', confirmNewPassword: '' }}
        t={(key, params) => translate('pt-PT', key, params)}
        username="alice"
      />
    )

    expect(screen.getByText('Repor palavra-passe de alice')).toBeInTheDocument()
    expect(screen.getByText('Defina uma palavra-passe temporária. O utilizador terá de a alterar após o próximo início de sessão com sucesso.')).toBeInTheDocument()
    expect(screen.getByText('Pelo menos 8 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Letra maiúscula')).toBeInTheDocument()
    expect(screen.getByText('A repetição da palavra-passe coincide')).toBeInTheDocument()
  })
})
