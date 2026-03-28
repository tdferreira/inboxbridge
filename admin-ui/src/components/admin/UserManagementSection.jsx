import { roleLabel } from '../../lib/formatters'
import CreateUserDialog from './CreateUserDialog'
import UserListItem from './UserListItem'
import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
import './UserManagementSection.css'

function sortUsersByUsername(users = []) {
  return [...users].sort((left, right) => left.username.localeCompare(right.username, undefined, {
    sensitivity: 'base',
    numeric: true
  }))
}

/**
 * Admin workspace for user lifecycle management and non-sensitive cross-user
 * configuration inspection.
 */
function UserManagementSection({
  collapsed,
  collapseLoading,
  createUserDialogOpen,
  createUserForm,
  createUserLoading,
  duplicateUsername,
  expandedUserId,
  onCloseCreateUserDialog,
  onCollapseToggle,
  onCreateUser,
  onCreateUserFormChange,
  onDeleteUser,
  onForcePasswordChange,
  onOpenCreateUserDialog,
  onLoadUserCustomRange,
  onOpenResetPasswordDialog,
  onResetUserPasskeys,
  onToggleMultiUserEnabled,
  onToggleExpandUser,
  onToggleUserActive,
  onUpdateUser,
  selectedUserConfig,
  selectedUserLoading,
  session,
  multiUserEnabled = true,
  modeToggleLoading = false,
  updatingPasskeysResetUserId,
  updatingUserId,
  users,
  sectionLoading = false,
  t,
  locale
}) {
  const sortedUsers = sortUsersByUsername(users)

  return (
    <section className="surface-card user-management-panel section-with-corner-toggle" id="user-management-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('users.title')}</div>
          <p className="section-copy">{t('users.copy')}</p>
        </div>
        {!collapsed && multiUserEnabled ? (
          <div className="panel-header-actions">
            <LoadingButton className="primary" isLoading={false} onClick={onOpenCreateUserDialog} type="button">
              {t('users.create')}
            </LoadingButton>
          </div>
        ) : null}
      </div>
      <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={collapseLoading} expandLabel={t('common.expandSection')} isLoading={collapseLoading} onClick={onCollapseToggle} />
      {sectionLoading ? (
        <div className="section-refresh-indicator" role="status">
          <span aria-hidden="true" className="section-refresh-spinner" />
          {t('common.refreshingSection')}
        </div>
      ) : null}

      {!collapsed ? (
        <>
          <div className="muted-box">
            <strong>{t('users.modeTitle')}</strong><br />
            {multiUserEnabled ? t('users.multiUserEnabledCopy') : t('users.singleUserEnabledCopy')}
            <div className="action-row" style={{ marginTop: '0.85rem' }}>
              <LoadingButton
                className="secondary"
                isLoading={modeToggleLoading}
                onClick={() => onToggleMultiUserEnabled(!multiUserEnabled)}
                type="button"
              >
                {multiUserEnabled ? t('users.switchToSingleUser') : t('users.switchToMultiUser')}
              </LoadingButton>
            </div>
          </div>
          {multiUserEnabled ? (
        sortedUsers.length > 0 ? (
          <div className="list-stack">
            {sortedUsers.map((user) => {
              const isExpanded = expandedUserId === user.id
              const config = isExpanded && selectedUserConfig?.user.id === user.id
                ? selectedUserConfig
                : {
                    user,
                    gmailConfig: {
                      redirectUri: '',
                      sharedClientConfigured: false,
                      clientIdConfigured: false,
                      clientSecretConfigured: false,
                      refreshTokenConfigured: false
                    },
                    pollingSettings: {
                      effectivePollEnabled: false,
                      effectivePollInterval: t('users.notSet'),
                      effectiveFetchWindow: t('users.notSet'),
                      pollEnabledOverride: null,
                      pollIntervalOverride: null,
                      fetchWindowOverride: null
                    },
                    pollingStats: null,
                    passkeys: [],
                    bridges: []
                  }

              return (
                <UserListItem
                  key={user.id}
                  config={config}
                  isExpanded={isExpanded}
                  isLoading={isExpanded && selectedUserLoading}
                  locale={locale}
                  onDeleteUser={onDeleteUser}
                  onForcePasswordChange={onForcePasswordChange}
                  onLoadCustomRange={onLoadUserCustomRange}
                  onOpenResetPasswordDialog={onOpenResetPasswordDialog}
                  onResetUserPasskeys={onResetUserPasskeys}
                  onToggleExpand={() => onToggleExpandUser(user.id)}
                  onToggleUserActive={onToggleUserActive}
                  onUpdateUser={onUpdateUser}
                  session={session}
                  t={t}
                  updatingPasskeysReset={updatingPasskeysResetUserId === user.id}
                  updatingUser={updatingUserId === user.id}
                />
              )
            })}
          </div>
        ) : (
          <div className="muted-box">{t('users.emptyState')}</div>
        )
          ) : (
            <div className="muted-box">{t('users.singleUserModeNote')}</div>
          )}
        </>
      ) : null}

      {createUserDialogOpen ? (
        <CreateUserDialog
          createUserForm={createUserForm}
          createUserLoading={createUserLoading}
          duplicateUsername={duplicateUsername}
          onClose={onCloseCreateUserDialog}
          onFormChange={onCreateUserFormChange}
          onSubmit={onCreateUser}
          roleLabel={(role) => roleLabel(role, locale)}
          t={t}
        />
      ) : null}
    </section>
  )
}

export default UserManagementSection
