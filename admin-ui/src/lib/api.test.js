import { describe, expect, it, beforeEach, vi } from 'vitest'
import { apiErrorText, AUTH_EXPIRED_EVENT } from './api'

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
