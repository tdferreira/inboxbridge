import { useState } from 'react'
import { apiErrorText } from './api'
import { normalizePasskeyError, parseCreateOptions, parseGetOptions, passkeysSupported, serializeCredential } from './passkeys'

const DEFAULT_LOGIN_FORM = { username: 'admin', password: 'nimda' }
const DEFAULT_REGISTER_FORM = { username: '', password: '', confirmPassword: '' }
const DEFAULT_PASSWORD_FORM = { currentPassword: '', newPassword: '', confirmNewPassword: '' }

export function useAuthSecurityController({
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
  const [loginForm, setLoginForm] = useState(DEFAULT_LOGIN_FORM)
  const [registerForm, setRegisterForm] = useState(DEFAULT_REGISTER_FORM)
  const [registerOpen, setRegisterOpen] = useState(false)
  const [passwordForm, setPasswordForm] = useState(DEFAULT_PASSWORD_FORM)
  const [myPasskeys, setMyPasskeys] = useState([])
  const [passkeyLabel, setPasskeyLabel] = useState('')
  const [showSecurityPanel, setShowSecurityPanel] = useState(false)
  const [securityTab, setSecurityTab] = useState('password')
  const [showPasskeyRegistrationDialog, setShowPasskeyRegistrationDialog] = useState(false)

  const securityDialogDirty = Boolean(
    passwordForm.currentPassword.trim()
    || passwordForm.newPassword.trim()
    || passwordForm.confirmNewPassword.trim()
  )

  function resetTransientMessages() {
    setAuthError('')
  }

  async function loadSession() {
    try {
      const response = await fetch('/api/auth/me')
      if (response.status === 401) {
        setSession(null)
        setAuthError('')
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
    pushNotification({ message: t('notifications.signedInWithPasskey'), tone: 'success' })
  }

  async function handleLogin(event) {
    event.preventDefault()
    resetTransientMessages()
    await withPending('login', async () => {
      try {
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
          await completePasskeyLogin(payload.passkeyChallenge)
          return
        }
        setSession(payload.user)
        if (!payload.user.mustChangePassword) {
          pushNotification({
            autoCloseMs: 10000,
            message: t('notifications.signedIn'),
            targetId: null,
            tone: 'success'
          })
        }
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
          body: JSON.stringify(registerForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, errorText('registrationFailed')))
        }
        const payload = await response.json()
        setRegisterForm(DEFAULT_REGISTER_FORM)
        setRegisterOpen(false)
        pushNotification({ message: payload.message || t('notifications.registrationSubmitted'), tone: 'success' })
      } catch (err) {
        setAuthError(err.message || errorText('registrationFailed'))
      }
    })
  }

  async function handleLogout() {
    await withPending('logout', async () => {
      await fetch('/api/auth/logout', { method: 'POST' })
      setSession(null)
      setAuthError('')
      setPasswordForm(DEFAULT_PASSWORD_FORM)
      setMyPasskeys([])
      setPasskeyLabel('')
      setShowSecurityPanel(false)
      setSecurityTab('password')
      setShowPasskeyRegistrationDialog(false)
      setRegisterOpen(false)
      if (onLogoutReset) {
        await onLogoutReset()
      }
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
        pushNotification({ message: t('notifications.passwordUpdated'), targetId: 'password-panel-section', tone: 'success' })
        await loadSession()
      } catch (err) {
        pushNotification({ autoCloseMs: null, copyText: err.message || errorText('changePassword'), message: err.message || errorText('changePassword'), targetId: 'password-panel-section', tone: 'error' })
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
            pushNotification({ autoCloseMs: null, message: t('notifications.passwordRemoved'), targetId: 'password-panel-section', tone: 'warning' })
            await loadAppData()
            await loadSession()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || errorText('removePassword'), message: err.message || errorText('removePassword'), targetId: 'password-panel-section', tone: 'error' })
          }
        })
      },
      title: t('password.removeConfirmTitle')
    })
  }

  async function handlePasskeyLogin() {
    resetTransientMessages()
    await withPending('passkeyLogin', async () => {
      try {
        if (!passkeysSupported()) {
          throw new Error(t('errors.passkeyUnsupported'))
        }
        const startResponse = await fetch('/api/auth/passkey/options', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: loginForm.username.trim() || null })
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
        pushNotification({ message: t('notifications.passkeyRegistered'), targetId: 'passkey-panel-section', tone: 'success' })
        await loadAppData()
        await loadSession()
      } catch (err) {
        const message = normalizePasskeyError(err, t, 'registration')
        pushNotification({ autoCloseMs: null, copyText: message, message, targetId: 'passkey-panel-section', tone: 'error' })
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
            pushNotification({ message: t('notifications.passkeyRemoved'), targetId: 'passkey-panel-section', tone: 'success' })
            await loadAppData()
            await loadSession()
          } catch (err) {
            pushNotification({ autoCloseMs: null, copyText: err.message || errorText('removePasskey'), message: err.message || errorText('removePasskey'), targetId: 'passkey-panel-section', tone: 'error' })
          }
        })
      },
      title: t('passkey.removeConfirmTitle')
    })
  }

  function openSecurityPanel(tab = 'password') {
    setSecurityTab(tab)
    setShowSecurityPanel(true)
  }

  function closeSecurityPanel() {
    setShowSecurityPanel(false)
  }

  function applyLoadedPasskeys(passkeysPayload) {
    setMyPasskeys(Array.isArray(passkeysPayload) ? passkeysPayload : [])
  }

  return {
    applyLoadedPasskeys,
    authError,
    authLoading,
    closePasskeyRegistrationDialog,
    closeRegisterDialog: () => setRegisterOpen(false),
    closeSecurityPanel,
    handleDeletePasskey,
    handleLogin,
    handleLogout,
    handlePasskeyLogin,
    handlePasskeyRegistration,
    handlePasswordChange,
    handlePasswordRemoval,
    handleRegister,
    loadSession,
    loginForm,
    myPasskeys,
    openPasskeyRegistrationDialog,
    openRegisterDialog: () => setRegisterOpen(true),
    openSecurityPanel,
    passkeyLabel,
    passkeysSupported: passkeysSupported(),
    passwordForm,
    registerForm,
    registerOpen,
    securityDialogDirty,
    securityTab,
    selectSecurityTab: setSecurityTab,
    session,
    setLoginForm,
    setPasskeyLabel,
    setPasswordForm,
    setRegisterForm,
    setSession,
    showPasskeyRegistrationDialog,
    showSecurityPanel
  }
}