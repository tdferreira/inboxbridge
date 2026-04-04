import { useEffect, useMemo, useRef, useState } from 'react'
import Banner from './components/common/Banner'
import ButtonLink from './components/common/ButtonLink'
import DeviceLocationPrompt from './components/common/DeviceLocationPrompt'
import FloatingActionMenu from './components/common/FloatingActionMenu'
import InstallPromptCard from './components/common/InstallPromptCard'
import LanguageMenuButton from './components/common/LanguageMenuButton'
import LoadingButton from './components/common/LoadingButton'
import LoadingScreen from './components/common/LoadingScreen'
import PasswordField from './components/common/PasswordField'
import { normalizePasskeyError, parseGetOptions, passkeysSupported, serializeCredential } from './lib/passkeys'
import {
  RemoteUnauthorizedError,
  remoteControl,
  remoteFinishPasskey,
  remoteLivePoll,
  remoteLogin,
  remoteLogout,
  remoteMoveSourceNext,
  remotePauseLivePoll,
  remoteResumeLivePoll,
  remoteRunAllUsersPoll,
  recordRemoteDeviceLocation,
  remoteRunSourcePoll,
  remoteRunUserPoll,
  remoteSession,
  remoteStopLivePoll,
  remoteStartPasskey
} from './lib/remoteApi'
import { languageOptions, normalizeLocale, translate } from './lib/i18n'
import { formatDate, formatDurationMeaning, formatPollError, formatRemoteImportedSizeSummary } from './lib/formatters'
import { formatLiveProgressLabel, formatLiveProgressSummary, hasDeterminateLiveProgress, liveProgressPercent } from './lib/livePollProgress'
import { normalizeManualTimeZone, normalizeTimeZoneMode, resetCurrentFormattingTimeZone, resolveEffectiveTimeZone, setCurrentFormattingTimeZone, TIMEZONE_MODE_AUTO } from './lib/timeZonePreferences'
import { usePwaInstallPrompt } from './lib/usePwaInstallPrompt'
import { useSessionDeviceLocation } from './lib/useSessionDeviceLocation'
import './RemoteApp.css'

const DEFAULT_LOGIN_FORM = {
  username: '',
  password: ''
}
const LOGIN_STAGE_USERNAME = 'username'
const LOGIN_STAGE_CREDENTIALS = 'credentials'
const AUTH_ACTION_NONE = ''
const AUTH_ACTION_SIGN_IN = 'sign-in'
const AUTH_ACTION_PASSKEY = 'passkey'
const REMOTE_INSTALL_PROMPT_DISMISSED_KEY = 'inboxbridge.remote.installPromptDismissed'
const LIVE_EVENTS_STALE_MS = 45000
function RemoteApp() {
  const [language, setLanguage] = useState(() => normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language))
  const [timezonePreference, setTimezonePreference] = useState({ timezoneMode: TIMEZONE_MODE_AUTO, timezone: '' })
  const t = useMemo(() => (key, params) => translate(language, key, params), [language])
  const effectiveTimeZone = useMemo(
    () => resolveEffectiveTimeZone(timezonePreference.timezoneMode, timezonePreference.timezone),
    [timezonePreference.timezone, timezonePreference.timezoneMode]
  )
  setCurrentFormattingTimeZone(effectiveTimeZone)
  const [session, setSession] = useState(null)
  const [control, setControl] = useState(null)
  const [loading, setLoading] = useState(true)
  const [authError, setAuthError] = useState('')
  const [authNotice, setAuthNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const [loginForm, setLoginForm] = useState(DEFAULT_LOGIN_FORM)
  const [loginStage, setLoginStage] = useState(LOGIN_STAGE_USERNAME)
  const [loginLoading, setLoginLoading] = useState(false)
  const [passkeyLoading, setPasskeyLoading] = useState(false)
  const [activeAuthAction, setActiveAuthAction] = useState(AUTH_ACTION_NONE)
  const [pollingKey, setPollingKey] = useState('')
  const [lastResult, setLastResult] = useState(null)
  const [livePoll, setLivePoll] = useState(null)
  const [liveEventsConnected, setLiveEventsConnected] = useState(false)
  const [expandedSources, setExpandedSources] = useState(() => new Set())
  const [installLoading, setInstallLoading] = useState(false)
  const [installPromptDismissed, setInstallPromptDismissed] = useState(() => window.localStorage.getItem(REMOTE_INSTALL_PROMPT_DISMISSED_KEY) === 'true')
  const liveEventsRef = useRef(null)
  const lastLiveEventAtRef = useRef(0)
  const passwordInputRef = useRef(null)
  const installPrompt = usePwaInstallPrompt()
  const deviceLocation = useSessionDeviceLocation({
    captureLocation: async (payload) => {
      await recordRemoteDeviceLocation(payload)
      setSession((current) => current ? { ...current, deviceLocationCaptured: true } : current)
    },
    session,
    storageScope: 'remote',
    t
  })

  useEffect(() => {
    document.title = t('remote.title')
    return () => {
      document.title = 'InboxBridge Admin'
    }
  }, [t])

  async function handleInstallApp() {
    setInstallLoading(true)
    try {
      await installPrompt.promptInstall()
    } finally {
      setInstallLoading(false)
    }
  }

  useEffect(() => {
    void loadRemoteState()
  }, [])

  useEffect(() => {
    window.localStorage.setItem('inboxbridge.language', language)
  }, [language])

  useEffect(() => {
    return () => {
      resetCurrentFormattingTimeZone()
    }
  }, [])

  useEffect(() => {
    if (installPrompt.installed) {
      setInstallPromptDismissed(false)
      window.localStorage.removeItem(REMOTE_INSTALL_PROMPT_DISMISSED_KEY)
      return
    }
    window.localStorage.setItem(REMOTE_INSTALL_PROMPT_DISMISSED_KEY, installPromptDismissed ? 'true' : 'false')
  }, [installPrompt.installed, installPromptDismissed])

  useEffect(() => {
    if (loginStage === LOGIN_STAGE_CREDENTIALS) {
      passwordInputRef.current?.focus()
      passwordInputRef.current?.select?.()
    }
  }, [loginStage])

  const selectableLanguages = useMemo(() => languageOptions.map((value) => ({
    value,
    label: translate(language, `language.${value}`)
  })), [language])

  const shouldShowInstallPromptCard = !installPrompt.installed && !installPromptDismissed

  function applySessionPreferences(nextLanguage, timezoneMode, timezone) {
    setLanguage(normalizeLocale(nextLanguage || language))
    setTimezonePreference({
      timezoneMode: normalizeTimeZoneMode(timezoneMode),
      timezone: normalizeManualTimeZone(timezone)
    })
  }

  function applyLivePoll(nextLivePoll) {
    setLivePoll(nextLivePoll?.running ? nextLivePoll : null)
  }

  function dismissInstallPrompt() {
    setInstallPromptDismissed(true)
  }

  function showInstallPrompt() {
    setInstallPromptDismissed(false)
  }

  function focusInstallPrompt() {
    showInstallPrompt()
    window.requestAnimationFrame(() => {
      const promptHeading = document.querySelector('.utility-prompt-card h2')
      promptHeading?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
    })
  }

  async function loadRemoteState() {
    setLoading(true)
    setAuthError('')
    try {
      const sessionPayload = await remoteSession()
      const [controlPayload, livePollPayload] = await Promise.all([
        remoteControl(),
        remoteLivePoll().catch(() => null)
      ])
      setSession(sessionPayload)
      applySessionPreferences(sessionPayload?.language, sessionPayload?.timezoneMode, sessionPayload?.timezone)
      setControl(controlPayload)
      applyLivePoll(livePollPayload)
      lastLiveEventAtRef.current = Date.now()
    } catch {
      clearRemoteSessionState()
    } finally {
      setLoading(false)
    }
  }

  async function refreshControl() {
    if (!session) return
    const controlPayload = await remoteControl()
    setControl(controlPayload)
  }

  async function refreshLivePoll() {
    if (!session) return
    const livePollPayload = await remoteLivePoll()
    applyLivePoll(livePollPayload)
  }

  function clearRemoteSessionState(notice = '') {
    setSession(null)
    setControl(null)
    setLastResult(null)
    setLivePoll(null)
    setLiveEventsConnected(false)
    setActionError('')
    setAuthError('')
    setAuthNotice(notice)
    setLoginStage(LOGIN_STAGE_USERNAME)
    setLoginLoading(false)
    setPasskeyLoading(false)
    setActiveAuthAction(AUTH_ACTION_NONE)
    setExpandedSources(new Set())
    setTimezonePreference({ timezoneMode: TIMEZONE_MODE_AUTO, timezone: '' })
  }

  async function handleLogin(event) {
    event.preventDefault()
    setAuthError('')
    setAuthNotice('')
    if (loginStage === LOGIN_STAGE_USERNAME) {
      setLoginStage(LOGIN_STAGE_CREDENTIALS)
      return
    }
    setActiveAuthAction(AUTH_ACTION_SIGN_IN)
    setLoginLoading(true)
    await submitLogin()
  }

  async function submitLogin() {
    try {
      const payload = await remoteLogin(loginForm)
      if (payload?.status === 'PASSKEY_REQUIRED') {
        if (!passkeysSupported()) {
          throw new Error(t('errors.passkeyIpHostUnsupported', { host: window.location.hostname || 'this host' }))
        }
        await continuePasskeyLogin(payload.passkeyChallenge || null)
        return
      }
      setSession(payload)
      applySessionPreferences(payload?.language, payload?.timezoneMode, payload?.timezone)
      setLoginForm(DEFAULT_LOGIN_FORM)
      const [controlPayload, livePollPayload] = await Promise.all([
        remoteControl(),
        remoteLivePoll().catch(() => null)
      ])
      setControl(controlPayload)
      applyLivePoll(livePollPayload)
    } catch (error) {
      setAuthError(error.message)
    } finally {
      setLoginLoading(false)
      setActiveAuthAction((current) => (current === AUTH_ACTION_SIGN_IN ? AUTH_ACTION_NONE : current))
    }
  }

  async function continuePasskeyLogin(challenge = null) {
    if (!passkeysSupported()) {
      setAuthError(t('auth.passkeySupport'))
      return
    }
    setPasskeyLoading(true)
    setAuthError('')
    setAuthNotice('')
    try {
      const passkeyChallenge = challenge || await remoteStartPasskey({})
      const credential = await navigator.credentials.get({
        publicKey: parseGetOptions(passkeyChallenge.publicKeyJson)
      })
      const payload = await remoteFinishPasskey({
        ceremonyId: passkeyChallenge.ceremonyId,
        credentialJson: serializeCredential(credential)
      })
      setSession(payload)
      applySessionPreferences(payload?.language, payload?.timezoneMode, payload?.timezone)
      setLoginForm(DEFAULT_LOGIN_FORM)
      const [controlPayload, livePollPayload] = await Promise.all([
        remoteControl(),
        remoteLivePoll().catch(() => null)
      ])
      setControl(controlPayload)
      applyLivePoll(livePollPayload)
    } catch (error) {
      setAuthError(normalizePasskeyError(error, t))
    } finally {
      setPasskeyLoading(false)
      setLoginLoading(false)
      setActiveAuthAction(AUTH_ACTION_NONE)
    }
  }

  async function handleLogout() {
    setPollingKey('logout')
    setActionError('')
    try {
      await remoteLogout()
      clearRemoteSessionState()
    } catch (error) {
      if (error instanceof RemoteUnauthorizedError) {
        clearRemoteSessionState(t('remote.sessionExpiredNotice'))
        return
      }
      setActionError(formatRemoteMessage(error.message, language))
    } finally {
      setPollingKey('')
    }
  }

  async function runPoll(actionKey, request) {
    setPollingKey(actionKey)
    setActionError('')
    try {
      const result = await request()
      setLastResult(result)
      await Promise.all([refreshControl(), refreshLivePoll().catch(() => {})])
    } catch (error) {
      if (error instanceof RemoteUnauthorizedError) {
        clearRemoteSessionState(t('remote.sessionExpiredNotice'))
        return
      }
      setActionError(formatRemoteMessage(error.message, language))
    } finally {
      setPollingKey('')
    }
  }

  async function runLivePollAction(actionKey, request) {
    setPollingKey(actionKey)
    setActionError('')
    try {
      const payload = await request()
      applyLivePoll(payload)
    } catch (error) {
      if (error instanceof RemoteUnauthorizedError) {
        clearRemoteSessionState(t('remote.sessionExpiredNotice'))
        return
      }
      setActionError(formatRemoteMessage(error.message, language))
    } finally {
      setPollingKey('')
    }
  }

  function toggleSource(sourceId) {
    setExpandedSources((current) => {
      const next = new Set(current)
      if (next.has(sourceId)) {
        next.delete(sourceId)
      } else {
        next.add(sourceId)
      }
      return next
    })
  }

  useEffect(() => {
    if (!session) {
      setLiveEventsConnected(false)
      if (liveEventsRef.current) {
        liveEventsRef.current.close()
        liveEventsRef.current = null
      }
      return
    }
    if (typeof window.EventSource !== 'function') {
      setLiveEventsConnected(false)
      return
    }

    const eventSource = new window.EventSource('/api/remote/poll/events')
    liveEventsRef.current = eventSource
    let closingStream = false

    const verifyRemoteSessionAfterStreamError = async () => {
      try {
        await remoteSession()
      } catch (error) {
        if (error instanceof RemoteUnauthorizedError) {
          clearRemoteSessionState(t('remote.sessionExpiredNotice'))
        }
      }
    }

    const handleLiveEvent = (event) => {
      try {
        const payload = JSON.parse(event.data)
        if (payload?.type === 'session-revoked' && (!payload?.revokedSessionId || payload.revokedSessionId === session?.currentSessionId)) {
          clearRemoteSessionState(t('remote.sessionExpiredNotice'))
          return
        }
        if (payload?.poll) {
          applyLivePoll(payload.poll)
        }
        lastLiveEventAtRef.current = Date.now()
        setLiveEventsConnected(true)
      } catch {
        setLiveEventsConnected(false)
      }
    }

    eventSource.onmessage = handleLiveEvent
    ;[
      'poll-snapshot',
      'poll-run-started',
      'poll-run-pausing',
      'poll-run-paused',
      'poll-run-resumed',
      'poll-run-stopping',
      'poll-run-finished',
      'poll-source-started',
      'poll-source-finished',
      'poll-source-reprioritized',
      'poll-source-retry-queued',
      'keepalive',
      'session-revoked'
    ].forEach((eventName) => eventSource.addEventListener(eventName, handleLiveEvent))

    eventSource.onerror = () => {
      if (closingStream) {
        return
      }
      setLiveEventsConnected(false)
      void verifyRemoteSessionAfterStreamError()
    }

    return () => {
      closingStream = true
      eventSource.close()
      if (liveEventsRef.current === eventSource) {
        liveEventsRef.current = null
      }
    }
  }, [session])

  useEffect(() => {
    if (!session || !livePoll?.running || !liveEventsConnected) {
      return
    }
    const timer = window.setInterval(() => {
      if (lastLiveEventAtRef.current && Date.now() - lastLiveEventAtRef.current > LIVE_EVENTS_STALE_MS) {
        setLiveEventsConnected(false)
      }
    }, 5000)
    return () => window.clearInterval(timer)
  }, [liveEventsConnected, livePoll?.running, session])

  useEffect(() => {
    if (!session || !livePoll?.running || !liveEventsConnected) {
      return
    }

    let cancelled = false

    async function reconcileRemoteLivePoll() {
      try {
        const payload = await remoteLivePoll()
        if (!cancelled) {
          applyLivePoll(payload)
        }
      } catch {
        // Keep the event stream authoritative unless the stale-stream fallback takes over.
      }
    }

    const timer = window.setInterval(() => {
      void reconcileRemoteLivePoll()
    }, 5000)

    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [liveEventsConnected, livePoll?.running, session])

  useEffect(() => {
    const hasPollRequestInFlight = Boolean(pollingKey && pollingKey !== 'logout')
    if (!session || liveEventsConnected) {
      return
    }

    let cancelled = false

    async function refreshRemoteLivePoll() {
      try {
        const payload = await remoteLivePoll()
        if (!cancelled) {
          applyLivePoll(payload)
        }
      } catch {
        if (!cancelled) {
          applyLivePoll(null)
        }
      }
    }

    void refreshRemoteLivePoll()
    const timer = window.setInterval(() => {
      void refreshRemoteLivePoll()
    }, (livePoll?.running || hasPollRequestInFlight) ? 1000 : 5000)

    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [liveEventsConnected, livePoll?.running, pollingKey, session])

  const liveSourcesById = useMemo(
    () => new Map((livePoll?.sources || []).map((source) => [source.sourceId, source])),
    [livePoll]
  )
  const livePollRunning = Boolean(livePoll?.running)
  const visibleSources = useMemo(
    () => (control?.sources || []).filter((source) => source.enabled !== false),
    [control]
  )

  if (loading) {
    return <LoadingScreen label={t('remote.loading')} />
  }

  if (!session) {
    const showCredentialStep = loginStage === LOGIN_STAGE_CREDENTIALS
    const loginReady = loginForm.username.trim() !== ''
    return (
      <div className="remote-shell">
        <main className="remote-auth-card">
          <LanguageMenuButton
            ariaLabel={t('preferences.language')}
            className="auth-language-picker remote-auth-language-picker"
            currentLanguage={language}
            onChange={(value) => setLanguage(normalizeLocale(value))}
            options={selectableLanguages}
          />
          <h1>{t('remote.heading')}</h1>
          <p className="remote-copy">{t('remote.authCopy')}</p>
          <form className="stack-form" onSubmit={handleLogin}>
            <label>
              <span>{t('auth.username')}</span>
              <input
                value={loginForm.username}
                onChange={(event) => setLoginForm((current) => ({
                  ...current,
                  username: event.target.value,
                  password: event.target.value === current.username ? current.password : ''
                }))}
              />
            </label>
            {showCredentialStep ? (
              <PasswordField
                hideLabel={t('common.hideField', { label: t('auth.password') })}
                label={t('auth.password')}
                onChange={(event) => setLoginForm((current) => ({ ...current, password: event.target.value }))}
                ref={passwordInputRef}
                showLabel={t('common.showField', { label: t('auth.password') })}
                value={loginForm.password}
              />
            ) : null}
            <div className="remote-auth-actions">
              <LoadingButton className="primary" disabled={!loginReady} isLoading={loginLoading && activeAuthAction === AUTH_ACTION_SIGN_IN} loadingLabel={t('auth.signInLoading')} type="submit">
                {t('remote.signIn')}
              </LoadingButton>
              {showCredentialStep ? (
                <LoadingButton className="secondary" disabled={!passkeysSupported() || !loginReady} isLoading={passkeyLoading || (loginLoading && activeAuthAction === AUTH_ACTION_PASSKEY)} loadingLabel={loginForm.password.trim() ? t('auth.signInLoading') : t('auth.signInWithPasskeyLoading')} onClick={() => {
                  setAuthError('')
                  setAuthNotice('')
                  setActiveAuthAction(AUTH_ACTION_PASSKEY)
                  if (loginForm.password.trim()) {
                    setLoginLoading(true)
                    void submitLogin()
                    return
                  }
                  void continuePasskeyLogin()
                }} type="button">
                  {t('auth.signInWithPasskey')}
                </LoadingButton>
              ) : null}
            </div>
            {authNotice ? <Banner tone="warning">{authNotice}</Banner> : null}
          </form>
          {!passkeysSupported() ? <div className="muted-box remote-note">{t('auth.passkeySupport')}</div> : null}
          {authError ? <Banner copyLabel={t('common.copyError')} copyText={authError} tone="error">{authError}</Banner> : null}
        </main>
      </div>
    )
  }

  if (control?.setupRequired) {
    const missingSteps = [
      !control.hasReadyDestinationMailbox ? t('remote.setupNeedDestination') : '',
      !control.hasOwnSourceEmailAccounts ? t('remote.setupNeedSources') : ''
    ].filter(Boolean)

    return (
      <div className="remote-shell">
        <main className="remote-panel">
          {shouldShowInstallPromptCard ? (
            <InstallPromptCard
              canPromptInstall={installPrompt.canPromptInstall}
              installLoading={installLoading}
              onDismiss={dismissInstallPrompt}
              onInstall={handleInstallApp}
              t={t}
            />
          ) : null}
          {deviceLocation.shouldPrompt ? (
            <DeviceLocationPrompt
              error={deviceLocation.error}
              onDismiss={deviceLocation.dismissPrompt}
              onRequestLocation={deviceLocation.requestLocation}
              saving={deviceLocation.saving}
              success={deviceLocation.success}
              t={t}
            />
          ) : null}
          <section className="remote-hero">
            <div>
              <p className="remote-eyebrow">{t('remote.eyebrow')}</p>
              <h1>{t('remote.dashboardTitle')}</h1>
              <p className="remote-copy">{formatRemoteSessionLine(session, t)}</p>
            </div>
            <div className="remote-hero-actions">
              <RemoteHeroMenu
                deviceLocation={deviceLocation}
                installPromptVisible={!installPrompt.installed}
                onLogout={handleLogout}
                onShowInstallPrompt={focusInstallPrompt}
                pollingKey={pollingKey}
                t={t}
              />
            </div>
          </section>

          <section className="remote-setup-card">
            <p className="remote-eyebrow">{t('remote.setupEyebrow')}</p>
            <h2>{t('remote.setupTitle')}</h2>
            <p className="remote-copy">{t('remote.setupCopy')}</p>
            <ul className="remote-setup-list">
              {missingSteps.map((step) => <li key={step}>{step}</li>)}
            </ul>
            <div className="remote-hero-actions">
              <ButtonLink href="/">{t('remote.openMyInboxBridge')}</ButtonLink>
            </div>
          </section>
        </main>
      </div>
    )
  }

  return (
    <div className="remote-shell">
      <main className="remote-panel">
        {shouldShowInstallPromptCard ? (
          <InstallPromptCard
            canPromptInstall={installPrompt.canPromptInstall}
            installLoading={installLoading}
            onDismiss={dismissInstallPrompt}
            onInstall={handleInstallApp}
            t={t}
          />
        ) : null}
        {deviceLocation.shouldPrompt ? (
          <DeviceLocationPrompt
            error={deviceLocation.error}
            onDismiss={deviceLocation.dismissPrompt}
            onRequestLocation={deviceLocation.requestLocation}
            saving={deviceLocation.saving}
            success={deviceLocation.success}
            t={t}
          />
        ) : null}
        <section className="remote-hero">
          <div>
            <p className="remote-eyebrow">{t('remote.eyebrow')}</p>
            <h1>{t('remote.dashboardTitle')}</h1>
            <p className="remote-copy">{formatRemoteSessionLine(session, t)}</p>
          </div>
          <div className="remote-hero-actions">
            <LoadingButton
              className="primary"
              disabled={livePollRunning}
              isLoading={pollingKey === 'user-poll'}
              loadingLabel={t('remote.runMyPollLoading')}
              onClick={() => runPoll('user-poll', remoteRunUserPoll)}
            >
              {t('remote.runMyPoll')}
            </LoadingButton>
            {session.canRunAllUsersPoll ? (
              <LoadingButton
                className="secondary"
                disabled={livePollRunning}
                isLoading={pollingKey === 'all-users-poll'}
                loadingLabel={t('remote.runAllUsersLoading')}
                onClick={() => runPoll('all-users-poll', remoteRunAllUsersPoll)}
            >
              {t('remote.runAllUsers')}
            </LoadingButton>
            ) : null}
            <RemoteHeroMenu
              deviceLocation={deviceLocation}
              installPromptVisible={!installPrompt.installed}
              onLogout={handleLogout}
              onShowInstallPrompt={focusInstallPrompt}
              pollingKey={pollingKey}
              t={t}
            />
          </div>
        </section>

        <section className="remote-summary-grid">
          <article className="remote-summary-card">
            <span className="remote-summary-label">{t('remote.sourceCount')}</span>
            <strong>{visibleSources.length}</strong>
          </article>
          <article className="remote-summary-card">
            <span className="remote-summary-label">{t('remote.remoteRateLimit')}</span>
            <strong>{t('remote.remoteRateLimitValue', {
              count: control?.remotePollRateLimitCount ?? 0,
              window: formatDurationDisplay(control?.remotePollRateLimitWindow, language)
            })}</strong>
          </article>
        </section>

        {actionError ? <Banner copyLabel={t('common.copyError')} copyText={actionError} tone="error">{actionError}</Banner> : null}
        {lastResult ? <PollResultCard language={language} result={lastResult} session={session} t={t} /> : null}

        <section className="remote-sources-section">
          <div className="remote-section-header">
            <div>
              <h2>{t('remote.sourcesTitle')}</h2>
              <p className="remote-copy">{t('remote.sourcesCopy')}</p>
            </div>
            {livePoll?.running && livePoll.viewerCanControl ? (
              <div className="remote-live-actions">
                {isPausedLivePoll(livePoll) ? (
                  <LoadingButton
                    className="secondary"
                    isLoading={pollingKey === 'resume-live-poll'}
                    loadingLabel={t('remote.resumeLoading')}
                    onClick={() => runLivePollAction('resume-live-poll', remoteResumeLivePoll)}
                  >
                    {t('remote.resume')}
                  </LoadingButton>
                ) : (
                  <LoadingButton
                    className="secondary"
                    isLoading={pollingKey === 'pause-live-poll'}
                    loadingLabel={t('remote.pauseLoading')}
                    onClick={() => runLivePollAction('pause-live-poll', remotePauseLivePoll)}
                  >
                    {t('remote.pause')}
                  </LoadingButton>
                )}
                <LoadingButton
                  className="danger"
                  isLoading={pollingKey === 'stop-live-poll'}
                  loadingLabel={t('remote.stopLoading')}
                  onClick={() => runLivePollAction('stop-live-poll', remoteStopLivePoll)}
                >
                  {t('remote.stop')}
                </LoadingButton>
              </div>
            ) : null}
          </div>
          <div className="remote-source-list">
            {visibleSources.map((source) => {
              const liveSource = liveSourcesById.get(source.sourceId) || null
              const summaryStatus = liveSource?.state || source.lastEvent?.status || null
              const summaryStatusTone = statusToneForRemoteState(summaryStatus)
              const resultStatusTone = statusToneForRemoteState(source.lastEvent?.status)
              const showOwnerInfo = session?.multiUserEnabled !== false
              const liveProgressCopy = liveSource ? formatRemoteLiveCopy(liveSource, t) : ''
              const showMoveNextAction = Boolean(
                livePoll?.viewerCanControl
                && liveSource?.actionable
                && (liveSource.state === 'QUEUED' || liveSource.state === 'RETRY_QUEUED')
                && liveSource.position > 1
              )
              const showLiveMeter = hasDeterminateLiveProgress(liveSource)
              const showSupplementaryLiveCopy = Boolean(liveProgressCopy && !showLiveMeter)
              const showLiveProgress = Boolean(showSupplementaryLiveCopy || showMoveNextAction)

              return (
                <article className={`remote-source-card${liveSource?.state === 'RUNNING' ? ' remote-source-card-running' : ''}`} key={source.sourceId}>
                  <div className="remote-source-header">
                    <div className="remote-source-heading">
                      <h3>{source.customLabel || source.sourceId}</h3>
                      <p className="remote-source-meta">{source.protocol} · {source.host}:{source.port}</p>
                    </div>
                    <div className="remote-source-actions">
                      <button
                        aria-expanded={expandedSources.has(source.sourceId)}
                        className="secondary remote-toggle-button"
                        onClick={() => toggleSource(source.sourceId)}
                        type="button"
                      >
                        {expandedSources.has(source.sourceId) ? t('remote.hideSourceDetails') : t('remote.showSourceDetails')}
                      </button>
                      <LoadingButton
                        className="primary"
                        isLoading={pollingKey === `source:${source.sourceId}` || liveSource?.state === 'RUNNING'}
                        disabled={livePollRunning}
                        loadingLabel={t('remote.runSourceLoading')}
                        onClick={() => runPoll(`source:${source.sourceId}`, () => remoteRunSourcePoll(source.sourceId))}
                      >
                        {t('remote.runSource')}
                      </LoadingButton>
                    </div>
                  </div>
                  <div className="remote-source-summary">
                    {showOwnerInfo ? <span className="status-pill tone-neutral remote-source-summary-pill">{source.ownerLabel}</span> : null}
                    <span className="status-pill tone-neutral remote-source-summary-pill">{source.folder}</span>
                    <span className="status-pill tone-neutral remote-source-summary-pill">
                      {source.effectivePollEnabled ? formatDurationDisplay(source.effectivePollInterval, language) : t('common.disabled')}
                    </span>
                    {summaryStatus ? (
                      showLiveMeter ? (
                        <span
                          aria-label={formatLiveProgressLabel(liveSource, t)}
                          aria-valuemax={liveSource.totalMessages}
                          aria-valuemin={0}
                          aria-valuenow={liveSource.processedMessages}
                          className="status-pill tone-neutral remote-source-summary-pill status-pill-progress remote-source-summary-progress-pill"
                          role="progressbar"
                        >
                          <span className="status-pill-progress-fill" style={{ width: `${liveProgressPercent(liveSource)}%` }} />
                          <span className="status-pill-progress-copy">{formatLiveProgressSummary(liveSource, language, t)}</span>
                        </span>
                      ) : (
                        <span className={`status-pill remote-source-summary-pill ${summaryStatusTone}`}>{formatRemoteStateLabel(summaryStatus, t)}</span>
                      )
                    ) : null}
                  </div>
                  {showLiveProgress ? (
                    <div className="remote-source-progress">
                      {showSupplementaryLiveCopy ? (
                        <p className="remote-source-progress-copy">
                          {liveProgressCopy}
                        </p>
                      ) : null}
                      {showMoveNextAction ? (
                        <div className="remote-source-progress-actions">
                          <LoadingButton
                            className="secondary"
                            isLoading={pollingKey === `move-next:${source.sourceId}`}
                            loadingLabel={t('remote.moveNextLoading')}
                            onClick={() => runLivePollAction(`move-next:${source.sourceId}`, () => remoteMoveSourceNext(source.sourceId))}
                          >
                            {t('remote.moveNext')}
                          </LoadingButton>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                  {expandedSources.has(source.sourceId) ? (
                    <>
                      <dl className="remote-source-details">
                        <div>
                          <dt>{t('remote.sourceId')}</dt>
                          <dd>{source.sourceId}</dd>
                        </div>
                        <div>
                          {showOwnerInfo ? (
                            <>
                              <dt>{t('remote.owner')}</dt>
                              <dd>{source.ownerLabel}</dd>
                            </>
                          ) : null}
                        </div>
                        <div>
                          <dt>{t('remote.folder')}</dt>
                          <dd>{source.folder}</dd>
                        </div>
                        <div>
                          <dt>{t('remote.polling')}</dt>
                          <dd>{source.effectivePollEnabled ? `${t('common.enabled')} · ${formatDurationDisplay(source.effectivePollInterval, language)}` : t('common.disabled')}</dd>
                        </div>
                        <div>
                          <dt>{t('remote.lastImport')}</dt>
                          <dd>{source.lastImportedAt ? formatDate(source.lastImportedAt, language) : t('common.never')}</dd>
                        </div>
                        <div className="remote-source-detail-full">
                          <dt>{t('remote.lastResult')}</dt>
                          <dd>
                            {source.lastEvent?.status ? (
                              <div className="remote-source-last-result">
                                <span className={`status-pill remote-source-summary-pill ${resultStatusTone}`}>{formatRemoteStateLabel(source.lastEvent.status, t)}</span>
                                {source.lastEvent.finishedAt || source.lastEvent.startedAt ? (
                                  <span className="status-pill tone-neutral remote-source-summary-pill remote-source-last-result-pill remote-source-last-result-time">
                                    {formatDate(source.lastEvent.finishedAt || source.lastEvent.startedAt, language)}
                                  </span>
                                ) : null}
                                <span className="status-pill tone-neutral remote-source-summary-pill remote-source-last-result-pill">{t('remote.fetched')}: {source.lastEvent.fetched}</span>
                                <span className="status-pill tone-neutral remote-source-summary-pill remote-source-last-result-pill">{t('remote.imported')}: {source.lastEvent.imported}</span>
                                <span className="status-pill tone-neutral remote-source-summary-pill remote-source-last-result-pill">{t('remote.duplicates')}: {source.lastEvent.duplicates}</span>
                                {formatRemoteImportedSizeSummary(source.lastEvent.importedBytes, language) ? (
                                  <span className="status-pill tone-neutral remote-source-summary-pill remote-source-last-result-pill">{formatRemoteImportedSizeSummary(source.lastEvent.importedBytes, language)}</span>
                                ) : null}
                                {source.lastEvent.spamJunkMessageCount > 0 ? (
                                  <span className="status-pill tone-neutral remote-source-summary-pill remote-source-last-result-pill">{t('remote.spamJunk')}: {source.lastEvent.spamJunkMessageCount}</span>
                                ) : null}
                              </div>
                            ) : t('common.never')}
                          </dd>
                        </div>
                      </dl>
                      {liveSource?.error && !isStoppedByUserMessage(liveSource.error) ? <Banner tone="warning">{formatRemoteMessage(liveSource.error, language)}</Banner> : null}
                      {source.lastEvent?.error && !isStoppedByUserMessage(source.lastEvent.error) ? <Banner tone="warning">{formatRemoteMessage(source.lastEvent.error, language)}</Banner> : null}
                    </>
                  ) : null}
                </article>
              )})}
          </div>
        </section>
      </main>
    </div>
  )
}

function PollResultCard({ language, result, session, t }) {
  const summary = [
    `${t('remote.fetched')}: ${result.fetched}`,
    `${t('remote.imported')}: ${result.imported}`,
    `${t('remote.duplicates')}: ${result.duplicates}`,
    ...(formatRemoteImportedSizeSummary(result.importedBytes, language) ? [formatRemoteImportedSizeSummary(result.importedBytes, language)] : []),
    ...(result.spamJunkMessageCount > 0 ? [`${t('remote.spamJunk')}: ${result.spamJunkMessageCount}`] : [])
  ].join(' · ')
  const formattedErrors = formatRemoteErrors(result, language)
  const normalizedState = String(result.state || '').toUpperCase()
  const stoppedByOtherUser = normalizedState === 'STOPPED'
    && result.stoppedByUsername
    && session?.username
    && result.stoppedByUsername !== session.username

  return (
    <section className="remote-result-card">
      <div className="remote-result-header">
        <div>
          <h2>{t('remote.lastRun')}</h2>
          <p className="remote-copy">{formatDate(result.finishedAt || result.startedAt, language)} · {summary}</p>
        </div>
      </div>
      {result.errors?.length ? (
        <Banner copyLabel={t('common.copyError')} copyText={formattedErrors.join('\n')} tone="warning">
          {formattedErrors.join(' ')}
        </Banner>
      ) : normalizedState === 'STOPPED' ? (
        <Banner tone="warning">{stoppedByOtherUser ? t('remote.lastRunStoppedByAdmin') : t('remote.lastRunStopped')}</Banner>
      ) : (
        <Banner tone="success">{t('remote.lastRunSuccess')}</Banner>
      )}
    </section>
  )
}

function RemoteHeroMenu({ deviceLocation, installPromptVisible, onLogout, onShowInstallPrompt, pollingKey, t }) {
  return (
    <FloatingActionMenu
      buttonClassName="icon-button fetcher-menu-button remote-hero-menu-button"
      buttonLabel={t('remote.moreActions')}
      className="remote-hero-menu"
      menuClassName="fetcher-menu remote-hero-menu-panel"
      menuContent={({ closeMenu }) => (
        <>
          {installPromptVisible ? (
            <button
              onClick={() => {
                closeMenu()
                onShowInstallPrompt()
              }}
              type="button"
            >
              {t('pwa.title')}
            </button>
          ) : null}
          {deviceLocation.shouldPrompt ? (
            <button
              disabled={deviceLocation.saving}
              onClick={() => {
                closeMenu()
                void deviceLocation.requestLocation()
              }}
              type="button"
            >
              {deviceLocation.saving ? t('deviceLocation.requestLoading') : t('deviceLocation.request')}
            </button>
          ) : null}
          <button
            disabled={pollingKey === 'logout'}
            onClick={() => {
              closeMenu()
              void onLogout()
            }}
            type="button"
          >
            {pollingKey === 'logout' ? t('hero.signOutLoading') : t('hero.signOut')}
          </button>
        </>
      )}
      title={t('remote.moreActions')}
    />
  )
}

function formatDurationDisplay(value, locale) {
  if (!value) return translate(locale, 'common.unavailable')
  return formatDurationMeaning(value, locale) || String(value)
}

function formatRemoteStateLabel(value, t) {
  const normalized = String(value || '').toUpperCase()
  switch (normalized) {
    case 'SUCCESS':
      return t('status.success')
    case 'ERROR':
    case 'FAILED':
      return t('status.error')
    case 'RUNNING':
      return t('status.running')
    case 'NOT_RUN':
      return t('status.notRun')
    case 'QUEUED':
      return t('remote.stateQueued')
    case 'RETRY_QUEUED':
      return t('remote.stateRetryQueued')
    case 'PAUSED':
      return t('remote.statePaused')
    case 'PAUSING':
      return t('remote.statePausing')
    case 'STOPPED':
      return t('remote.stateStopped')
    case 'COMPLETED':
      return t('remote.stateCompleted')
    default:
      return normalized.replaceAll('_', ' ')
  }
}

function isPausedLivePoll(livePoll) {
  const state = String(livePoll?.state || '')
  return state === 'PAUSED' || state === 'PAUSING'
}

function statusToneForRemoteState(value) {
  const normalized = String(value || '').toUpperCase()
  if (normalized === 'SUCCESS' || normalized === 'COMPLETED') {
    return 'tone-success'
  }
  if (normalized === 'ERROR' || normalized === 'FAILED' || normalized === 'STOPPED') {
    return 'tone-error'
  }
  return 'tone-neutral'
}

function formatRemoteLiveCopy(source, t) {
  const details = []
  if (hasDeterminateLiveProgress(source)) {
    details.push(t('remote.progressSummary', {
      fetched: source.fetched,
      imported: source.imported,
      duplicates: source.duplicates
    }))
  }
  if ((source.state === 'QUEUED' || source.state === 'RETRY_QUEUED') && Number.isInteger(source.position) && source.position > 0) {
    details.push(t('remote.queuePosition', { position: source.position }))
  }
  return details.join(' · ')
}

function formatRemoteSessionLine(session, t) {
  if (!session) {
    return ''
  }
  if (session.multiUserEnabled === false) {
    return t('remote.sessionLineSingleUser', { username: session.username })
  }
  return t('remote.sessionLine', { username: session.username, role: session.role })
}

function formatRemoteMessage(message, locale) {
  if (!message) return ''
  return formatPollError(message, locale)
}

function isStoppedByUserMessage(message) {
  return String(message || '').trim() === 'Stopped by user.'
}

function formatRemoteErrors(result, locale) {
  if (result?.errorDetails?.length) {
    return result.errorDetails.map((detail) => formatRemoteMessage(detail, locale))
  }
  return (result?.errors || []).map((error) => formatRemoteMessage(error, locale))
}

export default RemoteApp
