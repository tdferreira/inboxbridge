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
    statusText: 'Bad Request',
    json: () => Promise.resolve({ message }),
    text: () => Promise.resolve(message)
  }
}

function htmlError(status, statusText, html) {
  return {
    ok: false,
    status,
    statusText,
    json: () => Promise.resolve({}),
    text: () => Promise.resolve(html)
  }
}

describe('App', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    window.localStorage.clear()
  })

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
  }, 10000)

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

  it('lets admins switch between user and administration workspaces', async () => {
    const fetchMock = vi.fn(async (input) => {
      const url = String(input)
      if (url === '/api/auth/options') return jsonResponse({ multiUserEnabled: true })
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
          totalImportedMessages: 2,
          configuredMailFetchers: 1,
          enabledMailFetchers: 1,
          sourcesWithErrors: 0,
          importsByDay: [],
          importTimelines: {},
          duplicateTimelines: {},
          errorTimelines: {},
          health: { activeMailFetchers: 1, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
          providerBreakdown: [],
          manualRuns: 1,
          scheduledRuns: 2,
          averagePollDurationMillis: 1200
        })
      }
      if (url === '/api/app/bridges') return jsonResponse([])
      if (url === '/api/app/ui-preferences') return jsonResponse({})
      if (url === '/api/account/passkeys') return jsonResponse([])
      if (url === '/api/admin/oauth-app-settings') {
        return jsonResponse({
          effectiveMultiUserEnabled: true,
          multiUserEnabledOverride: null,
          googleDestinationUser: 'me',
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          googleClientId: '',
          googleClientSecretConfigured: false,
          googleRefreshTokenConfigured: false,
          microsoftClientId: '',
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback',
          microsoftClientSecretConfigured: false,
          secureStorageConfigured: true
        })
      }
      if (url === '/api/admin/dashboard') {
        return jsonResponse({
          overall: {
            configuredSources: 1,
            enabledSources: 1,
            totalImportedMessages: 10,
            sourcesWithErrors: 0,
            pollInterval: '5m',
            fetchWindow: 50
          },
          stats: {
            totalImportedMessages: 10,
            configuredMailFetchers: 3,
            enabledMailFetchers: 2,
            sourcesWithErrors: 0,
            importsByDay: [],
            importTimelines: {},
            duplicateTimelines: {},
            errorTimelines: {},
            health: { activeMailFetchers: 2, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 1 },
            providerBreakdown: [],
            manualRuns: 2,
            scheduledRuns: 8,
            averagePollDurationMillis: 1300
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
          bridges: [],
          recentEvents: []
        })
      }
      if (url === '/api/admin/users') return jsonResponse([])
      throw new Error(`Unexpected request: ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    expect(screen.getByRole('tab', { name: /User|Utilizador/, selected: true })).toBeInTheDocument()
    expect(screen.getByText(/My Gmail Account|A minha conta Gmail/, { selector: '.section-title' })).toBeInTheDocument()
    expect(screen.queryByText(/Global Polling Settings|Definições globais de verificação/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: /Administration|Administração/ }))

    expect(screen.getByRole('tab', { name: /Administration|Administração/, selected: true })).toBeInTheDocument()
    expect(await screen.findByText(/Global Polling Settings|Definições globais de verificação/)).toBeInTheDocument()
    expect(screen.queryByText(/My Gmail Account|A minha conta Gmail/, { selector: '.section-title' })).not.toBeInTheDocument()
  })

  it('keeps admin user rows collapsed until one is explicitly opened', async () => {
    const fetchMock = vi.fn(async (input) => {
      const url = String(input)
      if (url === '/api/auth/options') {
        return jsonResponse({
          multiUserEnabled: true,
          microsoftOAuthAvailable: true,
          googleOAuthAvailable: true,
          sourceOAuthProviders: ['MICROSOFT', 'GOOGLE']
        })
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
      if (url === '/api/app/bridges') return jsonResponse([])
      if (url === '/api/app/ui-preferences') return jsonResponse({})
      if (url === '/api/account/passkeys') return jsonResponse([])
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
            effectiveFetchWindow: 50,
            defaultManualTriggerLimitCount: 5,
            manualTriggerLimitCountOverride: null,
            effectiveManualTriggerLimitCount: 5,
            defaultManualTriggerLimitWindowSeconds: 60,
            manualTriggerLimitWindowSecondsOverride: null,
            effectiveManualTriggerLimitWindowSeconds: 60
          },
          bridges: [],
          recentEvents: []
        })
      }
      if (url === '/api/admin/oauth-app-settings') {
        return jsonResponse({
          effectiveMultiUserEnabled: true,
          multiUserEnabledOverride: null,
          googleDestinationUser: 'me',
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          googleClientId: '',
          googleClientSecretConfigured: false,
          googleRefreshTokenConfigured: false,
          microsoftClientId: '',
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback',
          microsoftClientSecretConfigured: false,
          secureStorageConfigured: true
        })
      }
      if (url === '/api/admin/users') {
        return jsonResponse([
          {
            id: 1,
            username: 'admin',
            role: 'ADMIN',
            approved: true,
            active: true,
            bridgeCount: 0,
            gmailConfigured: true,
            passwordConfigured: true,
            mustChangePassword: false,
            passkeyCount: 0
          },
          {
            id: 2,
            username: 'alice',
            role: 'USER',
            approved: true,
            active: true,
            bridgeCount: 0,
            gmailConfigured: false,
            passwordConfigured: true,
            mustChangePassword: false,
            passkeyCount: 0
          }
        ])
      }
      if (url.includes('/api/admin/users/') && url.endsWith('/configuration')) {
        throw new Error(`Unexpected user configuration preload: ${url}`)
      }
      throw new Error(`Unexpected request: ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    fireEvent.click(screen.getByRole('tab', { name: /Administration|Administração/ }))

    await screen.findByText('admin')
    await screen.findByText('alice')
    expect(screen.queryByText(/Gmail Destination|Destino Gmail/)).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/admin/users/1/configuration')
    expect(fetchMock).not.toHaveBeenCalledWith('/api/admin/users/2/configuration')
  })

  it('uses single-user confirmation copy for the admin run-poll action', async () => {
    const fetchMock = vi.fn(async (input) => {
      const url = String(input)
      if (url === '/api/auth/options') {
        return jsonResponse({
          multiUserEnabled: false,
          microsoftOAuthAvailable: true,
          googleOAuthAvailable: true,
          sourceOAuthProviders: ['MICROSOFT', 'GOOGLE']
        })
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
      if (url === '/api/app/gmail-config') return jsonResponse({ destinationUser: 'me', redirectUri: 'https://localhost:3000/api/google-oauth/callback', createMissingLabels: true, neverMarkSpam: false, processForCalendar: false })
      if (url === '/api/app/polling-settings') return jsonResponse({ defaultPollEnabled: true, pollEnabledOverride: null, effectivePollEnabled: true, defaultPollInterval: '5m', pollIntervalOverride: null, effectivePollInterval: '5m', defaultFetchWindow: 50, fetchWindowOverride: null, effectiveFetchWindow: 50 })
      if (url === '/api/app/polling-stats') return jsonResponse({ totalImportedMessages: 0, configuredMailFetchers: 0, enabledMailFetchers: 0, sourcesWithErrors: 0, importsByDay: [] })
      if (url === '/api/app/bridges') return jsonResponse([])
      if (url === '/api/app/ui-preferences') return jsonResponse({})
      if (url === '/api/account/passkeys') return jsonResponse([])
      if (url === '/api/admin/dashboard') {
        return jsonResponse({
          overall: { configuredSources: 0, enabledSources: 0, totalImportedMessages: 0, sourcesWithErrors: 0, pollInterval: '5m', fetchWindow: 50 },
          stats: { totalImportedMessages: 0, configuredMailFetchers: 0, enabledMailFetchers: 0, sourcesWithErrors: 0, importsByDay: [] },
          polling: {
            defaultPollEnabled: true,
            pollEnabledOverride: null,
            effectivePollEnabled: true,
            defaultPollInterval: '5m',
            pollIntervalOverride: null,
            effectivePollInterval: '5m',
            defaultFetchWindow: 50,
            fetchWindowOverride: null,
            effectiveFetchWindow: 50,
            defaultManualTriggerLimitCount: 5,
            manualTriggerLimitCountOverride: null,
            effectiveManualTriggerLimitCount: 5,
            defaultManualTriggerLimitWindowSeconds: 60,
            manualTriggerLimitWindowSecondsOverride: null,
            effectiveManualTriggerLimitWindowSeconds: 60
          },
          bridges: [],
          recentEvents: []
        })
      }
      if (url === '/api/admin/oauth-app-settings') {
        return jsonResponse({
          effectiveMultiUserEnabled: false,
          multiUserEnabledOverride: false,
          googleDestinationUser: 'me',
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          googleClientId: '',
          googleClientSecretConfigured: false,
          googleRefreshTokenConfigured: false,
          microsoftClientId: '',
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback',
          microsoftClientSecretConfigured: false,
          secureStorageConfigured: true
        })
      }
      throw new Error(`Unexpected request: ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    fireEvent.click(screen.getByRole('tab', { name: /Administration|Administração/ }))
    fireEvent.click(await screen.findByRole('button', { name: /Run Poll Now|Executar polling agora/ }))

    expect(await screen.findByText(/Run polling for the current account now\?|Executar agora a verificação para a conta atual\?/)).toBeInTheDocument()
    expect(screen.getByText(/single-user deployment|modo de utilizador único|modo de usuário único/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Run For Current Account|Executar para a conta atual/ })).toBeInTheDocument()
    expect(screen.queryByText(/Run polling for every user now\?|Executar agora a verificação para todos os utilizadores\?|Executar agora a verificação para todos os usuários\?/)).not.toBeInTheDocument()
  })

  it('shows the admin Google OAuth dialog as shared client configuration only', async () => {
    const fetchMock = vi.fn(async (input) => {
      const url = String(input)
      if (url === '/api/auth/options') {
        return jsonResponse({
          multiUserEnabled: true,
          microsoftOAuthAvailable: true,
          googleOAuthAvailable: true,
          sourceOAuthProviders: ['MICROSOFT', 'GOOGLE']
        })
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
      if (url === '/api/app/gmail-config') return jsonResponse({ destinationUser: 'me', redirectUri: 'https://localhost:3000/api/google-oauth/callback', createMissingLabels: true, neverMarkSpam: false, processForCalendar: false })
      if (url === '/api/app/polling-settings') return jsonResponse({ defaultPollEnabled: true, pollEnabledOverride: null, effectivePollEnabled: true, defaultPollInterval: '5m', pollIntervalOverride: null, effectivePollInterval: '5m', defaultFetchWindow: 50, fetchWindowOverride: null, effectiveFetchWindow: 50 })
      if (url === '/api/app/polling-stats') return jsonResponse({ totalImportedMessages: 0, configuredMailFetchers: 0, enabledMailFetchers: 0, sourcesWithErrors: 0, importsByDay: [] })
      if (url === '/api/app/bridges') return jsonResponse([])
      if (url === '/api/app/ui-preferences') return jsonResponse({})
      if (url === '/api/account/passkeys') return jsonResponse([])
      if (url === '/api/admin/dashboard') {
        return jsonResponse({
          overall: { configuredSources: 0, enabledSources: 0, totalImportedMessages: 0, sourcesWithErrors: 0, pollInterval: '5m', fetchWindow: 50 },
          stats: { totalImportedMessages: 0, configuredMailFetchers: 0, enabledMailFetchers: 0, sourcesWithErrors: 0, importsByDay: [] },
          polling: {
            defaultPollEnabled: true,
            pollEnabledOverride: null,
            effectivePollEnabled: true,
            defaultPollInterval: '5m',
            pollIntervalOverride: null,
            effectivePollInterval: '5m',
            defaultFetchWindow: 50,
            fetchWindowOverride: null,
            effectiveFetchWindow: 50,
            defaultManualTriggerLimitCount: 5,
            manualTriggerLimitCountOverride: null,
            effectiveManualTriggerLimitCount: 5,
            defaultManualTriggerLimitWindowSeconds: 60,
            manualTriggerLimitWindowSecondsOverride: null,
            effectiveManualTriggerLimitWindowSeconds: 60
          },
          bridges: [],
          recentEvents: []
        })
      }
      if (url === '/api/admin/oauth-app-settings') {
        return jsonResponse({
          effectiveMultiUserEnabled: true,
          multiUserEnabledOverride: null,
          googleDestinationUser: 'me',
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          googleClientId: 'google-client-id',
          googleClientSecretConfigured: true,
          googleRefreshTokenConfigured: false,
          microsoftClientId: '',
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback',
          microsoftClientSecretConfigured: false,
          secureStorageConfigured: true
        })
      }
      if (url === '/api/admin/users') return jsonResponse([])
      throw new Error(`Unexpected request: ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    fireEvent.click(screen.getByRole('tab', { name: /Administration|Administração/ }))
    fireEvent.click(await screen.findByRole('button', { name: /Edit Google App|Editar aplicação Google|Editar aplicativo Google/ }))

    expect((await screen.findAllByText(/Administration only stores the shared Google OAuth client registration|A administração guarda apenas o registo do cliente OAuth Google partilhado|A administração armazena apenas o cadastro compartilhado do cliente OAuth Google/)).length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: /Connect Shared Gmail OAuth|Ligar OAuth Gmail partilhado|Conectar OAuth Gmail compartilhado/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Google Refresh Token/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Gmail API User|Utilizador da API Gmail|Usuário da API Gmail/)).not.toBeInTheDocument()
  })

  it('shows one sanitized error notification when a fetcher poll hits a proxy error', async () => {
    const badGatewayHtml = `<html><head><title>502 Bad Gateway</title></head><body><center><h1>502 Bad Gateway</h1></center></body></html>`
    const fetchMock = vi.fn(async (input, init = {}) => {
      const url = String(input)
      const method = init.method || 'GET'

      if (url === '/api/auth/options') return jsonResponse({ multiUserEnabled: true })
      if (url === '/api/auth/me') {
        return jsonResponse({
          id: 1,
          username: 'alice',
          role: 'USER',
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
          configuredMailFetchers: 1,
          enabledMailFetchers: 1,
          sourcesWithErrors: 0,
          importsByDay: []
        })
      }
      if (url === '/api/app/bridges') {
        return jsonResponse([
          {
            bridgeId: 'outlook-main',
            customLabel: '',
            managementSource: 'DATABASE',
            protocol: 'IMAP',
            host: 'outlook.office365.com',
            port: 993,
            authMethod: 'OAUTH2',
            oauthProvider: 'MICROSOFT',
            tls: true,
            folder: 'INBOX',
            tokenStorageMode: 'DATABASE',
            oauthConnected: true,
            totalImportedMessages: 0,
            lastImportedAt: null,
            effectivePollEnabled: true,
            effectivePollInterval: '5m',
            effectiveFetchWindow: 50,
            pollingState: null,
            lastEvent: null,
            canEdit: true,
            canDelete: true,
            canConnectMicrosoft: true
          }
        ])
      }
      if (url === '/api/app/ui-preferences') return jsonResponse({ language: 'pt-PT' })
      if (url === '/api/account/passkeys') return jsonResponse([])
      if (url === '/api/app/bridges/outlook-main/poll/run' && method === 'POST') {
        return htmlError(502, 'Bad Gateway', badGatewayHtml)
      }
      throw new Error(`Unexpected request: ${method} ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    const { container } = render(<App />)

    await screen.findByText(/signed in as/i)
    await waitFor(() => {
      expect(container.querySelector('.fetcher-menu-button')).not.toBeNull()
    })
    fireEvent.click(container.querySelector('.fetcher-menu-button'))
    fireEvent.click(screen.getAllByRole('button', { name: /Run Poll Now|Executar polling agora/ })[1])

    const errorMatcher = /Unable to run mail account polling \(502 Bad Gateway\)|Não foi possível executar a verificação da conta de email \(502 Bad Gateway\)/
    await waitFor(() => {
      expect(screen.getByText(errorMatcher)).toBeInTheDocument()
    })

    expect(screen.getAllByText(errorMatcher)).toHaveLength(1)
  })

  it('translates fetcher poll fallback errors in portuguese', async () => {
    window.localStorage.setItem('inboxbridge.language', 'pt-PT')
    const badGatewayHtml = '<html><body><h1>502 Bad Gateway</h1></body></html>'
    const fetchMock = vi.fn(async (input, init = {}) => {
      const url = String(input)
      const method = init.method || 'GET'
      if (url === '/api/auth/options') return jsonResponse({ multiUserEnabled: true })
      if (url === '/api/auth/me') {
        return jsonResponse({
          id: 1,
          username: 'alice',
          role: 'USER',
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
          pollEnabledOverride: null,
          defaultPollEnabled: true,
          effectivePollEnabled: true,
          pollIntervalOverride: null,
          defaultPollInterval: '5m',
          effectivePollInterval: '5m',
          fetchWindowOverride: null,
          defaultFetchWindow: 50,
          effectiveFetchWindow: 50
        })
      }
      if (url === '/api/app/polling-stats') {
        return jsonResponse({
          totalImportedMessages: 0,
          configuredMailFetchers: 1,
          enabledMailFetchers: 1,
          sourcesWithErrors: 0,
          importsByDay: []
        })
      }
      if (url === '/api/app/bridges') {
        return jsonResponse([
          {
            bridgeId: 'outlook-main',
            customLabel: '',
            managementSource: 'DATABASE',
            protocol: 'IMAP',
            host: 'outlook.office365.com',
            port: 993,
            authMethod: 'OAUTH2',
            oauthProvider: 'MICROSOFT',
            tls: true,
            folder: 'INBOX',
            tokenStorageMode: 'DATABASE',
            oauthConnected: true,
            totalImportedMessages: 0,
            lastImportedAt: null,
            effectivePollEnabled: true,
            effectivePollInterval: '5m',
            effectiveFetchWindow: 50,
            pollingState: null,
            lastEvent: null,
            canEdit: true,
            canDelete: true,
            canConnectMicrosoft: true
          }
        ])
      }
      if (url === '/api/app/ui-preferences') return jsonResponse({ language: 'pt-PT' })
      if (url === '/api/account/passkeys') return jsonResponse([])
      if (url === '/api/app/bridges/outlook-main/poll/run' && method === 'POST') {
        return htmlError(502, 'Bad Gateway', badGatewayHtml)
      }
      throw new Error(`Unexpected request: ${method} ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    const { container } = render(<App />)

    await screen.findByText(/signed in as|sessão iniciada como/i)
    await waitFor(() => {
      expect(container.querySelector('.fetcher-menu-button')).not.toBeNull()
    })
    fireEvent.click(container.querySelector('.fetcher-menu-button'))
    fireEvent.click(screen.getAllByRole('button', { name: /executar polling agora/i })[1])

    await waitFor(() => {
      expect(screen.getByText('Não foi possível executar a verificação da conta de email (502 Bad Gateway)')).toBeInTheDocument()
    })
  })
})
