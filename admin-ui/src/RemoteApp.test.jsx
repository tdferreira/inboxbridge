import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import RemoteApp from './RemoteApp'

function jsonResponse(payload, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(JSON.stringify(payload)),
    json: () => Promise.resolve(payload)
  })
}

describe('RemoteApp', () => {
  it('shows the remote login screen when no remote session exists', async () => {
    vi.stubGlobal('fetch', vi.fn(() => jsonResponse({ code: 'unauthorized', message: 'Not authenticated' }, 401)))

    render(<RemoteApp />)

    expect(await screen.findByRole('heading', { name: 'Quick polling control' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open Remote Control' })).toBeInTheDocument()
  })

  it('runs a source poll from the remote dashboard', async () => {
    const fetchMock = vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true,
          deviceLocationCaptured: true
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true
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
  })

  it('keeps each source collapsed by default while leaving the poll action visible', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true
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
      throw new Error(`Unexpected fetch: ${url}`)
    }))

    render(<RemoteApp />)

    expect(await screen.findByText('Archive Mail')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Poll This Source' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Show details' })).toBeInTheDocument()
    expect(screen.queryByText('10 minutes')).not.toBeInTheDocument()
  })

  it('shows setup guidance when the signed-in user is not ready to poll', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === '/api/remote/auth/me') {
        return jsonResponse({
          id: 7,
          username: 'admin',
          role: 'ADMIN',
          canRunUserPoll: true,
          canRunAllUsersPoll: true
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true
          },
          sources: [],
          hasOwnSourceEmailAccounts: false,
          hasReadyDestinationMailbox: false,
          setupRequired: true,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
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
          canRunAllUsersPoll: true
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
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

    expect(await screen.findByRole('heading', { name: 'Quick polling control' })).toBeInTheDocument()
    expect(screen.getByText('Your remote session is no longer valid, so you were signed out. Please sign in again.')).toBeInTheDocument()
    expect(screen.queryByText('Remote request failed (401)')).not.toBeInTheDocument()
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
          deviceLocationCaptured: false
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
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
          deviceLocationCaptured: false
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 8,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
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
          deviceLocationCaptured: false
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 18,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
      }
      if (url === '/api/remote/auth/session/device-location') {
        return jsonResponse({}, 204)
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<RemoteApp />)

    expect(fetchMock).not.toHaveBeenCalledWith('/api/remote/auth/session/device-location', expect.any(Object))
    fireEvent.click(await screen.findByRole('button', { name: 'Share Device Location' }))

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
          deviceLocationCaptured: true
        })
      }
      if (url === '/api/remote/control') {
        return jsonResponse({
          session: {
            id: 7,
            username: 'admin',
            role: 'ADMIN',
            canRunUserPoll: true,
            canRunAllUsersPoll: true
          },
          sources: [],
          hasOwnSourceEmailAccounts: true,
          hasReadyDestinationMailbox: true,
          setupRequired: false,
          remotePollRateLimitCount: 60,
          remotePollRateLimitWindow: 'PT1M'
        })
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
})
