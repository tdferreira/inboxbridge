import LoadingButton from '../common/LoadingButton'
import './HeroPanel.css'

function HeroPanel({ loadingData, onChangePasswordAccess, onRefresh, onSignOut, passwordPanelVisible, refreshLoading, session, signOutLoading }) {
  return (
    <section className="hero-panel">
      <div>
        <div className="eyebrow">InboxBridge Control Plane</div>
        <h1>Multi-user bridge administration with secure OAuth and per-user mail routing.</h1>
        <p className="section-copy">
          Signed in as <strong>{session.username}</strong> ({session.role}). The admin UI runs separately from Quarkus and talks to it over the proxied REST API.
        </p>
      </div>
      <div className="action-row">
        <LoadingButton className="secondary" disabled={loadingData} isLoading={refreshLoading} loadingLabel="Refreshing…" onClick={onRefresh}>
          Refresh
        </LoadingButton>
        <button className="secondary" onClick={onChangePasswordAccess} type="button">
          {passwordPanelVisible ? 'Hide Password' : 'Change Password'}
        </button>
        <LoadingButton className="secondary" isLoading={signOutLoading} loadingLabel="Signing Out…" onClick={onSignOut}>
          Sign out
        </LoadingButton>
      </div>
    </section>
  )
}

export default HeroPanel
