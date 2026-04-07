import { useEffect, useRef, useState } from 'react'
import { AUTH_EXPIRED_EVENT, apiErrorText } from '@/lib/api'
import { pollErrorNotification, translatedNotification } from '@/lib/notifications'
import { normalizePasskeyError, parseCreateOptions, parseGetOptions, passkeysSupported, serializeCredential } from '@/lib/passkeys'
import { buildRecentSessionTargetId } from '@/lib/sectionTargets'

const BOOTSTRAP_LOGIN_FORM = { username: 'admin', password: 'nimda' }
const EMPTY_LOGIN_FORM = { username: '', password: '' }
const LOGIN_STAGE_USERNAME = 'username'
const LOGIN_STAGE_CREDENTIALS = 'credentials'
const DEFAULT_REGISTER_FORM = { username: '', password: '', confirmPassword: '', captchaToken: '' }
const DEFAULT_PASSWORD_FORM = { currentPassword: '', newPassword: '', confirmNewPassword: '' }
const DEFAULT_SESSION_ACTIVITY = { recentLogins: [], activeSessions: [], geoIpConfigured: false }
const SESSION_KIND_KEYS = Object.freeze({
  REMOTE: 'sessions.kindRemote',
  BROWSER: 'sessions.kindBrowser'
})

export function useAuthSecurityController({
  bootstrapLoginPrefillEnabled = false,
  closeConfirmation,
  errorText,
  loadAppData,
  onLogoutReset,
  openConfirmation,
  pushNotification,
  t,
  withPending
}) {
  const [session, setSession] = useState(null)
  const [authLoading, setAuthLoading] = useState(true)
  const [authError, setAuthError] = useState('')
  const [loginForm, setLoginForm] = useState(() => (bootstrapLoginPrefillEnabled ? BOOTSTRAP_LOGIN_FORM : EMPTY_LOGIN_FORM))
  const [loginStage, setLoginStage] = useState(LOGIN_STAGE_USERNAME)
  const [bootstrapPrefillDismissed, setBootstrapPrefillDismissed] = useState(false)
  const [registerForm, setRegisterForm] = useState(DEFAULT_REGISTER_FORM)
  const [registerOpen, setRegisterOpen] = useState(false)
  const [registerChallenge, setRegisterChallenge] = useState(null)
  const [registerChallengeLoading, setRegisterChallengeLoading] = useState(false)
  const [passwordForm, setPasswordForm] = useState(DEFAULT_PASSWORD_FORM)
  const [myPasskeys, setMyPasskeys] = useState([])
  const [passkeyLabel, setPasskeyLabel] = useState('')
  const [showSecurityPanel, setShowSecurityPanel] = useState(false)
  const [securityTab, setSecurityTab] = useState('password')
  const [showPasskeyRegistrationDialog, setShowPasskeyRegistrationDialog] = useState(false)
  const [sessionActivity, setSessionActivity] = useState(DEFAULT_SESSION_ACTIVITY)
  const latestRecentSessionKeyRef = useRef(null)
  const hasSessionActivityBaselineRef = useRef(false)

  const securityDialogDirty = Boolean(
    passwordForm.currentPassword.trim()
    || passwordForm.newPassword.trim()
    || passwordForm.confirmNewPassword.trim()
  )

  function resetTransientMessages() {
    setAuthError('')
  }

  async function clearSessionState({ showExpiredMessage = false } = {}) {
    setSession(null)
    setLoginStage(LOGIN_STAGE_USERNAME)
    setLoginForm(EMPTY_LOGIN_FORM)
    setBootstrapPrefillDismissed(true)
    setPasswordForm(DEFAULT_PASSWORD_FORM)
    setMyPasskeys([])
    setSessionActivity(DEFAULT_SESSION_ACTIVITY)
    setPasskeyLabel('')
    setShowSecurityPanel(false)
    setSecurityTab('password')
    setShowPasskeyRegistrationDialog(false)
    setRegisterOpen(false)
    setRegisterChallenge(null)
    setRegisterChallengeLoading(false)
    setRegisterForm(DEFAULT_REGISTER_FORM)
    latestRecentSessionKeyRef.current = null
    hasSessionActivityBaselineRef.current = false
    setAuthError(showExpiredMessage ? t('auth.sessionExpired') : '')
    if (onLogoutReset) {
      await onLogoutReset()
    }
  }

  useEffect(() => {
    function handleAuthExpired() {
      void clearSessionState({ showExpiredMessage: true })
    }

    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired)
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired)
  }, [onLogoutReset, t])

  useEffect(() => {
    if (session) {
      return
    }
    const usingBootstrapPrefill = loginForm.username === BOOTSTRAP_LOGIN_FORM.username
      && loginForm.password === BOOTSTRAP_LOGIN_FORM.password
    const usingEmptyForm = loginForm.username === ''
      && loginForm.password === ''

    if (!bootstrapPrefillDismissed && bootstrapLoginPrefillEnabled && (usingBootstrapPrefill || usingEmptyForm)) {
      if (!usingBootstrapPrefill) {
        setLoginForm(BOOTSTRAP_LOGIN_FORM)
      }
      return
    }

    if (!bootstrapLoginPrefillEnabled && usingBootstrapPrefill) {
      setLoginForm(EMPTY_LOGIN_FORM)
    }
  }, [bootstrapLoginPrefillEnabled, loginForm.password, loginForm.username, session])

  async function loadSession() {
    try {
      const response = await fetch('/api/auth/me')
      if (response.status === 401) {
        await clearSessionState()
        return
      }
      if (!response.ok) {
        throw new Error(`${errorText('loadSession')} (${response.status})`)
      }
      const payload = await response.json()
      setSession(payload)
      setAuthError('')
    } catch (err) {
      setAuthError(err.message || errorText('loadSession'))
    } finally {
      setAuthLoading(false)
    }
  }

  async function completePasskeyLogin(challenge) {
    const credential = await navigator.credentials.get({ publicKey: parseGetOptions(challenge.publicKeyJson) })
    if (!credential) {
      throw new Error(t('errors.passkeyCancelled'))
    }
    const finishResponse = await fetch('/api/auth/passkey/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ceremonyId: challenge.ceremonyId,
        credentialJson: serializeCredential(credential)
      })
    })
    if (!finishResponse.ok) {
      throw new Error(await apiErrorText(finishResponse, errorText('passkeySignInFailed')))
    }
    const payload = await finishResponse.json()
    setSession(payload.user)
  }

  async function runLoginSubmission() {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(loginForm)
    })
    if (!response.ok) {
      throw new Error(await apiErrorText(response, errorText('loginFailed')))
    }
    const payload = await response.json()
    if (payload.status === 'PASSKEY_REQUIRED' && payload.passkeyChallenge) {
      if (!passkeysSupported()) {
        throw new Error(t('errors.passkeyIpHostUnsupported', { host: window.location.hostname || 'this host' }))
      }
      await completePasskeyLogin(payload.passkeyChallenge)
      return
    }
    setSession(payload.user)
    if (!payload.user.mustChangePassword) {
      pushNotification({
        autoCloseMs: 10000,
        message: translatedNotification('notifications.signedIn'),
        targetId: null,
        tone: 'success'
      })
    }
  }

  async function handleLogin(event) {
    event.preventDefault()
    resetTransientMessages()
    if (loginStage === LOGIN_STAGE_USERNAME) {
      setLoginStage(LOGIN_STAGE_CREDENTIALS)
      return
    }
    await withPending('login', async () => {
      try {
        await runLoginSubmission()
      } catch (err) {
        setAuthError(err.message || errorText('loginFailed'))
      }
    })
  }

  async function handleRegister(event) {
    event.preventDefault()
    resetTransientMessages()
    await withPending('register', async () => {
      try {
        const response = await fetch('/api/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            username: registerForm.username,
            password: registerForm.password,
            confirmPassword: registerForm.confirmPassword,
            captchaToken: registerForm.captchaToken
          })
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('registrationFailed')))
        }
        const payload = await response.json()
        setRegisterForm(DEFAULT_REGISTER_FORM)
        setRegisterOpen(false)
        setRegisterChallenge(null)
        pushNotification({
          message: payload.message ? pollErrorNotification(payload.message) : translatedNotification('notifications.registrationSubmitted'),
          tone: 'success'
        })
      } catch (err) {
        setAuthError(err.message || errorText('registrationFailed'))
        await loadRegistrationChallenge()
      }
    })
  }

  async function loadRegistrationChallenge() {
    setRegisterChallengeLoading(true)
    try {
      const response = await fetch('/api/auth/register/challenge')
      if (!response.ok) {
        throw new Error(await apiErrorText(response, errorText('loadRegistrationChallenge')))
      }
      const payload = await response.json()
      if (!payload?.enabled) {
        setRegisterChallenge(null)
        setRegisterForm((current) => ({ ...current, captchaToken: '' }))
        return
      }
      setRegisterChallenge(payload)
      setRegisterForm((current) => ({ ...current, captchaToken: '' }))
    } catch (err) {
      setAuthError(err.message || errorText('loadRegistrationChallenge'))
    } finally {
      setRegisterChallengeLoading(false)
    }
  }

  async function handleLogout() {
    await withPending('logout', async () => {
      await fetch('/api/auth/logout', { method: 'POST' })
      await clearSessionState()
    })
  }

  async function handlePasswordChange(event) {
    event.preventDefault()
    await withPending('passwordChange', async () => {
      try {
        const response = await fetch('/api/account/password', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(passwordForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('changePassword')))
        }
        setPasswordForm(DEFAULT_PASSWORD_FORM)
        pushNotification({ message: translatedNotification('notifications.passwordUpdated'), targetId: 'password-panel-section', tone: 'success' })
        await loadSession()
      } catch (err) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.changePassword'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.changePassword'),
          targetId: 'password-panel-section',
          tone: 'error'
        })
      }
    })
  }

  async function handlePasswordRemoval() {
    openConfirmation({
      actionKey: 'passwordRemove',
      body: t('password.removeConfirmBody'),
      confirmLabel: t('password.remove'),
      confirmLoadingLabel: t('password.removeLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending('passwordRemove', async () => {
          try {
            const response = await fetch('/api/account/password', {
              method: 'DELETE',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ currentPassword: passwordForm.currentPassword })
            })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('removePassword')))
            }
            closeConfirmation?.()
            setPasswordForm(DEFAULT_PASSWORD_FORM)
            pushNotification({ autoCloseMs: null, message: translatedNotification('notifications.passwordRemoved'), targetId: 'password-panel-section', tone: 'warning' })
            await loadAppData()
            await loadSession()
          } catch (err) {
            pushNotification({
              autoCloseMs: null,
              copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.removePassword'),
              message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.removePassword'),
              targetId: 'password-panel-section',
              tone: 'error'
            })
          }
        })
      },
      title: t('password.removeConfirmTitle')
    })
  }

  async function handlePasskeyLogin() {
    resetTransientMessages()
    if (loginStage === LOGIN_STAGE_USERNAME) {
      setLoginStage(LOGIN_STAGE_CREDENTIALS)
      return
    }
    const hasTypedPassword = loginForm.password.trim() !== ''
    const pendingKey = hasTypedPassword ? 'login' : 'passkeyLogin'
    await withPending(pendingKey, async () => {
      try {
        if (hasTypedPassword) {
          await runLoginSubmission()
          return
        }
        if (!passkeysSupported()) {
          throw new Error(t('errors.passkeyUnsupported'))
        }
        const startResponse = await fetch('/api/auth/passkey/options', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({})
        })
        if (!startResponse.ok) {
          throw new Error(await apiErrorText(startResponse, errorText('startPasskeySignIn')))
        }
        const startPayload = await startResponse.json()
        await completePasskeyLogin(startPayload)
      } catch (err) {
        setAuthError(normalizePasskeyError(err, t, 'login'))
      }
    })
  }

  async function handlePasskeyRegistration(event) {
    event.preventDefault()
    await withPending('passkeyCreate', async () => {
      try {
        if (!passkeysSupported()) {
          throw new Error(t('errors.passkeyUnsupported'))
        }
        const startResponse = await fetch('/api/account/passkeys/options', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ label: passkeyLabel })
        })
        if (!startResponse.ok) {
          throw new Error(await apiErrorText(startResponse, errorText('startPasskeyRegistration')))
        }
        const startPayload = await startResponse.json()
        const credential = await navigator.credentials.create({ publicKey: parseCreateOptions(startPayload.publicKeyJson) })
        if (!credential) {
          throw new Error(t('errors.passkeyRegistrationCancelled'))
        }
        const finishResponse = await fetch('/api/account/passkeys/verify', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            ceremonyId: startPayload.ceremonyId,
            credentialJson: serializeCredential(credential)
          })
        })
        if (!finishResponse.ok) {
          throw new Error(await apiErrorText(finishResponse, errorText('registerPasskey')))
        }
        setPasskeyLabel('')
        setShowPasskeyRegistrationDialog(false)
        pushNotification({ message: translatedNotification('notifications.passkeyRegistered'), targetId: 'passkey-panel-section', tone: 'success' })
        await loadAppData()
        await loadSession()
      } catch (err) {
        const message = normalizePasskeyError(err, t, 'registration')
        pushNotification({ autoCloseMs: null, copyText: pollErrorNotification(message), message: pollErrorNotification(message), targetId: 'passkey-panel-section', tone: 'error' })
      }
    })
  }

  function openPasskeyRegistrationDialog() {
    setPasskeyLabel('')
    setShowPasskeyRegistrationDialog(true)
  }

  function closePasskeyRegistrationDialog() {
    setShowPasskeyRegistrationDialog(false)
    setPasskeyLabel('')
  }

  async function handleDeletePasskey(passkeyId) {
    openConfirmation({
      actionKey: `passkeyDelete:${passkeyId}`,
      body: t('passkey.removeConfirmBody'),
      confirmLabel: t('passkey.remove'),
      confirmLoadingLabel: t('passkey.removeLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`passkeyDelete:${passkeyId}`, async () => {
          try {
            const response = await fetch(`/api/account/passkeys/${passkeyId}`, { method: 'DELETE' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('removePasskey')))
            }
            closeConfirmation?.()
            pushNotification({ message: translatedNotification('notifications.passkeyRemoved'), targetId: 'passkey-panel-section', tone: 'success' })
            await loadAppData()
            await loadSession()
          } catch (err) {
            pushNotification({
              autoCloseMs: null,
              copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.removePasskey'),
              message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.removePasskey'),
              targetId: 'passkey-panel-section',
              tone: 'error'
            })
          }
        })
      },
      title: t('passkey.removeConfirmTitle')
    })
  }

  async function loadSessionActivity({ announceNewSessions = false, suppressErrors = false } = {}) {
    try {
      const response = await fetch('/api/account/sessions')
      if (!response.ok) {
        throw new Error(await apiErrorText(response, errorText('loadSessions')))
      }
      const payload = await response.json()
      const recentLogins = Array.isArray(payload?.recentLogins) ? payload.recentLogins : []
      const activeSessions = Array.isArray(payload?.activeSessions) ? payload.activeSessions : []
      const latestRecentSession = recentLogins[0] || null
      const latestRecentSessionKey = latestRecentSession
        ? `${latestRecentSession.sessionType || 'BROWSER'}:${latestRecentSession.id}:${latestRecentSession.createdAt || ''}:${latestRecentSession.current ? 'current' : 'other'}`
        : null

      if (announceNewSessions && latestRecentSessionKey) {
        if (!hasSessionActivityBaselineRef.current) {
          hasSessionActivityBaselineRef.current = true
          latestRecentSessionKeyRef.current = latestRecentSessionKey
        } else if (
          latestRecentSessionKeyRef.current !== latestRecentSessionKey
          && latestRecentSession
          && !latestRecentSession.current
        ) {
          latestRecentSessionKeyRef.current = latestRecentSessionKey
          pushNotification({
            message: buildNewSessionNotification(latestRecentSession),
            targetId: buildRecentSessionTargetId(latestRecentSession.sessionType, latestRecentSession.id),
            tone: 'warning'
          })
        } else {
          latestRecentSessionKeyRef.current = latestRecentSessionKey
        }
      } else if (!hasSessionActivityBaselineRef.current) {
        hasSessionActivityBaselineRef.current = true
        latestRecentSessionKeyRef.current = latestRecentSessionKey
      } else if (latestRecentSessionKey) {
        latestRecentSessionKeyRef.current = latestRecentSessionKey
      }

      setSessionActivity({
        recentLogins,
        activeSessions,
        geoIpConfigured: Boolean(payload?.geoIpConfigured)
      })
    } catch (err) {
      if (suppressErrors) {
        return
      }
      pushNotification({
        autoCloseMs: null,
        copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadSessions'),
        message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.loadSessions'),
        targetId: 'security-sessions-panel-section',
        tone: 'error'
      })
    }
  }

  async function handleRevokeSession(session) {
    const sessionId = session?.id
    const sessionType = session?.sessionType || 'BROWSER'
    openConfirmation({
      actionKey: `sessionRevoke:${sessionType}:${sessionId}`,
      body: t('sessions.revokeConfirmBody'),
      confirmLabel: t('sessions.revoke'),
      confirmLoadingLabel: t('sessions.revokeLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending(`sessionRevoke:${sessionType}:${sessionId}`, async () => {
          try {
            const response = await fetch(`/api/account/sessions/${sessionId}/revoke?type=${encodeURIComponent(sessionType)}`, { method: 'POST' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('revokeSession')))
            }
            closeConfirmation?.()
            await loadSessionActivity()
            pushNotification({ message: translatedNotification('notifications.sessionRevoked'), targetId: 'security-sessions-panel-section', tone: 'success' })
          } catch (err) {
            pushNotification({
              autoCloseMs: null,
              copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.revokeSession'),
              message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.revokeSession'),
              targetId: 'security-sessions-panel-section',
              tone: 'error'
            })
          }
        })
      },
      title: t('sessions.revokeConfirmTitle')
    })
  }

  async function handleRevokeOtherSessions() {
    openConfirmation({
      actionKey: 'sessionsRevokeOthers',
      body: t('sessions.revokeOthersConfirmBody'),
      confirmLabel: t('sessions.revokeOthers'),
      confirmLoadingLabel: t('sessions.revokeOthersLoading'),
      confirmTone: 'danger',
      onConfirm: async () => {
        await withPending('sessionsRevokeOthers', async () => {
          try {
            const response = await fetch('/api/account/sessions/revoke-others', { method: 'POST' })
            if (!response.ok) {
              throw new Error(await apiErrorText(response, errorText('revokeSession')))
            }
            closeConfirmation?.()
            await loadSessionActivity()
            pushNotification({ message: translatedNotification('notifications.otherSessionsRevoked'), targetId: 'security-sessions-panel-section', tone: 'success' })
          } catch (err) {
            pushNotification({
              autoCloseMs: null,
              copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.revokeSession'),
              message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.revokeSession'),
              targetId: 'security-sessions-panel-section',
              tone: 'error'
            })
          }
        })
      },
      title: t('sessions.revokeOthersConfirmTitle')
    })
  }

  async function openSecurityPanel(tab = 'password') {
    setSecurityTab(tab)
    setShowSecurityPanel(true)
    if (tab === 'sessions') {
      await loadSessionActivity()
    }
  }

  function closeSecurityPanel() {
    setShowSecurityPanel(false)
  }

  async function selectSecurityTab(tab) {
    setSecurityTab(tab)
    if (tab === 'sessions') {
      await loadSessionActivity()
    }
  }

  function applyLoadedPasskeys(passkeysPayload) {
    setMyPasskeys(Array.isArray(passkeysPayload) ? passkeysPayload : [])
  }

  function updateLoginForm(updater) {
    setBootstrapPrefillDismissed(true)
    setLoginForm((current) => {
      const next = typeof updater === 'function' ? updater(current) : updater
      if (!next || typeof next !== 'object') {
        return current
      }
      if (next.username !== current.username && next.username !== BOOTSTRAP_LOGIN_FORM.username && next.password === BOOTSTRAP_LOGIN_FORM.password) {
        return { ...next, password: '' }
      }
      return next
    })
  }

  function sessionKindKey(sessionType) {
    return SESSION_KIND_KEYS[sessionType] || SESSION_KIND_KEYS.BROWSER
  }

  function buildNewSessionNotification(sessionLike) {
    const locationLabel = String(sessionLike?.locationLabel || '').trim()
    const unusualLocation = Boolean(sessionLike?.unusualLocation)
    const params = {
      sessionType: translatedNotification(sessionKindKey(sessionLike?.sessionType))
    }
    if (locationLabel && unusualLocation) {
      return translatedNotification('notifications.newSessionDetectedWithUnusualLocation', {
        ...params,
        location: locationLabel
      })
    }
    if (locationLabel) {
      return translatedNotification('notifications.newSessionDetectedWithLocation', {
        ...params,
        location: locationLabel
      })
    }
    return translatedNotification('notifications.newSessionDetectedWithoutLocation', params)
  }

  return {
    applyLoadedPasskeys,
    authError,
    authLoading,
    closePasskeyRegistrationDialog,
    closeRegisterDialog: () => {
      setRegisterOpen(false)
      setRegisterChallenge(null)
      setRegisterForm(DEFAULT_REGISTER_FORM)
    },
    closeSecurityPanel,
    handleDeletePasskey,
    handleLogin,
    handleLogout,
    handlePasskeyLogin,
    handlePasskeyRegistration,
    handlePasswordChange,
    handlePasswordRemoval,
    handleRegister,
    loginStage,
    loadSession,
    loginForm,
    myPasskeys,
    openPasskeyRegistrationDialog,
    handleRevokeOtherSessions,
    handleRevokeSession,
    pollSessionActivity: loadSessionActivity,
    openRegisterDialog: async () => {
      setRegisterOpen(true)
      await loadRegistrationChallenge()
    },
    openSecurityPanel,
    passkeyLabel,
    passkeysSupported: passkeysSupported(),
    passwordForm,
    registerForm,
    registerChallenge,
    registerChallengeLoading,
    registerOpen,
    securityDialogDirty,
    securityTab,
    sessionActivity,
    selectSecurityTab,
    session,
    setLoginForm: updateLoginForm,
    setPasskeyLabel,
    setPasswordForm,
    setRegisterForm,
    setSession,
    showPasskeyRegistrationDialog,
    showSecurityPanel
  }
}
