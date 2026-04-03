import { useEffect, useRef } from 'react'
import Banner from '../common/Banner'
import LanguageMenuButton from '../common/LanguageMenuButton'
import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'
import PasswordField from '../common/PasswordField'
import RegistrationCaptchaField from './RegistrationCaptchaField'
import './AuthScreen.css'

/**
 * Handles the unauthenticated landing experience and self-registration for
 * admin approval.
 */
function AuthScreen({
  authError,
  language,
  languageOptions = [],
  loginStage = 'username',
  loginLoading,
  loginForm,
  multiUserEnabled,
  notice,
  onLanguageChange = () => {},
  onLogin,
  onLoginChange,
  onPasskeyLogin,
  onCloseRegisterDialog,
  registerLoading,
  registerOpen,
  passkeyLoading,
  passkeysSupported,
  onOpenRegisterDialog,
  onRegister,
  onRegisterChange,
  registerChallenge,
  registerChallengeLoading,
  registerForm,
  t
}) {
  const registerPasswordsMatch = registerForm.password !== '' && registerForm.password === registerForm.confirmPassword
  const registrationReady = registerPasswordsMatch && (!registerChallenge?.enabled || registerForm.captchaToken.trim() !== '')
  const showLanguageSelector = languageOptions.length > 0
  const showCredentialStep = loginStage === 'credentials'
  const loginReady = loginForm.username.trim() !== ''
  const showBrandEyebrow = t('auth.brand') !== t('auth.title')
  const passwordInputRef = useRef(null)

  useEffect(() => {
    if (showCredentialStep) {
      passwordInputRef.current?.focus()
      passwordInputRef.current?.select?.()
    }
  }, [showCredentialStep])

  function renderLanguageSelector(keySuffix) {
    if (!showLanguageSelector) {
      return null
    }
    if (keySuffix === 'login') {
      return (
        <LanguageMenuButton
          ariaLabel={t('preferences.language')}
          className="auth-language-picker auth-login-language-picker"
          currentLanguage={language}
          key={keySuffix}
          onChange={onLanguageChange}
          options={languageOptions}
        />
      )
    }
    return (
      <label className="auth-language-picker" key={keySuffix}>
        <span>{t('preferences.language')}</span>
        <select value={language} onChange={(event) => onLanguageChange(event.target.value)}>
          {languageOptions.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
      </label>
    )
  }

  return (
    <div className="page-shell">
      <main className="auth-screen-card">
        {renderLanguageSelector('login')}
        {showBrandEyebrow ? <div className="eyebrow">{t('auth.brand')}</div> : null}
        <h1>{t('auth.title')}</h1>
        <form className="stack-form" onSubmit={onLogin}>
          <label>
            <span>{t('auth.username')}</span>
            <input
              value={loginForm.username}
              onChange={(event) => onLoginChange((current) => ({ ...current, username: event.target.value }))}
            />
          </label>
          {showCredentialStep ? (
            <PasswordField
              hideLabel={t('common.hideField', { label: t('auth.password') })}
              label={t('auth.password')}
              ref={passwordInputRef}
              value={loginForm.password}
              onChange={(event) => onLoginChange((current) => ({ ...current, password: event.target.value }))}
              showLabel={t('common.showField', { label: t('auth.password') })}
            />
          ) : null}
          <LoadingButton className="primary" disabled={!loginReady} isLoading={loginLoading} loadingLabel={t('auth.signInLoading')} type="submit">
            {t('auth.signIn')}
          </LoadingButton>
          {showCredentialStep ? (
            <LoadingButton className="secondary" disabled={!passkeysSupported || !loginReady} isLoading={passkeyLoading} loadingLabel={t('auth.signInWithPasskeyLoading')} onClick={onPasskeyLogin} type="button">
              {t('auth.signInWithPasskey')}
            </LoadingButton>
          ) : null}
        </form>
        {!passkeysSupported ? <div className="muted-box auth-screen-note">{t('auth.passkeySupport')}</div> : null}

        {multiUserEnabled ? (
          <>
            <div className="muted-box auth-screen-note">
              <strong>{t('auth.needAccessTitle')}</strong><br />
              {t('auth.needAccessBody')}
            </div>
            <LoadingButton className="secondary auth-screen-register-trigger" isLoading={false} onClick={onOpenRegisterDialog} type="button">
              {t('auth.openRegister')}
            </LoadingButton>
          </>
        ) : (
          <div className="muted-box auth-screen-note">{t('auth.singleUserMode')}</div>
        )}

        {authError ? <Banner copyLabel={t('common.copyError')} copyText={authError} dismissLabel={t('common.dismissNotification')} focusLabel={t('common.focusSection')} tone="error">{authError}</Banner> : null}
        {notice ? <Banner tone="success">{notice}</Banner> : null}
      </main>
      {multiUserEnabled && registerOpen ? (
        <ModalDialog
          closeLabel={t('auth.closeRegisterDialog')}
          isDirty={registerForm.username.trim() !== '' || registerForm.password !== '' || registerForm.confirmPassword !== '' || registerForm.captchaToken !== ''}
          onClose={onCloseRegisterDialog}
          size="wide"
          title={t('auth.registerDialogTitle')}
          unsavedChangesMessage={t('common.unsavedChangesConfirm')}
        >
          <p className="section-copy">{t('auth.registerDialogCopy')}</p>
          {renderLanguageSelector('register')}
          <form className="stack-form" onSubmit={onRegister}>
            <label>
              <span>{t('auth.requestedUsername')}</span>
              <input
                value={registerForm.username}
                onChange={(event) => onRegisterChange((current) => ({ ...current, username: event.target.value }))}
              />
            </label>
            <PasswordField
              hideLabel={t('common.hideField', { label: t('auth.requestedPassword') })}
              label={t('auth.requestedPassword')}
              value={registerForm.password}
              onChange={(event) => onRegisterChange((current) => ({ ...current, password: event.target.value }))}
              showLabel={t('common.showField', { label: t('auth.requestedPassword') })}
            />
            <PasswordField
              hideLabel={t('common.hideField', { label: t('auth.repeatRequestedPassword') })}
              label={t('auth.repeatRequestedPassword')}
              value={registerForm.confirmPassword}
              onChange={(event) => onRegisterChange((current) => ({ ...current, confirmPassword: event.target.value }))}
              showLabel={t('common.showField', { label: t('auth.repeatRequestedPassword') })}
            />
            <RegistrationCaptchaField
              onRegisterChange={onRegisterChange}
              registerChallenge={registerChallenge}
              registerChallengeLoading={registerChallengeLoading}
              registerForm={registerForm}
              t={t}
            />
            {!registerPasswordsMatch && registerForm.confirmPassword !== '' ? <div className="auth-screen-hint">{t('auth.repeatPasswordHint')}</div> : null}
            <div className="action-row">
              <LoadingButton className="primary" disabled={!registrationReady || registerChallengeLoading} isLoading={registerLoading} loadingLabel={t('auth.registerLoading')} type="submit">
                {t('auth.register')}
              </LoadingButton>
              <button className="secondary" onClick={onCloseRegisterDialog} type="button">
                {t('common.cancel')}
              </button>
            </div>
          </form>
        </ModalDialog>
      ) : null}
    </div>
  )
}

export default AuthScreen
