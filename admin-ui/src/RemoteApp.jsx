import { useEffect, useMemo, useState } from 'react'
import Banner from './components/common/Banner'
import ButtonLink from './components/common/ButtonLink'
import DeviceLocationPrompt from './components/common/DeviceLocationPrompt'
import InstallPromptCard from './components/common/InstallPromptCard'
import LoadingButton from './components/common/LoadingButton'
import LoadingScreen from './components/common/LoadingScreen'
import PasswordField from './components/common/PasswordField'
import { normalizePasskeyError, parseGetOptions, passkeysSupported, serializeCredential } from './lib/passkeys'
import {
  RemoteUnauthorizedError,
  remoteControl,
  remoteFinishPasskey,
  remoteLogin,
  remoteLogout,
  remoteRunAllUsersPoll,
  recordRemoteDeviceLocation,
  remoteRunSourcePoll,
  remoteRunUserPoll,
  remoteSession,
  remoteStartPasskey
} from './lib/remoteApi'
import { normalizeLocale, translate } from './lib/i18n'
import { formatDate, formatDurationMeaning, formatPollError } from './lib/formatters'
import { usePwaInstallPrompt } from './lib/usePwaInstallPrompt'
import { useSessionDeviceLocation } from './lib/useSessionDeviceLocation'
import './RemoteApp.css'

const DEFAULT_LOGIN_FORM = {
  username: '',
  password: ''
}

function RemoteApp() {
  const [language] = useState(() => normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language))
  const t = useMemo(() => (key, params) => translate(language, key, params), [language])
  const [session, setSession] = useState(null)
  const [control, setControl] = useState(null)
  const [loading, setLoading] = useState(true)
  const [authError, setAuthError] = useState('')
  const [authNotice, setAuthNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const [loginForm, setLoginForm] = useState(DEFAULT_LOGIN_FORM)
  const [loginLoading, setLoginLoading] = useState(false)
  const [passkeyLoading, setPasskeyLoading] = useState(false)
  const [pollingKey, setPollingKey] = useState('')
  const [lastResult, setLastResult] = useState(null)
  const [expandedSources, setExpandedSources] = useState(() => new Set())
  const [installLoading, setInstallLoading] = useState(false)
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

  async function loadRemoteState() {
    setLoading(true)
    setAuthError('')
    try {
      const sessionPayload = await remoteSession()
      const controlPayload = await remoteControl()
      setSession(sessionPayload)
      setControl(controlPayload)
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

  function clearRemoteSessionState(notice = '') {
    setSession(null)
    setControl(null)
    setLastResult(null)
    setActionError('')
    setAuthError('')
    setAuthNotice(notice)
    setLoginLoading(false)
    setPasskeyLoading(false)
    setExpandedSources(new Set())
  }

  async function handleLogin(event) {
    event.preventDefault()
    setLoginLoading(true)
    setAuthError('')
    setAuthNotice('')
    try {
      const payload = await remoteLogin(loginForm)
      if (payload?.status === 'PASSKEY_REQUIRED') {
        if (!passkeysSupported()) {
          throw new Error(t('errors.passkeyIpHostUnsupported', { host: window.location.hostname || 'this host' }))
        }
        await continuePasskeyLogin(loginForm.username.trim())
        return
      }
      setSession(payload)
      setLoginForm(DEFAULT_LOGIN_FORM)
      const controlPayload = await remoteControl()
      setControl(controlPayload)
    } catch (error) {
      setAuthError(error.message)
    } finally {
      setLoginLoading(false)
    }
  }

  async function continuePasskeyLogin(username = '') {
    if (!passkeysSupported()) {
      setAuthError(t('auth.passkeySupport'))
      return
    }
    setPasskeyLoading(true)
    setAuthError('')
    setAuthNotice('')
    try {
      const challenge = await remoteStartPasskey({ username })
      const credential = await navigator.credentials.get({
        publicKey: parseGetOptions(challenge.publicKeyJson)
      })
      const payload = await remoteFinishPasskey({
        ceremonyId: challenge.ceremonyId,
        credentialJson: serializeCredential(credential)
      })
      setSession(payload)
      setLoginForm(DEFAULT_LOGIN_FORM)
      const controlPayload = await remoteControl()
      setControl(controlPayload)
    } catch (error) {
      setAuthError(normalizePasskeyError(error, t))
    } finally {
      setPasskeyLoading(false)
      setLoginLoading(false)
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
      await refreshControl()
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

  if (loading) {
    return <LoadingScreen label={t('remote.loading')} />
  }

  if (!session) {
    return (
      <div className="remote-shell">
        <main className="remote-auth-card">
          <p className="remote-eyebrow">{t('remote.eyebrow')}</p>
          <h1>{t('remote.heading')}</h1>
          <p className="remote-copy">{t('remote.authCopy')}</p>
          {authNotice ? <Banner tone="warning">{authNotice}</Banner> : null}
          <form className="stack-form" onSubmit={handleLogin}>
            <label>
              <span>{t('auth.username')}</span>
              <input
                value={loginForm.username}
                onChange={(event) => setLoginForm((current) => ({ ...current, username: event.target.value }))}
              />
            </label>
            <PasswordField
              hideLabel={t('common.hideField', { label: t('auth.password') })}
              label={t('auth.password')}
              onChange={(event) => setLoginForm((current) => ({ ...current, password: event.target.value }))}
              showLabel={t('common.showField', { label: t('auth.password') })}
              value={loginForm.password}
            />
            <div className="remote-auth-actions">
              <LoadingButton className="primary" isLoading={loginLoading} loadingLabel={t('auth.signInLoading')} type="submit">
                {t('remote.signIn')}
              </LoadingButton>
              <LoadingButton className="secondary" disabled={!passkeysSupported()} isLoading={passkeyLoading} loadingLabel={t('auth.signInWithPasskeyLoading')} onClick={() => continuePasskeyLogin(loginForm.username.trim())} type="button">
                {t('auth.signInWithPasskey')}
              </LoadingButton>
            </div>
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
          {installPrompt.canPromptInstall ? (
            <InstallPromptCard installLoading={installLoading} onInstall={handleInstallApp} t={t} />
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
              <p className="remote-copy">{t('remote.sessionLine', { username: session.username, role: session.role })}</p>
            </div>
            <div className="remote-hero-actions">
              <LoadingButton className="secondary" isLoading={pollingKey === 'logout'} loadingLabel={t('hero.signOutLoading')} onClick={handleLogout}>
                {t('hero.signOut')}
              </LoadingButton>
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
        {installPrompt.canPromptInstall ? (
          <InstallPromptCard installLoading={installLoading} onInstall={handleInstallApp} t={t} />
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
            <p className="remote-copy">{t('remote.sessionLine', { username: session.username, role: session.role })}</p>
          </div>
          <div className="remote-hero-actions">
            <LoadingButton
              className="primary"
              isLoading={pollingKey === 'user-poll'}
              loadingLabel={t('remote.runMyPollLoading')}
              onClick={() => runPoll('user-poll', remoteRunUserPoll)}
            >
              {t('remote.runMyPoll')}
            </LoadingButton>
            {session.canRunAllUsersPoll ? (
              <LoadingButton
                className="secondary"
                isLoading={pollingKey === 'all-users-poll'}
                loadingLabel={t('remote.runAllUsersLoading')}
                onClick={() => runPoll('all-users-poll', remoteRunAllUsersPoll)}
              >
                {t('remote.runAllUsers')}
              </LoadingButton>
            ) : null}
            <LoadingButton className="secondary" isLoading={pollingKey === 'logout'} loadingLabel={t('hero.signOutLoading')} onClick={handleLogout}>
              {t('hero.signOut')}
            </LoadingButton>
          </div>
        </section>

        <section className="remote-summary-grid">
          <article className="remote-summary-card">
            <span className="remote-summary-label">{t('remote.sourceCount')}</span>
            <strong>{control?.sources?.length || 0}</strong>
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
        {lastResult ? <PollResultCard language={language} result={lastResult} t={t} /> : null}

        <section className="remote-sources-section">
          <div className="remote-section-header">
            <div>
              <h2>{t('remote.sourcesTitle')}</h2>
              <p className="remote-copy">{t('remote.sourcesCopy')}</p>
            </div>
          </div>
          <div className="remote-source-list">
            {(control?.sources || []).map((source) => (
              <article className="remote-source-card" key={source.sourceId}>
                <div className="remote-source-header">
                  <div className="remote-source-heading">
                    <h3>{source.customLabel || source.sourceId}</h3>
                    <p className="remote-source-meta">{source.ownerLabel} · {source.protocol} · {source.host}:{source.port}</p>
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
                      isLoading={pollingKey === `source:${source.sourceId}`}
                      loadingLabel={t('remote.runSourceLoading')}
                      onClick={() => runPoll(`source:${source.sourceId}`, () => remoteRunSourcePoll(source.sourceId))}
                    >
                      {t('remote.runSource')}
                    </LoadingButton>
                  </div>
                </div>
                {expandedSources.has(source.sourceId) ? (
                  <>
                    <dl className="remote-source-details">
                      <div>
                        <dt>{t('remote.sourceId')}</dt>
                        <dd>{source.sourceId}</dd>
                      </div>
                      <div>
                        <dt>{t('remote.owner')}</dt>
                        <dd>{source.ownerLabel}</dd>
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
                      <div>
                        <dt>{t('remote.lastResult')}</dt>
                        <dd>{source.lastEvent?.status || t('common.never')}</dd>
                      </div>
                    </dl>
                    {source.lastEvent?.error ? <Banner tone="warning">{formatRemoteMessage(source.lastEvent.error, language)}</Banner> : null}
                  </>
                ) : null}
              </article>
            ))}
          </div>
        </section>
      </main>
    </div>
  )
}

function PollResultCard({ language, result, t }) {
  const summary = [
    `${t('remote.fetched')}: ${result.fetched}`,
    `${t('remote.imported')}: ${result.imported}`,
    `${t('remote.duplicates')}: ${result.duplicates}`
  ].join(' · ')
  const formattedErrors = formatRemoteErrors(result, language)

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
      ) : (
        <Banner tone="success">{t('remote.lastRunSuccess')}</Banner>
      )}
    </section>
  )
}

function formatDurationDisplay(value, locale) {
  if (!value) return translate(locale, 'common.unavailable')
  return formatDurationMeaning(value, locale) || String(value)
}

function formatRemoteMessage(message, locale) {
  if (!message) return ''
  return formatPollError(message, locale)
}

function formatRemoteErrors(result, locale) {
  if (result?.errorDetails?.length) {
    return result.errorDetails.map((detail) => formatRemoteMessage(detail, locale))
  }
  return (result?.errors || []).map((error) => formatRemoteMessage(error, locale))
}

export default RemoteApp
