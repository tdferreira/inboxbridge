import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import App from './App'
import { clearLocalStorage, createWorkspaceRouteFetch, htmlError, jsonResponse, textError } from './test/appTestHelpers'

describe('App', () => {
  beforeEach(() => {
    clearLocalStorage()
    window.history.replaceState({}, '', '/')
  })

  afterEach(() => {
    clearLocalStorage()
    window.history.replaceState({}, '', '/')
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
    expect(screen.queryByText('Signed in with passkey.')).not.toBeInTheDocument()
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
      if (url === '/api/app/email-accounts') {
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
      if (url === '/api/app/email-accounts') {
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
          emailAccounts: []
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
      if (url === '/api/app/email-accounts') {
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

  it('shows a remote control entry point in the My InboxBridge workspace', async () => {
    vi.stubGlobal('fetch', createWorkspaceRouteFetch({
      session: {
        id: 1,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true
      }
    }))

    render(<App />)

    expect(await screen.findByText('Remote control')).toBeInTheDocument()
    expect(screen.getByText('Use the lightweight remote page to trigger polling quickly from phones, tablets, laptops, or shared devices without opening the full workspace.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open Remote Control' })).toHaveAttribute('href', '/remote')
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
      if (url === '/api/app/email-accounts') return jsonResponse([])
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
      if (url === '/api/admin/auth-security-settings') {
        return jsonResponse({
          defaultLoginFailureThreshold: 5,
          loginFailureThresholdOverride: null,
          effectiveLoginFailureThreshold: 5,
          defaultLoginInitialBlock: 'PT5M',
          loginInitialBlockOverride: null,
          effectiveLoginInitialBlock: 'PT5M',
          defaultLoginMaxBlock: 'PT1H',
          loginMaxBlockOverride: null,
          effectiveLoginMaxBlock: 'PT1H',
          defaultRegistrationChallengeEnabled: true,
          registrationChallengeEnabledOverride: null,
          effectiveRegistrationChallengeEnabled: true,
          defaultRegistrationChallengeTtl: 'PT10M',
          registrationChallengeTtlOverride: null,
          effectiveRegistrationChallengeTtl: 'PT10M'
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
          emailAccounts: [],
          recentEvents: []
        })
      }
      if (url === '/api/admin/users') return jsonResponse([])
      throw new Error(`Unexpected request: ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    expect(screen.getByRole('tab', { name: /My InboxBridge/, selected: true })).toBeInTheDocument()
    expect(screen.getByText(/My Destination Mailbox/, { selector: '.section-title' })).toBeInTheDocument()
    expect(screen.queryByText(/Global Polling Settings|Definições globais de verificação/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: /Administration|Administração/ }))

    expect(screen.getByRole('tab', { name: /Administration|Administração/, selected: true })).toBeInTheDocument()
    expect(await screen.findByText(/Global Polling Settings|Definições globais de verificação/)).toBeInTheDocument()
    expect(screen.queryByText(/My Destination Mailbox/, { selector: '.section-title' })).not.toBeInTheDocument()
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
      if (url === '/api/app/email-accounts') return jsonResponse([])
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
          emailAccounts: [],
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
      if (url === '/api/admin/auth-security-settings') {
        return jsonResponse({
          defaultLoginFailureThreshold: 5,
          loginFailureThresholdOverride: null,
          effectiveLoginFailureThreshold: 5,
          defaultLoginInitialBlock: 'PT5M',
          loginInitialBlockOverride: null,
          effectiveLoginInitialBlock: 'PT5M',
          defaultLoginMaxBlock: 'PT1H',
          loginMaxBlockOverride: null,
          effectiveLoginMaxBlock: 'PT1H',
          defaultRegistrationChallengeEnabled: true,
          registrationChallengeEnabledOverride: null,
          effectiveRegistrationChallengeEnabled: true,
          defaultRegistrationChallengeTtl: 'PT10M',
          registrationChallengeTtlOverride: null,
          effectiveRegistrationChallengeTtl: 'PT10M'
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
            emailAccountCount: 0,
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
            emailAccountCount: 0,
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

  it('keeps the administration workspace rendered when an expanded user returns a partial configuration payload', async () => {
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
      if (url === '/api/app/email-accounts') return jsonResponse([])
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
          emailAccounts: [],
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
      if (url === '/api/admin/auth-security-settings') {
        return jsonResponse({
          defaultLoginFailureThreshold: 5,
          loginFailureThresholdOverride: null,
          effectiveLoginFailureThreshold: 5,
          defaultLoginInitialBlock: 'PT5M',
          loginInitialBlockOverride: null,
          effectiveLoginInitialBlock: 'PT5M',
          defaultLoginMaxBlock: 'PT1H',
          loginMaxBlockOverride: null,
          effectiveLoginMaxBlock: 'PT1H',
          defaultRegistrationChallengeEnabled: true,
          registrationChallengeEnabledOverride: null,
          effectiveRegistrationChallengeEnabled: true,
          defaultRegistrationChallengeTtl: 'PT10M',
          registrationChallengeTtlOverride: null,
          effectiveRegistrationChallengeTtl: 'PT10M'
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
            emailAccountCount: 0,
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
            emailAccountCount: 1,
            gmailConfigured: false,
            passwordConfigured: true,
            mustChangePassword: false,
            passkeyCount: 0
          }
        ])
      }
      if (url === '/api/admin/users/2/configuration') {
        return jsonResponse({
          user: {
            id: 2,
            username: 'alice',
            role: 'USER',
            approved: true,
            active: true,
            emailAccountCount: 1
          },
          destinationConfig: null,
          pollingSettings: {
            effectivePollEnabled: true,
            effectivePollInterval: '5m',
            effectiveFetchWindow: 25
          },
          pollingStats: {
            totalImportedMessages: 0
          },
          emailAccounts: [null, { emailAccountId: 'outlook-main' }],
          passkeys: null
        })
      }
      throw new Error(`Unexpected request: ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    fireEvent.click(screen.getByRole('tab', { name: /Administration|Administração/ }))
    await screen.findByText('alice')

    fireEvent.click(await screen.findByTitle(/Inspect configuration and security controls for alice|Inspecionar a configuração e os controlos de segurança de alice/))

    expect(await screen.findByText('Destination Mailbox')).toBeInTheDocument()
    expect(await screen.findByText('outlook-main')).toBeInTheDocument()
    expect(screen.getByText('Users', { selector: '.section-title' })).toBeInTheDocument()
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
      if (url === '/api/app/email-accounts') return jsonResponse([])
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
          emailAccounts: [],
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
      if (url === '/api/admin/auth-security-settings') {
        return jsonResponse({
          defaultLoginFailureThreshold: 5,
          loginFailureThresholdOverride: null,
          effectiveLoginFailureThreshold: 5,
          defaultLoginInitialBlock: 'PT5M',
          loginInitialBlockOverride: null,
          effectiveLoginInitialBlock: 'PT5M',
          defaultLoginMaxBlock: 'PT1H',
          loginMaxBlockOverride: null,
          effectiveLoginMaxBlock: 'PT1H',
          defaultRegistrationChallengeEnabled: true,
          registrationChallengeEnabledOverride: null,
          effectiveRegistrationChallengeEnabled: true,
          defaultRegistrationChallengeTtl: 'PT10M',
          registrationChallengeTtlOverride: null,
          effectiveRegistrationChallengeTtl: 'PT10M'
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
      if (url === '/api/app/email-accounts') return jsonResponse([])
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
          emailAccounts: [],
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
      if (url === '/api/admin/auth-security-settings') {
        return jsonResponse({
          defaultLoginFailureThreshold: 5,
          loginFailureThresholdOverride: null,
          effectiveLoginFailureThreshold: 5,
          defaultLoginInitialBlock: 'PT5M',
          loginInitialBlockOverride: null,
          effectiveLoginInitialBlock: 'PT5M',
          defaultLoginMaxBlock: 'PT1H',
          loginMaxBlockOverride: null,
          effectiveLoginMaxBlock: 'PT1H',
          defaultRegistrationChallengeEnabled: true,
          registrationChallengeEnabledOverride: null,
          effectiveRegistrationChallengeEnabled: true,
          defaultRegistrationChallengeTtl: 'PT10M',
          registrationChallengeTtlOverride: null,
          effectiveRegistrationChallengeTtl: 'PT10M'
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
      if (url === '/api/app/email-accounts') {
        return jsonResponse([
          {
            emailAccountId: 'outlook-main',
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
      if (url === '/api/app/email-accounts/outlook-main/poll/run' && method === 'POST') {
        return htmlError(502, 'Bad Gateway', badGatewayHtml)
      }
      throw new Error(`Unexpected request: ${method} ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    const { container } = render(<App />)

    await screen.findByText(/signed in as/i)
    await waitFor(() => {
      expect(container.querySelector('.fetcher-run-button')).not.toBeNull()
    })
    fireEvent.click(container.querySelector('.fetcher-run-button'))

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
      if (url === '/api/app/email-accounts') {
        return jsonResponse([
          {
            emailAccountId: 'outlook-main',
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
      if (url === '/api/app/email-accounts/outlook-main/poll/run' && method === 'POST') {
        return htmlError(502, 'Bad Gateway', badGatewayHtml)
      }
      throw new Error(`Unexpected request: ${method} ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    const { container } = render(<App />)

    await screen.findByText(/signed in as|sessão iniciada como/i)
    await waitFor(() => {
      expect(container.querySelector('.fetcher-run-button')).not.toBeNull()
    })
    fireEvent.click(container.querySelector('.fetcher-run-button'))

    await waitFor(() => {
      expect(screen.getByText('Não foi possível executar a verificação da conta de email (502 Bad Gateway)')).toBeInTheDocument()
    })
  })

  it('loads destination folder options for a linked IMAP destination mailbox', async () => {
    const fetchMock = vi.fn(async (input) => {
      const url = String(input)
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
      if (url === '/api/app/destination-config') {
        return jsonResponse({
          provider: 'OUTLOOK_IMAP',
          configured: true,
          linked: true,
          passwordConfigured: false,
          oauthConnected: true,
          host: 'outlook.office365.com',
          port: 993,
          tls: true,
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          username: 'owner@example.com',
          folder: 'INBOX'
        })
      }
      if (url === '/api/app/destination-config/folders') {
        return jsonResponse({ folders: ['INBOX', 'Archive'] })
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
      if (url === '/api/app/email-accounts') return jsonResponse([])
      if (url === '/api/app/ui-preferences') return jsonResponse({})
      if (url === '/api/account/passkeys') return jsonResponse([])
      throw new Error(`Unexpected request: GET ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    fireEvent.click(await screen.findByRole('button', { name: 'Edit Destination Mailbox' }))
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: 'Folder' })).toHaveValue('INBOX')
    })
    expect(fetchMock).toHaveBeenCalledWith('/api/app/destination-config/folders')
    expect(screen.getByRole('option', { name: 'Archive' })).toBeInTheDocument()
  })

  it('does not request destination folder options for a Gmail destination', async () => {
    const fetchMock = vi.fn(async (input) => {
      const url = String(input)
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
      if (url === '/api/app/destination-config') {
        return jsonResponse({
          provider: 'GMAIL_API',
          linked: true,
          oauthConnected: true,
          sharedGoogleClientConfigured: true,
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback'
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
      if (url === '/api/app/email-accounts') return jsonResponse([])
      if (url === '/api/app/ui-preferences') return jsonResponse({})
      if (url === '/api/account/passkeys') return jsonResponse([])
      throw new Error(`Unexpected request: GET ${url}`)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    expect(fetchMock).not.toHaveBeenCalledWith('/api/app/destination-config/folders')
  })

  it('opens the administration workspace directly from /admin for admins', async () => {
    window.history.replaceState({}, '', '/admin')
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 1,
        username: 'admin',
        role: 'ADMIN',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)

    expect(await screen.findByRole('tab', { name: 'Administration', selected: true })).toBeInTheDocument()
    expect(screen.getByText('Global Polling Settings', { selector: '.section-title' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/admin')
  })

  it('normalizes localized user workspace URLs back to root for admins', async () => {
    window.history.replaceState({}, '', '/utilizador')
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 1,
        username: 'admin',
        role: 'ADMIN',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true
      },
      uiPreferences: {
        language: 'pt-PT'
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/sess[aã]o iniciada como/i)

    expect(await screen.findByRole('tab', { name: 'My InboxBridge', selected: true })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Administração', selected: false })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/')
  })

  it('normalizes explicit user workspace URLs back to / for non-admin sessions', async () => {
    window.history.replaceState({}, '', '/user')
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 1,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)

    await waitFor(() => {
      expect(window.location.pathname).toBe('/')
    })
    expect(screen.queryByRole('tablist', { name: 'Workspace' })).not.toBeInTheDocument()
    expect(screen.getByText('My Destination Mailbox', { selector: '.section-title' })).toBeInTheDocument()
  })

  it('translates explicit workspace URLs when the language changes', async () => {
    window.history.replaceState({}, '', '/admin')
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 1,
        username: 'admin',
        role: 'ADMIN',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    fireEvent.click(screen.getByRole('button', { name: 'Preferences' }))
    fireEvent.change(screen.getByLabelText('Language'), { target: { value: 'pt-PT' } })

    await waitFor(() => {
      expect(window.location.pathname).toBe('/administracao')
    })
    expect(await screen.findByRole('tab', { name: 'Administração', selected: true })).toBeInTheDocument()
  })

  it('announces new sign-ins when the header refresh button is used', async () => {
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 1,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 1,
        passwordConfigured: true
      },
      sessionActivityResponses: [
        {
          recentLogins: [
            {
              id: 'session-current',
              sessionType: 'BROWSER',
              current: true,
              createdAt: '2026-03-31T09:00:00Z',
              loginMethod: 'PASSWORD',
              ipAddress: '127.0.0.1'
            }
          ],
          activeSessions: [],
          geoIpConfigured: false
        },
        {
          recentLogins: [
            {
              id: 'session-other',
              sessionType: 'REMOTE',
              current: false,
              createdAt: '2026-03-31T09:05:00Z',
              loginMethod: 'PASSWORD',
              ipAddress: '192.168.1.20',
              locationLabel: 'Lisbon, PT'
            }
          ],
          activeSessions: [],
          geoIpConfigured: false
        }
      ]
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(await screen.findByText('A new Remote control sign-in was detected for this account from approximately Lisbon, PT. Review the Sessions tab.')).toBeInTheDocument()
  })

})
