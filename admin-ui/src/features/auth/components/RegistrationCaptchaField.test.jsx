import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import RegistrationCaptchaField from './RegistrationCaptchaField'
import { translate } from '@/lib/i18n'

const solveAltchaChallenge = vi.fn()

vi.mock('@/lib/altcha', () => ({
  solveAltchaChallenge: (...args) => solveAltchaChallenge(...args)
}))

const t = (key, params) => translate('en', key, params)

function buildAltchaChallenge() {
  return {
    enabled: true,
    provider: 'ALTCHA',
    altcha: {
      challengeId: 'challenge-1',
      parameters: {
        algorithm: 'PBKDF2/SHA-256',
        nonce: '00112233445566778899aabbccddeeff',
        salt: '0f0e0d0c0b0a09080706050403020100',
        cost: 5000,
        keyLength: 32,
        keyPrefix: '00'
      },
      signature: 'sig'
    }
  }
}

describe('RegistrationCaptchaField', () => {
  beforeEach(() => {
    solveAltchaChallenge.mockReset()
    delete window.turnstile
    delete window.hcaptcha
    document.head.querySelectorAll('script[data-external-script]').forEach((script) => script.remove())
  })

  it('shows loading feedback while Altcha verification is processing', async () => {
    let resolveSolve
    solveAltchaChallenge.mockReturnValue(new Promise((resolve) => {
      resolveSolve = resolve
    }))

    const onRegisterChange = vi.fn()

    render(
      <RegistrationCaptchaField
        onRegisterChange={onRegisterChange}
        registerChallenge={buildAltchaChallenge()}
        registerChallengeLoading={false}
        registerForm={{ captchaToken: '' }}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Verify CAPTCHA' }))

    expect(await screen.findByRole('button', { name: 'Verifying…' })).toBeDisabled()

    resolveSolve('solved-token')

    await waitFor(() => {
      expect(onRegisterChange).toHaveBeenCalledTimes(1)
    })
    expect(screen.getByRole('button', { name: 'Verify CAPTCHA' })).toBeInTheDocument()
  })

  it('shows challenge loading feedback while the registration challenge is loading', () => {
    render(
      <RegistrationCaptchaField
        onRegisterChange={vi.fn()}
        registerChallenge={null}
        registerChallengeLoading
        registerForm={{ captchaToken: '' }}
        t={t}
      />
    )

    expect(screen.getByText('Loading anti-robot check…')).toBeInTheDocument()
  })

  it('renders nothing when captcha verification is disabled', () => {
    const { container } = render(
      <RegistrationCaptchaField
        onRegisterChange={vi.fn()}
        registerChallenge={{ enabled: false }}
        registerChallengeLoading={false}
        registerForm={{ captchaToken: '' }}
        t={t}
      />
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('allows resetting a solved Altcha token', () => {
    const onRegisterChange = vi.fn()

    render(
      <RegistrationCaptchaField
        onRegisterChange={onRegisterChange}
        registerChallenge={buildAltchaChallenge()}
        registerChallengeLoading={false}
        registerForm={{ captchaToken: 'solved-token' }}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Reset Verification' }))

    expect(onRegisterChange).toHaveBeenCalledTimes(1)
    const updater = onRegisterChange.mock.calls[0][0]
    expect(updater({ captchaToken: 'solved-token', username: 'alice' })).toEqual({
      captchaToken: '',
      username: 'alice'
    })
  })

  it('mounts the Turnstile widget and forwards solved tokens', async () => {
    const onRegisterChange = vi.fn()
    let widgetOptions
    window.turnstile = {
      render: vi.fn((_container, options) => {
        widgetOptions = options
        return 'widget-1'
      }),
      remove: vi.fn()
    }

    const { unmount } = render(
      <RegistrationCaptchaField
        onRegisterChange={onRegisterChange}
        registerChallenge={{ enabled: true, provider: 'TURNSTILE', siteKey: 'site-key' }}
        registerChallengeLoading={false}
        registerForm={{ captchaToken: '' }}
        t={t}
      />
    )

    const script = document.head.querySelector('script[data-external-script="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"]')
    expect(script).not.toBeNull()
    fireEvent.load(script)

    await waitFor(() => {
      expect(window.turnstile.render).toHaveBeenCalledTimes(1)
    })

    widgetOptions.callback('turnstile-token')
    expect(onRegisterChange).toHaveBeenCalledTimes(1)
    const updater = onRegisterChange.mock.calls[0][0]
    expect(updater({ captchaToken: '', username: 'alice' })).toEqual({
      captchaToken: 'turnstile-token',
      username: 'alice'
    })

    unmount()
    expect(window.turnstile.remove).toHaveBeenCalledWith('widget-1')
  })

  it('shows an external captcha load error when the widget script fails', async () => {
    render(
      <RegistrationCaptchaField
        onRegisterChange={vi.fn()}
        registerChallenge={{ enabled: true, provider: 'HCAPTCHA', siteKey: 'site-key' }}
        registerChallengeLoading={false}
        registerForm={{ captchaToken: '' }}
        t={t}
      />
    )

    const script = document.head.querySelector('script[data-external-script="https://js.hcaptcha.com/1/api.js?render=explicit"]')
    expect(script).not.toBeNull()
    fireEvent.error(script)

    expect(await screen.findByText('The CAPTCHA widget could not be loaded. Check the provider configuration or try again.')).toBeInTheDocument()
  })
})
