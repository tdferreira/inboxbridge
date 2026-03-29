import { buildPasswordChecks, canSubmitPasswordChange } from './passwordPolicy'

describe('passwordPolicy', () => {
  it('includes the different-password rule when current password is present', () => {
    const checks = buildPasswordChecks('Current1!', 'Current1!', 'Current1!', (key) => key)

    expect(checks.map((item) => item.label)).toContain('passwordPolicy.different')
    expect(checks.find((item) => item.label === 'passwordPolicy.different')).toEqual({
      label: 'passwordPolicy.different',
      valid: false
    })
  })

  it('omits the different-password rule when explicitly disabled', () => {
    const checks = buildPasswordChecks('', 'NewPass1!', 'NewPass1!', (key) => key, { requireDifferent: false })

    expect(checks.map((item) => item.label)).not.toContain('passwordPolicy.different')
  })

  it('only allows submission when all checks pass', () => {
    expect(canSubmitPasswordChange('Current1!', 'NewPass1!', 'NewPass1!')).toBe(true)
    expect(canSubmitPasswordChange('Current1!', 'short', 'short')).toBe(false)
  })

  it('can allow password setup without a current password when configured', () => {
    expect(canSubmitPasswordChange('', 'NewPass1!', 'NewPass1!', {
      requireCurrentPassword: false,
      requireDifferent: false
    })).toBe(true)
  })
})
