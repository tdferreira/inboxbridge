import { formatDate, tokenStorageLabel } from '../../lib/formatters'
import LoadingButton from '../common/LoadingButton'
import './UserManagementSection.css'

/**
 * Admin workspace for user lifecycle management and non-sensitive cross-user
 * configuration inspection.
 */
function UserManagementSection({
  collapsed,
  collapseLoading,
  createUserForm,
  createUserLoading,
  onCollapseToggle,
  onCreateUser,
  onCreateUserFormChange,
  onSelectUser,
  onUpdateUser,
  selectedUserConfig,
  selectedUserId,
  selectedUserLoading,
  updatingUserId,
  users
}) {
  return (
    <section className="app-columns">
      <section className="surface-card user-management-panel">
        <div className="panel-header">
          <div>
            <div className="section-title">Users</div>
            <p className="section-copy">Admins can see non-sensitive configuration summaries across all users without exposing their actual client secrets or refresh tokens.</p>
          </div>
          <div className="action-row">
            <LoadingButton className="secondary" isLoading={collapseLoading} loadingLabel={collapsed ? 'Expanding…' : 'Collapsing…'} onClick={onCollapseToggle} type="button">
              {collapsed ? 'Expand' : 'Collapse'}
            </LoadingButton>
          </div>
        </div>
        {!collapsed ? (
          <>
            <form className="settings-grid" onSubmit={onCreateUser}>
              <label><span>Username</span><input value={createUserForm.username} onChange={(event) => onCreateUserFormChange((current) => ({ ...current, username: event.target.value }))} /></label>
              <label><span>Initial Password</span><input type="password" value={createUserForm.password} onChange={(event) => onCreateUserFormChange((current) => ({ ...current, password: event.target.value }))} /></label>
              <label><span>Role</span><select value={createUserForm.role} onChange={(event) => onCreateUserFormChange((current) => ({ ...current, role: event.target.value }))}><option value="USER">USER</option><option value="ADMIN">ADMIN</option></select></label>
              <div className="full action-row">
                <LoadingButton className="primary" isLoading={createUserLoading} loadingLabel="Creating User…" type="submit">
                  Create User
                </LoadingButton>
              </div>
            </form>

            <div className="list-stack">
              {users.map((user) => (
                <button
                  key={user.id}
                  className={`user-list-item ${selectedUserId === user.id ? 'selected' : ''}`}
                  type="button"
                  onClick={() => onSelectUser(user.id)}
                >
                  <span>{user.username}</span>
                  <span>
                    {user.role} · {user.approved ? 'approved' : 'pending'} · {user.active ? 'active' : 'inactive'} · {user.bridgeCount} bridges
                    {selectedUserId === user.id && selectedUserLoading ? (
                      <span className="user-list-inline-loading">
                        <span aria-hidden="true" className="user-list-inline-spinner" />
                        Loading…
                      </span>
                    ) : null}
                  </span>
                </button>
              ))}
            </div>
          </>
        ) : null}
      </section>

      <aside className="surface-card user-management-panel">
        <div className="section-title">Selected User Configuration</div>
        {!collapsed && selectedUserConfig ? (
          <div className="detail-stack">
            <div className="muted-box">
              <strong>{selectedUserConfig.user.username}</strong><br />
              {selectedUserConfig.user.role} · {selectedUserConfig.user.approved ? 'approved' : 'pending approval'} · {selectedUserConfig.user.active ? 'active' : 'inactive'}<br />
              Gmail configured: {selectedUserConfig.user.gmailConfigured ? 'Yes' : 'No'} · must change password: {selectedUserConfig.user.mustChangePassword ? 'Yes' : 'No'}
            </div>

            <div className="action-row">
              {!selectedUserConfig.user.approved ? (
                <LoadingButton className="primary" isLoading={updatingUserId === selectedUserConfig.user.id} loadingLabel="Saving User…" type="button" onClick={() => onUpdateUser(selectedUserConfig.user.id, { approved: true, active: true }, `Approved ${selectedUserConfig.user.username}.`)}>
                  Approve user
                </LoadingButton>
              ) : null}
              <LoadingButton className="secondary" isLoading={updatingUserId === selectedUserConfig.user.id} loadingLabel="Saving User…" type="button" onClick={() => onUpdateUser(selectedUserConfig.user.id, { active: !selectedUserConfig.user.active }, `${selectedUserConfig.user.active ? 'Suspended' : 'Reactivated'} ${selectedUserConfig.user.username}.`)}>
                {selectedUserConfig.user.active ? 'Suspend user' : 'Reactivate user'}
              </LoadingButton>
              <LoadingButton className="secondary" isLoading={updatingUserId === selectedUserConfig.user.id} loadingLabel="Saving User…" type="button" onClick={() => onUpdateUser(selectedUserConfig.user.id, { role: selectedUserConfig.user.role === 'ADMIN' ? 'USER' : 'ADMIN' }, `${selectedUserConfig.user.username} role updated.`)}>
                {selectedUserConfig.user.role === 'ADMIN' ? 'Make regular user' : 'Grant admin rights'}
              </LoadingButton>
              <LoadingButton className="secondary" isLoading={updatingUserId === selectedUserConfig.user.id} loadingLabel="Saving User…" type="button" onClick={() => onUpdateUser(selectedUserConfig.user.id, { mustChangePassword: true }, `Forced password reset for ${selectedUserConfig.user.username}.`)}>
                Force password change
              </LoadingButton>
            </div>

            <div className="muted-box">
              Gmail redirect URI: {selectedUserConfig.gmailConfig.redirectUri || 'Not set'}<br />
              Shared client available: {selectedUserConfig.gmailConfig.sharedClientConfigured ? 'Yes' : 'No'}<br />
              Client ID stored: {selectedUserConfig.gmailConfig.clientIdConfigured ? 'Yes' : 'No'}<br />
              Client secret stored: {selectedUserConfig.gmailConfig.clientSecretConfigured ? 'Yes' : 'No'}<br />
              Refresh token stored: {selectedUserConfig.gmailConfig.refreshTokenConfigured ? 'Yes' : 'No'}
            </div>

            <div className="list-stack">
              {selectedUserConfig.bridges.map((bridge) => (
                <div key={bridge.bridgeId} className="muted-box">
                  <strong>{bridge.bridgeId}</strong><br />
                  {bridge.protocol} via {bridge.authMethod}{bridge.oauthProvider !== 'NONE' ? ` / ${bridge.oauthProvider}` : ''}<br />
                  {bridge.host}:{bridge.port} · token storage {tokenStorageLabel(bridge.tokenStorageMode)}<br />
                  last run {formatDate(bridge.lastEvent?.finishedAt)}
                </div>
              ))}
            </div>
          </div>
        ) : <div className="muted-box">{collapsed ? 'Expand this section to manage users and inspect configuration.' : 'Choose a user to inspect.'}</div>}
      </aside>
    </section>
  )
}

export default UserManagementSection
