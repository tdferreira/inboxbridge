import BridgeCard from './BridgeCard'
import LoadingButton from '../common/LoadingButton'
import './UserBridgesSection.css'

/**
 * Displays the current user's source bridges and the form used to create or
 * edit bridge records stored in PostgreSQL.
 */
function UserBridgesSection({
  bridgeForm,
  bridges,
  collapsed,
  collapseLoading,
  connectingBridgeId,
  deletingBridgeId,
  onBridgeFormChange,
  onCollapseToggle,
  onConnectMicrosoft,
  onDeleteBridge,
  onEditBridge,
  onSaveBridge,
  saveLoading
}) {
  return (
    <section className="surface-card user-bridges-section" id="source-bridges-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">My Source Bridges</div>
          <p className="section-copy">These bridges live in the database, import into your Gmail destination, and keep their secrets encrypted at rest.</p>
        </div>
        <div className="action-row">
          <LoadingButton className="secondary" isLoading={collapseLoading} loadingLabel={collapsed ? 'Expanding…' : 'Collapsing…'} onClick={onCollapseToggle} type="button">
            {collapsed ? 'Expand' : 'Collapse'}
          </LoadingButton>
        </div>
      </div>
      {!collapsed ? (
        <>
          <div className="bridge-grid">
            {bridges.map((bridge) => (
              <BridgeCard
                key={bridge.bridgeId}
                bridge={bridge}
                connectLoading={connectingBridgeId === bridge.bridgeId}
                deleteLoading={deletingBridgeId === bridge.bridgeId}
                onConnectMicrosoft={onConnectMicrosoft}
                onDelete={onDeleteBridge}
                onEdit={onEditBridge}
                showDelete
                showEdit
              />
            ))}
          </div>

          <form className="settings-grid" onSubmit={onSaveBridge}>
            <label><span>Bridge ID</span><input value={bridgeForm.bridgeId} onChange={(event) => onBridgeFormChange((current) => ({ ...current, bridgeId: event.target.value }))} /></label>
            <label><span>Host</span><input value={bridgeForm.host} onChange={(event) => onBridgeFormChange((current) => ({ ...current, host: event.target.value }))} /></label>
            <label><span>Protocol</span><select value={bridgeForm.protocol} onChange={(event) => onBridgeFormChange((current) => ({ ...current, protocol: event.target.value, port: event.target.value === 'IMAP' ? 993 : 995 }))}><option>IMAP</option><option>POP3</option></select></label>
            <label><span>Port</span><input type="number" value={bridgeForm.port} onChange={(event) => onBridgeFormChange((current) => ({ ...current, port: Number(event.target.value) }))} /></label>
            <label><span>Auth Method</span><select value={bridgeForm.authMethod} onChange={(event) => onBridgeFormChange((current) => ({ ...current, authMethod: event.target.value }))}><option>PASSWORD</option><option>OAUTH2</option></select></label>
            <label><span>OAuth Provider</span><select value={bridgeForm.oauthProvider} onChange={(event) => onBridgeFormChange((current) => ({ ...current, oauthProvider: event.target.value }))}><option>NONE</option><option>MICROSOFT</option></select></label>
            <label><span>Username</span><input value={bridgeForm.username} onChange={(event) => onBridgeFormChange((current) => ({ ...current, username: event.target.value }))} /></label>
            <label><span>Password</span><input type="password" value={bridgeForm.password} onChange={(event) => onBridgeFormChange((current) => ({ ...current, password: event.target.value }))} placeholder="Leave blank to keep existing" /></label>
            <label><span>OAuth Refresh Token</span><input type="password" value={bridgeForm.oauthRefreshToken} onChange={(event) => onBridgeFormChange((current) => ({ ...current, oauthRefreshToken: event.target.value }))} placeholder="Optional manual token" /></label>
            <label><span>Folder</span><input value={bridgeForm.folder} onChange={(event) => onBridgeFormChange((current) => ({ ...current, folder: event.target.value }))} /></label>
            <label className="full"><span>Custom Label</span><input value={bridgeForm.customLabel} onChange={(event) => onBridgeFormChange((current) => ({ ...current, customLabel: event.target.value }))} /></label>
            <label className="checkbox-row"><input type="checkbox" checked={bridgeForm.enabled} onChange={(event) => onBridgeFormChange((current) => ({ ...current, enabled: event.target.checked }))} /><span>Enabled</span></label>
            <label className="checkbox-row"><input type="checkbox" checked={bridgeForm.tls} onChange={(event) => onBridgeFormChange((current) => ({ ...current, tls: event.target.checked }))} /><span>TLS only</span></label>
            <label className="checkbox-row"><input type="checkbox" checked={bridgeForm.unreadOnly} onChange={(event) => onBridgeFormChange((current) => ({ ...current, unreadOnly: event.target.checked }))} /><span>Unread only</span></label>
            <div className="full action-row">
              <LoadingButton className="primary" isLoading={saveLoading} loadingLabel="Saving Bridge…" type="submit">
                Save Bridge
              </LoadingButton>
            </div>
          </form>
        </>
      ) : null}
    </section>
  )
}

export default UserBridgesSection
