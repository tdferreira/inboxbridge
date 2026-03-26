import { authMethodLabel, formatDate, oauthProviderLabel, protocolLabel, roleLabel, tokenStorageLabel } from '../../lib/formatters'
import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
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
  onForcePasswordChange,
  onOpenResetPasswordDialog,
  onResetUserPasskeys,
  onSelectUser,
  onToggleUserActive,
  onUpdateUser,
  session,
  selectedUserConfig,
  selectedUserId,
  selectedUserLoading,
  updatingPasskeysResetUserId,
  updatingUserId,
  users,
  t,
  locale
}) {
  const viewingSelfAdmin = selectedUserConfig?.user.id === session.id && selectedUserConfig?.user.role === 'ADMIN'
  const selectedUserHasPasskeys = (selectedUserConfig?.passkeys?.length || 0) > 0

  return (
    <section className="app-columns">
      <section className="surface-card user-management-panel section-with-corner-toggle" id="user-management-section" tabIndex="-1">
        <div className="panel-header">
          <div>
            <div className="section-title">{t('users.title')}</div>
            <p className="section-copy">{t('users.copy')}</p>
          </div>
        </div>
        <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={collapseLoading} expandLabel={t('common.expandSection')} isLoading={collapseLoading} onClick={onCollapseToggle} />
        {!collapsed ? (
          <>
            <form className="settings-grid" onSubmit={onCreateUser}>
              <label><span>{t('auth.username')}</span><input value={createUserForm.username} onChange={(event) => onCreateUserFormChange((current) => ({ ...current, username: event.target.value }))} /></label>
              <label><span>{t('users.initialPassword')}</span><input type="password" value={createUserForm.password} onChange={(event) => onCreateUserFormChange((current) => ({ ...current, password: event.target.value }))} /></label>
              <label><span>{t('users.role')}</span><select value={createUserForm.role} onChange={(event) => onCreateUserFormChange((current) => ({ ...current, role: event.target.value }))}><option value="USER">{roleLabel('USER', locale)}</option><option value="ADMIN">{roleLabel('ADMIN', locale)}</option></select></label>
              <div className="full action-row">
              <LoadingButton className="primary" isLoading={createUserLoading} loadingLabel={t('users.createLoading')} type="submit">
                {t('users.create')}
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
                  title={t('users.inspectHint', { username: user.username })}
                >
                  <span>{user.username}</span>
                  <span>
                    {roleLabel(user.role, locale)} · {user.approved ? t('users.approved') : t('users.pending')} · {user.active ? t('users.active') : t('users.inactive')} · {t('users.bridges', { count: user.bridgeCount })}
                    {selectedUserId === user.id && selectedUserLoading ? (
                      <span className="user-list-inline-loading">
                        <span aria-hidden="true" className="user-list-inline-spinner" />
                        {t('users.loading')}
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
        <div className="section-title">{t('users.selectedTitle')}</div>
        {!collapsed && selectedUserConfig ? (
          <div className="detail-stack">
            <div className="muted-box">
              <strong>{selectedUserConfig.user.username}</strong><br />
              {roleLabel(selectedUserConfig.user.role, locale)} · {selectedUserConfig.user.approved ? t('users.approved') : t('users.pendingApproval')} · {selectedUserConfig.user.active ? t('users.active') : t('users.inactive')}<br />
              {t('users.gmailConfigured', { value: t(selectedUserConfig.user.gmailConfigured ? 'common.yes' : 'common.no') })} · {t('users.passwordConfigured', { value: t(selectedUserConfig.user.passwordConfigured ? 'common.yes' : 'common.no') })} · {t('users.mustChangePassword', { value: t(selectedUserConfig.user.mustChangePassword ? 'common.yes' : 'common.no') })} · {t('users.passkeys', { value: selectedUserConfig.user.passkeyCount })}
            </div>

            <div className="action-row">
              {!selectedUserConfig.user.approved ? (
                <LoadingButton className="primary" isLoading={updatingUserId === selectedUserConfig.user.id} loadingLabel={t('users.saving')} type="button" onClick={() => onUpdateUser(selectedUserConfig.user.id, { approved: true, active: true }, t('notifications.userApproved', { username: selectedUserConfig.user.username }))}>
                  {t('users.approve')}
                </LoadingButton>
              ) : null}
              <LoadingButton className="secondary" hint={selectedUserConfig.user.active ? t('users.suspendHint') : t('users.reactivateHint')} isLoading={updatingUserId === selectedUserConfig.user.id} loadingLabel={t('users.saving')} type="button" onClick={() => onToggleUserActive(selectedUserConfig.user)}>
                {selectedUserConfig.user.active ? t('users.suspend') : t('users.reactivate')}
              </LoadingButton>
              <LoadingButton className="secondary" disabled={viewingSelfAdmin} hint={selectedUserConfig.user.role === 'ADMIN' ? t('users.makeRegularHint') : t('users.grantAdminHint')} isLoading={updatingUserId === selectedUserConfig.user.id} loadingLabel={t('users.saving')} type="button" onClick={() => onUpdateUser(selectedUserConfig.user.id, { role: selectedUserConfig.user.role === 'ADMIN' ? 'USER' : 'ADMIN' }, t('notifications.userRoleUpdated', { username: selectedUserConfig.user.username }))}>
                {selectedUserConfig.user.role === 'ADMIN' ? t('users.makeRegular') : t('users.grantAdmin')}
              </LoadingButton>
              <LoadingButton className="secondary" hint={t('users.forcePasswordChangeHint')} isLoading={updatingUserId === selectedUserConfig.user.id} loadingLabel={t('users.saving')} type="button" onClick={() => onForcePasswordChange(selectedUserConfig.user)}>
                {t('users.forcePasswordChange')}
              </LoadingButton>
              <LoadingButton className="secondary" hint={t('users.resetPasswordHint')} type="button" onClick={onOpenResetPasswordDialog}>
                {t('users.resetPassword')}
              </LoadingButton>
              <LoadingButton className="danger" disabled={!selectedUserHasPasskeys} hint={t('users.resetPasskeysHint')} isLoading={updatingPasskeysResetUserId === selectedUserConfig.user.id} loadingLabel={t('users.resetPasskeysLoading')} type="button" onClick={onResetUserPasskeys}>
                {t('users.resetPasskeys')}
              </LoadingButton>
            </div>
            {viewingSelfAdmin ? <div className="muted-box">{t('users.selfAdminWarning')}</div> : null}

            <div className="muted-box">
              {t('users.gmailRedirectUri', { value: selectedUserConfig.gmailConfig.redirectUri || t('users.notSet') })}<br />
              {t('users.sharedClientAvailable', { value: t(selectedUserConfig.gmailConfig.sharedClientConfigured ? 'common.yes' : 'common.no') })}<br />
              {t('users.clientIdStored', { value: t(selectedUserConfig.gmailConfig.clientIdConfigured ? 'common.yes' : 'common.no') })}<br />
              {t('users.clientSecretStored', { value: t(selectedUserConfig.gmailConfig.clientSecretConfigured ? 'common.yes' : 'common.no') })}<br />
              {t('users.refreshTokenStored', { value: t(selectedUserConfig.gmailConfig.refreshTokenConfigured ? 'common.yes' : 'common.no') })}
            </div>

            <div className="muted-box">
              {t('users.pollingEnabled', { value: t(selectedUserConfig.pollingSettings.effectivePollEnabled ? 'common.yes' : 'common.no') })}<br />
              {t('users.pollIntervalValue', { value: selectedUserConfig.pollingSettings.effectivePollInterval })}<br />
              {t('users.fetchWindowValue', { value: selectedUserConfig.pollingSettings.effectiveFetchWindow })}<br />
              {t('users.pollingOverrideState', {
                value: selectedUserConfig.pollingSettings.pollEnabledOverride === null
                  && !selectedUserConfig.pollingSettings.pollIntervalOverride
                  && selectedUserConfig.pollingSettings.fetchWindowOverride === null
                  ? t('users.noneApplied')
                  : t('common.yes')
              })}
            </div>

            <div className="list-stack">
              {selectedUserConfig.passkeys.length > 0 ? selectedUserConfig.passkeys.map((passkey) => (
                <div key={passkey.id} className="muted-box">
                  <strong>{passkey.label}</strong><br />
                  {t('users.discoverable', { value: t(passkey.discoverable ? 'common.yes' : 'common.no').toLowerCase() })} · {t('users.backedUp', { value: t(passkey.backedUp ? 'common.yes' : 'common.no').toLowerCase() })}<br />
                  {t('users.created', { value: formatDate(passkey.createdAt, locale) })} · {t('users.lastUsed', { value: formatDate(passkey.lastUsedAt, locale) })}
                </div>
              )) : <div className="muted-box">{t('users.noPasskeys')}</div>}
            </div>

            <div className="list-stack">
              {selectedUserConfig.bridges.map((bridge) => (
                <div key={bridge.bridgeId} className="muted-box">
                  <strong>{bridge.bridgeId}</strong><br />
                  {protocolLabel(bridge.protocol, locale)} {t('users.via')} {authMethodLabel(bridge.authMethod, locale)}{bridge.oauthProvider !== 'NONE' ? ` / ${oauthProviderLabel(bridge.oauthProvider, locale)}` : ''}<br />
                  {bridge.host}:{bridge.port} · {t('bridge.tokenStorage').toLowerCase()} {tokenStorageLabel(bridge.tokenStorageMode, locale)}<br />
                  {t('users.pollIntervalValue', { value: bridge.effectivePollInterval })} · {t('users.fetchWindowValue', { value: bridge.effectiveFetchWindow })}<br />
                  {bridge.pollingState?.cooldownUntil ? `${t('users.cooldownUntil', { value: formatDate(bridge.pollingState.cooldownUntil, locale) })} · ` : ''}{t('users.lastUsed', { value: formatDate(bridge.lastEvent?.finishedAt, locale) })}
                  {bridge.pollingState?.lastFailureReason ? <><br />{t('users.lastFailure', { value: bridge.pollingState.lastFailureReason })}</> : null}
                </div>
              ))}
            </div>
          </div>
        ) : <div className="muted-box">{collapsed ? t('users.expandToManage') : t('users.noUserSelected')}</div>}
      </aside>
    </section>
  )
}

export default UserManagementSection
