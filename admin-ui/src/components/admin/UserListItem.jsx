import { Suspense, lazy, useEffect, useState } from 'react'
import { authMethodLabel, formatDate, formatPollError, oauthProviderLabel, protocolLabel, roleLabel, tokenStorageLabel } from '../../lib/formatters'
import { translatedNotification } from '../../lib/notifications'
import FloatingActionMenu from '../common/FloatingActionMenu'
import LoadingButton from '../common/LoadingButton'

const PollingStatisticsSection = lazy(() => import('../stats/PollingStatisticsSection'))

function roleUpdateNotification(user) {
  return user.role === 'ADMIN'
    ? translatedNotification('notifications.userAdminRevoked', { username: user.username })
    : translatedNotification('notifications.userAdminGranted', { username: user.username })
}

function hasMeaningfulStats(stats) {
  if (!stats) return false
  return (stats.totalImportedMessages || 0) > 0
    || (stats.manualRuns || 0) > 0
    || (stats.scheduledRuns || 0) > 0
    || (stats.errorPolls || 0) > 0
    || (stats.sourcesWithErrors || 0) > 0
    || (stats.providerBreakdown?.length || 0) > 0
}

function UserListItem({
  config,
  isExpanded,
  isLoading,
  locale,
  onDeleteUser,
  onForcePasswordChange,
  onLoadCustomRange,
  onOpenResetPasswordDialog,
  onResetUserPasskeys,
  onToggleExpand,
  onToggleUserActive,
  onUpdateUser,
  session,
  t,
  updatingPasskeysReset,
  updatingUser
}) {
  const user = {
    id: null,
    username: '',
    role: 'USER',
    approved: false,
    active: false,
    gmailConfigured: false,
    passwordConfigured: false,
    mustChangePassword: false,
    passkeyCount: 0,
    emailAccountCount: 0,
    ...(config?.user || {})
  }
  const destinationConfig = {
    provider: '',
    deliveryMode: '',
    linked: false,
    host: '',
    port: null,
    authMethod: '',
    username: '',
    folder: '',
    ...(config?.destinationConfig || {})
  }
  const pollingSettings = {
    effectivePollEnabled: false,
    effectivePollInterval: t('users.notSet'),
    effectiveFetchWindow: t('users.notSet'),
    pollEnabledOverride: null,
    pollIntervalOverride: null,
    fetchWindowOverride: null,
    ...(config?.pollingSettings || {})
  }
  const passkeys = Array.isArray(config?.passkeys)
    ? config.passkeys.filter(Boolean)
    : []
  const configuredEmailAccounts = Array.isArray(config?.emailAccounts)
    ? config.emailAccounts
    : Array.isArray(config?.bridges)
      ? config.bridges
      : []
  const viewingSelfAdmin = user.id === session?.id && user.role === 'ADMIN'
  const userHasPasskeys = passkeys.length > 0
  const statsAvailable = hasMeaningfulStats(config?.pollingStats)
  const [statsCollapsed, setStatsCollapsed] = useState(!statsAvailable)

  useEffect(() => {
    setStatsCollapsed(!statsAvailable)
  }, [statsAvailable, user.id])

  return (
    <article className={`surface-card user-list-entry ${isExpanded ? 'expanded' : ''}`}>
      <div className="user-list-entry-summary">
        <button className="user-list-entry-main" onClick={onToggleExpand} title={t('users.inspectHint', { username: user.username })} type="button">
          <div>
            <div className="user-list-entry-title-row">
              <strong>{user.username}</strong>
            </div>
            <div className="section-copy">
              {roleLabel(user.role, locale)} · {user.approved ? t('users.approved') : t('users.pending')} · {user.active ? t('users.active') : t('users.inactive')} · {t('users.mailFetchers', { count: user.emailAccountCount })}
              {isLoading ? (
                <span className="user-list-inline-loading">
                  <span aria-hidden="true" className="user-list-inline-spinner" />
                  {t('users.loading')}
                </span>
              ) : null}
            </div>
          </div>
        </button>
        <FloatingActionMenu
          buttonLabel={t('users.actions')}
          className="user-list-entry-menu"
          menuContent={({ closeMenu }) => (
            <>
              {!user.approved ? (
                <button className="secondary" onClick={() => { onUpdateUser(user.id, { approved: true, active: true }, translatedNotification('notifications.userApproved', { username: user.username })); closeMenu() }} type="button">
                  {t('users.approve')}
                </button>
              ) : null}
              <button className="secondary" onClick={() => { onToggleUserActive(user); closeMenu() }} type="button">
                {user.active ? t('users.suspend') : t('users.reactivate')}
              </button>
              {user.role === 'ADMIN' ? (
                <button className="secondary" disabled={viewingSelfAdmin} onClick={() => { onUpdateUser(user.id, { role: 'USER' }, roleUpdateNotification(user)); closeMenu() }} type="button">
                  {t('users.makeRegular')}
                </button>
              ) : null}
              <button className="secondary" onClick={() => { onForcePasswordChange(user); closeMenu() }} type="button">
                {t('users.forcePasswordChange')}
              </button>
              <button className="secondary" onClick={() => { onOpenResetPasswordDialog(user); closeMenu() }} type="button">
                {t('users.resetPassword')}
              </button>
              <button className="danger" disabled={!userHasPasskeys} onClick={() => { onResetUserPasskeys(user); closeMenu() }} type="button">
                {t('users.resetPasskeys')}
              </button>
              <button className="danger" disabled={viewingSelfAdmin} onClick={() => { onDeleteUser(user); closeMenu() }} type="button">
                {t('users.delete')}
              </button>
            </>
          )}
        />
      </div>

      {isExpanded ? (
        <div className="detail-stack">
          <section className="user-detail-section">
            <div className="user-detail-section-title">{t('users.accountSection')}</div>
            <div className="muted-box">
            {roleLabel(user.role, locale)} · {user.approved ? t('users.approved') : t('users.pendingApproval')} · {user.active ? t('users.active') : t('users.inactive')}<br />
            {t('users.gmailConfigured', { value: t(user.gmailConfigured ? 'common.yes' : 'common.no') })} · {t('users.passwordConfigured', { value: t(user.passwordConfigured ? 'common.yes' : 'common.no') })} · {t('users.mustChangePassword', { value: t(user.mustChangePassword ? 'common.yes' : 'common.no') })} · {t('users.passkeys', { value: user.passkeyCount })}
            </div>

            <div className="action-row">
            {!user.approved ? (
              <LoadingButton className="primary" isLoading={updatingUser} loadingLabel={t('users.saving')} type="button" onClick={() => onUpdateUser(user.id, { approved: true, active: true }, translatedNotification('notifications.userApproved', { username: user.username }))}>
                {t('users.approve')}
              </LoadingButton>
            ) : null}
            <LoadingButton className="secondary" disabled={viewingSelfAdmin} hint={user.role === 'ADMIN' ? t('users.makeRegularHint') : t('users.grantAdminHint')} isLoading={updatingUser} loadingLabel={t('users.saving')} type="button" onClick={() => onUpdateUser(user.id, { role: user.role === 'ADMIN' ? 'USER' : 'ADMIN' }, roleUpdateNotification(user))}>
              {user.role === 'ADMIN' ? t('users.makeRegular') : t('users.grantAdmin')}
            </LoadingButton>
            <LoadingButton className="secondary" hint={user.active ? t('users.suspendHint') : t('users.reactivateHint')} isLoading={updatingUser} loadingLabel={t('users.saving')} type="button" onClick={() => onToggleUserActive(user)}>
              {user.active ? t('users.suspend') : t('users.reactivate')}
            </LoadingButton>
            <LoadingButton className="danger" disabled={viewingSelfAdmin} hint={viewingSelfAdmin ? t('users.deleteSelfHint') : t('users.deleteHint')} isLoading={updatingUser} loadingLabel={t('users.confirmLoading')} type="button" onClick={() => onDeleteUser(user)}>
              {t('users.delete')}
            </LoadingButton>
            </div>
            {viewingSelfAdmin ? <div className="muted-box">{t('users.selfAdminWarning')}</div> : null}
          </section>

          <section className="user-detail-section">
            <div className="user-detail-section-title">{t('users.destinationSection')}</div>
            <div className="muted-box">
            {t('users.destinationProvider', { value: destinationConfig.provider || t('users.notSet') })}<br />
            {t('users.destinationMode', { value: destinationConfig.deliveryMode || t('users.notSet') })}<br />
            {t('users.destinationLinked', { value: t(destinationConfig.linked ? 'common.yes' : 'common.no') })}<br />
            {destinationConfig.host ? <>{t('users.destinationHost', { value: destinationConfig.port ? `${destinationConfig.host}:${destinationConfig.port}` : destinationConfig.host })}<br /></> : null}
            {destinationConfig.username ? <>{t('users.destinationUsername', { value: destinationConfig.username })}<br /></> : null}
            {destinationConfig.folder ? <>{t('users.destinationFolder', { value: destinationConfig.folder })}<br /></> : null}
            {t('users.destinationAuth', { value: destinationConfig.authMethod || t('users.notSet') })}
            </div>
          </section>

          <section className="user-detail-section">
            <div className="user-detail-section-title">{t('users.pollingSection')}</div>
            <div className="muted-box">
            {t('users.pollingEnabled', { value: t(pollingSettings.effectivePollEnabled ? 'common.yes' : 'common.no') })}<br />
            {t('users.pollIntervalValue', { value: pollingSettings.effectivePollInterval || t('users.notSet') })}<br />
            {t('users.fetchWindowValue', { value: pollingSettings.effectiveFetchWindow || t('users.notSet') })}<br />
            {t('users.pollingOverrideState', {
              value: pollingSettings.pollEnabledOverride === null
                && !pollingSettings.pollIntervalOverride
                && pollingSettings.fetchWindowOverride === null
                ? t('users.noneApplied')
                : t('common.yes')
            })}
            </div>
          </section>

          <section className="user-detail-section">
            <div className="user-detail-section-title">{t('users.passkeysSection')}</div>
            <div className="list-stack">
            {passkeys.length > 0 ? passkeys.map((passkey, index) => (
              <div
                key={passkey.id || `${user.id || 'user'}-passkey-${index}`}
                className="muted-box"
              >
                <strong>{passkey.label || t('users.notSet')}</strong><br />
                {t('users.discoverable', { value: t(passkey.discoverable ? 'common.yes' : 'common.no').toLowerCase() })} · {t('users.backedUp', { value: t(passkey.backedUp ? 'common.yes' : 'common.no').toLowerCase() })}<br />
                {t('users.created', { value: formatDate(passkey.createdAt, locale) })} · {t('users.lastUsed', { value: formatDate(passkey.lastUsedAt, locale) })}
              </div>
            )) : <div className="muted-box">{t('users.noPasskeys')}</div>}
            </div>
          </section>

          <section className="user-detail-section">
            <div className="user-detail-section-title">{t('users.mailFetchersSection')}</div>
            <div className="list-stack">
            {configuredEmailAccounts.length > 0 ? configuredEmailAccounts.map((emailAccount, index) => {
              const emailAccountId = emailAccount?.emailAccountId || emailAccount?.emailAccountId || `${user.id || 'user'}-email-account-${index}`

              return (
              <div key={emailAccountId} className="muted-box">
                <strong>{emailAccount?.emailAccountId || emailAccount?.emailAccountId || t('users.notSet')}</strong><br />
                {protocolLabel(emailAccount?.protocol, locale)} {t('users.via')} {authMethodLabel(emailAccount?.authMethod, locale)}{emailAccount?.oauthProvider && emailAccount.oauthProvider !== 'NONE' ? ` / ${oauthProviderLabel(emailAccount.oauthProvider, locale)}` : ''}<br />
                {emailAccount?.host || t('users.notSet')}:{emailAccount?.port ?? t('users.notSet')} · {t('users.tokenStorageLabel')} {tokenStorageLabel(emailAccount?.tokenStorageMode, locale)}<br />
                {t('users.pollIntervalValue', { value: emailAccount?.effectivePollInterval || t('users.notSet') })} · {t('users.fetchWindowValue', { value: emailAccount?.effectiveFetchWindow ?? t('users.notSet') })}<br />
                {emailAccount?.pollingState?.cooldownUntil ? `${t('users.cooldownUntil', { value: formatDate(emailAccount.pollingState.cooldownUntil, locale) })} · ` : ''}{t('users.lastUsed', { value: formatDate(emailAccount?.lastEvent?.finishedAt, locale) })}
                {emailAccount?.pollingState?.lastFailureReason ? <><br />{t('users.lastFailure', { value: formatPollError(emailAccount.pollingState.lastFailureReason, locale) })}</> : null}
              </div>
            )}) : <div className="muted-box">{t('users.noMailFetchers')}</div>}
            </div>
          </section>

          <Suspense fallback={<div className="muted-box">{t('common.refreshingSection')}</div>}>
            <PollingStatisticsSection
              collapsed={statsCollapsed}
              copy={t('pollingStats.userDetailCopy')}
              customRangeLoader={onLoadCustomRange ? (range) => onLoadCustomRange(user.id, range) : null}
              id={null}
              onCollapseToggle={() => setStatsCollapsed((current) => !current)}
              sectionLoading={isLoading}
              showCollapseToggle={true}
              stats={config?.pollingStats || null}
              t={t}
              title={t('pollingStats.userDetailTitle', { username: user.username })}
            />
          </Suspense>

          {isLoading ? <div className="muted-box">{t('users.loadingDetails')}</div> : null}
          {updatingPasskeysReset ? <div className="muted-box">{t('users.resetPasskeysLoading')}</div> : null}
        </div>
      ) : null}
    </article>
  )
}

export default UserListItem
