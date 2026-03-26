import { fireEvent, render, screen } from '@testing-library/react'
import AuthScreen from './AuthScreen'

describe('AuthScreen', () => {
  it('renders bootstrap guidance and submits login and registration actions', () => {
    const onLogin = vi.fn((event) => event.preventDefault())
    const onRegister = vi.fn((event) => event.preventDefault())
    let loginForm = { username: 'admin', password: 'nimda' }
    let registerForm = { username: '', password: '' }

    const { rerender } = render(
      <AuthScreen
        authError=""
        loginLoading={false}
        loginForm={loginForm}
        notice=""
        onLogin={onLogin}
        onLoginChange={(updater) => {
          loginForm = typeof updater === 'function' ? updater(loginForm) : updater
          rerenderUi()
        }}
        registerLoading={false}
        onRegister={onRegister}
        onRegisterChange={(updater) => {
          registerForm = typeof updater === 'function' ? updater(registerForm) : updater
          rerenderUi()
        }}
        registerForm={registerForm}
      />
    )

    function rerenderUi() {
      rerender(
        <AuthScreen
          authError=""
          loginLoading={false}
          loginForm={loginForm}
          notice=""
          onLogin={onLogin}
          onLoginChange={(updater) => {
            loginForm = typeof updater === 'function' ? updater(loginForm) : updater
            rerenderUi()
          }}
          registerLoading={false}
          onRegister={onRegister}
          onRegisterChange={(updater) => {
            registerForm = typeof updater === 'function' ? updater(registerForm) : updater
            rerenderUi()
          }}
          registerForm={registerForm}
        />
      )
    }

    expect(screen.getByText(/bootstrap account/i)).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'ops-admin' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form'))
    fireEvent.change(screen.getByLabelText('Requested Username'), { target: { value: 'new-user' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Register For Approval' }).closest('form'))

    expect(onLogin).toHaveBeenCalledTimes(1)
    expect(onRegister).toHaveBeenCalledTimes(1)
    expect(loginForm.username).toBe('ops-admin')
    expect(registerForm.username).toBe('new-user')
  })

  it('shows loading feedback for login and registration buttons', () => {
    render(
      <AuthScreen
        authError=""
        loginLoading
        loginForm={{ username: 'admin', password: 'nimda' }}
        notice=""
        onLogin={vi.fn()}
        onLoginChange={vi.fn()}
        onRegister={vi.fn()}
        onRegisterChange={vi.fn()}
        registerForm={{ username: '', password: '' }}
        registerLoading
      />
    )

    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Registering…' })).toBeDisabled()
  })
})
