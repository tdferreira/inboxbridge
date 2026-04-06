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

  it('updates only the selected workspace quick setup visibility state', () => {
    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUiPreferences({
        persistLayout: true,
        quickSetupPinnedVisible: true,
        adminQuickSetupPinnedVisible: false
      }, 42)
      result.current.handleQuickSetupVisibilityChange('admin', true, true)
    })

    expect(result.current.uiPreferences.quickSetupPinnedVisible).toBe(true)
    expect(result.current.uiPreferences.adminQuickSetupPinnedVisible).toBe(true)
    expect(result.current.uiPreferences.adminQuickSetupDismissed).toBe(false)
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

  it('switches to a manual timezone and persists it', async () => {
    fetch.mockResolvedValue(createFetchResponse({ timezoneMode: 'MANUAL', timezone: 'Europe/Lisbon' }))

    const { result } = renderController()

    await act(async () => {
      await result.current.handleTimeZoneModeChange('MANUAL')
    })

    await act(async () => {
      await result.current.handleTimeZoneChange('Europe/Lisbon')
    })

    expect(result.current.uiPreferences.timezoneMode).toBe('MANUAL')
    expect(result.current.uiPreferences.timezone).toBe('Europe/Lisbon')
    expect(fetch).toHaveBeenCalledWith('/api/app/ui-preferences', expect.objectContaining({
      method: 'PUT'
    }))
  })

  it('persists a manually selected date format', async () => {
    fetch.mockResolvedValue(createFetchResponse({ dateFormat: 'YMD_24' }))

    const { result } = renderController()

    await act(async () => {
      await result.current.handleDateFormatChange('YMD_24')
    })

    expect(result.current.uiPreferences.dateFormat).toBe('YMD_24')
    expect(fetch).toHaveBeenCalledWith('/api/app/ui-preferences', expect.objectContaining({
      method: 'PUT'
    }))
  })

  it('persists a valid custom date format in the single dateFormat preference', async () => {
    fetch.mockResolvedValue(createFetchResponse({ dateFormat: 'DD/MM/YYYY HH:mm:ss' }))

    const { result } = renderController()

    await act(async () => {
      await result.current.handleDateFormatChange('DD/MM/YYYY HH:mm:ss')
    })

    expect(result.current.uiPreferences.dateFormat).toBe('DD/MM/YYYY HH:mm:ss')
    expect(fetch).toHaveBeenCalledWith('/api/app/ui-preferences', expect.objectContaining({
      method: 'PUT',
      body: expect.stringContaining('"dateFormat":"DD/MM/YYYY HH:mm:ss"')
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

    await waitFor(() => {
      expect(result.current.uiPreferences.layoutEditEnabled).toBe(true)
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
        'userStats',
        'remoteControl'
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

  it('keeps layout editing active while moving sections during an edit session', async () => {
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

    expect(result.current.uiPreferences.layoutEditEnabled).toBe(true)
  })

  it('can move the remote control section even when older saved preferences did not include it yet', async () => {
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
      await result.current.moveSection('user', 'remoteControl', 'up')
    })

    expect(result.current.uiPreferences.userSectionOrder).toEqual([
      'quickSetup',
      'destination',
      'userPolling',
      'userStats',
      'remoteControl',
      'sourceEmailAccounts'
    ])
    expect(result.current.uiPreferences.layoutEditEnabled).toBe(true)
  })

  it('reorders the first section into the second position when dropped into the next insertion slot', async () => {
    fetch.mockImplementation(async (_url, options = {}) => createFetchResponse({
      ...(options.body ? JSON.parse(options.body) : {}),
      persistLayout: true
    }))

    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUiPreferences({
        persistLayout: true,
        userSectionOrder: ['quickSetup', 'destination', 'userPolling']
      }, 42)
      result.current.startLayoutEditingFromPreferences()
    })

    await act(async () => {
      await result.current.reorderSections('user', 'quickSetup', 2)
    })

    expect(result.current.uiPreferences.userSectionOrder.slice(0, 3)).toEqual([
      'destination',
      'quickSetup',
      'userPolling'
    ])
    expect(result.current.uiPreferences.layoutEditEnabled).toBe(true)
  })

  it('reorders the last section into the previous position when dropped into the prior insertion slot', async () => {
    fetch.mockImplementation(async (_url, options = {}) => createFetchResponse({
      ...(options.body ? JSON.parse(options.body) : {}),
      persistLayout: true
    }))

    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUiPreferences({
        persistLayout: true,
        userSectionOrder: ['quickSetup', 'destination', 'userPolling']
      }, 42)
      result.current.startLayoutEditingFromPreferences()
    })

    await act(async () => {
      await result.current.reorderSections('user', 'userPolling', 1)
    })

    expect(result.current.uiPreferences.userSectionOrder.slice(0, 3)).toEqual([
      'quickSetup',
      'userPolling',
      'destination'
    ])
    expect(result.current.uiPreferences.layoutEditEnabled).toBe(true)
  })

  it('moves a visible section into the last visible position', async () => {
    fetch.mockImplementation(async (_url, options = {}) => createFetchResponse({
      ...(options.body ? JSON.parse(options.body) : {}),
      persistLayout: true
    }))

    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUiPreferences({
        persistLayout: true,
        userSectionOrder: ['quickSetup', 'destination', 'sourceEmailAccounts', 'userPolling', 'remoteControl', 'userStats']
      }, 42)
      result.current.startLayoutEditingFromPreferences()
    })

    await act(async () => {
      await result.current.reorderSections('user', 'destination', 4, ['destination', 'sourceEmailAccounts', 'userPolling', 'remoteControl'])
    })

    expect(result.current.uiPreferences.userSectionOrder.slice(0, 4)).toEqual([
      'sourceEmailAccounts',
      'userPolling',
      'remoteControl',
      'destination'
    ])
    expect(result.current.uiPreferences.layoutEditEnabled).toBe(true)
  })
})
