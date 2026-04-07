import { act, renderHook } from '@testing-library/react'
import { useSessionDeviceLocation } from './useSessionDeviceLocation'

function geolocationError(code, message) {
  const error = new Error(message)
  error.code = code
  return error
}

describe('useSessionDeviceLocation', () => {
  const t = (key, params = {}) => `${key}${params.detail || ''}`

  beforeEach(() => {
    window.localStorage.clear()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('dismisses the prompt and persists dismissal per session key', async () => {
    const captureLocation = vi.fn()
    Object.assign(navigator, {
      geolocation: {
        getCurrentPosition: vi.fn()
      },
      permissions: {
        query: vi.fn().mockResolvedValue({ state: 'prompt', onchange: null })
      }
    })

    let result
    let unmount
    await act(async () => {
      const rendered = renderHook(() => useSessionDeviceLocation({
        captureLocation,
        session: { id: 'session-1', deviceLocationCaptured: false },
        storageScope: 'admin',
        t
      }))
      result = rendered.result
      unmount = rendered.unmount
      await Promise.resolve()
    })

    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(result.current.permissionState).toBe('prompt')
    expect(result.current.shouldPrompt).toBe(true)

    act(() => {
      result.current.dismissPrompt()
    })

    expect(result.current.shouldPrompt).toBe(false)
    expect(window.localStorage.getItem('inboxbridge.device-location.dismissed:admin:session-1')).toBe('true')

    await act(async () => {
      unmount()
      await Promise.resolve()
    })
  })

  it('captures the location manually and reports success', async () => {
    const captureLocation = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, {
      geolocation: {
        getCurrentPosition: vi.fn((success) => {
          success({
            coords: {
              latitude: 38.72,
              longitude: -9.13,
              accuracy: 12
            }
          })
        })
      },
      permissions: {
        query: vi.fn().mockResolvedValue({ state: 'prompt', onchange: null })
      }
    })

    const { result } = renderHook(() => useSessionDeviceLocation({
      captureLocation,
      session: { id: 'session-2', deviceLocationCaptured: false },
      storageScope: 'remote',
      t
    }))

    await act(async () => {
      await result.current.requestLocation()
    })

    expect(captureLocation).toHaveBeenCalledWith({
      latitude: 38.72,
      longitude: -9.13,
      accuracyMeters: 12
    })
    expect(result.current.success).toBe('deviceLocation.success')
    expect(window.localStorage.getItem('inboxbridge.device-location.dismissed:remote:session-2')).toBe('true')
  })

  it('auto-captures after geolocation permission is granted and retries transient failures', async () => {
    const captureLocation = vi.fn().mockResolvedValue(undefined)
    const getCurrentPosition = vi
      .fn()
      .mockImplementationOnce((success, error) => error(geolocationError(2, 'Unavailable')))
      .mockImplementationOnce((success, error) => error(geolocationError(2, 'Still unavailable')))
      .mockImplementationOnce((success) => success({
        coords: {
          latitude: 40.41,
          longitude: -3.7,
          accuracy: 24
        }
      }))

    Object.assign(navigator, {
      geolocation: {
        getCurrentPosition
      },
      permissions: {
        query: vi.fn().mockResolvedValue({ state: 'granted', onchange: null })
      }
    })

    renderHook(() => useSessionDeviceLocation({
      captureLocation,
      session: { id: 'session-3', deviceLocationCaptured: false },
      storageScope: 'remote',
      t
    }))

    await act(async () => {
      await Promise.resolve()
    })

    expect(getCurrentPosition).toHaveBeenCalledTimes(2)

    await act(async () => {
      vi.advanceTimersByTime(5000)
    })

    await act(async () => {
      await Promise.resolve()
    })

    expect(getCurrentPosition).toHaveBeenCalledTimes(3)
    expect(captureLocation).toHaveBeenCalledWith({
      latitude: 40.41,
      longitude: -3.7,
      accuracyMeters: 24
    })
  })
})
