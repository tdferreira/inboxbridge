import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import App from './App'
import { jsonResponse } from '@/test/appTestHelpers'

describe('App OAuth callback routes', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
    window.history.replaceState({}, '', '/')
  })

  it('bypasses the main workspace fetches on Google callback routes', async () => {
    window.history.replaceState({}, '', '/oauth/google/callback?code=code-123&state=state-1&lang=en')
    const fetchSpy = vi.fn(async (input) => {
      const url = String(input)
      if (url === '/api/google-oauth/exchange') {
        return jsonResponse({
          storedInDatabase: true,
          usingEnvironmentFallback: false,
          replacedExistingAccount: false,
          sameLinkedAccount: true,
          previousGrantRevoked: false,
          credentialKey: 'db:GOOGLE:gmail-destination',
          scope: 'gmail.insert gmail.labels',
          tokenType: 'Bearer',
          accessTokenExpiresAt: '2026-04-08T10:00:00Z',
          nextStep: 'Stored securely in encrypted storage.'
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchSpy)

    render(<App />)

    expect(await screen.findByText('Complete Google OAuth')).toBeInTheDocument()
    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledTimes(1)
    })
    expect(fetchSpy).toHaveBeenCalledWith('/api/google-oauth/exchange', expect.objectContaining({
      method: 'POST'
    }))
  })
})
