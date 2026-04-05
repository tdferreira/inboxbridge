import { useEffect, useState } from 'react'

function detectManualInstallSupport() {
  if (typeof window === 'undefined') return { manualInstallSupported: false, prefersAddToHomeScreenLabel: false }
  const userAgent = window.navigator?.userAgent || ''
  const isIos = /iPhone|iPad|iPod/i.test(userAgent)
  const isAndroid = /Android/i.test(userAgent)
  const isMobile = /Mobile|Android|iPhone|iPad|iPod/i.test(userAgent)
    || window.matchMedia?.('(pointer: coarse)')?.matches === true

  return {
    manualInstallSupported: isIos || (isAndroid && isMobile),
    prefersAddToHomeScreenLabel: isIos
  }
}

export function usePwaInstallPrompt() {
  const [installEvent, setInstallEvent] = useState(null)
  const [installed, setInstalled] = useState(() => {
    if (typeof window === 'undefined') return false
    if (window.matchMedia?.('(display-mode: standalone)').matches) {
      return true
    }
    return window.navigator?.standalone === true
  })

  useEffect(() => {
    function handleBeforeInstallPrompt(event) {
      event.preventDefault()
      setInstallEvent(event)
    }

    function handleAppInstalled() {
      setInstalled(true)
      setInstallEvent(null)
    }

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
    window.addEventListener('appinstalled', handleAppInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
      window.removeEventListener('appinstalled', handleAppInstalled)
    }
  }, [])

  async function promptInstall() {
    if (!installEvent) {
      return { available: false, outcome: 'unavailable' }
    }
    await installEvent.prompt()
    const choice = await installEvent.userChoice.catch(() => null)
    setInstallEvent(null)
    if (choice?.outcome === 'accepted') {
      setInstalled(true)
    }
    return { available: true, outcome: choice?.outcome || 'dismissed' }
  }

  return {
    canPromptInstall: Boolean(installEvent) && !installed,
    installed,
    ...detectManualInstallSupport(),
    promptInstall
  }
}
