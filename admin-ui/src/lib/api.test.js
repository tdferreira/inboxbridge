import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiErrorText, AUTH_EXPIRED_EVENT } from '@/lib/api'

describe('apiErrorText', () => {
  beforeEach(() => {
    window.localStorage.setItem('inboxbridge.language', 'pt-PT')
  })

  it('translates structured backend error codes in the frontend', async () => {
    const response = {
      text: vi.fn().mockResolvedValue(JSON.stringify({
        code: 'auth_invalid_credentials',
        message: 'Invalid username or password.'
      }))
    }

    await expect(apiErrorText(response, 'fallback')).resolves.toBe('Nome de utilizador ou palavra-passe inválidos.')
  })

  it('falls back to backend message when the code has no frontend translation', async () => {
    const response = {
      text: vi.fn().mockResolvedValue(JSON.stringify({
        code: 'unknown_code',
        message: 'Raw backend message'
      }))
    }

    await expect(apiErrorText(response, 'fallback')).resolves.toBe('Raw backend message')
  })

  it('prefers structured details over the generic bad request translation', async () => {
    const response = {
      text: vi.fn().mockResolvedValue(JSON.stringify({
        code: 'bad_request',
        message: 'Failed to connect to IMAP mail fetcher outlook-main',
        details: 'AUTHENTICATE failed'
      }))
    }

    await expect(apiErrorText(response, 'fallback')).resolves.toBe('AUTHENTICATE failed')
  })

  it('formats login lockout errors with the blocked-until timestamp', async () => {
    const response = {
      text: vi.fn().mockResolvedValue(JSON.stringify({
        code: 'auth_login_blocked',
        message: 'Too many failed sign-in attempts from this address.',
        meta: {
          blockedUntil: '2026-03-30T15:45:00Z'
        }
      }))
    }

    await expect(apiErrorText(response, 'fallback')).resolves.toContain('30/03/2026')
  })

  it('dispatches the auth-expired event on 401 responses', async () => {
    const listener = vi.fn()
    window.addEventListener(AUTH_EXPIRED_EVENT, listener)

    const response = {
      status: 401,
      text: vi.fn().mockResolvedValue(JSON.stringify({
        code: 'auth_session_invalid',
        message: 'Session is no longer valid'
      }))
    }

    await expect(apiErrorText(response, 'fallback')).resolves.toBe('Session is no longer valid')

    expect(listener).toHaveBeenCalledTimes(1)
    window.removeEventListener(AUTH_EXPIRED_EVENT, listener)
  })
})

describe('installSecureApiFetch', () => {
  afterEach(() => {
    document.cookie = 'inboxbridge_csrf=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it('adds the csrf header to unsafe same-origin requests', async () => {
    const baseFetch = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', baseFetch)
    document.cookie = 'inboxbridge_csrf=csrf-123; path=/'

    const { installSecureApiFetch } = await import('./api')
    installSecureApiFetch()

    await window.fetch('/api/poll/live/pause', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    })

    expect(baseFetch).toHaveBeenCalledTimes(1)
    const [, init] = baseFetch.mock.calls[0]
    const headers = new Headers(init.headers)
    expect(init.credentials).toBe('same-origin')
    expect(headers.get('X-InboxBridge-CSRF')).toBe('csrf-123')
  })

  it('does not inject the csrf header into cross-origin requests', async () => {
    const baseFetch = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', baseFetch)
    document.cookie = 'inboxbridge_csrf=csrf-123; path=/'

    const { installSecureApiFetch } = await import('./api')
    installSecureApiFetch()

    await window.fetch('https://other.example.com/api/poll/live/pause', { method: 'POST' })

    const [, init] = baseFetch.mock.calls[0]
    const headers = new Headers(init.headers)
    expect(headers.has('X-InboxBridge-CSRF')).toBe(false)
  })
})
