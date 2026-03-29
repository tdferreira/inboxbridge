import { act, renderHook } from '@testing-library/react'
import { usePollingControllers } from './usePollingControllers'

describe('usePollingControllers', () => {
  function renderController(overrides = {}) {
    const closeConfirmation = vi.fn()
    const loadAppData = vi.fn()
    const openConfirmation = vi.fn()
    const pushNotification = vi.fn()
    const withPending = vi.fn(async (_key, action) => action())
    const t = vi.fn((key) => key)
    const errorText = vi.fn((key) => key)

    const hook = renderHook((props) => usePollingControllers(props), {
      initialProps: {
        authOptions: { multiUserEnabled: true },
        closeConfirmation,
        errorText,
        language: 'en',
        loadAppData,
        openConfirmation,
        pushNotification,
        t,
        withPending,
        ...overrides
      }
    })

    return {
      ...hook,
      openConfirmation,
      withPending
    }
  }

  it('hydrates user polling settings while the form is pristine', () => {
    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUserPolling({
        pollEnabledOverride: true,
        pollIntervalOverride: '10m',
        fetchWindowOverride: 25
      })
    })

    expect(result.current.userPollingForm).toEqual({
      pollEnabledMode: 'ENABLED',
      pollIntervalOverride: '10m',
      fetchWindowOverride: '25'
    })
  })

  it('does not overwrite a dirty user polling form with loaded data', () => {
    const { result } = renderController()

    act(() => {
      result.current.handleUserPollingFormChange({
        pollEnabledMode: 'DISABLED',
        pollIntervalOverride: '2m',
        fetchWindowOverride: '12'
      })
    })

    act(() => {
      result.current.applyLoadedUserPolling({
        pollEnabledOverride: null,
        pollIntervalOverride: '10m',
        fetchWindowOverride: 25
      })
    })

    expect(result.current.userPollingForm).toEqual({
      pollEnabledMode: 'DISABLED',
      pollIntervalOverride: '2m',
      fetchWindowOverride: '12'
    })
  })

  it('opens a global poll confirmation with the multi-user copy', async () => {
    const { result, openConfirmation } = renderController({
      authOptions: { multiUserEnabled: true }
    })

    await act(async () => {
      await result.current.runPoll()
    })

    expect(openConfirmation).toHaveBeenCalledWith(expect.objectContaining({
      actionKey: 'runPoll',
      title: 'system.runPollConfirmTitle'
    }))
  })
})
