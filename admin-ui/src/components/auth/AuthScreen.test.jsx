import { fireEvent, render, screen } from '@testing-library/react'
import AuthScreen from './AuthScreen'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)
const languageOptions = ['en', 'pt-PT'].map((value) => ({ value, label: translate('en', `language.${value}`) }))

function buildAltchaChallenge() {
  return {
    enabled: true,
    provider: 'ALTCHA',
    altcha: {
      challengeId: 'challenge-1',
      algorithm: 'SHA-256',
      challenge: 'abc',
      salt: 'salt',
      signature: 'sig',
      maxNumber: 1000
    }
  }
}

describe('AuthScreen', () => {
  it('opens registration modal and submits login and registration actions', () => {
    const onLogin = vi.fn((event) => event.preventDefault())
    const onPasskeyLogin = vi.fn()
    const onRegister = vi.fn((event) => event.preventDefault())
    const onCloseRegisterDialog = vi.fn()
    let loginForm = { username: 'admin', password: 'nimda' }
    let registerForm = { username: '', password: '', confirmPassword: '', captchaToken: '' }
    let registerOpen = false

    const { rerender } = render(
      <AuthScreen
        authError=""
        loginStage="credentials"
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
        onRegister={onRegister}
        onRegisterChange={(updater) => {
          registerForm = typeof updater === 'function' ? updater(registerForm) : updater
          rerenderUi()
        }}
        passkeyLoading={false}
        passkeysSupported={true}
        registerChallenge={buildAltchaChallenge()}
        registerChallengeLoading={false}
        registerForm={registerForm}
        registerLoading={false}
        registerOpen={registerOpen}
        t={t}
      />
    )

    function rerenderUi() {
      rerender(
        <AuthScreen
          authError=""
          loginStage="credentials"
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
          onRegister={onRegister}
          onRegisterChange={(updater) => {
            registerForm = typeof updater === 'function' ? updater(registerForm) : updater
            rerenderUi()
          }}
          passkeyLoading={false}
          passkeysSupported={true}
          registerChallenge={buildAltchaChallenge()}
          registerChallengeLoading={false}
          registerForm={registerForm}
          registerLoading={false}
          registerOpen={registerOpen}
          t={t}
        />
      )
    }

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'ops-admin' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form'))
    fireEvent.click(screen.getByRole('button', { name: 'Sign in with passkey' }))
    fireEvent.click(screen.getByRole('button', { name: 'Register for access' }))
    fireEvent.change(screen.getByLabelText('Requested Username'), { target: { value: 'new-user' } })
    fireEvent.change(screen.getByLabelText('Requested Password'), { target: { value: 'Secret#123' } })
    fireEvent.change(screen.getByLabelText('Repeat Requested Password'), { target: { value: 'Secret#123' } })

    expect(onLogin).toHaveBeenCalledTimes(1)
    expect(onPasskeyLogin).toHaveBeenCalledTimes(1)
    expect(loginForm.username).toBe('ops-admin')
    expect(registerForm.username).toBe('new-user')
    expect(registerForm.confirmPassword).toBe('Secret#123')
  })

  it('shows loading feedback for login and registration buttons', () => {
    render(
      <AuthScreen
        authError=""
        loginStage="credentials"
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
        passkeyLoading
        passkeysSupported={true}
        registerChallenge={null}
        registerChallengeLoading={false}
        registerForm={{ username: '', password: '', confirmPassword: '', captchaToken: '' }}
        registerLoading
        registerOpen={false}
        t={t}
      />
    )

    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Opening Passkey…' })).toBeDisabled()
  })

  it('blocks registration until the repeated password matches and captcha is solved', () => {
    render(
      <AuthScreen
        authError=""
        loginStage="credentials"
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
        passkeyLoading={false}
        passkeysSupported={true}
        registerChallenge={buildAltchaChallenge()}
        registerChallengeLoading={false}
        registerForm={{ username: 'new-user', password: 'Secret#123', confirmPassword: 'Mismatch#123', captchaToken: '' }}
        registerLoading={false}
        registerOpen
        t={t}
      />
    )

    expect(screen.getByText(/repeat password must match/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Register For Approval' })).toBeDisabled()
  })

  it('requires completed captcha before enabling registration submit', () => {
    render(
      <AuthScreen
        authError=""
        loginStage="credentials"
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
        passkeyLoading={false}
        passkeysSupported={true}
        registerChallenge={buildAltchaChallenge()}
        registerChallengeLoading={false}
        registerForm={{ username: 'new-user', password: 'Secret#123', confirmPassword: 'Secret#123', captchaToken: '' }}
        registerLoading={false}
        registerOpen
        t={t}
      />
    )

    expect(screen.getByRole('button', { name: 'Register For Approval' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Verify CAPTCHA' })).toBeInTheDocument()
  })

  it('hides self-registration in single-user mode', () => {
    render(
      <AuthScreen
        authError=""
        loginStage="username"
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
        passkeyLoading={false}
        passkeysSupported={true}
        registerChallenge={null}
        registerChallengeLoading={false}
        registerForm={{ username: '', password: '', confirmPassword: '', captchaToken: '' }}
        registerLoading={false}
        registerOpen={false}
        t={t}
      />
    )

    expect(screen.queryByRole('button', { name: 'Register for access' })).not.toBeInTheDocument()
    expect(screen.getByText(/single-user mode/i)).toBeInTheDocument()
  })

  it('asks for only the username before showing password and passkey controls', () => {
    render(
      <AuthScreen
        authError=""
        loginStage="username"
        loginLoading={false}
        loginForm={{ username: '', password: '' }}
        multiUserEnabled
        notice=""
        onCloseRegisterDialog={vi.fn()}
        onLanguageChange={vi.fn()}
        onLogin={vi.fn()}
        onLoginChange={vi.fn()}
        onPasskeyLogin={vi.fn()}
        onOpenRegisterDialog={vi.fn()}
        onRegister={vi.fn()}
        onRegisterChange={vi.fn()}
        passkeyLoading={false}
        passkeysSupported
        registerChallenge={null}
        registerChallengeLoading={false}
        registerForm={{ username: '', password: '', confirmPassword: '', captchaToken: '' }}
        registerLoading={false}
        registerOpen={false}
        t={t}
      />
    )

    expect(screen.getByLabelText('Username')).toBeInTheDocument()
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sign in with passkey' })).not.toBeInTheDocument()
  })

  it('moves focus to the password field when the credential step opens', () => {
    const onLogin = vi.fn((event) => event.preventDefault())
    let loginStage = 'username'
    let loginForm = { username: 'admin', password: '' }

    const { rerender } = render(
      <AuthScreen
        authError=""
        loginStage={loginStage}
        loginLoading={false}
        loginForm={loginForm}
        multiUserEnabled
        notice=""
        onCloseRegisterDialog={vi.fn()}
        onLogin={(event) => {
          onLogin(event)
          loginStage = 'credentials'
          rerenderUi()
        }}
        onLoginChange={(updater) => {
          loginForm = typeof updater === 'function' ? updater(loginForm) : updater
          rerenderUi()
        }}
        onPasskeyLogin={vi.fn()}
        onOpenRegisterDialog={vi.fn()}
        onRegister={vi.fn()}
        onRegisterChange={vi.fn()}
        passkeyLoading={false}
        passkeysSupported={true}
        registerChallenge={null}
        registerChallengeLoading={false}
        registerForm={{ username: '', password: '', confirmPassword: '', captchaToken: '' }}
        registerLoading={false}
        registerOpen={false}
        t={t}
      />
    )

    function rerenderUi() {
      rerender(
        <AuthScreen
          authError=""
          loginStage={loginStage}
          loginLoading={false}
          loginForm={loginForm}
          multiUserEnabled
          notice=""
          onCloseRegisterDialog={vi.fn()}
          onLogin={(event) => {
            onLogin(event)
            loginStage = 'credentials'
            rerenderUi()
          }}
          onLoginChange={(updater) => {
            loginForm = typeof updater === 'function' ? updater(loginForm) : updater
            rerenderUi()
          }}
          onPasskeyLogin={vi.fn()}
          onOpenRegisterDialog={vi.fn()}
          onRegister={vi.fn()}
          onRegisterChange={vi.fn()}
          passkeyLoading={false}
          passkeysSupported={true}
          registerChallenge={null}
          registerChallengeLoading={false}
          registerForm={{ username: '', password: '', confirmPassword: '', captchaToken: '' }}
          registerLoading={false}
          registerOpen={false}
          t={t}
        />
      )
    }

    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByLabelText('Password')).toHaveFocus()
  })

  it('renders translated login and registration copy in portuguese', () => {
    render(
      <AuthScreen
        authError=""
        loginStage="credentials"
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
        passkeyLoading={false}
        passkeysSupported={true}
        registerChallenge={buildAltchaChallenge()}
        registerChallengeLoading={false}
        registerForm={{ username: '', password: '', confirmPassword: '', captchaToken: '' }}
        registerLoading={false}
        registerOpen
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByRole('heading', { name: 'InboxBridge' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar com passkey' })).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Pedir acesso' })).toBeInTheDocument()
    expect(screen.getByLabelText('Nome de utilizador pedido')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Registar para aprovação' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Verificar CAPTCHA' })).toBeInTheDocument()
  })

  it('does not repeat the eyebrow when the login title already shows InboxBridge', () => {
    render(
      <AuthScreen
        authError=""
        loginStage="credentials"
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
        passkeyLoading={false}
        passkeysSupported={true}
        registerChallenge={null}
        registerChallengeLoading={false}
        registerForm={{ username: '', password: '', confirmPassword: '', captchaToken: '' }}
        registerLoading={false}
        registerOpen={false}
        t={t}
      />
    )

    expect(screen.getByRole('heading', { name: 'InboxBridge' })).toBeInTheDocument()
    expect(screen.queryByText('InboxBridge', { selector: '.eyebrow' })).not.toBeInTheDocument()
  })

  it('lets unauthenticated users change the language from both the login and registration views', () => {
    const onLanguageChange = vi.fn()

    render(
      <AuthScreen
        authError=""
        language="en"
        languageOptions={languageOptions}
        loginLoading={false}
        loginForm={{ username: '', password: '' }}
        multiUserEnabled
        notice=""
        onCloseRegisterDialog={vi.fn()}
        onLanguageChange={onLanguageChange}
        onLogin={vi.fn()}
        onLoginChange={vi.fn()}
        onPasskeyLogin={vi.fn()}
        onOpenRegisterDialog={vi.fn()}
        onRegister={vi.fn()}
        onRegisterChange={vi.fn()}
        passkeyLoading={false}
        passkeysSupported={true}
        registerChallenge={buildAltchaChallenge()}
        registerChallengeLoading={false}
        registerForm={{ username: '', password: '', confirmPassword: '', captchaToken: '' }}
        registerLoading={false}
        registerOpen
        t={t}
      />
    )

    expect(screen.getByRole('button', { name: 'Language' })).toHaveTextContent('🇬🇧')
    expect(screen.getAllByLabelText('Language')).toHaveLength(2)

    fireEvent.click(screen.getByRole('button', { name: 'Language' }))
    fireEvent.click(screen.getByRole('menuitemradio', { name: 'Português (Portugal)' }))

    expect(onLanguageChange).toHaveBeenCalledWith('pt-PT')
  })
})
