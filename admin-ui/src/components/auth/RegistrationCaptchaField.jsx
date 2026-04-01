import { useEffect, useRef, useState } from 'react'
import LoadingButton from '../common/LoadingButton'
import { solveAltchaChallenge } from '../../lib/altcha'

function loadScriptOnce(src) {
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[data-external-script="${src}"]`)
    if (existing) {
      if (existing.dataset.loaded === 'true') {
        resolve()
      } else {
        existing.addEventListener('load', () => resolve(), { once: true })
        existing.addEventListener('error', () => reject(new Error(`Failed to load ${src}`)), { once: true })
      }
      return
    }
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.defer = true
    script.dataset.externalScript = src
    script.addEventListener('load', () => {
      script.dataset.loaded = 'true'
      resolve()
    }, { once: true })
    script.addEventListener('error', () => reject(new Error(`Failed to load ${src}`)), { once: true })
    document.head.appendChild(script)
  })
}

function ExternalCaptchaWidget({
  provider,
  siteKey,
  t,
  token,
  onTokenChange
}) {
  const containerRef = useRef(null)
  const widgetIdRef = useRef(null)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let cancelled = false
    async function mountWidget() {
      setLoadError('')
      widgetIdRef.current = null
      if (!containerRef.current || !siteKey) {
        return
      }
      containerRef.current.innerHTML = ''
      try {
        if (provider === 'TURNSTILE') {
          await loadScriptOnce('https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit')
          if (cancelled || !window.turnstile) return
          widgetIdRef.current = window.turnstile.render(containerRef.current, {
            sitekey: siteKey,
            callback: (value) => onTokenChange(value || ''),
            'expired-callback': () => onTokenChange(''),
            'error-callback': () => {
              onTokenChange('')
              setLoadError(t('auth.externalCaptchaLoadError'))
            }
          })
          return
        }
        if (provider === 'HCAPTCHA') {
          await loadScriptOnce('https://js.hcaptcha.com/1/api.js?render=explicit')
          if (cancelled || !window.hcaptcha) return
          widgetIdRef.current = window.hcaptcha.render(containerRef.current, {
            sitekey: siteKey,
            callback: (value) => onTokenChange(value || ''),
            'expired-callback': () => onTokenChange(''),
            'error-callback': () => {
              onTokenChange('')
              setLoadError(t('auth.externalCaptchaLoadError'))
            }
          })
        }
      } catch {
        if (!cancelled) {
          onTokenChange('')
          setLoadError(t('auth.externalCaptchaLoadError'))
        }
      }
    }
    void mountWidget()
    return () => {
      cancelled = true
      if (provider === 'TURNSTILE' && widgetIdRef.current != null && window.turnstile?.remove) {
        window.turnstile.remove(widgetIdRef.current)
      }
      if (provider === 'HCAPTCHA' && widgetIdRef.current != null && window.hcaptcha?.removeCaptcha) {
        window.hcaptcha.removeCaptcha(widgetIdRef.current)
      }
    }
  }, [onTokenChange, provider, siteKey, t])

  return (
    <div className="auth-captcha-block">
      <div className="auth-screen-hint">{t('auth.externalCaptchaHelp')}</div>
      <div className="auth-external-captcha-frame" ref={containerRef} />
      {token ? <div className="auth-screen-hint">{t('auth.captchaVerified')}</div> : null}
      {loadError ? <div className="auth-screen-hint">{loadError}</div> : null}
    </div>
  )
}

function AltchaCaptcha({
  altchaChallenge,
  solving,
  t,
  token,
  onSolve,
  onReset
}) {
  return (
    <div className="auth-captcha-block">
      <div className="auth-screen-hint">{t('auth.altchaHelp')}</div>
      <div className="muted-box auth-altcha-box">
        <strong>{t('auth.altchaTitle')}</strong><br />
        {t('auth.altchaCopy')}
      </div>
      <div className="action-row">
        <LoadingButton className="secondary" disabled={!altchaChallenge} isLoading={solving} loadingLabel={t('auth.captchaSolving')} onClick={onSolve} type="button">
          {token ? t('auth.captchaReverify') : t('auth.captchaVerify')}
        </LoadingButton>
        {token ? (
          <button className="secondary" onClick={onReset} type="button">
            {t('auth.captchaReset')}
          </button>
        ) : null}
      </div>
      {token ? <div className="auth-screen-hint">{t('auth.captchaVerified')}</div> : null}
    </div>
  )
}

function RegistrationCaptchaField({
  registerChallenge,
  registerChallengeLoading,
  registerForm,
  t,
  onRegisterChange
}) {
  const [solving, setSolving] = useState(false)
  const token = registerForm.captchaToken || ''

  async function handleAltchaSolve() {
    if (!registerChallenge?.altcha) {
      return
    }
    setSolving(true)
    try {
      await new Promise((resolve) => window.setTimeout(resolve, 0))
      const solvedToken = await solveAltchaChallenge(registerChallenge.altcha)
      onRegisterChange((current) => ({ ...current, captchaToken: solvedToken }))
    } finally {
      setSolving(false)
    }
  }

  if (registerChallengeLoading) {
    return <div className="auth-screen-hint">{t('auth.challengeLoading')}</div>
  }
  if (!registerChallenge?.enabled) {
    return null
  }
  if (registerChallenge.provider === 'ALTCHA') {
    return (
      <AltchaCaptcha
        altchaChallenge={registerChallenge.altcha}
        onReset={() => onRegisterChange((current) => ({ ...current, captchaToken: '' }))}
        onSolve={handleAltchaSolve}
        solving={solving}
        t={t}
        token={token}
      />
    )
  }
  if (registerChallenge.provider === 'TURNSTILE' || registerChallenge.provider === 'HCAPTCHA') {
    return (
      <ExternalCaptchaWidget
        onTokenChange={(value) => onRegisterChange((current) => ({ ...current, captchaToken: value }))}
        provider={registerChallenge.provider}
        siteKey={registerChallenge.siteKey}
        t={t}
        token={token}
      />
    )
  }
  return null
}

export default RegistrationCaptchaField
