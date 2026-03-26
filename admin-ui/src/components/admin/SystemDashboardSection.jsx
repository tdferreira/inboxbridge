import BridgeCard from '../bridges/BridgeCard'
import LoadingButton from '../common/LoadingButton'
import { tokenStorageLabel } from '../../lib/formatters'
import './SystemDashboardSection.css'

/**
 * Admin-only system view for env-managed bridges, system Gmail status, and the
 * manual poll controls.
 */
function SystemDashboardSection({
  collapsed,
  collapseLoading,
  connectingBridgeId,
  dashboard,
  onCollapseToggle,
  onConnectMicrosoft,
  onConnectSystemGoogle,
  onRunPoll,
  runningPoll,
  systemGoogleLoading
}) {
  return (
    <section className="surface-card system-dashboard-section" id="system-dashboard-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">Environment-Managed System Bridges</div>
          <p className="section-copy">These are the bridges still coming from <code>.env</code>. They remain visible here even though user-managed bridges now live in PostgreSQL.</p>
        </div>
        <div className="action-row">
          <LoadingButton className="primary" isLoading={runningPoll} loadingLabel="Polling…" onClick={onRunPoll}>
            Run Poll Now
          </LoadingButton>
          <LoadingButton className="secondary" isLoading={systemGoogleLoading} loadingLabel="Starting System Gmail OAuth…" onClick={onConnectSystemGoogle}>
            Connect System Gmail OAuth
          </LoadingButton>
          <LoadingButton className="secondary" isLoading={collapseLoading} loadingLabel={collapsed ? 'Expanding…' : 'Collapsing…'} onClick={onCollapseToggle} type="button">
            {collapsed ? 'Expand' : 'Collapse'}
          </LoadingButton>
        </div>
      </div>

      {!collapsed && dashboard ? (
        <>
          <div className="system-dashboard-summary">
            <article className="surface-card metric-card"><span className="metric-label">Configured Bridges</span><strong>{dashboard.overall.configuredSources}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">Imported Messages</span><strong>{dashboard.overall.totalImportedMessages}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">Sources With Errors</span><strong>{dashboard.overall.sourcesWithErrors}</strong></article>
            <article className="surface-card metric-card metric-card-accent"><span className="metric-label">System Gmail</span><strong>{tokenStorageLabel(dashboard.destination.tokenStorageMode)}</strong></article>
          </div>
          <div className="bridge-grid">
            {dashboard.bridges.map((bridge) => (
              <BridgeCard
                key={bridge.id}
                bridge={bridge}
                connectLabel="Reconnect Microsoft OAuth"
                connectLoading={connectingBridgeId === bridge.id}
                onConnectMicrosoft={onConnectMicrosoft}
              />
            ))}
          </div>
        </>
      ) : null}
    </section>
  )
}

export default SystemDashboardSection
