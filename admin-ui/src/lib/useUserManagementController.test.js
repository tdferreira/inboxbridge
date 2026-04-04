import { act, renderHook } from '@testing-library/react'
import { useUserManagementController } from './useUserManagementController'
import { resetCurrentFormattingTimeZone, setCurrentFormattingTimeZone } from './timeZonePreferences'

function jsonResponse(payload, ok = true) {
  return {
    ok,
    json: async () => payload
  }
}

describe('useUserManagementController', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    resetCurrentFormattingTimeZone()
    vi.unstubAllGlobals()
  })

  function renderController(overrides = {}) {
    const closeConfirmation = vi.fn()
    const loadAppData = vi.fn()
    const loadAuthOptions = vi.fn()
    const openConfirmation = vi.fn()
    const pushNotification = vi.fn()
    const t = vi.fn((key, params = {}) => key === 'users.duplicateUsername' ? `Duplicate ${params.username}` : key)
    const errorText = vi.fn((key) => key)
    const withPending = vi.fn(async (_key, action) => action())

    const hook = renderHook((props) => useUserManagementController(props), {
      initialProps: {
        authOptions: { multiUserEnabled: true },
        closeConfirmation,
        errorText,
        loadAppData,
        loadAuthOptions,
        openConfirmation,
        pushNotification,
        session: { role: 'ADMIN', username: 'admin' },
        t,
        withPending,
        ...overrides
      }
    })

    return {
      ...hook,
      pushNotification
    }
  }

  it('derives duplicate usernames from loaded users', () => {
    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUsers([{ id: 1, username: 'Alice' }])
      result.current.setCreateUserForm({
        username: 'alice',
        password: 'NewPass1!',
        confirmPassword: 'NewPass1!',
        role: 'USER'
      })
    })

    expect(result.current.duplicateCreateUsername).toBe(true)
  })

  it('resets the create-user dialog state when opened', () => {
    const { result } = renderController()

    act(() => {
      result.current.setCreateUserForm({
        username: 'stale',
        password: 'NewPass1!',
        confirmPassword: 'NewPass1!',
        role: 'ADMIN'
      })
      result.current.openCreateUserDialog()
    })

    expect(result.current.showCreateUserDialog).toBe(true)
    expect(result.current.createUserForm).toEqual({
      username: '',
      password: '',
      confirmPassword: '',
      role: 'USER'
    })
  })

  it('blocks duplicate user creation before issuing a request', async () => {
    const event = { preventDefault: vi.fn() }
    const { result, pushNotification } = renderController()

    act(() => {
      result.current.applyLoadedUsers([{ id: 1, username: 'Alice' }])
      result.current.setCreateUserForm({
        username: 'alice',
        password: 'NewPass1!',
        confirmPassword: 'NewPass1!',
        role: 'USER'
      })
    })

    await act(async () => {
      await result.current.createUser(event)
    })

    expect(event.preventDefault).toHaveBeenCalled()
    expect(fetch).not.toHaveBeenCalled()
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: expect.objectContaining({
        key: 'users.duplicateUsername',
        kind: 'translation',
        params: { username: 'alice' }
      }),
      tone: 'error'
    }))
  })

  it('normalizes partial selected-user configuration payloads from the API', async () => {
    fetch.mockResolvedValueOnce(jsonResponse({
      pollingSettings: {
        effectivePollEnabled: true,
        effectivePollInterval: '5m',
        effectiveFetchWindow: 25
      },
      emailAccounts: [{ emailAccountId: 'outlook-main' }]
    }))

    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUsers([{ id: 7, username: 'admin', role: 'ADMIN', approved: true, active: true, emailAccountCount: 1 }])
    })

    await act(async () => {
      await result.current.loadSelectedUserConfiguration(7)
    })

    expect(result.current.selectedUserConfig).toEqual(expect.objectContaining({
      user: expect.objectContaining({ id: 7, username: 'admin', emailAccountCount: 1 }),
      destinationConfig: expect.objectContaining({ provider: '', linked: false }),
      pollingSettings: expect.objectContaining({ effectivePollEnabled: true, effectivePollInterval: '5m', effectiveFetchWindow: 25 }),
      emailAccounts: [{ emailAccountId: 'outlook-main' }],
      emailAccounts: [{ emailAccountId: 'outlook-main' }],
      passkeys: []
    }))
  })

  it('keeps the selected-user loader stable when the users list refreshes', () => {
    const { result } = renderController()
    const initialLoader = result.current.loadSelectedUserConfiguration

    act(() => {
      result.current.applyLoadedUsers([{ id: 7, username: 'admin', role: 'ADMIN', approved: true, active: true }])
    })

    expect(result.current.loadSelectedUserConfiguration).toBe(initialLoader)

    act(() => {
      result.current.applyLoadedUsers([{ id: 7, username: 'admin', role: 'ADMIN', approved: true, active: true, emailAccountCount: 1 }])
    })

    expect(result.current.loadSelectedUserConfiguration).toBe(initialLoader)
  })

  it('uses the current effective formatting timezone for each selected-user stats request', async () => {
    fetch.mockResolvedValue(jsonResponse({
      pollingSettings: {
        effectivePollEnabled: true,
        effectivePollInterval: '5m',
        effectiveFetchWindow: 25
      }
    }))

    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUsers([{ id: 7, username: 'admin', role: 'ADMIN', approved: true, active: true }])
      setCurrentFormattingTimeZone('Europe/Lisbon')
    })

    await act(async () => {
      await result.current.loadSelectedUserConfiguration(7)
    })

    act(() => {
      setCurrentFormattingTimeZone('America/New_York')
    })

    await act(async () => {
      await result.current.loadSelectedUserConfiguration(7)
    })

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/admin/users/7/configuration', expect.objectContaining({
      headers: { 'X-InboxBridge-Timezone': 'Europe/Lisbon' }
    }))
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/admin/users/7/configuration', expect.objectContaining({
      headers: { 'X-InboxBridge-Timezone': 'America/New_York' }
    }))
  })
})
