import { useEffect, useMemo, useRef, useState } from 'react'

const DISMISS_PREFIX = 'inboxbridge.device-location.dismissed'
const AUTO_CAPTURE_RETRY_DELAYS_MS = [5000, 15000, 30000]
const PERMISSION_UNSUPPORTED = 'unsupported'

function errorMessage(error, t) {
  const detail = typeof error?.message === 'string' && error.message.trim() ? ` ${error.message.trim()}` : ''
  if (!error || typeof error.code !== 'number') {
    return t('deviceLocation.errors.generic', { detail })
  }
  if (error.code === 1) return t('deviceLocation.errors.denied', { detail })
  if (error.code === 2) {
    if (typeof window !== 'undefined' && window.isSecureContext === false) {
      return t('deviceLocation.errors.insecureContext', { detail })
    }
    return t('deviceLocation.errors.unavailable', { detail })
  }
  if (error.code === 3) return t('deviceLocation.errors.timeout', { detail })
  return t('deviceLocation.errors.generic', { detail })
}

function browserPosition(options) {
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(resolve, reject, options)
  })
}

async function requestBrowserLocation() {
  try {
    return await browserPosition({
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 300000
    })
  } catch (error) {
    if (error?.code === 1) {
      throw error
    }
    return browserPosition({
      enableHighAccuracy: false,
      timeout: 15000,
      maximumAge: 1800000
    })
  }
}

export function useSessionDeviceLocation({ captureLocation, session, storageScope, t }) {
  const [dismissed, setDismissed] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [permissionState, setPermissionState] = useState('')
  const [success, setSuccess] = useState('')
  const autoCaptureAttemptedRef = useRef(new Set())
  const autoCaptureRetryCountRef = useRef(new Map())
  const autoCaptureRetryTimerRef = useRef(new Map())
  const supported = typeof navigator !== 'undefined' && !!navigator.geolocation
  const sessionKey = useMemo(
    () => `${DISMISS_PREFIX}:${storageScope}:${session?.id || 'anonymous'}`,
    [session?.id, storageScope]
  )

  useEffect(() => {
    if (typeof window === 'undefined') return
    setDismissed(window.localStorage.getItem(sessionKey) === 'true')
    setError('')
    setPermissionState('')
    setSuccess('')
    autoCaptureAttemptedRef.current.delete(sessionKey)
    autoCaptureRetryCountRef.current.delete(sessionKey)
    const existingTimer = autoCaptureRetryTimerRef.current.get(sessionKey)
    if (existingTimer) {
      window.clearTimeout(existingTimer)
      autoCaptureRetryTimerRef.current.delete(sessionKey)
    }
  }, [sessionKey])

  const shouldPrompt = Boolean(session) && supported && !session.deviceLocationCaptured && !dismissed

  function dismissPrompt() {
    setDismissed(true)
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(sessionKey, 'true')
    }
  }

  async function requestLocation({ automatic = false } = {}) {
    if (!supported || !session) return
    setSaving(true)
    setError('')
    if (!automatic) {
      setSuccess('')
    }
    try {
      const position = await requestBrowserLocation()
      await captureLocation({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracyMeters: typeof position.coords.accuracy === 'number' ? position.coords.accuracy : null
      })
      if (!automatic) {
        setSuccess(t('deviceLocation.success'))
      }
      setDismissed(true)
      autoCaptureRetryCountRef.current.delete(sessionKey)
      const existingTimer = autoCaptureRetryTimerRef.current.get(sessionKey)
      if (existingTimer && typeof window !== 'undefined') {
        window.clearTimeout(existingTimer)
        autoCaptureRetryTimerRef.current.delete(sessionKey)
      }
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(sessionKey, 'true')
      }
    } catch (captureError) {
      setError(errorMessage(captureError, t))
      if (automatic && typeof window !== 'undefined' && (captureError?.code === 2 || captureError?.code === 3)) {
        const retryCount = autoCaptureRetryCountRef.current.get(sessionKey) || 0
        if (retryCount < AUTO_CAPTURE_RETRY_DELAYS_MS.length) {
          const delay = AUTO_CAPTURE_RETRY_DELAYS_MS[retryCount]
          autoCaptureRetryCountRef.current.set(sessionKey, retryCount + 1)
          const timerId = window.setTimeout(() => {
            autoCaptureRetryTimerRef.current.delete(sessionKey)
            void requestLocation({ automatic: true })
          }, delay)
          autoCaptureRetryTimerRef.current.set(sessionKey, timerId)
        }
      }
    } finally {
      setSaving(false)
    }
  }

  useEffect(() => {
    if (!supported || !session || typeof navigator === 'undefined') {
      return
    }
    if (!navigator.permissions?.query) {
      setPermissionState(PERMISSION_UNSUPPORTED)
      return
    }

    let active = true
    let permissionStatus = null

    async function loadPermissionState() {
      try {
        permissionStatus = await navigator.permissions.query({ name: 'geolocation' })
        if (!active) return
        setPermissionState(permissionStatus.state || '')
        permissionStatus.onchange = () => {
          if (!active) return
          setPermissionState(permissionStatus.state || '')
        }
      } catch {
        if (active) {
          setPermissionState(PERMISSION_UNSUPPORTED)
        }
      }
    }

    void loadPermissionState()

    return () => {
      active = false
      if (permissionStatus) {
        permissionStatus.onchange = null
      }
    }
  }, [session, supported])

  useEffect(() => {
    if (!supported || !session || session.deviceLocationCaptured || typeof navigator === 'undefined') {
      return
    }
    if (permissionState !== 'granted') {
      return
    }
    if (autoCaptureAttemptedRef.current.has(sessionKey)) {
      return
    }

    autoCaptureAttemptedRef.current.add(sessionKey)
    void requestLocation({ automatic: true })
  }, [permissionState, session, sessionKey, supported])

  useEffect(() => () => {
    if (typeof window === 'undefined') return
    autoCaptureRetryTimerRef.current.forEach((timerId) => window.clearTimeout(timerId))
    autoCaptureRetryTimerRef.current.clear()
  }, [])

  return {
    dismissPrompt,
    error,
    saving,
    shouldPrompt,
    success,
    supported,
    permissionState,
    requestLocation
  }
}
