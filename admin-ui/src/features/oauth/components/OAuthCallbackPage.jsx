import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import LoadingScreen from '@/shared/components/LoadingScreen'
import { apiErrorText } from '@/lib/api'
import { ensureLocaleLoaded, normalizeLocale, translate } from '@/lib/i18n'
import './OAuthCallbackPage.css'

const AUTO_RETURN_SECONDS = 5

const PROVIDERS = {
  google: {
    exchangePath: '/api/google-oauth/exchange',
    titleKey: 'oauth.google.title',
    eyebrowKey: 'oauth.google.eyebrow',
    consentDeniedKey: 'oauth.google.accessDenied',
    errorPrefixKey: 'oauth.google.errorPrefix',
    invalidStateKey: 'oauth.google.invalidState',
    missingCallbackKey: 'oauth.google.missingCallback',
    missingCodeKey: 'oauth.google.missingCode',
    exchangeSuccessKey: 'oauth.google.success',
    storageLabelKey: 'oauth.google.storageLabel'
  },
  microsoft: {
    exchangePath: '/api/microsoft-oauth/exchange',
    titleKey: 'oauth.microsoft.title',
    eyebrowKey: 'oauth.microsoft.eyebrow',
    consentDeniedKey: 'oauth.microsoft.accessDenied',
    errorPrefixKey: 'oauth.microsoft.errorPrefix',
    invalidStateKey: 'oauth.microsoft.invalidState',
    missingCallbackKey: 'oauth.microsoft.missingCallback',
    missingCodeKey: 'oauth.microsoft.missingCode',
    exchangeSuccessKey: 'oauth.microsoft.success',
    storageLabelKey: 'oauth.microsoft.storageLabel'
  }
}

function resolvedLocaleFromQuery(searchParams) {
  return normalizeLocale(
    searchParams.get('lang')
    || window.localStorage.getItem('inboxbridge.language')
    || navigator.language
  )
}

function renderedErrorMessage(providerConfig, locale, error, errorDescription) {
  const normalizedError = String(error || '').toLowerCase()
  if (normalizedError === 'access_denied') {
    return translate(locale, providerConfig.consentDeniedKey)
  }
  if (normalizedError === 'invalid_state') {
    return translate(locale, providerConfig.invalidStateKey)
  }
  if (errorDescription) {
    return `${translate(locale, providerConfig.errorPrefixKey)} ${errorDescription}`
  }
  return `${translate(locale, providerConfig.errorPrefixKey)} ${error}`
}

function linesForGoogleResult(payload, locale, providerConfig) {
  return [
    `${translate(locale, providerConfig.storageLabelKey)} ${payload.storedInDatabase ? translate(locale, 'oauth.storage.database') : translate(locale, 'oauth.storage.environment')}`,
    `${translate(locale, 'oauth.result.credentialKey')} ${payload.credentialKey || translate(locale, 'common.unavailable')}`,
    `${translate(locale, 'oauth.result.scope')} ${payload.scope || translate(locale, 'common.unavailable')}`,
    `${translate(locale, 'oauth.result.tokenType')} ${payload.tokenType || translate(locale, 'common.unavailable')}`,
    `${translate(locale, 'oauth.result.expiresAt')} ${payload.accessTokenExpiresAt || translate(locale, 'common.unavailable')}`,
    `${translate(locale, 'oauth.result.nextStep')} ${payload.nextStep || translate(locale, 'common.unavailable')}`
  ]
}

function linesForMicrosoftResult(payload, locale, providerConfig) {
  return [
    `${translate(locale, providerConfig.storageLabelKey)} ${payload.storedInDatabase ? translate(locale, 'oauth.storage.database') : translate(locale, 'oauth.storage.environment')}`,
    `${translate(locale, 'oauth.result.sourceId')} ${payload.sourceId || translate(locale, 'common.unavailable')}`,
    `${translate(locale, 'oauth.result.credentialKey')} ${payload.credentialKey || translate(locale, 'common.unavailable')}`,
    `${translate(locale, 'oauth.result.scope')} ${payload.scope || translate(locale, 'common.unavailable')}`,
    `${translate(locale, 'oauth.result.tokenType')} ${payload.tokenType || translate(locale, 'common.unavailable')}`,
    `${translate(locale, 'oauth.result.expiresAt')} ${payload.accessTokenExpiresAt || translate(locale, 'common.unavailable')}`,
    `${translate(locale, 'oauth.result.nextStep')} ${payload.nextStep || translate(locale, 'common.unavailable')}`
  ]
}

function OAuthCallbackPage({ provider }) {
  const providerConfig = PROVIDERS[provider]
  const location = useLocation()
  const searchParams = new URLSearchParams(location.search)
  const [locale, setLocale] = useState(() => resolvedLocaleFromQuery(searchParams))
  const [localeReady, setLocaleReady] = useState(false)
  const [exchangeStatus, setExchangeStatus] = useState('idle')
  const [statusMessage, setStatusMessage] = useState('')
  const [resultLines, setResultLines] = useState([])
  const [attemptedExchange, setAttemptedExchange] = useState(false)
  const [redirectSecondsRemaining, setRedirectSecondsRemaining] = useState(null)
  const [returnCanceled, setReturnCanceled] = useState(false)

  const code = searchParams.get('code') || ''
  const state = searchParams.get('state') || ''
  const error = searchParams.get('error') || ''
  const errorDescription = searchParams.get('error_description') || ''

  useEffect(() => {
    let active = true
    const nextLocale = resolvedLocaleFromQuery(new URLSearchParams(location.search))
    setLocale(nextLocale)
    setLocaleReady(false)
    void ensureLocaleLoaded(nextLocale).then(() => {
      if (active) {
        setLocaleReady(true)
      }
    })
    return () => {
      active = false
    }
  }, [location.search])

  useEffect(() => {
    if (!localeReady || attemptedExchange || error || !code || !state) {
      return
    }
    setAttemptedExchange(true)
    void exchangeCode()
  }, [attemptedExchange, code, error, localeReady, state])

  useEffect(() => {
    if (exchangeStatus !== 'success' || returnCanceled) {
      return
    }
    setRedirectSecondsRemaining(AUTO_RETURN_SECONDS)
    const countdownId = window.setInterval(() => {
      setRedirectSecondsRemaining((current) => {
        if (current == null || current <= 1) {
          window.clearInterval(countdownId)
          return 0
        }
        return current - 1
      })
    }, 1000)
    const redirectId = window.setTimeout(() => {
      window.location.assign('/')
    }, AUTO_RETURN_SECONDS * 1000)
    return () => {
      window.clearInterval(countdownId)
      window.clearTimeout(redirectId)
    }
  }, [exchangeStatus, returnCanceled])

  async function exchangeCode() {
    setExchangeStatus('loading')
    setStatusMessage(translate(locale, 'oauth.exchangeLoading'))
    try {
      const response = await fetch(providerConfig.exchangePath, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, state })
      })
      if (!response.ok) {
        setExchangeStatus('error')
        setStatusMessage(await apiErrorText(response, translate(locale, 'oauth.exchangeFailed')))
        return
      }
      const payload = await response.json()
      const lines = provider === 'google'
        ? linesForGoogleResult(payload, locale, providerConfig)
        : linesForMicrosoftResult(payload, locale, providerConfig)
      setResultLines(lines)
      setExchangeStatus('success')
      setStatusMessage(translate(locale, providerConfig.exchangeSuccessKey))
    } catch {
      setExchangeStatus('error')
      setStatusMessage(translate(locale, 'oauth.exchangeFailed'))
    }
  }

  if (!providerConfig) {
    return <LoadingScreen label="Loading InboxBridge…" />
  }

  if (!localeReady) {
    return <LoadingScreen label={translate(locale, 'oauth.loading')} />
  }

  const title = translate(locale, providerConfig.titleKey)
  const showRetry = !error && !!code && !!state && exchangeStatus !== 'loading' && exchangeStatus !== 'success'
  const directError = error
    ? renderedErrorMessage(providerConfig, locale, error, errorDescription)
    : (!code && !state
        ? translate(locale, providerConfig.missingCallbackKey)
        : (!code ? translate(locale, providerConfig.missingCodeKey) : ''))
  const visibleMessage = directError || statusMessage

  return (
    <div className="page-shell">
      <main className="oauth-callback-card">
        <p className="oauth-callback-eyebrow">{translate(locale, providerConfig.eyebrowKey)}</p>
        <h1>{title}</h1>
        <p className="oauth-callback-copy">{translate(locale, 'oauth.callbackCopy')}</p>

        <div className="oauth-callback-summary">
          <div>
            <span className="oauth-callback-label">{translate(locale, 'oauth.callbackCode')}</span>
            <code>{code || translate(locale, 'common.unavailable')}</code>
          </div>
          <div>
            <span className="oauth-callback-label">{translate(locale, 'oauth.callbackState')}</span>
            <code>{state || translate(locale, 'common.unavailable')}</code>
          </div>
        </div>

        {visibleMessage ? (
          <p
            className={`oauth-callback-status oauth-callback-status-${exchangeStatus === 'success' ? 'success' : (directError || exchangeStatus === 'error' ? 'error' : 'neutral')}`}
            role="status"
          >
            {visibleMessage}
          </p>
        ) : null}

        {resultLines.length ? (
          <pre className="oauth-callback-result">{resultLines.join('\n')}</pre>
        ) : null}

        <div className="oauth-callback-actions">
          {showRetry ? (
            <button className="primary" onClick={() => void exchangeCode()} type="button">
              {translate(locale, 'oauth.exchangeNow')}
            </button>
          ) : null}
          {exchangeStatus === 'success' && !returnCanceled ? (
            <button className="secondary" onClick={() => setReturnCanceled(true)} type="button">
              {translate(locale, 'oauth.cancelAutoReturn')}
            </button>
          ) : null}
          <button className="secondary" onClick={() => window.location.assign('/')} type="button">
            {translate(locale, 'oauth.returnToInboxBridge')}
          </button>
        </div>

        {exchangeStatus === 'success' && !returnCanceled && redirectSecondsRemaining != null ? (
          <p className="oauth-callback-redirect">
            {translate(locale, 'oauth.autoReturnCountdown', { seconds: redirectSecondsRemaining })}
          </p>
        ) : null}

        {returnCanceled ? (
          <p className="oauth-callback-redirect">
            {translate(locale, 'oauth.autoReturnCanceled')}
          </p>
        ) : null}
      </main>
    </div>
  )
}

export default OAuthCallbackPage
