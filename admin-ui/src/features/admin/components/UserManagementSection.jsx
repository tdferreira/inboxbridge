import { roleLabel } from '@/lib/formatters'
import CollapsibleSection from '@/shared/components/CollapsibleSection'
import CreateUserDialog from './CreateUserDialog'
import UserListItem from './UserListItem'
import LoadingButton from '@/shared/components/LoadingButton'
import './UserManagementSection.css'

function sortUsersByUsername(users = []) {
  return [...users].sort((left, right) => left.username.localeCompare(right.username, undefined, {
    sensitivity: 'base',
    numeric: true
  }))
}

function createFallbackUserConfig(user, t) {
  return {
    user,
    destinationConfig: {
      provider: '',
      deliveryMode: '',
      linked: false,
      host: '',
      port: null,
      authMethod: '',
      username: '',
      folder: ''
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
    emailAccounts: []
  }
}

function normalizeExpandedUserConfig(config, user, t) {
  const fallback = createFallbackUserConfig(user, t)
  const emailAccounts = Array.isArray(config?.emailAccounts)
    ? config.emailAccounts.filter(Boolean)
    : Array.isArray(config?.bridges)
      ? config.bridges.filter(Boolean)
      : []

  return {
    ...fallback,
    ...(config || {}),
    user: {
      ...user,
      ...(config?.user || {})
    },
    destinationConfig: {
      ...fallback.destinationConfig,
      ...(config?.destinationConfig || {})
    },
    pollingSettings: {
      ...fallback.pollingSettings,
      ...(config?.pollingSettings || {})
    },
    pollingStats: config?.pollingStats || null,
    passkeys: Array.isArray(config?.passkeys) ? config.passkeys : [],
    emailAccounts
  }
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
    <CollapsibleSection
      actions={!collapsed && multiUserEnabled ? (
            <LoadingButton className="primary" isLoading={false} onClick={onOpenCreateUserDialog} type="button">
              {t('users.create')}
            </LoadingButton>
      ) : null}
      className="user-management-panel"
      collapsed={collapsed}
      collapseLoading={collapseLoading}
      copy={t('users.copy')}
      id="user-management-section"
      onCollapseToggle={onCollapseToggle}
      sectionLoading={sectionLoading}
      t={t}
      title={t('users.title')}
    >
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
                const config = isExpanded && selectedUserConfig?.user?.id === user.id
                  ? normalizeExpandedUserConfig(selectedUserConfig, user, t)
                  : createFallbackUserConfig(user, t)

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
      </>
    </CollapsibleSection>
  )
}

export default UserManagementSection
