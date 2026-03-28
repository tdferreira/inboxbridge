import { describe, expect, it, beforeEach, vi } from 'vitest'
import { apiErrorText } from './api'

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
})
