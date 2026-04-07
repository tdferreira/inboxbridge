import { normalizeLocale, translate } from '@/lib/i18n'
import { formatDate } from '@/lib/formatters'

export const AUTH_EXPIRED_EVENT = 'inboxbridge:auth-expired'
const APP_CSRF_COOKIE = 'inboxbridge_csrf'
const APP_CSRF_HEADER = 'X-InboxBridge-CSRF'

let secureFetchInstalled = false

function currentLocale() {
  return normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language)
}

function readCookie(name) {
  if (typeof document === 'undefined') return ''
  return document.cookie
    .split(';')
    .map((chunk) => chunk.trim())
    .find((chunk) => chunk.startsWith(`${name}=`))
    ?.slice(name.length + 1) || ''
}

function isUnsafeMethod(method) {
  const normalized = String(method || 'GET').toUpperCase()
  return normalized !== 'GET' && normalized !== 'HEAD' && normalized !== 'OPTIONS'
}

function isSameOriginUrl(input) {
  if (typeof window === 'undefined') return true
  const resolved = new URL(typeof input === 'string' ? input : String(input?.url || input), window.location.origin)
  return resolved.origin === window.location.origin
}

export function installSecureApiFetch() {
  if (secureFetchInstalled || typeof window === 'undefined' || typeof window.fetch !== 'function') {
    return
  }
  const baseFetch = window.fetch.bind(window)
  window.fetch = (input, init = {}) => {
    const method = init.method || 'GET'
    const headers = new Headers(init.headers || {})
    if (isSameOriginUrl(input) && isUnsafeMethod(method) && !headers.has(APP_CSRF_HEADER)) {
      const csrfToken = readCookie(APP_CSRF_COOKIE)
      if (csrfToken) {
        headers.set(APP_CSRF_HEADER, csrfToken)
      }
    }
    return baseFetch(input, {
      credentials: 'same-origin',
      ...init,
      headers
    })
  }
  secureFetchInstalled = true
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

export async function requestSessionDeviceLocation(payload) {
  const response = await fetch('/api/auth/session/device-location', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  if (!response.ok) {
    throw new Error(await apiErrorText(response, 'Unable to save this device location'))
  }
  return null
}
