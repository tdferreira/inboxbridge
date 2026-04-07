import { act, renderHook, waitFor } from '@testing-library/react'
import { usePwaInstallPrompt } from './usePwaInstallPrompt'

describe('usePwaInstallPrompt', () => {
  function setUserAgent(userAgent) {
    Object.defineProperty(window.navigator, 'userAgent', {
      configurable: true,
      value: userAgent
    })
  }

  it('captures beforeinstallprompt and resolves accepted installs', async () => {
    setUserAgent('Mozilla/5.0')
    window.matchMedia = vi.fn().mockReturnValue({ matches: false })

    const prompt = vi.fn().mockResolvedValue(undefined)
    const event = new Event('beforeinstallprompt')
    event.preventDefault = vi.fn()
    event.prompt = prompt
    event.userChoice = Promise.resolve({ outcome: 'accepted' })

    const { result } = renderHook(() => usePwaInstallPrompt())

    act(() => {
      window.dispatchEvent(event)
    })

    expect(result.current.canPromptInstall).toBe(true)

    await act(async () => {
      await result.current.promptInstall()
    })

    expect(prompt).toHaveBeenCalled()
    expect(result.current.installed).toBe(true)
    expect(result.current.canPromptInstall).toBe(false)
  })

  it('detects manual mobile install support', () => {
    setUserAgent('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)')
    window.matchMedia = vi.fn().mockReturnValue({ matches: true })

    const { result } = renderHook(() => usePwaInstallPrompt())

    expect(result.current.manualInstallSupported).toBe(true)
    expect(result.current.prefersAddToHomeScreenLabel).toBe(true)
  })

  it('marks the app as installed after appinstalled fires', async () => {
    setUserAgent('Mozilla/5.0')
    window.matchMedia = vi.fn().mockReturnValue({ matches: false })

    const { result } = renderHook(() => usePwaInstallPrompt())

    act(() => {
      window.dispatchEvent(new Event('appinstalled'))
    })

    await waitFor(() => {
      expect(result.current.installed).toBe(true)
    })
  })
})
