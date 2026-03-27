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
      .mockResolvedValueOnce(jsonResponse({ multiUserEnabled: true }))
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
      .mockResolvedValueOnce(jsonResponse({
        defaultPollEnabled: true,
        pollEnabledOverride: null,
        effectivePollEnabled: true,
        defaultPollInterval: '5m',
        pollIntervalOverride: null,
        effectivePollInterval: '5m',
        defaultFetchWindow: 50,
        fetchWindowOverride: null,
        effectiveFetchWindow: 50
      }))
      .mockResolvedValueOnce(jsonResponse({
        totalImportedMessages: 0,
        configuredMailFetchers: 0,
        enabledMailFetchers: 0,
        sourcesWithErrors: 0,
        importsByDay: []
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
      if (url === '/api/auth/options') {
        return jsonResponse({ multiUserEnabled: true })
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
      if (url === '/api/app/polling-settings') {
        return jsonResponse({
          defaultPollEnabled: true,
          pollEnabledOverride: null,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          pollIntervalOverride: null,
          effectivePollInterval: '5m',
          defaultFetchWindow: 50,
          fetchWindowOverride: null,
          effectiveFetchWindow: 50
        })
      }
      if (url === '/api/app/polling-stats') {
        return jsonResponse({
          totalImportedMessages: 0,
          configuredMailFetchers: 0,
          enabledMailFetchers: 0,
          sourcesWithErrors: 0,
          importsByDay: []
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
    fireEvent.change(screen.getByLabelText('Current Password'), { target: { value: 'Secret#123' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Remove Password' }))

    expect(screen.getByText('Remove password?')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/account/password', expect.objectContaining({ method: 'DELETE' }))

    fireEvent.click(within(screen.getByRole('dialog', { name: 'Remove password?' })).getByRole('button', { name: 'Remove Password' }))

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/account/password', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPassword: 'Secret#123' })
      })
    })
    expect(await screen.findByText('Password removed. This account now requires passkey sign-in.')).toBeInTheDocument()
  })

  it('hides multi-user surfaces when the deployment runs in single-user mode', async () => {
    const fetchMock = vi.fn(async (input) => {
      const url = String(input)
      if (url === '/api/auth/options') {
        return jsonResponse({ multiUserEnabled: false })
      }
      if (url === '/api/auth/me') {
        return jsonResponse({
          id: 1,
          username: 'admin',
          role: 'ADMIN',
          approved: true,
          mustChangePassword: false,
          passkeyCount: 0,
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
      if (url === '/api/app/polling-settings') {
        return jsonResponse({
          defaultPollEnabled: true,
          pollEnabledOverride: null,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          pollIntervalOverride: null,
          effectivePollInterval: '5m',
          defaultFetchWindow: 50,
          fetchWindowOverride: null,
          effectiveFetchWindow: 50
        })
      }
      if (url === '/api/app/polling-stats') {
        return jsonResponse({
          totalImportedMessages: 0,
          configuredMailFetchers: 0,
          enabledMailFetchers: 0,
          sourcesWithErrors: 0,
          importsByDay: []
        })
      }
      if (url === '/api/app/bridges') {
        return jsonResponse([])
      }
      if (url === '/api/app/ui-preferences') {
        return jsonResponse({})
      }
      if (url === '/api/account/passkeys') {
        return jsonResponse([])
      }
      if (url === '/api/admin/dashboard') {
        return jsonResponse({
          overall: {
            configuredSources: 0,
            enabledSources: 0,
            totalImportedMessages: 0,
            sourcesWithErrors: 0,
            pollInterval: '5m',
            fetchWindow: 50
          },
          stats: {
            totalImportedMessages: 0,
            configuredMailFetchers: 0,
            enabledMailFetchers: 0,
            sourcesWithErrors: 0,
            importsByDay: []
          },
          polling: {
            defaultPollEnabled: true,
            pollEnabledOverride: null,
            effectivePollEnabled: true,
            defaultPollInterval: '5m',
            pollIntervalOverride: null,
            effectivePollInterval: '5m',
            defaultFetchWindow: 50,
            fetchWindowOverride: null,
            effectiveFetchWindow: 50
          },
          bridges: []
        })
      }
      throw new Error(`Unexpected request: GET ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    expect(screen.queryByText('Users')).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/admin/users')
  })

  it('shows password and passkey tools in separate security tabs', async () => {
    const fetchMock = vi.fn(async (input) => {
      const url = String(input)
      if (url === '/api/auth/options') {
        return jsonResponse({ multiUserEnabled: true })
      }
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
      if (url === '/api/app/polling-settings') {
        return jsonResponse({
          defaultPollEnabled: true,
          pollEnabledOverride: null,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          pollIntervalOverride: null,
          effectivePollInterval: '5m',
          defaultFetchWindow: 50,
          fetchWindowOverride: null,
          effectiveFetchWindow: 50
        })
      }
      if (url === '/api/app/polling-stats') {
        return jsonResponse({
          totalImportedMessages: 0,
          configuredMailFetchers: 0,
          enabledMailFetchers: 0,
          sourcesWithErrors: 0,
          importsByDay: []
        })
      }
      if (url === '/api/app/bridges') {
        return jsonResponse([])
      }
      if (url === '/api/app/ui-preferences') {
        return jsonResponse({})
      }
      if (url === '/api/account/passkeys') {
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

      throw new Error(`Unexpected request: ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)

    fireEvent.click(screen.getByRole('button', { name: 'Security' }))

    expect(screen.getByRole('tab', { name: 'Password', selected: true })).toBeInTheDocument()
    expect(screen.queryByText('Register Passkey')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: 'Passkeys' }))

    expect(screen.getByRole('tab', { name: 'Passkeys', selected: true })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Register Passkey' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Current Password')).not.toBeInTheDocument()
  })
})
