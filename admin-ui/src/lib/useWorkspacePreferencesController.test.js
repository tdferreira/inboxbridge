import { act, renderHook, waitFor } from '@testing-library/react'
import { useWorkspacePreferencesController } from './useWorkspacePreferencesController'

function createFetchResponse(payload) {
  return {
    ok: true,
    json: async () => payload
  }
}

function clearLocalStorage() {
  if (typeof window.localStorage?.clear === 'function') {
    window.localStorage.clear()
    return
  }
  Object.keys(window.localStorage || {}).forEach((key) => {
    delete window.localStorage[key]
  })
}

describe('useWorkspacePreferencesController', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn())
    clearLocalStorage()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function renderController(overrides = {}) {
    const pushNotification = vi.fn()
    const setLanguage = vi.fn()
    const withPending = vi.fn(async (_key, action) => action())

    const hook = renderHook((props) => useWorkspacePreferencesController(props), {
      initialProps: {
        language: 'en',
        pushNotification,
        session: { id: 42, role: 'ADMIN' },
        setLanguage,
        withPending,
        ...overrides
      }
    })

    return {
      ...hook,
      pushNotification,
      setLanguage,
      withPending
    }
  }

  it('normalizes loaded preferences including legacy user section ids', () => {
    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUiPreferences({
        persistLayout: true,
        userSectionOrder: ['sourceBridges', 'gmail', 'userStats']
      }, 42)
    })

    expect(result.current.uiPreferences.userSectionOrder).toEqual([
      'sourceEmailAccounts',
      'destination',
      'userStats'
    ])
  })

  it('expands a collapsed section and persists the updated preferences', async () => {
    fetch.mockResolvedValue(createFetchResponse({ persistLayout: true }))

    const { result, withPending } = renderController()
    const onExpand = vi.fn()

    act(() => {
      result.current.applyLoadedUiPreferences({
        persistLayout: true,
        destinationMailboxCollapsed: true
      }, 42)
    })

    await act(async () => {
      await result.current.toggleSection('destinationMailboxCollapsed', onExpand)
    })

    expect(onExpand).toHaveBeenCalledTimes(1)
    expect(result.current.uiPreferences.destinationMailboxCollapsed).toBe(false)
    expect(withPending).toHaveBeenCalledWith('uiPreferences', expect.any(Function))
    expect(fetch).toHaveBeenCalledWith('/api/app/ui-preferences', expect.objectContaining({
      method: 'PUT'
    }))
  })

  it('updates the active language and stores it locally', async () => {
    fetch.mockResolvedValue(createFetchResponse({ language: 'pt-BR' }))

    const { result, rerender, setLanguage } = renderController()

    await act(async () => {
      await result.current.handleLanguageChange('pt-BR')
    })

    expect(setLanguage).toHaveBeenCalledWith('pt-BR')
    rerender({
      language: 'pt-BR',
      pushNotification: vi.fn(),
      session: { id: 42, role: 'ADMIN' },
      setLanguage,
      withPending: vi.fn(async (_key, action) => action())
    })

    expect(window.localStorage.getItem('inboxbridge.language')).toBe('pt-BR')
    expect(fetch).toHaveBeenCalledWith('/api/app/ui-preferences', expect.objectContaining({
      method: 'PUT'
    }))
  })

  it('tracks layout edits against a snapshot and can discard them', async () => {
    fetch.mockImplementation(async (_url, options = {}) => createFetchResponse({
      ...(options.body ? JSON.parse(options.body) : {}),
      persistLayout: true
    }))

    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUiPreferences({
        persistLayout: true,
        userSectionOrder: ['quickSetup', 'destination', 'userPolling', 'userStats', 'sourceEmailAccounts']
      }, 42)
      result.current.startLayoutEditingFromPreferences()
    })

    await act(async () => {
      await result.current.moveSection('user', 'sourceEmailAccounts', 'up')
    })

    await waitFor(() => {
      expect(result.current.uiPreferences.userSectionOrder).toEqual([
        'quickSetup',
        'destination',
        'userPolling',
        'sourceEmailAccounts',
        'userStats'
      ])
    })

    await act(async () => {
      await result.current.discardLayoutEditingChanges()
    })

    expect(result.current.uiPreferences.layoutEditEnabled).toBe(false)
    expect(result.current.uiPreferences.userSectionOrder).toEqual([
      'quickSetup',
      'destination',
      'userPolling',
      'userStats',
      'sourceEmailAccounts'
    ])
  })
})
