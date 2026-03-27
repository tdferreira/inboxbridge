import { fireEvent, render, screen } from '@testing-library/react'
import AuthScreen from './AuthScreen'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('AuthScreen', () => {
  it('opens registration modal and submits login and registration actions', () => {
    const onLogin = vi.fn((event) => event.preventDefault())
    const onPasskeyLogin = vi.fn()
    const onRegister = vi.fn((event) => event.preventDefault())
    const onCloseRegisterDialog = vi.fn()
    let loginForm = { username: 'admin', password: 'nimda' }
    let registerForm = { username: '', password: '', confirmPassword: '' }
    let registerOpen = false

    const { rerender } = render(
      <AuthScreen
        authError=""
        loginLoading={false}
        loginForm={loginForm}
        multiUserEnabled
        notice=""
        onCloseRegisterDialog={() => {
          registerOpen = false
          onCloseRegisterDialog()
          rerenderUi()
        }}
        onLogin={onLogin}
        onLoginChange={(updater) => {
          loginForm = typeof updater === 'function' ? updater(loginForm) : updater
          rerenderUi()
        }}
        onPasskeyLogin={onPasskeyLogin}
        onOpenRegisterDialog={() => {
          registerOpen = true
          rerenderUi()
        }}
        registerLoading={false}
        registerOpen={registerOpen}
        passkeyLoading={false}
        passkeysSupported={true}
        onRegister={onRegister}
        onRegisterChange={(updater) => {
          registerForm = typeof updater === 'function' ? updater(registerForm) : updater
          rerenderUi()
        }}
        registerForm={registerForm}
        t={t}
      />
    )

    function rerenderUi() {
      rerender(
        <AuthScreen
          authError=""
          loginLoading={false}
          loginForm={loginForm}
          multiUserEnabled
          notice=""
          onCloseRegisterDialog={() => {
            registerOpen = false
            onCloseRegisterDialog()
            rerenderUi()
          }}
          onLogin={onLogin}
          onLoginChange={(updater) => {
            loginForm = typeof updater === 'function' ? updater(loginForm) : updater
            rerenderUi()
          }}
          onPasskeyLogin={onPasskeyLogin}
          onOpenRegisterDialog={() => {
            registerOpen = true
            rerenderUi()
          }}
          registerLoading={false}
          registerOpen={registerOpen}
          passkeyLoading={false}
          passkeysSupported={true}
          onRegister={onRegister}
          onRegisterChange={(updater) => {
            registerForm = typeof updater === 'function' ? updater(registerForm) : updater
            rerenderUi()
          }}
          registerForm={registerForm}
          t={t}
        />
      )
    }

    expect(screen.queryByLabelText('Requested Username')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'ops-admin' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form'))
    fireEvent.click(screen.getByRole('button', { name: 'Sign in with passkey' }))
    fireEvent.click(screen.getByRole('button', { name: 'Register for access' }))
    fireEvent.change(screen.getByLabelText('Requested Username'), { target: { value: 'new-user' } })
    fireEvent.change(screen.getByLabelText('Requested Password'), { target: { value: 'Secret#123' } })
    fireEvent.change(screen.getByLabelText('Repeat Requested Password'), { target: { value: 'Secret#123' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Register For Approval' }).closest('form'))

    expect(onLogin).toHaveBeenCalledTimes(1)
    expect(onPasskeyLogin).toHaveBeenCalledTimes(1)
    expect(onRegister).toHaveBeenCalledTimes(1)
    expect(loginForm.username).toBe('ops-admin')
    expect(registerForm.username).toBe('new-user')
    expect(registerForm.confirmPassword).toBe('Secret#123')
  })

  it('shows loading feedback for login and registration buttons', () => {
    render(
      <AuthScreen
        authError=""
        loginLoading
        loginForm={{ username: 'admin', password: 'nimda' }}
        multiUserEnabled
        notice=""
        onCloseRegisterDialog={vi.fn()}
        onLogin={vi.fn()}
        onLoginChange={vi.fn()}
        onPasskeyLogin={vi.fn()}
        onOpenRegisterDialog={vi.fn()}
        onRegister={vi.fn()}
        onRegisterChange={vi.fn()}
        registerOpen={false}
        passkeyLoading
        passkeysSupported={true}
        registerForm={{ username: '', password: '', confirmPassword: '' }}
        registerLoading
        t={t}
      />
    )

    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Opening Passkey…' })).toBeDisabled()
  })

  it('blocks registration until the repeated password matches', () => {
    render(
      <AuthScreen
        authError=""
        loginLoading={false}
        loginForm={{ username: 'admin', password: '' }}
        multiUserEnabled
        notice=""
        onCloseRegisterDialog={vi.fn()}
        onLogin={vi.fn()}
        onLoginChange={vi.fn()}
        onPasskeyLogin={vi.fn()}
        onOpenRegisterDialog={vi.fn()}
        onRegister={vi.fn()}
        onRegisterChange={vi.fn()}
        registerOpen
        passkeyLoading={false}
        passkeysSupported={true}
        registerForm={{ username: 'new-user', password: 'Secret#123', confirmPassword: 'Mismatch#123' }}
        registerLoading={false}
        t={t}
      />
    )

    expect(screen.getByText(/repeat password must match/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Register For Approval' })).toBeDisabled()
  })

  it('hides self-registration in single-user mode', () => {
    render(
      <AuthScreen
        authError=""
        loginLoading={false}
        loginForm={{ username: 'admin', password: '' }}
        multiUserEnabled={false}
        notice=""
        onCloseRegisterDialog={vi.fn()}
        onLogin={vi.fn()}
        onLoginChange={vi.fn()}
        onPasskeyLogin={vi.fn()}
        onOpenRegisterDialog={vi.fn()}
        onRegister={vi.fn()}
        onRegisterChange={vi.fn()}
        registerOpen={false}
        passkeyLoading={false}
        passkeysSupported={true}
        registerForm={{ username: '', password: '', confirmPassword: '' }}
        registerLoading={false}
        t={t}
      />
    )

    expect(screen.queryByRole('button', { name: 'Register for access' })).not.toBeInTheDocument()
    expect(screen.getByText(/single-user mode/i)).toBeInTheDocument()
  })

  it('renders translated login and registration copy in portuguese', () => {
    render(
      <AuthScreen
        authError=""
        loginLoading={false}
        loginForm={{ username: '', password: '' }}
        multiUserEnabled
        notice=""
        onCloseRegisterDialog={vi.fn()}
        onLogin={vi.fn()}
        onLoginChange={vi.fn()}
        onPasskeyLogin={vi.fn()}
        onOpenRegisterDialog={vi.fn()}
        onRegister={vi.fn()}
        onRegisterChange={vi.fn()}
        registerOpen
        passkeyLoading={false}
        passkeysSupported={true}
        registerForm={{ username: '', password: '', confirmPassword: '' }}
        registerLoading={false}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('Início de sessão administrativo seguro')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar com passkey' })).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Pedir acesso' })).toBeInTheDocument()
    expect(screen.getByLabelText('Nome de utilizador pedido')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Registar para aprovação' })).toBeInTheDocument()
  })
})
