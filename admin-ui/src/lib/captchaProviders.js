export const registrationCaptchaProviderCatalog = {
  ALTCHA: {
    id: 'ALTCHA',
    label: 'ALTCHA',
    docsUrl: 'https://altcha.org/docs/',
    termsUrl: 'https://altcha.org/terms',
    requiresSiteKey: false,
    requiresSecret: false
  },
  TURNSTILE: {
    id: 'TURNSTILE',
    label: 'Cloudflare Turnstile',
    docsUrl: 'https://developers.cloudflare.com/turnstile/',
    termsUrl: 'https://www.cloudflare.com/website-terms/',
    requiresSiteKey: true,
    requiresSecret: true
  },
  HCAPTCHA: {
    id: 'HCAPTCHA',
    label: 'hCaptcha',
    docsUrl: 'https://docs.hcaptcha.com/',
    termsUrl: 'https://www.hcaptcha.com/terms',
    requiresSiteKey: true,
    requiresSecret: true
  }
}

export function parseCaptchaProviderList(value) {
  if (!value) {
    return []
  }
  return value
    .split(',')
    .map((item) => item.trim().toUpperCase())
    .filter(Boolean)
}

export function captchaProviderLabel(providerId) {
  return registrationCaptchaProviderCatalog[providerId]?.label || providerId
}
