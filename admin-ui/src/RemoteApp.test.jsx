import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { within } from '@testing-library/react'
import RemoteApp from './RemoteApp'

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

function jsonResponse(payload, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(JSON.stringify(payload)),
    json: () => Promise.resolve(payload)
  })
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('RemoteApp', () => {
  beforeEach(() => {
    FakeEventSource.instances = []
    window.localStorage.clear()
  })

  it('shows the remote login screen when no remote session exists', async () => {
    vi.stubGlobal('fetch', vi.fn(() => jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)))

    render(<RemoteApp />)

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('shows only one InboxBridge Go title on the remote login screen', async () => {
    vi.stubGlobal('fetch', vi.fn(() => jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)))

    render(<RemoteApp />)

    expect(await screen.findByRole('heading', { name: 'InboxBridge Go' })).toBeInTheDocument()
    expect(screen.getAllByText('InboxBridge Go')).toHaveLength(1)
  })

  it('lets unauthenticated remote users choose the language on the login screen', async () => {
    vi.stubGlobal('fetch', vi.fn(() => jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)))

    render(<RemoteApp />)

    expect(await screen.findByRole('button', { name: 'Language' })).toBeInTheDocument()
    expect(screen.queryByText('Language')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Language' }))
    fireEvent.click(screen.getByRole('menuitemradio', { name: 'Português (Portugal)' }))

    expect(await screen.findByLabelText('Utilizador')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('shows the selected language flag and label in the remote login language button', async () => {
    vi.stubGlobal('fetch', vi.fn(() => jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)))

    render(<RemoteApp />)

    const languageButton = await screen.findByRole('button', { name: 'Language' })
    expect(languageButton).toHaveTextContent('🇬🇧')
    expect(languageButton).toHaveTextContent('English')

    fireEvent.click(languageButton)
    fireEvent.click(screen.getByRole('menuitemradio', { name: 'Português (Brasil)' }))

    expect(await screen.findByRole('button', { name: 'Idioma' })).toHaveTextContent('🇧🇷')
    expect(screen.getByRole('button', { name: 'Idioma' })).toHaveTextContent('Português (Brasil)')
  })

  it('uses the password-aware login flow when the remote passkey button is clicked with a typed password', async () => {
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

    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)
      }
      if (url === '/api/remote/auth/login') {
        return jsonResponse({
          status: 'PASSKEY_REQUIRED',
          passkeyChallenge: { ceremonyId: 'ceremony-1', publicKeyJson: '{"challenge":"AQID","allowCredentials":[]}' }
        })
      }
      if (url === '/api/remote/auth/passkey/verify') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    fireEvent.change(await screen.findByLabelText('Username'), { target: { value: 'alice' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    fireEvent.change(await screen.findByLabelText('Password'), { target: { value: 'Secret#123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in with passkey' }))

    await screen.findByRole('button', { name: 'Poll My Sources' })
    expect(fetchMock).toHaveBeenCalledWith('/api/remote/auth/login', expect.any(Object))
    expect(fetchMock).toHaveBeenCalledWith('/api/remote/auth/passkey/verify', expect.any(Object))
    expect(fetchMock).not.toHaveBeenCalledWith('/api/remote/auth/passkey/options', expect.any(Object))
  })

  it('shows loading only on the clicked remote auth button while a password-aware passkey login is in flight', async () => {
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

    const loginRequest = deferred()
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)
      }
      if (url === '/api/remote/auth/login') {
        return loginRequest.promise
      }
      if (url === '/api/remote/auth/passkey/verify') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    fireEvent.change(await screen.findByLabelText('Username'), { target: { value: 'alice' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    fireEvent.change(await screen.findByLabelText('Password'), { target: { value: 'Secret#123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in with passkey' }))

    expect(await screen.findByRole('button', { name: 'Signing in…' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()

    loginRequest.resolve(jsonResponse({
      status: 'PASSKEY_REQUIRED',
      passkeyChallenge: { ceremonyId: 'ceremony-1', publicKeyJson: '{"challenge":"AQID","allowCredentials":[]}' }
    }))

    await screen.findByRole('button', { name: 'Poll My Sources' })
  })

  it('moves focus to the password field when InboxBridge Go opens the credential step', async () => {
    vi.stubGlobal('fetch', vi.fn(() => jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)))

    render(<RemoteApp />)

    fireEvent.change(await screen.findByLabelText('Username'), { target: { value: 'admin' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByLabelText('Password')).toHaveFocus()
  })

  it('runs a source poll from the remote dashboard', async () => {
    let controlRequests = 0
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        controlRequests += 1
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'System',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: controlRequests > 1
                ? {
                    sourceId: 'source-1',
                    trigger: 'MANUAL',
                    status: 'SUCCESS',
                    startedAt: '2026-03-31T10:00:00Z',
                    finishedAt: '2026-03-31T10:00:10Z',
                    fetched: 2,
                    imported: 1,
                    duplicates: 1,
                    error: null
                  }
                : null,
              customLabel: 'Main Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      if (url === '/api/remote/sources/source-1/poll/run') {
        return jsonResponse({
          startedAt: '2026-03-31T10:00:00Z',
          finishedAt: '2026-03-31T10:00:10Z',
          fetched: 2,
          imported: 1,
          duplicates: 1,
          errors: []
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    expect(await screen.findByText('Main Inbox')).toBeInTheDocument()
    expect(screen.getByText('60 actions per 1 minute')).toBeInTheDocument()
    expect(screen.queryByText('PT1M')).not.toBeInTheDocument()
    expect(screen.queryByText('Enabled · 5 minutes')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Show details' }))
    expect(screen.getByText('Enabled · 5 minutes')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Poll This Source' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/remote/sources/source-1/poll/run', expect.any(Object)))
    expect(await screen.findByText('The remote polling request finished without reported errors.')).toBeInTheDocument()
    expect(screen.queryByText('2026-03-31T10:00:10Z')).not.toBeInTheDocument()
    expect(screen.getByText(/Fetched: 2 · Imported: 1 · Duplicates: 1/)).toBeInTheDocument()
    expect(screen.getByText('Fetched: 2')).toBeInTheDocument()
    expect(screen.getByText('Imported: 1')).toBeInTheDocument()
    expect(screen.getByText('Duplicates: 1')).toBeInTheDocument()
  })

  it('shows a stopped message when the signed-in user stops the remote poll', async () => {
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'System',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Main Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      if (url === '/api/remote/sources/source-1/poll/run') {
        return jsonResponse({
          startedAt: '2026-03-31T10:00:00Z',
          finishedAt: '2026-03-31T10:00:10Z',
          fetched: 2,
          imported: 1,
          duplicates: 1,
          state: 'STOPPED',
          stoppedByUsername: 'admin',
          errors: []
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    fireEvent.click(await screen.findByRole('button', { name: 'Poll This Source' }))

    expect(await screen.findByText('The polling request was stopped.')).toBeInTheDocument()
  })

  it('shows an administrator-stopped message when another admin stops the remote poll', async () => {
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'System',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Main Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      if (url === '/api/remote/sources/source-1/poll/run') {
        return jsonResponse({
          startedAt: '2026-03-31T10:00:00Z',
          finishedAt: '2026-03-31T10:00:10Z',
          fetched: 2,
          imported: 1,
          duplicates: 1,
          state: 'STOPPED',
          stoppedByUsername: 'alice',
          errors: []
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    fireEvent.click(await screen.findByRole('button', { name: 'Poll This Source' }))

    expect(await screen.findByText('The polling request was stopped by an administrator.')).toBeInTheDocument()
  })

  it('does not render a warning banner for intentionally stopped source runs', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'System',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: {
                sourceId: 'source-1',
                trigger: 'MANUAL',
                status: 'STOPPED',
                startedAt: '2026-03-31T10:00:00Z',
                finishedAt: '2026-03-31T10:00:10Z',
                fetched: 2,
                imported: 1,
                duplicates: 1,
                error: 'Stopped by user.'
              },
              customLabel: 'Main Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    fireEvent.click(await screen.findByRole('button', { name: 'Show details' }))

    expect(screen.getAllByText('Stopped').length).toBeGreaterThan(0)
    expect(screen.queryByText('Stopped by user.')).not.toBeInTheDocument()
  })

  it('keeps each source collapsed by default while leaving the poll action visible', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-2',
              ownerLabel: 'Alice',
              protocol: 'POP3',
              host: 'pop.example.com',
              port: 995,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: 'PT10M',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Archive Mail'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByText('Archive Mail')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Poll This Source' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Show details' })).toBeInTheDocument()
    expect(screen.getByText('10 minutes')).toBeInTheDocument()
    expect(screen.getByText(/POP3/)).toBeInTheDocument()
    expect(screen.queryByText('Source ID')).not.toBeInTheDocument()
  })

  it('does not render disabled sources on the remote dashboard', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'System',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              enabled: true,
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Main Inbox'
            },
            {
              sourceId: 'source-2',
              ownerLabel: 'System',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              enabled: false,
              effectivePollEnabled: false,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Disabled Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByText('Main Inbox')).toBeInTheDocument()
    expect(screen.queryByText('Disabled Inbox')).not.toBeInTheDocument()
    expect(screen.getByText('Visible sources').nextElementSibling).toHaveTextContent('1')
  })

  it('shows source errors only after details are expanded and colors the last result pill', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: false,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: false,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-err',
              ownerLabel: 'Alice',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              enabled: true,
              effectivePollEnabled: true,
              effectivePollInterval: 'PT5M',
              lastImportedAt: null,
              lastEvent: {
                status: 'ERROR',
                error: 'Mailbox auth failed'
              },
              customLabel: 'Broken Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    const errorPill = await screen.findByText('Error')
    expect(errorPill).toHaveClass('tone-error')
    expect(screen.queryByText('Mailbox auth failed')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Show details' }))

    expect(await screen.findByText('Mailbox auth failed')).toBeInTheDocument()
  })

  it('shows the latest completed source counts in expanded details', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'System',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: '2026-03-31T10:00:10Z',
              lastEvent: {
                sourceId: 'source-1',
                trigger: 'MANUAL',
                status: 'SUCCESS',
                startedAt: '2026-03-31T10:00:00Z',
                finishedAt: '2026-03-31T10:00:10Z',
                fetched: 7,
                imported: 4,
                duplicates: 3,
                spamJunkMessageCount: 6,
                error: null
              },
              customLabel: 'Main Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    fireEvent.click(await screen.findByRole('button', { name: 'Show details' }))

    const lastResultLabel = screen.getByText('Last result')
    const lastResultValue = lastResultLabel.closest('div')
    const scoped = within(lastResultValue)
    const successPill = scoped.getByText('Success')
    const fetchedPill = scoped.getByText('Fetched: 7')
    const importedPill = scoped.getByText('Imported: 4')
    const duplicatesPill = scoped.getByText('Duplicates: 3')
    const spamJunkPill = scoped.getByText('Spam / Junk: 6')

    expect(successPill.closest('.status-pill')).toBeInTheDocument()
    expect(fetchedPill.closest('.remote-source-last-result-pill')).toBeInTheDocument()
    expect(importedPill.closest('.remote-source-last-result-pill')).toBeInTheDocument()
    expect(duplicatesPill.closest('.remote-source-last-result-pill')).toBeInTheDocument()
    expect(spamJunkPill.closest('.remote-source-last-result-pill')).toBeInTheDocument()
  })

  it('shows setup guidance when the signed-in user is not ready to poll', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: false,
          hasReadyDestinationMailbox: false,
          setupRequired: true,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByRole('heading', { name: 'Finish setup in My InboxBridge' })).toBeInTheDocument()
    expect(screen.getByText('Connect your destination mailbox from My InboxBridge.')).toBeInTheDocument()
    expect(screen.getByText('Add at least one source email account from My InboxBridge.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open My InboxBridge' })).toHaveAttribute('href', '/')
    expect(screen.queryByRole('button', { name: 'Poll My Sources' })).not.toBeInTheDocument()
  })

  it('returns to the login page when a poll request receives remote unauthorized', async () => {
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      if (url === '/api/remote/poll/run') {
        return jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    expect(await screen.findByRole('button', { name: 'Poll My Sources' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Poll My Sources' }))

    const signInButton = await screen.findByRole('button', { name: 'Sign in' })
    const notice = screen.getByText('Your remote session is no longer valid, so you were signed out. Please sign in again.')
    expect(signInButton.compareDocumentPosition(notice) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0)
    expect(screen.queryByText('Remote request failed (401)')).not.toBeInTheDocument()
  })

  it('returns to the login page immediately when the live event stream reports remote session revocation', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByRole('button', { name: 'Poll My Sources' })).toBeInTheDocument()

    act(() => {
      FakeEventSource.instances[0].emit('session-revoked', {
        type: 'session-revoked'
      })
    })

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByText('Your remote session is no longer valid, so you were signed out. Please sign in again.')).toBeInTheDocument()
  })

  it('returns to the login page when the remote live event stream drops and the session check comes back unauthorized', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    let authMeCount = 0
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        authMeCount += 1
        if (authMeCount > 1) {
          return jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)
        }
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByRole('button', { name: 'Poll My Sources' })).toBeInTheDocument()

    await act(async () => {
      await FakeEventSource.instances[0].onerror?.()
    })

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByText('Your remote session is no longer valid, so you were signed out. Please sign in again.')).toBeInTheDocument()
  })

  it('keeps the remote live event stream open across transient errors so later revocation events still work', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          currentSessionId: 707,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            currentSessionId: 707,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByRole('button', { name: 'Poll My Sources' })).toBeInTheDocument()

    await act(async () => {
      await FakeEventSource.instances[0].onerror?.()
    })

    expect(FakeEventSource.instances[0].closed).toBe(false)

    act(() => {
      FakeEventSource.instances[0].emit('session-revoked', {
        type: 'session-revoked',
        revokedSessionId: 707
      })
    })

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('accepts remote keepalive live events without disrupting the authenticated remote page', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          currentSessionId: 707,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            currentSessionId: 707,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    await screen.findByRole('button', { name: 'Poll My Sources' })

    act(() => {
      FakeEventSource.instances[0].emit('keepalive', {
        type: 'keepalive'
      })
    })

    expect(screen.getByRole('button', { name: 'Poll My Sources' })).toBeInTheDocument()
  })

  it('automatically captures browser-reported device location for the current remote session when permission is already granted', async () => {
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

    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: false,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      if (url === '/api/remote/auth/session/device-location') {
        return jsonResponse({}, 204)
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/remote/auth/session/device-location', expect.any(Object)))
  })

  it('still captures remote device location when the permissions api already reports granted', async () => {
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

    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 8,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: false,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 8,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      if (url === '/api/remote/auth/session/device-location') {
        return jsonResponse({})
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/remote/auth/session/device-location', expect.any(Object)))
  })

  it('captures remote device location after an explicit button click when the browser still needs a user gesture', async () => {
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

    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 18,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: false,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 18,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      if (url === '/api/remote/auth/session/device-location') {
        return jsonResponse({}, 204)
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    expect(fetchMock).not.toHaveBeenCalledWith('/api/remote/auth/session/device-location', expect.any(Object))
    fireEvent.click(await screen.findByRole('button', { name: 'More actions' }))
    fireEvent.click(within(document.querySelector('.remote-hero-menu-panel')).getByRole('button', { name: 'Share Device Location' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/remote/auth/session/device-location', expect.any(Object)))
  })

  it('shows an install prompt card when the browser exposes PWA installation', async () => {
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    const prompt = vi.fn().mockResolvedValue(undefined)

    render(<RemoteApp />)

    const installEvent = new Event('beforeinstallprompt')
    Object.defineProperty(installEvent, 'prompt', { configurable: true, value: prompt })
    Object.defineProperty(installEvent, 'userChoice', { configurable: true, value: Promise.resolve({ outcome: 'accepted' }) })
    await act(async () => {
      window.dispatchEvent(installEvent)
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Install App' }))

    await waitFor(() => expect(prompt).toHaveBeenCalled())
  })

  it('shows manual install guidance even when the browser does not expose an install prompt', async () => {
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    expect(await screen.findByText('Install InboxBridge')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Install App' })).not.toBeInTheDocument()
    expect(screen.getByText('Chrome or Edge: use the Install App button here, or open the browser menu and choose Install app.')).toBeInTheDocument()
    expect(screen.getByText('Safari on iPhone or iPad: open Share, then choose Add to Home Screen.')).toBeInTheDocument()
    expect(screen.getByText('Firefox on desktop does not currently expose a full install flow for this app, so use Chrome/Edge or Safari on Apple devices instead.')).toBeInTheDocument()
  })

  it('lets the user hide the install section and reopen it from the hero menu', async () => {
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    expect(await screen.findByText('Install InboxBridge')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Not now' }))

    expect(screen.queryByText('Install InboxBridge')).not.toBeInTheDocument()
    expect(window.localStorage.getItem('inboxbridge.remote.installPromptDismissed')).toBe('true')

    fireEvent.click(screen.getByRole('button', { name: 'More actions' }))
    fireEvent.click(screen.getByRole('button', { name: 'Install InboxBridge' }))

    expect(await screen.findByText('Install InboxBridge')).toBeInTheDocument()
    expect(window.localStorage.getItem('inboxbridge.remote.installPromptDismissed')).toBe('false')
  })

  it('shows live remote poll progress and control actions', async () => {
    vi.stubGlobal('EventSource', FakeEventSource)
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'System',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Main Inbox'
            },
            {
              sourceId: 'source-2',
              ownerLabel: 'Alice',
              protocol: 'IMAP',
              host: 'imap.backup.example.com',
              port: 993,
              folder: 'Archive',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Archive Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      if (url === '/api/remote/poll/live/pause') {
        return jsonResponse({
          running: true,
          state: 'PAUSING',
          ownerUsername: 'admin',
          viewerCanControl: true,
          activeSourceId: 'source-1',
          sources: [
            { sourceId: 'source-1', label: 'Main Inbox', state: 'RUNNING', actionable: true, position: 1, fetched: 0, imported: 0, duplicates: 0 },
            { sourceId: 'source-2', label: 'Archive Inbox', state: 'QUEUED', actionable: true, position: 2, fetched: 0, imported: 0, duplicates: 0 }
          ]
        })
      }
      if (url === '/api/remote/poll/live/stop') {
        return jsonResponse({
          running: true,
          state: 'STOPPING',
          ownerUsername: 'admin',
          viewerCanControl: true,
          activeSourceId: 'source-1',
          sources: [
            { sourceId: 'source-1', label: 'Main Inbox', state: 'RUNNING', actionable: true, position: 1, fetched: 0, imported: 0, duplicates: 0 },
            { sourceId: 'source-2', label: 'Archive Inbox', state: 'QUEUED', actionable: true, position: 2, fetched: 0, imported: 0, duplicates: 0 }
          ]
        })
      }
      if (url === '/api/remote/poll/live/sources/source-2/move-next') {
        return jsonResponse({
          running: true,
          state: 'RUNNING',
          ownerUsername: 'admin',
          viewerCanControl: true,
          activeSourceId: 'source-1',
          sources: [
            { sourceId: 'source-1', label: 'Main Inbox', state: 'RUNNING', actionable: true, position: 1, fetched: 0, imported: 0, duplicates: 0 },
            { sourceId: 'source-2', label: 'Archive Inbox', state: 'QUEUED', actionable: true, position: 1, fetched: 0, imported: 0, duplicates: 0 }
          ]
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    await screen.findByText('Main Inbox')

    act(() => {
      FakeEventSource.instances[0].emit('poll-source-started', {
        poll: {
          running: true,
          runId: 'run-1',
          state: 'RUNNING',
          ownerUsername: 'admin',
          viewerCanControl: true,
          activeSourceId: 'source-1',
          sources: [
            { sourceId: 'source-1', label: 'Main Inbox', state: 'RUNNING', actionable: true, position: 1, fetched: 0, imported: 0, duplicates: 0 },
            { sourceId: 'source-2', label: 'Archive Inbox', state: 'QUEUED', actionable: true, position: 2, fetched: 0, imported: 0, duplicates: 0 }
          ]
        }
      })
    })

    expect(screen.queryByText('Live Poll Progress')).not.toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Pause' })).toBeInTheDocument()
    expect(screen.queryByText(/Queue 2147483647/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Fetched 0, imported 0, duplicates 0/)).not.toBeInTheDocument()
    expect(screen.getAllByText('Running…').length).toBe(1)

    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/remote/poll/live/pause', expect.any(Object))
    })

    act(() => {
      FakeEventSource.instances[0].emit('poll-source-started', {
        poll: {
          running: true,
          runId: 'run-1',
          state: 'RUNNING',
          ownerUsername: 'admin',
          viewerCanControl: true,
          activeSourceId: 'source-1',
          sources: [
            { sourceId: 'source-1', label: 'Main Inbox', state: 'RUNNING', actionable: true, position: 1, fetched: 0, imported: 0, duplicates: 0 },
            { sourceId: 'source-2', label: 'Archive Inbox', state: 'QUEUED', actionable: true, position: 2, fetched: 0, imported: 0, duplicates: 0 }
          ]
        }
      })
    })

    fireEvent.click(screen.getByRole('button', { name: 'Move Next' }))
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/remote/poll/live/sources/source-2/move-next', expect.any(Object))
    })

    act(() => {
      FakeEventSource.instances[0].emit('poll-source-finished', {
        poll: {
          running: true,
          runId: 'run-1',
          state: 'RUNNING',
          ownerUsername: 'admin',
          viewerCanControl: true,
          activeSourceId: 'source-1',
          sources: [
            { sourceId: 'source-1', label: 'Main Inbox', state: 'RUNNING', actionable: true, position: 1, fetched: 0, imported: 0, duplicates: 0 },
            { sourceId: 'source-2', label: 'Archive Inbox', state: 'FAILED', actionable: true, position: Number.MAX_SAFE_INTEGER, fetched: 5, imported: 0, duplicates: 0, error: 'Auth failed' }
          ]
        }
      })
    })

    fireEvent.click(screen.getAllByRole('button', { name: 'Show details' })[1])
    expect(await screen.findByText('Auth failed')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Fetched 5, imported 0, duplicates 0/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Stop' }))
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/remote/poll/live/stop', expect.any(Object))
    })
  })

  it('only shows queue position for sources that are still queued', async () => {
    const fetchMock = vi.fn(async (url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 1,
          username: 'admin',
          role: 'ADMIN',
          canRunAllUsersPoll: true,
          canRunUserPoll: true,
          canRunOwnSourcePolls: true,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 1,
            username: 'admin',
            role: 'ADMIN',
            canRunAllUsersPoll: true,
            canRunUserPoll: true,
            canRunOwnSourcePolls: true,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'admin',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Main Inbox'
            },
            {
              sourceId: 'source-2',
              ownerLabel: 'alice',
              protocol: 'IMAP',
              host: 'imap.backup.example.com',
              port: 993,
              folder: 'Archive',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Archive Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({
          running: true,
          runId: 'run-1',
          state: 'RUNNING',
          ownerUsername: 'admin',
          viewerCanControl: true,
          activeSourceId: 'source-1',
          sources: [
            { sourceId: 'source-1', ownerUsername: 'admin', label: 'Main Inbox', state: 'RUNNING', actionable: true, position: 2147483647, fetched: 0, imported: 0, duplicates: 0 },
            { sourceId: 'source-2', ownerUsername: 'alice', label: 'Archive Inbox', state: 'QUEUED', actionable: true, position: 2, fetched: 0, imported: 0, duplicates: 0 }
          ]
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    await screen.findByText('Main Inbox')

    expect(screen.queryByText('Queue 2147483647')).not.toBeInTheDocument()
    expect(screen.queryByText('Owner admin')).not.toBeInTheDocument()
    expect(screen.getByText('Queue 2')).toBeInTheDocument()
  })

  it('hides owner labels on InboxBridge Go when single-user mode is active', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 1,
          username: 'admin',
          role: 'ADMIN',
          canRunAllUsersPoll: true,
          canRunUserPoll: true,
          multiUserEnabled: false,
          language: 'en'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 1,
            username: 'admin',
            role: 'ADMIN',
            canRunAllUsersPoll: true,
            canRunUserPoll: true,
            multiUserEnabled: false,
            language: 'en'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'admin',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: '5m',
              lastImportedAt: null,
              lastEvent: null,
              customLabel: 'Main Inbox'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({
          running: true,
          runId: 'run-1',
          state: 'RUNNING',
          ownerUsername: 'admin',
          viewerCanControl: true,
          activeSourceId: 'source-1',
          sources: [
            { sourceId: 'source-1', ownerUsername: 'admin', label: 'Main Inbox', state: 'RUNNING', actionable: true, position: 1, fetched: 0, imported: 0, duplicates: 0 }
          ]
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByText('Main Inbox')).toBeInTheDocument()
    expect(screen.getByText('Signed in as admin.')).toBeInTheDocument()
    expect(screen.queryByText('Owner admin')).not.toBeInTheDocument()
    expect(screen.queryByText(/\(ADMIN\)/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Show details' }))
    expect(screen.queryByText('Owner')).not.toBeInTheDocument()
  })

  it('uses the saved user preference language after the remote session loads', async () => {
    window.localStorage.setItem('inboxbridge.language', 'en')
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 12,
          username: 'alice',
          role: 'USER',
          canRunUserPoll: true,
          canRunAllUsersPoll: false,
          deviceLocationCaptured: true,
          language: 'pt-PT'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 12,
            username: 'alice',
            role: 'USER',
            canRunUserPoll: true,
            canRunAllUsersPoll: false,
            language: 'pt-PT'
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByRole('heading', { name: 'InboxBridge Go' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Mais ações' }))
    expect(within(document.querySelector('.remote-hero-menu-panel')).getByRole('button', { name: 'Sair' })).toBeInTheDocument()
  })

  it('renders the remote dashboard and install guidance fully translated in Portuguese', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 12,
          username: 'alice',
          role: 'USER',
          canRunUserPoll: true,
          canRunAllUsersPoll: false,
          deviceLocationCaptured: true,
          language: 'pt-PT'
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 12,
            username: 'alice',
            role: 'USER',
            canRunUserPoll: true,
            canRunAllUsersPoll: false,
            language: 'pt-PT'
          },
          sources: [
            {
              sourceId: 'source-1',
              ownerLabel: 'Sistema',
              protocol: 'IMAP',
              host: 'imap.example.com',
              port: 993,
              folder: 'INBOX',
              effectivePollEnabled: true,
              effectivePollInterval: 'PT5M',
              lastImportedAt: '2026-03-31T10:00:10Z',
              lastEvent: {
                sourceId: 'source-1',
                trigger: 'MANUAL',
                status: 'SUCCESS',
                startedAt: '2026-03-31T10:00:00Z',
                finishedAt: '2026-03-31T10:00:10Z',
                fetched: 7,
                imported: 4,
                duplicates: 3,
                spamJunkMessageCount: 6,
                error: null
              },
              customLabel: 'Caixa principal'
            }
          ],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/poll/live') {
        return jsonResponse({ running: false, sources: [] })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByText('Instalar InboxBridge')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Recolher as minhas origens' })).toBeInTheDocument()
    expect(screen.getByText('Origens visíveis')).toBeInTheDocument()
    expect(screen.getByText('Limite de ações remotas')).toBeInTheDocument()
    expect(screen.getByText(/60 ações por 1 minuto/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Contas de email de origem' })).toBeInTheDocument()
    expect(screen.getByText('Execute uma recolha direcionada para uma origem ou dispare uma execução mais abrangente a partir das ações no topo.')).toBeInTheDocument()
    expect(screen.getAllByText('Sucesso').length).toBeGreaterThan(0)

    fireEvent.click(screen.getByRole('button', { name: 'Mostrar detalhes' }))

    expect(screen.getByText('Último resultado')).toBeInTheDocument()
    expect(screen.getByText('Obtidas: 7')).toBeInTheDocument()
    expect(screen.getByText('Importadas: 4')).toBeInTheDocument()
    expect(screen.getByText('Duplicadas: 3')).toBeInTheDocument()
    expect(screen.getByText('Spam / Lixo: 6')).toBeInTheDocument()
  })
})
