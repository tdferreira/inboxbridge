import Banner from '../common/Banner'
import LoadingButton from '../common/LoadingButton'
import './AuthScreen.css'

/**
 * Handles the unauthenticated landing experience, including bootstrap sign-in
 * and self-registration for admin approval.
 */
function AuthScreen({
  authError,
  loginLoading,
  loginForm,
  notice,
  onLogin,
  onLoginChange,
  registerLoading,
  onRegister,
  onRegisterChange,
  registerForm
}) {
  return (
    <div className="page-shell">
      <main className="auth-screen-card">
        <div className="eyebrow">InboxBridge</div>
        <h1>Secure admin sign-in</h1>
        <p className="section-copy">
          The bootstrap account is <code>admin</code> / <code>nimda</code>. Change it immediately after the first login.
        </p>
        <form className="stack-form" onSubmit={onLogin}>
          <label>
            <span>Username</span>
            <input
              value={loginForm.username}
              onChange={(event) => onLoginChange((current) => ({ ...current, username: event.target.value }))}
            />
          </label>
          <label>
            <span>Password</span>
            <input
              type="password"
              value={loginForm.password}
              onChange={(event) => onLoginChange((current) => ({ ...current, password: event.target.value }))}
            />
          </label>
          <LoadingButton className="primary" isLoading={loginLoading} loadingLabel="Signing in…" type="submit">
            Sign in
          </LoadingButton>
        </form>

        <div className="muted-box auth-screen-note">
          <strong>Need access?</strong><br />
          Register below and wait for an admin to approve the account.
        </div>

        <form className="stack-form" onSubmit={onRegister}>
          <label>
            <span>Requested Username</span>
            <input
              value={registerForm.username}
              onChange={(event) => onRegisterChange((current) => ({ ...current, username: event.target.value }))}
            />
          </label>
          <label>
            <span>Password</span>
            <input
              type="password"
              value={registerForm.password}
              onChange={(event) => onRegisterChange((current) => ({ ...current, password: event.target.value }))}
            />
          </label>
          <LoadingButton className="secondary" isLoading={registerLoading} loadingLabel="Registering…" type="submit">
            Register For Approval
          </LoadingButton>
        </form>

        {authError ? <Banner tone="error" copyText={authError}>{authError}</Banner> : null}
        {notice ? <Banner tone="success">{notice}</Banner> : null}
      </main>
    </div>
  )
}

export default AuthScreen
