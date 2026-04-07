import {
  captchaProviderLabel,
  parseCaptchaProviderList,
  registrationCaptchaProviderCatalog
} from './captchaProviders'

describe('captchaProviders', () => {
  it('parses the configured provider list and resolves labels', () => {
    expect(parseCaptchaProviderList('ALTCHA, TURNSTILE')).toEqual(['ALTCHA', 'TURNSTILE'])
    expect(captchaProviderLabel('HCAPTCHA')).toBe('hCaptcha')
  })

  it('exposes the known provider catalog', () => {
    expect(Object.values(registrationCaptchaProviderCatalog)).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ id: 'ALTCHA' }),
        expect.objectContaining({ id: 'TURNSTILE' }),
        expect.objectContaining({ id: 'HCAPTCHA' })
      ])
    )
  })
})
