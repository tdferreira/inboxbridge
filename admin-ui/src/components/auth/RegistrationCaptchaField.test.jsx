import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import RegistrationCaptchaField from './RegistrationCaptchaField'
import { translate } from '../../lib/i18n'

const solveAltchaChallenge = vi.fn()

vi.mock('../../lib/altcha', () => ({
  solveAltchaChallenge: (...args) => solveAltchaChallenge(...args)
}))

const t = (key, params) => translate('en', key, params)

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

describe('RegistrationCaptchaField', () => {
  beforeEach(() => {
    solveAltchaChallenge.mockReset()
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
})
