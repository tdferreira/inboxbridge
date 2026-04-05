import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import App from './App'
import { clearLocalStorage, createWorkspaceRouteFetch, htmlError, jsonResponse, textError } from './test/appTestHelpers'

class FakeEventSource {
  static instances = []

  constructor(url) {
    this.url = url
    this.listeners = new Map()
    this.onmessage = null
    this.onerror = null
    this.closed = false
    FakeEventSource.instances.push(this)
  }

  addEventListener(type, listener) {
    this.listeners.set(type, listener)
  }

  emit(type, payload) {
    if (this.closed) {
      return
    }
    const event = { data: JSON.stringify(payload) }
    const listener = this.listeners.get(type)
    if (listener) {
      listener(event)
      return
    }
    this.onmessage?.(event)
  }

  close() {
    this.closed = true
  }
}

describe('App', () => {
  beforeEach(() => {
    clearLocalStorage()
    window.history.replaceState({}, '', '/')
    FakeEventSource.instances = []
  })

  afterEach(() => {
    clearLocalStorage()
    window.history.replaceState({}, '', '/')
    vi.restoreAllMocks()
    vi.useRealTimers()
    vi.unstubAllGlobals()
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

    render(<App timingOverrides={{ liveConnectedSnapshotReconcileMs: 10 }} />)

    fireEvent.change(await screen.findByLabelText('Username'), { target: { value: 'alice' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Sign in' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Sign in' }))

    await screen.findByText(/signed in as/i)
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/passkey/verify', expect.any(Object))
    expect(screen.queryByText('Signed in with passkey.')).not.toBeInTheDocument()
  })

  it('uses the password-aware login flow when the passkey button is clicked with a typed password', async () => {
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
          passwordConfigured: true
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

    render(<App timingOverrides={{ liveConnectedSnapshotReconcileMs: 10 }} />)

    fireEvent.change(await screen.findByLabelText('Username'), { target: { value: 'alice' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    fireEvent.change(await screen.findByLabelText('Password'), { target: { value: 'Secret#123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in with passkey' }))

    await screen.findByText(/signed in as/i)
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.any(Object))
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/passkey/verify', expect.any(Object))
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/passkey/options', expect.any(Object))
  })

  it('automatically captures browser-reported device location for the current admin session when permission is already granted', async () => {
    Object.assign(navigator, {
      geolocation: {
        getCurrentPosition: vi.fn((success) => success({
          coords: {
            latitude: 38.7223,
            longitude: -9.1393,
            accuracy: 25
          }
        }))
      },
      permissions: {
        query: vi.fn().mockResolvedValue({ state: 'granted' })
      }
    })

    const baseFetch = createWorkspaceRouteFetch({
      session: {
        id: 1,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: false
      }
    })

    const fetchMock = vi.fn((input, init = {}) => {
      if (String(input) === '/api/auth/session/device-location') {
        return jsonResponse({})
      }
      return baseFetch(input, init)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App timingOverrides={{ statsAnomalyAttentionMs: 80 }} />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/session/device-location', expect.any(Object)))
  })

  it('still captures browser-reported device location when the permissions api already reports granted', async () => {
    Object.assign(navigator, {
      geolocation: {
        getCurrentPosition: vi.fn((success) => success({
          coords: {
            latitude: 38.7223,
            longitude: -9.1393,
            accuracy: 25
          }
        }))
      },
      permissions: {
        query: vi.fn().mockResolvedValue({ state: 'granted' })
      }
    })

    const baseFetch = createWorkspaceRouteFetch({
      session: {
        id: 2,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: false
      }
    })

    const fetchMock = vi.fn((input, init = {}) => {
      if (String(input) === '/api/auth/session/device-location') {
        return jsonResponse({})
      }
      return baseFetch(input, init)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App timingOverrides={{ statsAnomalyAttentionMs: 80 }} />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/session/device-location', expect.any(Object)))
  })

  it('captures browser-reported device location after an explicit button click when permission still needs a user gesture', async () => {
    Object.assign(navigator, {
      geolocation: {
        getCurrentPosition: vi.fn((success) => success({
          coords: {
            latitude: 38.7223,
            longitude: -9.1393,
            accuracy: 25
          }
        }))
      }
    })

    const baseFetch = createWorkspaceRouteFetch({
      session: {
        id: 21,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: false
      }
    })

    const fetchMock = vi.fn((input, init = {}) => {
      if (String(input) === '/api/auth/session/device-location') {
        return jsonResponse({})
      }
      return baseFetch(input, init)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App timingOverrides={{ statsAnomalyAttentionMs: 80 }} />)

    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/session/device-location', expect.any(Object))
    fireEvent.click(await screen.findByRole('button', { name: 'Share Device Location' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/session/device-location', expect.any(Object)))
  })

  it('keeps layout editing active after moving a section from the workspace controls', async () => {
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 30,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      },
      uiPreferences: {
        persistLayout: true
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    const { container } = render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Preferences' }))
    fireEvent.click(screen.getByRole('button', { name: 'Edit Layout' }))

    await waitFor(() => {
      expect(container.querySelector('.hero-layout-editing-dock')).toBeInTheDocument()
    })

    const moveButtons = Array.from(container.querySelectorAll('.workspace-section-window-button'))
    const enabledMoveButton = moveButtons.find((button) => !button.disabled)

    expect(enabledMoveButton).toBeTruthy()
    fireEvent.click(enabledMoveButton)

    await waitFor(() => {
      expect(container.querySelector('.hero-layout-editing-dock')).toBeInTheDocument()
    })
  })

  it('pushes authenticated notifications immediately from the live event stream', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.stubGlobal('fetch', createWorkspaceRouteFetch({
      session: {
        id: 40,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      }
    }))

    render(<App />)

    await screen.findByText(/signed in as/i)
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1))

    act(() => {
      FakeEventSource.instances[0].emit('notification-created', {
        notification: {
          message: { kind: 'translation', key: 'notifications.userPollingUpdated', params: {} },
          copyText: { kind: 'translation', key: 'notifications.userPollingUpdated', params: {} },
          targetId: 'user-polling-section',
          tone: 'success',
          replaceGroup: false,
          supersedesGroupKeys: []
        }
      })
    })

    expect(await screen.findByText('Your polling settings were updated.')).toBeInTheDocument()
  })

  it('returns to login immediately when the live event stream reports session revocation', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.stubGlobal('fetch', createWorkspaceRouteFetch({
      session: {
        id: 41,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      }
    }))

    render(<App />)

    await screen.findByText(/signed in as/i)

    act(() => {
      FakeEventSource.instances[0].emit('session-revoked', {
        type: 'session-revoked'
      })
    })

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByText('This session is no longer valid. Please sign in again.')).toBeInTheDocument()
  })

  it('returns to login when the live event stream drops and the session check comes back unauthorized', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    let authMeCount = 0
    const baseFetch = createWorkspaceRouteFetch({
      session: {
        id: 41,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      }
    })
    vi.stubGlobal('fetch', vi.fn((input, init = {}) => {
      if (String(input) === '/api/auth/me') {
        authMeCount += 1
        if (authMeCount > 1) {
          return Promise.resolve({
            ok: false,
            status: 401,
            text: () => Promise.resolve(JSON.stringify({ code: 'unauthorized', message: 'Not authenticated' })),
            json: () => Promise.resolve({ code: 'unauthorized', message: 'Not authenticated' })
          })
        }
      }
      return baseFetch(input, init)
    }))

    render(<App />)

    await screen.findByText(/signed in as/i)
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1))

    await act(async () => {
      await FakeEventSource.instances[0].onerror?.()
    })

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByText('This session is no longer valid. Please sign in again.')).toBeInTheDocument()
  })

  it('keeps the live event stream open across transient errors so later revocation events still work', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.stubGlobal('fetch', createWorkspaceRouteFetch({
      session: {
        id: 41,
        currentSessionId: 141,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      }
    }))

    render(<App />)

    await screen.findByText(/signed in as/i)
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1))

    await act(async () => {
      await FakeEventSource.instances[0].onerror?.()
    })

    expect(FakeEventSource.instances[0].closed).toBe(false)

    act(() => {
      FakeEventSource.instances[0].emit('session-revoked', {
        type: 'session-revoked',
        revokedSessionId: 141
      })
    })

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('accepts keepalive live events without disrupting the authenticated app', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.stubGlobal('fetch', createWorkspaceRouteFetch({
      session: {
        id: 41,
        currentSessionId: 141,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      }
    }))

    render(<App />)

    await screen.findByText(/signed in as/i)

    act(() => {
      FakeEventSource.instances[0].emit('keepalive', {
        type: 'keepalive'
      })
    })

    expect(screen.getByText(/signed in as/i)).toBeInTheDocument()
  })

  it('moves the remote control section through the layout editor even for older saved layouts', async () => {
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 31,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      },
      uiPreferences: {
        persistLayout: true,
        userSectionOrder: ['quickSetup', 'destination', 'userPolling', 'userStats', 'sourceEmailAccounts']
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Preferences' }))
    fireEvent.click(screen.getByRole('button', { name: 'Edit Layout' }))

    const remoteHeading = await screen.findByText('InboxBridge Go')
    const remoteWindow = await waitFor(() => {
      const node = remoteHeading.closest('[data-workspace-section-window="true"]')
      expect(node).toBeTruthy()
      return node
    })
    const moveUpButton = remoteWindow.querySelectorAll('.workspace-section-window-button')[0]

    fireEvent.click(moveUpButton)

    await waitFor(() => {
      const windows = Array.from(document.querySelectorAll('[data-workspace-section-window="true"]'))
      const order = windows.map((windowNode) => windowNode.getAttribute('data-section-id'))
      expect(order.indexOf('remoteControl')).toBeLessThan(order.indexOf('sourceEmailAccounts'))
    })
  })

  it('uses the requested default section order for the My InboxBridge workspace', async () => {
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 32,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)

    const order = Array.from(document.querySelectorAll('[data-workspace-key="user"] [data-workspace-section-window="true"]'))
      .map((node) => node.getAttribute('data-section-id'))

    expect(order).toEqual([
      'quickSetup',
      'destination',
      'sourceEmailAccounts',
      'userPolling',
      'remoteControl',
      'userStats'
    ])
  })

  it('uses the requested default section order for the Administration workspace', async () => {
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 33,
        username: 'admin',
        role: 'ADMIN',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    fireEvent.click(await screen.findByRole('tab', { name: 'Administration' }))

    const order = await waitFor(() => Array.from(document.querySelectorAll('[data-workspace-key="admin"] [data-workspace-section-window="true"]'))
      .map((node) => node.getAttribute('data-section-id')))

    expect(order).toEqual([
      'adminQuickSetup',
      'systemDashboard',
      'oauthApps',
      'userManagement',
      'authSecurity',
      'globalStats'
    ])
  })

  it('falls back to a lower-accuracy browser location lookup when the first attempt fails', async () => {
    const getCurrentPosition = vi.fn()
      .mockImplementationOnce((success, reject) => reject({ code: 2 }))
      .mockImplementationOnce((success) => success({
        coords: {
          latitude: 38.7223,
          longitude: -9.1393,
          accuracy: 250
        }
      }))

    Object.assign(navigator, {
      geolocation: {
        getCurrentPosition
      },
      permissions: {
        query: vi.fn().mockResolvedValue({ state: 'granted' })
      }
    })

    const baseFetch = createWorkspaceRouteFetch({
      session: {
        id: 3,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: false
      }
    })

    const fetchMock = vi.fn((input, init = {}) => {
      if (String(input) === '/api/auth/session/device-location') {
        return jsonResponse({})
      }
      return baseFetch(input, init)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/session/device-location', expect.any(Object)))
    expect(getCurrentPosition).toHaveBeenCalledTimes(2)
    expect(getCurrentPosition).toHaveBeenNthCalledWith(
      1,
      expect.any(Function),
      expect.any(Function),
      expect.objectContaining({ enableHighAccuracy: true })
    )
    expect(getCurrentPosition).toHaveBeenNthCalledWith(
      2,
      expect.any(Function),
      expect.any(Function),
      expect.objectContaining({ enableHighAccuracy: false })
    )
  })

  it('shows actionable guidance when the browser cannot determine a location after an explicit request', async () => {
    Object.assign(navigator, {
      geolocation: {
        getCurrentPosition: vi.fn((success, reject) => reject({ code: 2, message: 'Position unavailable' }))
      }
    })

    const baseFetch = createWorkspaceRouteFetch({
      session: {
        id: 4,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: false
      }
    })

    vi.stubGlobal('fetch', baseFetch)

    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Share Device Location' }))
    expect(await screen.findByText(/could not determine its current location/i)).toBeInTheDocument()
    expect(screen.getByText(/os\/browser location services/i)).toBeInTheDocument()
    expect(screen.getByText(/position unavailable/i)).toBeInTheDocument()
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

    expect(await screen.findByText('InboxBridge Go')).toBeInTheDocument()
    expect(screen.getByText('Use InboxBridge Go to trigger inbox fetches quickly from phones, tablets, laptops, or shared devices without opening the full workspace.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open InboxBridge Go' })).toHaveAttribute('href', '/remote')
  })

  it('clears stale running source state from snapshot fallback when event streaming is unavailable', async () => {
    vi.stubGlobal('EventSource', undefined)

    const baseFetch = createWorkspaceRouteFetch({
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
    let liveSnapshotCalls = 0

    const fetchMock = vi.fn((input, init = {}) => {
      const url = String(input)

      if (url === '/api/app/email-accounts') {
        return Promise.resolve(jsonResponse([{
          emailAccountId: 'source-1',
          customLabel: 'Inbox',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          tls: true,
          folder: 'INBOX',
          tokenStorageMode: 'DATABASE',
          enabled: true,
          canRunPoll: true,
          canEdit: true,
          canDelete: true,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50
        }]))
      }
      if (url === '/api/poll/live') {
        liveSnapshotCalls += 1
        return Promise.resolve(jsonResponse(liveSnapshotCalls <= 2
          ? {
              running: true,
              state: 'RUNNING',
              viewerCanControl: true,
              activeSourceId: 'source-1',
              sources: [
                {
                  sourceId: 'source-1',
                  label: 'Inbox',
                  state: 'RUNNING',
                  actionable: false,
                  position: 1,
                  fetched: 0,
                  imported: 0,
                  duplicates: 0
                }
              ]
            }
          : {
              running: false,
              state: 'IDLE',
              viewerCanControl: false,
              activeSourceId: null,
              sources: []
            }))
      }

      return baseFetch(input, init)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as alice/i)
    await waitFor(() => {
      expect(liveSnapshotCalls).toBeGreaterThanOrEqual(2)
      expect(screen.queryByText('Running…')).not.toBeInTheDocument()
    }, { timeout: 8000 })
  }, 10000)

  it('reconciles stale running source state from snapshots while SSE is still connected', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)

    const baseFetch = createWorkspaceRouteFetch({
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
    let liveSnapshotCalls = 0

    const fetchMock = vi.fn((input, init = {}) => {
      const url = String(input)

      if (url === '/api/app/email-accounts') {
        return Promise.resolve(jsonResponse([{
          emailAccountId: 'source-1',
          customLabel: 'Inbox',
          managementSource: 'DATABASE',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          tls: true,
          folder: 'INBOX',
          tokenStorageMode: 'DATABASE',
          enabled: true,
          canRunPoll: true,
          canEdit: true,
          canDelete: true,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50
        }]))
      }
      if (url === '/api/poll/live') {
        liveSnapshotCalls += 1
        return Promise.resolve(jsonResponse(liveSnapshotCalls === 1
          ? {
              running: true,
              state: 'RUNNING',
              viewerCanControl: true,
              activeSourceId: 'source-1',
              sources: [
                {
                  sourceId: 'source-1',
                  label: 'Inbox',
                  state: 'RUNNING',
                  actionable: false,
                  position: 1,
                  fetched: 0,
                  imported: 0,
                  duplicates: 0
                }
              ]
            }
          : {
              running: false,
              state: 'IDLE',
              viewerCanControl: false,
              activeSourceId: null,
              sources: []
            }))
      }

      return baseFetch(input, init)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as alice/i)
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1))
    await waitFor(() => expect(liveSnapshotCalls).toBeGreaterThanOrEqual(1))

    act(() => {
      FakeEventSource.instances[0].emit('keepalive', { type: 'keepalive' })
    })

    await act(async () => {
      await new Promise((resolve) => window.setTimeout(resolve, 140))
    })

    await waitFor(() => {
      expect(liveSnapshotCalls).toBeGreaterThanOrEqual(2)
      expect(screen.getAllByRole('button', { name: 'Run Poll Now' }).every((button) => !button.disabled)).toBe(true)
    })
  }, 10000)

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

    render(<App timingOverrides={{ statsAnomalyAttentionMs: 10 }} />)

    await screen.findByText(/signed in as/i)
    expect(screen.getByRole('tab', { name: /My InboxBridge/, selected: true })).toBeInTheDocument()
    expect(screen.getByText(/My Destination Mailbox/, { selector: '.section-title' })).toBeInTheDocument()
    expect(screen.queryByText(/Global Polling Settings|Definições globais de verificação/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: /Administration|Administração/ }))

    expect(screen.getByRole('tab', { name: /Administration|Administração/, selected: true })).toBeInTheDocument()
    expect(await screen.findByText(/Global Polling Settings|Definições globais de verificação/)).toBeInTheDocument()
    expect(screen.queryByText(/My Destination Mailbox/, { selector: '.section-title' })).not.toBeInTheDocument()
  })

  it('notifies admins about global statistics anomalies and only pulses that section temporarily', async () => {
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 1,
        username: 'admin',
        role: 'ADMIN',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      }
    })

    vi.stubGlobal('fetch', vi.fn(async (input, init) => {
      const url = String(input)
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
            configuredMailFetchers: 1,
            enabledMailFetchers: 1,
            sourcesWithErrors: 0,
            importsByDay: [],
            importTimelines: {},
            duplicateTimelines: {},
            errorTimelines: {},
            manualRunTimelines: {},
            scheduledRunTimelines: {
              today: [
                { bucketLabel: '03:00', importedMessages: 720 }
              ]
            },
            health: { activeMailFetchers: 1, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
            providerBreakdown: [],
            manualRuns: 0,
            scheduledRuns: 720,
            averagePollDurationMillis: 1200
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
      return fetchMock(input, init)
    }))

    render(<App timingOverrides={{ statsAnomalyAttentionMs: 80 }} />)

    await screen.findByText(/signed in as admin/i)
    expect(await screen.findByText('Global Statistics detected scheduled polling activity that looks unusually high. Review that section.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: /Administration|Administração/ }))

    const statsSection = await screen.findByText('Global Statistics')
    const statsCard = statsSection.closest('section')
    expect(statsCard).toHaveClass('polling-statistics-section-alerting')
    await waitFor(() => {
      expect(statsCard).toHaveClass('polling-statistics-section-attention')
    })

    await act(async () => {
      await new Promise((resolve) => window.setTimeout(resolve, 120))
    })

    expect(statsCard).toHaveClass('polling-statistics-section-alerting')
    await waitFor(() => {
      expect(statsCard).not.toHaveClass('polling-statistics-section-attention')
    })
  }, 10000)

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
    fireEvent.click(await screen.findByTitle(/Show or hide Google OAuth details|Mostrar ou esconder os detalhes de Google OAuth/))
    fireEvent.click(await screen.findByRole('button', { name: /Edit Google App|Edit Google OAuth|Editar aplicação Google|Editar aplicação Google OAuth|Editar aplicativo Google|Editar aplicativo Google OAuth/ }))

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
    fireEvent.click(screen.getByRole('button', { name: 'Language' }))
    fireEvent.click(screen.getByRole('menuitemradio', { name: 'Português (Portugal)' }))

    await waitFor(() => {
      expect(window.location.pathname).toBe('/administracao')
    })
    expect(await screen.findByRole('tab', { name: 'Administração', selected: true })).toBeInTheDocument()
  })

  it('reloads polling statistics in the newly selected timezone and keeps using it on refresh', async () => {
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
    fireEvent.change(screen.getByDisplayValue('Detect automatically'), { target: { value: 'MANUAL' } })
    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'America/New_York' } })

    await waitFor(() => {
      const statsCalls = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/app/polling-stats')
      const dashboardCalls = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/admin/dashboard')
      expect(statsCalls.some(([, options]) => options?.headers?.['X-InboxBridge-Timezone'] === 'America/New_York')).toBe(true)
      expect(dashboardCalls.some(([, options]) => options?.headers?.['X-InboxBridge-Timezone'] === 'America/New_York')).toBe(true)
    })

    const statsCallCountBeforeRefresh = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/app/polling-stats').length
    const dashboardCallCountBeforeRefresh = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/admin/dashboard').length
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))

    await waitFor(() => {
      const statsCalls = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/app/polling-stats')
      const dashboardCalls = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/admin/dashboard')
      const newStatsCalls = statsCalls.slice(statsCallCountBeforeRefresh)
      const newDashboardCalls = dashboardCalls.slice(dashboardCallCountBeforeRefresh)
      expect(newStatsCalls.length).toBeGreaterThan(0)
      expect(newDashboardCalls.length).toBeGreaterThan(0)
      expect(newStatsCalls.some(([, options]) => options?.headers?.['X-InboxBridge-Timezone'] === 'America/New_York')).toBe(true)
      expect(newDashboardCalls.some(([, options]) => options?.headers?.['X-InboxBridge-Timezone'] === 'America/New_York')).toBe(true)
    })
  })

  it('keeps the selected statistics range across app refreshes even if the refreshed payload is sparser', async () => {
    const baseFetch = createWorkspaceRouteFetch({
      session: {
        id: 7,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true,
        deviceLocationCaptured: true
      }
    })

    let statsRequestCount = 0
    const fetchMock = vi.fn((input, init = {}) => {
      const url = String(input)
      if (url === '/api/app/polling-stats') {
        statsRequestCount += 1
        return jsonResponse({
          totalImportedMessages: 10,
          configuredMailFetchers: 1,
          enabledMailFetchers: 1,
          sourcesWithErrors: 0,
          importsByDay: [],
          importTimelines: statsRequestCount === 1
            ? {
                today: [
                  { bucketLabel: '08:00', importedMessages: 1 },
                  { bucketLabel: '09:00', importedMessages: 2 }
                ],
                pastMonth: [
                  { bucketLabel: '2026-03-01', importedMessages: 1 },
                  { bucketLabel: '2026-03-02', importedMessages: 2 }
                ]
              }
            : {
                today: [
                  { bucketLabel: '08:00', importedMessages: 1 },
                  { bucketLabel: '09:00', importedMessages: 2 }
                ]
              },
          duplicateTimelines: {},
          errorTimelines: {},
          manualRunTimelines: {},
          scheduledRunTimelines: {},
          health: {
            activeMailFetchers: 1,
            coolingDownMailFetchers: 0,
            failingMailFetchers: 0,
            disabledMailFetchers: 0
          },
          providerBreakdown: [],
          manualRuns: 0,
          scheduledRuns: 0,
          averagePollDurationMillis: 0
        })
      }
      return baseFetch(input, init)
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText(/signed in as/i)

    const rangeSelect = await screen.findByRole('combobox', { name: 'Range' })
    fireEvent.change(rangeSelect, { target: { value: 'pastMonth' } })
    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('pastMonth')

    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))

    await waitFor(() => {
      expect(statsRequestCount).toBeGreaterThan(1)
    })
    expect(screen.getByRole('combobox', { name: 'Range' })).toHaveValue('pastMonth')
  })

  it('shows the global statistics anomaly notification only for recent admin anomalies', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-04-04T12:00:00Z'))
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
      adminDashboard: {
        overall: {
          configuredSources: 1,
          enabledSources: 1,
          totalImportedMessages: 0,
          sourcesWithErrors: 0,
          pollInterval: '5m',
          fetchWindow: 50
        },
        stats: {
          totalImportedMessages: 0,
          configuredMailFetchers: 1,
          enabledMailFetchers: 1,
          sourcesWithErrors: 0,
          importsByDay: [],
          importTimelines: {},
          duplicateTimelines: {},
          errorTimelines: {},
          manualRunTimelines: {},
          scheduledRunTimelines: {
            custom: [
              { bucketLabel: '2026-04-04T10:00:00Z', importedMessages: 720 }
            ]
          },
          health: { activeMailFetchers: 1, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
          providerBreakdown: [],
          manualRuns: 0,
          scheduledRuns: 720,
          averagePollDurationMillis: 0
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
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findByText(/signed in as/i)).toBeInTheDocument()
    expect(await screen.findByText('Global Statistics detected scheduled polling activity that looks unusually high. Review that section.')).toBeInTheDocument()
  })

  it('hides stale global statistics anomaly notifications for old admin anomalies', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-04-12T12:00:00Z'))
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
      adminDashboard: {
        overall: {
          configuredSources: 1,
          enabledSources: 1,
          totalImportedMessages: 0,
          sourcesWithErrors: 0,
          pollInterval: '5m',
          fetchWindow: 50
        },
        stats: {
          totalImportedMessages: 0,
          configuredMailFetchers: 1,
          enabledMailFetchers: 1,
          sourcesWithErrors: 0,
          importsByDay: [],
          importTimelines: {},
          duplicateTimelines: {},
          errorTimelines: {},
          manualRunTimelines: {},
          scheduledRunTimelines: {
            custom: [
              { bucketLabel: '2026-04-04T09:00:00Z', importedMessages: 720 }
            ]
          },
          health: { activeMailFetchers: 1, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
          providerBreakdown: [],
          manualRuns: 0,
          scheduledRuns: 720,
          averagePollDurationMillis: 0
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
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findByText(/signed in as/i)).toBeInTheDocument()
    expect(screen.queryByText('Global Statistics detected scheduled polling activity that looks unusually high. Review that section.')).not.toBeInTheDocument()
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
              locationLabel: 'Lisbon, PT',
              unusualLocation: true
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
    await waitFor(() => {
      expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/account/sessions').length).toBeGreaterThanOrEqual(1)
    })
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(await screen.findByText('A new Remote control sign-in was detected for this account from approximately Lisbon, PT, which looks unusual compared with your recent activity. Review the Sessions tab.')).toBeInTheDocument()
  })

  it('announces new sign-ins from the live event stream and refreshes session activity', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 1,
        currentSessionId: 101,
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
    await waitFor(() => {
      expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/account/sessions').length).toBeGreaterThanOrEqual(1)
    })

    act(() => {
      FakeEventSource.instances[0].emit('notification-created', {
        type: 'notification-created',
        notification: {
          message: { kind: 'translation', key: 'notifications.newSessionDetected', params: {} },
          copyText: { kind: 'translation', key: 'notifications.newSessionDetected', params: {} },
          groupKey: 'session-activity',
          replaceGroup: false,
          supersedesGroupKeys: [],
          targetId: 'recent-session-REMOTE-session-other',
          tone: 'warning'
        }
      })
    })

    expect(await screen.findByText('A new sign-in was detected for this account. Review the Sessions tab.')).toBeInTheDocument()
    await waitFor(() => {
      expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/account/sessions').length).toBeGreaterThanOrEqual(2)
    })
  })

  it('hydrates saved notifications from persisted ui preferences after loading the workspace', async () => {
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 41,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true
      },
      uiPreferences: {
        notificationHistory: [{
          id: 'note-1',
          message: { kind: 'translation', key: 'notifications.signedIn', params: {} },
          copyText: { kind: 'translation', key: 'notifications.signedIn', params: {} },
          tone: 'success',
          createdAt: Date.parse('2026-03-31T09:00:00Z'),
          floatingVisible: true,
          autoCloseMs: null
        }]
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findAllByText('Signed in.')).not.toHaveLength(0)
    expect(screen.getByRole('button', { name: /notifications/i })).toHaveTextContent('1')
  })

  it('keeps only the viewport-safe number of floating notifications visible while preserving the full notification history', async () => {
    const originalInnerHeight = window.innerHeight
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 160 })

    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 43,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true
      },
      uiPreferences: {
        notificationHistory: [
          {
            id: 'note-1',
            message: 'Oldest hidden notification',
            copyText: 'Oldest hidden notification',
            tone: 'success',
            createdAt: Date.parse('2026-03-31T09:00:00Z'),
            floatingVisible: true,
            autoCloseMs: null
          },
          {
            id: 'note-2',
            message: 'Middle hidden notification',
            copyText: 'Middle hidden notification',
            tone: 'warning',
            createdAt: Date.parse('2026-03-31T09:01:00Z'),
            floatingVisible: true,
            autoCloseMs: null
          },
          {
            id: 'note-3',
            message: 'Newest visible notification',
            copyText: 'Newest visible notification',
            tone: 'error',
            createdAt: Date.parse('2026-03-31T09:02:00Z'),
            floatingVisible: true,
            autoCloseMs: null
          }
        ]
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findByText('Newest visible notification')).toBeInTheDocument()
    expect(screen.queryByText('Oldest hidden notification')).not.toBeInTheDocument()
    expect(screen.queryByText('Middle hidden notification')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /notifications/i })).toHaveTextContent('3')
    expect(screen.getAllByRole('button', { name: 'Dismiss notification' })).toHaveLength(1)

    Object.defineProperty(window, 'innerHeight', { configurable: true, value: originalInnerHeight })
  })

  it('persists notification dismissals back to the ui preferences endpoint', async () => {
    const fetchMock = createWorkspaceRouteFetch({
      session: {
        id: 42,
        username: 'alice',
        role: 'USER',
        approved: true,
        mustChangePassword: false,
        passkeyCount: 0,
        passwordConfigured: true
      },
      uiPreferences: {
        notificationHistory: [{
          id: 'note-1',
          message: { kind: 'translation', key: 'notifications.signedIn', params: {} },
          copyText: { kind: 'translation', key: 'notifications.signedIn', params: {} },
          tone: 'success',
          createdAt: Date.parse('2026-03-31T09:00:00Z'),
          floatingVisible: true,
          autoCloseMs: null
        }]
      }
    })

    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    await screen.findByText('Signed in.')
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss notification' }))

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/app/ui-preferences', expect.objectContaining({
        method: 'PUT',
        body: expect.stringContaining('"notificationHistory":[]')
      }))
    })
  })

})
