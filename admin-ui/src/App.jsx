import { useEffect, useState } from 'react'
import AuthScreen from './components/auth/AuthScreen'
import Banner from './components/common/Banner'
import LoadingScreen from './components/common/LoadingScreen'
import PasswordPanel from './components/account/PasswordPanel'
import GmailDestinationSection from './components/gmail/GmailDestinationSection'
import HeroPanel from './components/layout/HeroPanel'
import SetupGuidePanel from './components/layout/SetupGuidePanel'
import SystemDashboardSection from './components/admin/SystemDashboardSection'
import UserManagementSection from './components/admin/UserManagementSection'
import UserBridgesSection from './components/bridges/UserBridgesSection'
import { apiErrorText } from './lib/api'
import { buildSetupGuideState } from './lib/setupGuide'

const REFRESH_MS = 30000

const DEFAULT_LOGIN_FORM = { username: 'admin', password: 'nimda' }
const DEFAULT_REGISTER_FORM = { username: '', password: '' }
const DEFAULT_PASSWORD_FORM = { currentPassword: '', newPassword: '', confirmNewPassword: '' }
const DEFAULT_CREATE_USER_FORM = { username: '', password: '', role: 'USER' }
const DEFAULT_GMAIL_CONFIG = {
  destinationUser: 'me',
  clientId: '',
  clientSecret: '',
  refreshToken: '',
  redirectUri: '',
  createMissingLabels: true,
  neverMarkSpam: false,
  processForCalendar: false
}
const DEFAULT_BRIDGE_FORM = {
  bridgeId: '',
  enabled: true,
  protocol: 'IMAP',
  host: '',
  port: 993,
  tls: true,
  authMethod: 'PASSWORD',
  oauthProvider: 'NONE',
  username: '',
  password: '',
  oauthRefreshToken: '',
  folder: 'INBOX',
  unreadOnly: false,
  customLabel: ''
}
const DEFAULT_UI_PREFERENCES = {
  persistLayout: false,
  quickSetupCollapsed: false,
  gmailDestinationCollapsed: false,
  sourceBridgesCollapsed: false,
  systemDashboardCollapsed: false,
  userManagementCollapsed: false
}

/**
 * Coordinates admin-ui data fetching and browser interactions while delegating
 * UI structure to smaller reusable components.
 */
function App() {
  const [session, setSession] = useState(null)
  const [authLoading, setAuthLoading] = useState(true)
  const [authError, setAuthError] = useState('')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [loadingData, setLoadingData] = useState(false)
  const [pendingActions, setPendingActions] = useState({})
  const [selectedUserLoading, setSelectedUserLoading] = useState(false)

  const [loginForm, setLoginForm] = useState(DEFAULT_LOGIN_FORM)
  const [registerForm, setRegisterForm] = useState(DEFAULT_REGISTER_FORM)
  const [passwordForm, setPasswordForm] = useState(DEFAULT_PASSWORD_FORM)
  const [createUserForm, setCreateUserForm] = useState(DEFAULT_CREATE_USER_FORM)

  const [gmailConfig, setGmailConfig] = useState(DEFAULT_GMAIL_CONFIG)
  const [gmailMeta, setGmailMeta] = useState(null)

  const [bridgeForm, setBridgeForm] = useState(DEFAULT_BRIDGE_FORM)

  const [myBridges, setMyBridges] = useState([])
  const [systemDashboard, setSystemDashboard] = useState(null)
  const [users, setUsers] = useState([])
  const [selectedUserId, setSelectedUserId] = useState(null)
  const [selectedUserConfig, setSelectedUserConfig] = useState(null)
  const [runningPoll, setRunningPoll] = useState(false)
  const [uiPreferences, setUiPreferences] = useState(DEFAULT_UI_PREFERENCES)
  const [uiPreferencesLoadedForUserId, setUiPreferencesLoadedForUserId] = useState(null)
  const [touchedSections, setTouchedSections] = useState({})
  const [showPasswordPanel, setShowPasswordPanel] = useState(false)

  function isPending(actionKey) {
    return Boolean(pendingActions[actionKey])
  }

  async function withPending(actionKey, action) {
    setPendingActions((current) => ({ ...current, [actionKey]: true }))
    try {
      return await action()
    } finally {
      setPendingActions((current) => {
        const next = { ...current }
        delete next[actionKey]
        return next
      })
    }
  }

  function resetTransientMessages() {
    setError('')
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
        throw new Error(`Unable to load session (${response.status})`)
      }
      const payload = await response.json()
      setSession(payload)
      setAuthError('')
    } catch (err) {
      setAuthError(err.message || 'Unable to load session')
    } finally {
      setAuthLoading(false)
    }
  }

  async function loadSelectedUserConfiguration(userId) {
    if (!session || session.role !== 'ADMIN' || !userId) return
    setSelectedUserLoading(true)
    try {
      const response = await fetch(`/api/admin/users/${userId}/configuration`)
      if (!response.ok) {
        throw new Error(await apiErrorText(response, 'Unable to load user configuration'))
      }
      setSelectedUserConfig(await response.json())
    } catch (err) {
      setError(err.message || 'Unable to load user configuration')
    } finally {
      setSelectedUserLoading(false)
    }
  }

  async function loadAppData() {
    if (!session) return
    setLoadingData(true)
    try {
      const requests = [
        fetch('/api/app/gmail-config'),
        fetch('/api/app/bridges'),
        fetch('/api/app/ui-preferences')
      ]

      if (session.role === 'ADMIN') {
        requests.push(fetch('/api/admin/dashboard'))
        requests.push(fetch('/api/admin/users'))
      }

      const responses = await Promise.all(requests)
      if (responses.some((response) => !response.ok)) {
        const firstFailed = responses.find((response) => !response.ok)
        throw new Error(await apiErrorText(firstFailed, 'Unable to load admin data'))
      }

      const payloads = await Promise.all(responses.map((response) => response.json()))
      const [gmailPayload, bridgesPayload, uiPreferencesPayload, adminPayload, usersPayload] = payloads

      setGmailMeta(gmailPayload)
      setGmailConfig({
        ...DEFAULT_GMAIL_CONFIG,
        destinationUser: gmailPayload.destinationUser || 'me',
        redirectUri: gmailPayload.redirectUri || gmailPayload.defaultRedirectUri || `${window.location.origin}/api/google-oauth/callback`,
        createMissingLabels: gmailPayload.createMissingLabels,
        neverMarkSpam: gmailPayload.neverMarkSpam,
        processForCalendar: gmailPayload.processForCalendar
      })
      setMyBridges(Array.isArray(bridgesPayload) ? bridgesPayload : [])
      if (uiPreferencesLoadedForUserId !== session.id) {
        const nextUiPreferences = {
          ...DEFAULT_UI_PREFERENCES,
          ...(uiPreferencesPayload || {})
        }
        setUiPreferences({
          ...nextUiPreferences,
          ...(nextUiPreferences.persistLayout ? {} : DEFAULT_UI_PREFERENCES)
        })
        setUiPreferencesLoadedForUserId(session.id)
        setTouchedSections({})
      }

      if (adminPayload) {
        setSystemDashboard(adminPayload)
      }
      if (usersPayload) {
        setUsers(usersPayload)
        if (!selectedUserId && usersPayload.length > 0) {
          setSelectedUserId(usersPayload[0].id)
        }
      }
      setError('')
    } catch (err) {
      setError(err.message || 'Unable to load application data')
    } finally {
      setLoadingData(false)
    }
  }

  useEffect(() => {
    loadSession()
  }, [])

  useEffect(() => {
    if (!session) return
    loadAppData()
    const timer = window.setInterval(loadAppData, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [session])

  useEffect(() => {
    if (selectedUserId) {
      loadSelectedUserConfiguration(selectedUserId)
    }
  }, [selectedUserId, session?.role])

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
          throw new Error(await apiErrorText(response, 'Login failed'))
        }
        const payload = await response.json()
        setSession(payload)
        setNotice(payload.mustChangePassword ? 'Change the bootstrap password before doing anything else.' : 'Signed in.')
      } catch (err) {
        setAuthError(err.message || 'Login failed')
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
          throw new Error(await apiErrorText(response, 'Registration failed'))
        }
        const payload = await response.json()
        setRegisterForm(DEFAULT_REGISTER_FORM)
        setNotice(payload.message || 'Registration submitted.')
      } catch (err) {
        setAuthError(err.message || 'Registration failed')
      }
    })
  }

  async function handleLogout() {
    await withPending('logout', async () => {
      await fetch('/api/auth/logout', { method: 'POST' })
      setSession(null)
      setSystemDashboard(null)
      setUsers([])
      setSelectedUserConfig(null)
      setSelectedUserId(null)
      setUiPreferences(DEFAULT_UI_PREFERENCES)
      setUiPreferencesLoadedForUserId(null)
      setTouchedSections({})
      setShowPasswordPanel(false)
      setNotice('Signed out.')
    })
  }

  async function handlePasswordChange(event) {
    event.preventDefault()
    setError('')
    await withPending('passwordChange', async () => {
      try {
        const response = await fetch('/api/account/password', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(passwordForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to change password'))
        }
        setPasswordForm(DEFAULT_PASSWORD_FORM)
        setNotice('Password updated.')
        await loadSession()
      } catch (err) {
        setError(err.message || 'Unable to change password')
      }
    })
  }

  async function saveGmailConfig(event) {
    event.preventDefault()
    setError('')
    await withPending('gmailSave', async () => {
      try {
        const response = await fetch('/api/app/gmail-config', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(gmailConfig)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to save Gmail configuration'))
        }
        setNotice('Gmail configuration saved.')
        await loadAppData()
      } catch (err) {
        setError(err.message || 'Unable to save Gmail configuration')
      }
    })
  }

  async function saveBridge(event) {
    event.preventDefault()
    setError('')
    await withPending('bridgeSave', async () => {
      try {
        const response = await fetch('/api/app/bridges', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(bridgeForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to save bridge'))
        }
        setNotice(`Bridge ${bridgeForm.bridgeId} saved.`)
        setBridgeForm(DEFAULT_BRIDGE_FORM)
        await loadAppData()
      } catch (err) {
        setError(err.message || 'Unable to save bridge')
      }
    })
  }

  async function deleteBridge(bridgeId) {
    if (!window.confirm(`Delete bridge ${bridgeId}?`)) return
    setError('')
    await withPending(`bridgeDelete:${bridgeId}`, async () => {
      try {
        const response = await fetch(`/api/app/bridges/${encodeURIComponent(bridgeId)}`, { method: 'DELETE' })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to delete bridge'))
        }
        setNotice(`Bridge ${bridgeId} deleted.`)
        await loadAppData()
      } catch (err) {
        setError(err.message || 'Unable to delete bridge')
      }
    })
  }

  async function createUser(event) {
    event.preventDefault()
    setError('')
    await withPending('createUser', async () => {
      try {
        const response = await fetch('/api/admin/users', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(createUserForm)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to create user'))
        }
        const payload = await response.json()
        setCreateUserForm(DEFAULT_CREATE_USER_FORM)
        setNotice(`User ${payload.username} created.`)
        await loadAppData()
        setSelectedUserId(payload.id)
      } catch (err) {
        setError(err.message || 'Unable to create user')
      }
    })
  }

  async function updateUser(userId, patch, successMessage) {
    setError('')
    await withPending(`updateUser:${userId}`, async () => {
      try {
        const response = await fetch(`/api/admin/users/${userId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(patch)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to update user'))
        }
        const payload = await response.json()
        setNotice(successMessage || `Updated ${payload.username}.`)
        await loadAppData()
        setSelectedUserId(payload.id)
        await loadSelectedUserConfiguration(payload.id)
      } catch (err) {
        setError(err.message || 'Unable to update user')
      }
    })
  }

  async function runPoll() {
    setError('')
    setRunningPoll(true)
    await withPending('runPoll', async () => {
      try {
        const response = await fetch('/api/admin/poll/run', { method: 'POST' })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to run poll'))
        }
        const payload = await response.json()
        setNotice(`Poll finished: fetched ${payload.fetched}, imported ${payload.imported}, duplicates ${payload.duplicates}, errors ${payload.errors.length}.`)
        await loadAppData()
      } catch (err) {
        setError(err.message || 'Unable to run poll')
      } finally {
        setRunningPoll(false)
      }
    })
  }

  function startGoogleOAuthSelf() {
    withPending('googleOAuthSelf', async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign('/api/google-oauth/start/self')
          resolve()
        }, 75)
      })
    })
  }

  function startGoogleOAuthSystem() {
    withPending('googleOAuthSystem', async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign('/api/google-oauth/start/system')
          resolve()
        }, 75)
      })
    })
  }

  function startMicrosoftOAuth(sourceId) {
    withPending(`microsoftOAuth:${sourceId}`, async () => {
      await new Promise((resolve) => {
        window.setTimeout(() => {
          window.location.assign(`/api/microsoft-oauth/start?sourceId=${encodeURIComponent(sourceId)}`)
          resolve()
        }, 75)
      })
    })
  }

  async function handleRefresh() {
    await withPending('refresh', async () => {
      await loadAppData()
    })
  }

  async function persistUiPreferences(nextPreferences) {
    await withPending('uiPreferences', async () => {
      try {
        const response = await fetch('/api/app/ui-preferences', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(nextPreferences)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, 'Unable to save layout preference'))
        }
        const payload = await response.json()
        setUiPreferences({
          ...DEFAULT_UI_PREFERENCES,
          ...payload
        })
      } catch (err) {
        setError(err.message || 'Unable to save layout preference')
      }
    })
  }

  function toggleSection(sectionKey) {
    setTouchedSections((current) => ({ ...current, [sectionKey]: true }))
    const nextPreferences = {
      ...uiPreferences,
      [sectionKey]: !uiPreferences[sectionKey]
    }
    setUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      persistUiPreferences(nextPreferences)
    }
  }

  function handlePersistLayoutChange(enabled) {
    const nextPreferences = {
      ...uiPreferences,
      persistLayout: enabled
    }
    setUiPreferences(nextPreferences)
    persistUiPreferences(nextPreferences)
  }

  function focusSection(event, step) {
    event.preventDefault()
    if (step.sectionKey && uiPreferences[step.sectionKey]) {
      setTouchedSections((current) => ({ ...current, [step.sectionKey]: true }))
      const nextPreferences = {
        ...uiPreferences,
        [step.sectionKey]: false
      }
      setUiPreferences(nextPreferences)
      if (nextPreferences.persistLayout) {
        persistUiPreferences(nextPreferences)
      }
    }
    if (step.targetId === 'password-panel-section') {
      setShowPasswordPanel(true)
    }
    window.setTimeout(() => {
      const target = document.getElementById(step.targetId)
      if (!target) {
        return
      }
      target.scrollIntoView({ behavior: 'smooth', block: 'start' })
      target.focus()
    }, 150)
  }

  function editBridge(bridge) {
    setBridgeForm({
      bridgeId: bridge.bridgeId,
      enabled: bridge.enabled,
      protocol: bridge.protocol,
      host: bridge.host,
      port: bridge.port,
      tls: bridge.tls,
      authMethod: bridge.authMethod,
      oauthProvider: bridge.oauthProvider,
      username: bridge.username,
      password: '',
      oauthRefreshToken: '',
      folder: bridge.folder,
      unreadOnly: bridge.unreadOnly,
      customLabel: bridge.customLabel
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const setupGuideState = buildSetupGuideState({
    gmailMeta,
    myBridges,
    session,
    systemDashboard
  })

  useEffect(() => {
    if (!session || uiPreferences.persistLayout || touchedSections.quickSetupCollapsed || !setupGuideState.allStepsComplete) {
      return
    }
    setUiPreferences((current) => {
      if (current.quickSetupCollapsed) {
        return current
      }
      return {
        ...current,
        quickSetupCollapsed: true
      }
    })
  }, [session, setupGuideState.allStepsComplete, touchedSections.quickSetupCollapsed, uiPreferences.persistLayout])

  if (authLoading) {
    return <LoadingScreen label="Loading InboxBridge…" />
  }

  if (!session) {
    return (
      <AuthScreen
        authError={authError}
        loginLoading={isPending('login')}
        loginForm={loginForm}
        notice={notice}
        onLogin={handleLogin}
        onLoginChange={setLoginForm}
        registerLoading={isPending('register')}
        onRegister={handleRegister}
        onRegisterChange={setRegisterForm}
        registerForm={registerForm}
      />
    )
  }

  return (
    <div className="page-shell">
      <main className="dashboard">
        <HeroPanel
          loadingData={loadingData}
          onRefresh={handleRefresh}
          onSignOut={handleLogout}
          onChangePasswordAccess={() => setShowPasswordPanel((current) => !current)}
          passwordPanelVisible={showPasswordPanel}
          refreshLoading={isPending('refresh')}
          session={session}
          signOutLoading={isPending('logout')}
        />

        {showPasswordPanel ? (
          <PasswordPanel
            onPasswordChange={handlePasswordChange}
            onPasswordFormChange={setPasswordForm}
            passwordForm={passwordForm}
            passwordLoading={isPending('passwordChange')}
          />
        ) : null}

        <SetupGuidePanel
          collapsed={uiPreferences.quickSetupCollapsed}
          onFocusSection={focusSection}
          onPersistLayoutChange={handlePersistLayoutChange}
          onToggleCollapse={() => toggleSection('quickSetupCollapsed')}
          persistLayout={uiPreferences.persistLayout}
          savingLayout={isPending('uiPreferences')}
          steps={setupGuideState.steps}
        />

        {session.mustChangePassword ? <Banner tone="warning">This account must change its password before normal use.</Banner> : null}
        {notice ? <Banner tone="success">{notice}</Banner> : null}
        {error ? <Banner tone="error" copyText={error}>{error}</Banner> : null}

        <GmailDestinationSection
          collapsed={uiPreferences.gmailDestinationCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          gmailConfig={gmailConfig}
          gmailMeta={gmailMeta}
          oauthLoading={isPending('googleOAuthSelf')}
          onCollapseToggle={() => toggleSection('gmailDestinationCollapsed')}
          onConnectOAuth={startGoogleOAuthSelf}
          onSave={saveGmailConfig}
          saveLoading={isPending('gmailSave')}
          setGmailConfig={setGmailConfig}
        />

        <UserBridgesSection
          bridgeForm={bridgeForm}
          bridges={myBridges}
          collapsed={uiPreferences.sourceBridgesCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          connectingBridgeId={myBridges.find((bridge) => isPending(`microsoftOAuth:${bridge.bridgeId}`))?.bridgeId || null}
          deletingBridgeId={myBridges.find((bridge) => isPending(`bridgeDelete:${bridge.bridgeId}`))?.bridgeId || null}
          onBridgeFormChange={setBridgeForm}
          onCollapseToggle={() => toggleSection('sourceBridgesCollapsed')}
          onConnectMicrosoft={startMicrosoftOAuth}
          onDeleteBridge={deleteBridge}
          onEditBridge={editBridge}
          onSaveBridge={saveBridge}
          saveLoading={isPending('bridgeSave')}
        />

        {session.role === 'ADMIN' ? (
          <>
            <SystemDashboardSection
              collapsed={uiPreferences.systemDashboardCollapsed}
              collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
              connectingBridgeId={systemDashboard?.bridges.find((bridge) => isPending(`microsoftOAuth:${bridge.id}`))?.id || null}
              dashboard={systemDashboard}
              onCollapseToggle={() => toggleSection('systemDashboardCollapsed')}
              onConnectMicrosoft={startMicrosoftOAuth}
              onConnectSystemGoogle={startGoogleOAuthSystem}
              onRunPoll={runPoll}
              runningPoll={runningPoll}
              systemGoogleLoading={isPending('googleOAuthSystem')}
            />
            <UserManagementSection
              collapsed={uiPreferences.userManagementCollapsed}
              collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
              createUserForm={createUserForm}
              createUserLoading={isPending('createUser')}
              onCollapseToggle={() => toggleSection('userManagementCollapsed')}
              onCreateUser={createUser}
              onCreateUserFormChange={setCreateUserForm}
              onSelectUser={setSelectedUserId}
              onUpdateUser={updateUser}
              selectedUserConfig={selectedUserConfig}
              selectedUserId={selectedUserId}
              selectedUserLoading={selectedUserLoading}
              updatingUserId={selectedUserConfig && isPending(`updateUser:${selectedUserConfig.user.id}`) ? selectedUserConfig.user.id : null}
              users={users}
            />
          </>
        ) : null}
      </main>
    </div>
  )
}

export default App
