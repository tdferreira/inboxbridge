import { normalizeLocale, translate } from './i18n'
import { formatDate } from './formatters'

export const AUTH_EXPIRED_EVENT = 'inboxbridge:auth-expired'

function currentLocale() {
  return normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language)
}

function translatedApiCode(code, locale) {
  if (!code) return ''
  const translated = translate(locale, `api.${code}`)
  return translated === `api.${code}` ? '' : translated
}

export function apiErrorText(response, fallback) {
  if (response?.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
  }
  return response.text().then((text) => {
    if (!text) return fallback
    const trimmed = text.trim()
    const looksLikeHtml = trimmed.startsWith('<!DOCTYPE') || trimmed.startsWith('<html') || trimmed.startsWith('<HTML')
    try {
      const parsed = JSON.parse(text)
      const locale = currentLocale()
      if (parsed.code === 'auth_login_blocked' && parsed.meta?.blockedUntil) {
        return translate(locale, 'api.auth_login_blocked', {
          value: formatDate(parsed.meta.blockedUntil, locale)
        })
      }
      const translated = translatedApiCode(parsed.code, locale)
      if (parsed.code === 'bad_request' || parsed.code === 'forbidden') {
        return parsed.details || parsed.message || translated || fallback
      }
      return translated || parsed.details || parsed.message || fallback
    } catch {
      if (looksLikeHtml) {
        const statusSuffix = response?.status
          ? ` (${response.status}${response.statusText ? ` ${response.statusText}` : ''})`
          : ''
        return `${fallback}${statusSuffix}`
      }
      return text
    }
  })
}
