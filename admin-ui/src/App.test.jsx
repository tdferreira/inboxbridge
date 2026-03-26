import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import App from './App'

function jsonResponse(payload) {
  return {
    ok: true,
    status: 200,
    json: () => Promise.resolve(payload),
    text: () => Promise.resolve(JSON.stringify(payload))
  }
}

function textError(message) {
  return {
    ok: false,
    status: 400,
    json: () => Promise.resolve({ message }),
    text: () => Promise.resolve(message)
  }
}

describe('App', () => {
  it('falls back to passkey login when password login is blocked by passkey policy', async () => {
    Object.defineProperty(window, 'PublicKeyCredential', {
      configurable: true,
      value: function PublicKeyCredential() {}
    })
    Object.assign(navigator, {
      credentials: {
        get: vi.fn().mockResolvedValue({
          id: 'cred-1',
          rawId: new Uint8Array([1, 2, 3]).buffer,
          type: 'public-key',
          response: {
            clientDataJSON: new Uint8Array([1, 2, 3]).buffer,
            authenticatorData: new Uint8Array([4, 5, 6]).buffer,
            signature: new Uint8Array([7, 8, 9]).buffer,
            userHandle: null
          },
          getClientExtensionResults: () => ({})
        })
      }
    })

    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 401, text: () => Promise.resolve(''), json: () => Promise.resolve({}) })
      .mockResolvedValueOnce(jsonResponse({
        status: 'PASSKEY_REQUIRED',
        passkeyChallenge: { ceremonyId: 'ceremony-1', publicKeyJson: '{"challenge":"AQID","allowCredentials":[]}' }
      }))
      .mockResolvedValueOnce(jsonResponse({
        status: 'AUTHENTICATED',
        user: {
          id: 1,
          username: 'alice',
          role: 'USER',
          approved: true,
          mustChangePassword: false,
          passkeyCount: 1,
          passwordConfigured: false
        }
      }))
      .mockResolvedValueOnce(jsonResponse({
        destinationUser: 'me',
        redirectUri: '',
        createMissingLabels: true,
        neverMarkSpam: false,
        processForCalendar: false
      }))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({}))
      .mockResolvedValueOnce(jsonResponse([]))

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Sign in' }))

    await screen.findByText(/signed in as/i)
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/passkey/verify', expect.any(Object))
    expect(await screen.findByText('Signed in with passkey.')).toBeInTheDocument()
  })

  it('confirms password removal before deleting the password', async () => {
    const fetchMock = vi.fn(async (input, init = {}) => {
      const url = String(input)
      const method = init.method || 'GET'

      if (url === '/api/auth/me') {
        return jsonResponse({
          id: 1,
          username: 'alice',
          role: 'USER',
          approved: true,
          mustChangePassword: false,
          passkeyCount: 1,
          passwordConfigured: true
        })
      }
      if (url === '/api/app/gmail-config') {
        return jsonResponse({
          destinationUser: 'me',
          redirectUri: 'https://localhost:3000/api/google-oauth/callback',
          createMissingLabels: true,
          neverMarkSpam: false,
          processForCalendar: false
        })
      }
      if (url === '/api/app/bridges') {
        return jsonResponse([])
      }
      if (url === '/api/app/ui-preferences') {
        return jsonResponse({})
      }
      if (url === '/api/account/passkeys') {
        if (method === 'DELETE') {
          throw new Error('Unexpected passkey deletion during password removal test')
        }
        return jsonResponse([
          {
            id: 8,
            label: 'MacBook',
            discoverable: true,
            backupEligible: true,
            backedUp: true,
            createdAt: '2026-03-26T10:00:00Z',
            lastUsedAt: '2026-03-26T10:05:00Z'
          }
        ])
      }
      if (url === '/api/account/password' && method === 'DELETE') {
        return jsonResponse({})
      }

      throw new Error(`Unexpected request: ${method} ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)

    fireEvent.click(screen.getByRole('button', { name: 'Security' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Remove Password' }))

    expect(screen.getByText('Remove password?')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/account/password', expect.objectContaining({ method: 'DELETE' }))

    fireEvent.click(within(screen.getByRole('dialog', { name: 'Remove password?' })).getByRole('button', { name: 'Remove Password' }))

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/account/password', { method: 'DELETE' })
    })
    expect(await screen.findByText('Password removed. This account now requires passkey sign-in.')).toBeInTheDocument()
  })
})
