import { act, renderHook } from '@testing-library/react'
import { useUserManagementController } from './useUserManagementController'

describe('useUserManagementController', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
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
      message: 'Duplicate alice',
      tone: 'error'
    }))
  })
})
