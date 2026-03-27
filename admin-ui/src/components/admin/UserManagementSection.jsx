import { roleLabel } from '../../lib/formatters'
import CreateUserDialog from './CreateUserDialog'
import UserListItem from './UserListItem'
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
  createUserDialogOpen,
  createUserForm,
  createUserLoading,
  duplicateUsername,
  expandedUserId,
  onCloseCreateUserDialog,
  onCollapseToggle,
  onCreateUser,
  onCreateUserFormChange,
  onForcePasswordChange,
  onOpenCreateUserDialog,
  onLoadUserCustomRange,
  onOpenResetPasswordDialog,
  onResetUserPasskeys,
  onToggleExpandUser,
  onToggleUserActive,
  onUpdateUser,
  selectedUserConfig,
  selectedUserLoading,
  session,
  updatingPasskeysResetUserId,
  updatingUserId,
  users,
  sectionLoading = false,
  t,
  locale
}) {
  return (
    <section className="surface-card user-management-panel section-with-corner-toggle" id="user-management-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('users.title')}</div>
          <p className="section-copy">{t('users.copy')}</p>
        </div>
        {!collapsed ? (
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
        users.length > 0 ? (
          <div className="list-stack">
            {users.map((user) => {
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
