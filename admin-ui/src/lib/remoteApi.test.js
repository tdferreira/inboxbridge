import {
  RemoteUnauthorizedError,
  recordRemoteDeviceLocation,
  remoteRunSourcePoll,
  remoteSession
} from './remoteApi'

describe('remoteApi', () => {
  beforeEach(() => {
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      value: 'inboxbridge_remote_csrf=csrf-token'
    })
  })

  it('sends the remote CSRF token on unsafe requests and encodes source ids', async () => {
    const fetchMock = vi.spyOn(window, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    )

    await remoteRunSourcePoll('source/id')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/remote/sources/source%2Fid/poll/run',
      expect.objectContaining({
        credentials: 'include',
        method: 'POST',
        headers: expect.objectContaining({
          'X-InboxBridge-CSRF': 'csrf-token'
        })
      })
    )
  })

  it('returns null for 204 responses', async () => {
    vi.spyOn(window, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))

    await expect(remoteSession()).resolves.toBeNull()
  })

  it('throws a dedicated unauthorized error for expired sessions', async () => {
    vi.spyOn(window, 'fetch').mockResolvedValue(
      new Response('Expired', {
        status: 401,
        headers: { 'Content-Type': 'text/plain' }
      })
    )

    await expect(recordRemoteDeviceLocation({ latitude: 1, longitude: 2 })).rejects.toBeInstanceOf(RemoteUnauthorizedError)
  })
})
