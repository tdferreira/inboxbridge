import { Suspense, lazy, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { authMethodLabel, formatDate, formatPollError, oauthProviderLabel, protocolLabel, roleLabel, tokenStorageLabel } from '../../lib/formatters'
import { resolveFloatingMenuPosition } from '../../lib/floatingMenu'
import LoadingButton from '../common/LoadingButton'

const PollingStatisticsSection = lazy(() => import('../stats/PollingStatisticsSection'))

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
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuStyle, setMenuStyle] = useState(null)
  const [menuPlacement, setMenuPlacement] = useState('bottom')
  const menuContainerRef = useRef(null)
  const menuPanelRef = useRef(null)
  const menuButtonRef = useRef(null)
  const user = config.user
  const viewingSelfAdmin = user.id === session.id && user.role === 'ADMIN'
  const userHasPasskeys = (config.passkeys?.length || 0) > 0
  const statsAvailable = hasMeaningfulStats(config.pollingStats)
  const [statsCollapsed, setStatsCollapsed] = useState(!statsAvailable)

  useEffect(() => {
    setStatsCollapsed(!statsAvailable)
  }, [statsAvailable, user.id])

  useEffect(() => {
    if (!menuOpen) {
      return undefined
    }
    function handlePointerDown(event) {
      if (!menuContainerRef.current || menuContainerRef.current.contains(event.target)) {
        return
      }
      setMenuOpen(false)
    }
    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        setMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [menuOpen])

  useLayoutEffect(() => {
    if (!menuOpen || !menuPanelRef.current || !menuButtonRef.current) {
      return undefined
    }

    function updatePosition() {
      const anchorRect = menuButtonRef.current.getBoundingClientRect()
      const margin = 12
      const hasMeasuredAnchor = anchorRect.width > 0 || anchorRect.height > 0
      if (hasMeasuredAnchor && (
        anchorRect.bottom < margin ||
        anchorRect.top > window.innerHeight - margin ||
        anchorRect.right < margin ||
        anchorRect.left > window.innerWidth - margin
      )) {
        setMenuOpen(false)
        return
      }
      const next = resolveFloatingMenuPosition(
        anchorRect,
        menuPanelRef.current.getBoundingClientRect(),
        window.innerWidth,
        window.innerHeight
      )
      setMenuPlacement(next.placement)
      setMenuStyle({
        left: `${next.left}px`,
        top: `${next.top}px`
      })
    }

    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [menuOpen])

  return (
    <article className={`surface-card user-list-entry ${isExpanded ? 'expanded' : ''}`}>
      <div className="user-list-entry-summary">
        <button className="user-list-entry-main" onClick={onToggleExpand} title={t('users.inspectHint', { username: user.username })} type="button">
          <div>
            <div className="user-list-entry-title-row">
              <strong>{user.username}</strong>
            </div>
            <div className="section-copy">
              {roleLabel(user.role, locale)} · {user.approved ? t('users.approved') : t('users.pending')} · {user.active ? t('users.active') : t('users.inactive')} · {t('users.mailFetchers', { count: user.bridgeCount })}
              {isLoading ? (
                <span className="user-list-inline-loading">
                  <span aria-hidden="true" className="user-list-inline-spinner" />
                  {t('users.loading')}
                </span>
              ) : null}
            </div>
          </div>
        </button>
        <div ref={menuContainerRef} className="user-list-entry-menu">
          <button aria-label={t('users.actions')} className="secondary fetcher-menu-button" onClick={() => setMenuOpen((current) => !current)} ref={menuButtonRef} title={t('users.actions')} type="button">
            <span aria-hidden="true" className="menu-icon-hamburger">
              <span />
              <span />
              <span />
            </span>
          </button>
          {menuOpen ? (
            <div className="fetcher-menu" data-placement={menuPlacement} ref={menuPanelRef} style={menuStyle}>
              {!user.approved ? (
                <button className="secondary" onClick={() => { onUpdateUser(user.id, { approved: true, active: true }, t('notifications.userApproved', { username: user.username })); setMenuOpen(false) }} type="button">
                  {t('users.approve')}
                </button>
              ) : null}
              <button className="secondary" onClick={() => { onToggleUserActive(user); setMenuOpen(false) }} type="button">
                {user.active ? t('users.suspend') : t('users.reactivate')}
              </button>
              {user.role === 'ADMIN' ? (
                <button className="secondary" disabled={viewingSelfAdmin} onClick={() => { onUpdateUser(user.id, { role: 'USER' }, t('notifications.userRoleUpdated', { username: user.username })); setMenuOpen(false) }} type="button">
                  {t('users.makeRegular')}
                </button>
              ) : null}
              <button className="secondary" onClick={() => { onForcePasswordChange(user); setMenuOpen(false) }} type="button">
                {t('users.forcePasswordChange')}
              </button>
              <button className="secondary" onClick={() => { onOpenResetPasswordDialog(user); setMenuOpen(false) }} type="button">
                {t('users.resetPassword')}
              </button>
              <button className="danger" disabled={!userHasPasskeys} onClick={() => { onResetUserPasskeys(user); setMenuOpen(false) }} type="button">
                {t('users.resetPasskeys')}
              </button>
            </div>
          ) : null}
        </div>
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
              <LoadingButton className="primary" isLoading={updatingUser} loadingLabel={t('users.saving')} type="button" onClick={() => onUpdateUser(user.id, { approved: true, active: true }, t('notifications.userApproved', { username: user.username }))}>
                {t('users.approve')}
              </LoadingButton>
            ) : null}
            <LoadingButton className="secondary" disabled={viewingSelfAdmin} hint={user.role === 'ADMIN' ? t('users.makeRegularHint') : t('users.grantAdminHint')} isLoading={updatingUser} loadingLabel={t('users.saving')} type="button" onClick={() => onUpdateUser(user.id, { role: user.role === 'ADMIN' ? 'USER' : 'ADMIN' }, t('notifications.userRoleUpdated', { username: user.username }))}>
              {user.role === 'ADMIN' ? t('users.makeRegular') : t('users.grantAdmin')}
            </LoadingButton>
            <LoadingButton className="secondary" hint={user.active ? t('users.suspendHint') : t('users.reactivateHint')} isLoading={updatingUser} loadingLabel={t('users.saving')} type="button" onClick={() => onToggleUserActive(user)}>
              {user.active ? t('users.suspend') : t('users.reactivate')}
            </LoadingButton>
            </div>
            {viewingSelfAdmin ? <div className="muted-box">{t('users.selfAdminWarning')}</div> : null}
          </section>

          <section className="user-detail-section">
            <div className="user-detail-section-title">{t('users.gmailSection')}</div>
            <div className="muted-box">
            {t('users.gmailDestinationUser', { value: config.gmailConfig.destinationUser || t('users.notSet') })}<br />
            {t('users.gmailRedirectUri', { value: config.gmailConfig.redirectUri || t('users.notSet') })}<br />
            {t('users.sharedClientAvailable', { value: t(config.gmailConfig.sharedClientConfigured ? 'common.yes' : 'common.no') })}<br />
            {t('users.clientIdStored', { value: t(config.gmailConfig.clientIdConfigured ? 'common.yes' : 'common.no') })}<br />
            {t('users.clientSecretStored', { value: t(config.gmailConfig.clientSecretConfigured ? 'common.yes' : 'common.no') })}<br />
            {t('users.refreshTokenStored', { value: t(config.gmailConfig.refreshTokenConfigured ? 'common.yes' : 'common.no') })}
            </div>
          </section>

          <section className="user-detail-section">
            <div className="user-detail-section-title">{t('users.pollingSection')}</div>
            <div className="muted-box">
            {t('users.pollingEnabled', { value: t(config.pollingSettings.effectivePollEnabled ? 'common.yes' : 'common.no') })}<br />
            {t('users.pollIntervalValue', { value: config.pollingSettings.effectivePollInterval })}<br />
            {t('users.fetchWindowValue', { value: config.pollingSettings.effectiveFetchWindow })}<br />
            {t('users.pollingOverrideState', {
              value: config.pollingSettings.pollEnabledOverride === null
                && !config.pollingSettings.pollIntervalOverride
                && config.pollingSettings.fetchWindowOverride === null
                ? t('users.noneApplied')
                : t('common.yes')
            })}
            </div>
          </section>

          <section className="user-detail-section">
            <div className="user-detail-section-title">{t('users.passkeysSection')}</div>
            <div className="list-stack">
            {config.passkeys.length > 0 ? config.passkeys.map((passkey) => (
              <div key={passkey.id} className="muted-box">
                <strong>{passkey.label}</strong><br />
                {t('users.discoverable', { value: t(passkey.discoverable ? 'common.yes' : 'common.no').toLowerCase() })} · {t('users.backedUp', { value: t(passkey.backedUp ? 'common.yes' : 'common.no').toLowerCase() })}<br />
                {t('users.created', { value: formatDate(passkey.createdAt, locale) })} · {t('users.lastUsed', { value: formatDate(passkey.lastUsedAt, locale) })}
              </div>
            )) : <div className="muted-box">{t('users.noPasskeys')}</div>}
            </div>
          </section>

          <section className="user-detail-section">
            <div className="user-detail-section-title">{t('users.mailFetchersSection')}</div>
            <div className="list-stack">
            {config.bridges.length > 0 ? config.bridges.map((bridge) => (
              <div key={bridge.bridgeId} className="muted-box">
                <strong>{bridge.bridgeId}</strong><br />
                {protocolLabel(bridge.protocol, locale)} {t('users.via')} {authMethodLabel(bridge.authMethod, locale)}{bridge.oauthProvider !== 'NONE' ? ` / ${oauthProviderLabel(bridge.oauthProvider, locale)}` : ''}<br />
                {bridge.host}:{bridge.port} · {t('users.tokenStorageLabel')} {tokenStorageLabel(bridge.tokenStorageMode, locale)}<br />
                {t('users.pollIntervalValue', { value: bridge.effectivePollInterval })} · {t('users.fetchWindowValue', { value: bridge.effectiveFetchWindow })}<br />
                {bridge.pollingState?.cooldownUntil ? `${t('users.cooldownUntil', { value: formatDate(bridge.pollingState.cooldownUntil, locale) })} · ` : ''}{t('users.lastUsed', { value: formatDate(bridge.lastEvent?.finishedAt, locale) })}
                {bridge.pollingState?.lastFailureReason ? <><br />{t('users.lastFailure', { value: formatPollError(bridge.pollingState.lastFailureReason, locale) })}</> : null}
              </div>
            )) : <div className="muted-box">{t('users.noMailFetchers')}</div>}
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
              stats={config.pollingStats || null}
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
