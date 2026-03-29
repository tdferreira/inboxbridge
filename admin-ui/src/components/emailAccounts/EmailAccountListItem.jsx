import { Suspense, lazy, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { authMethodLabel, formatDate, formatPollError, oauthProviderLabel, protocolLabel, statusLabel, statusTone, tokenStorageLabel, triggerLabel } from '../../lib/formatters'
import { resolveFloatingMenuPosition } from '../../lib/floatingMenu'
import CopyButton from '../common/CopyButton'
import './EmailAccountCard.css'
import './EmailAccountListItem.css'

const PollingStatisticsSection = lazy(() => import('../stats/PollingStatisticsSection'))

function inferProviderLabel(fetcher, locale, t) {
  if (fetcher.oauthProvider && fetcher.oauthProvider !== 'NONE') {
    return oauthProviderLabel(fetcher.oauthProvider, locale)
  }
  const host = (fetcher.host || '').toLowerCase()
  if (host.includes('gmail')) return t('emailAccount.providerGmail')
  if (host.includes('office365') || host.includes('outlook') || host.includes('hotmail') || host.includes('live.com')) {
    return t('emailAccount.providerMicrosoft')
  }
  if (host.includes('yahoo')) return t('emailAccount.providerYahoo')
  if (host === '127.0.0.1' && fetcher.port === 1143) return t('emailAccount.providerProton')
  return fetcher.protocol === 'POP3' ? t('emailAccount.providerGenericPop3') : t('emailAccount.providerGenericImap')
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

function EmailAccountListItem({
  connectLoading = false,
  deleteLoading = false,
  fetcher,
  locale,
  onConfigurePolling,
  onConnectOAuth,
  onConnectMicrosoft,
  onDelete,
  onEdit,
  onLoadCustomRange,
  onRunPoll,
  onToggleExpand,
  pollLoading = false,
  refreshLoading = false,
  stats = null,
  statsLoading = false,
  t
}) {
  const [expanded, setExpanded] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuStyle, setMenuStyle] = useState(null)
  const [menuPlacement, setMenuPlacement] = useState('bottom')
  const menuContainerRef = useRef(null)
  const menuPanelRef = useRef(null)
  const menuButtonRef = useRef(null)
  const isEnvManaged = fetcher.managementSource === 'ENVIRONMENT'
  const oauthConnected = fetcher.authMethod === 'OAUTH2' && fetcher.oauthConnected === true
  const canConnectOAuth = fetcher.canConnectOAuth ?? fetcher.canConnectMicrosoft ?? false
  const handleConnectOAuth = onConnectOAuth || ((bridgeId) => onConnectMicrosoft?.(bridgeId))
  const statsAvailable = hasMeaningfulStats(stats)
  const [statsCollapsed, setStatsCollapsed] = useState(!statsAvailable)

  useEffect(() => {
    setStatsCollapsed(!statsAvailable)
  }, [fetcher.bridgeId, statsAvailable])

  function toggleExpanded() {
    setExpanded((current) => {
      const next = !current
      onToggleExpand?.(fetcher, next)
      return next
    })
  }

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
    <article className="surface-card fetcher-list-item">
      <div className="fetcher-list-item-summary">
        <button className="fetcher-list-item-main" onClick={toggleExpanded} type="button">
          <div>
            <div className="fetcher-list-item-title-row">
              <strong>{fetcher.customLabel || fetcher.bridgeId}</strong>
              {isEnvManaged ? <span className="status-pill tone-neutral">{t('emailAccounts.envManagedBadge')}</span> : null}
            </div>
            <div className="section-copy">
              {fetcher.bridgeId} · {protocolLabel(fetcher.protocol, locale)} · {fetcher.host}:{fetcher.port} · {authMethodLabel(fetcher.authMethod, locale)}{fetcher.oauthProvider !== 'NONE' ? ` / ${oauthProviderLabel(fetcher.oauthProvider, locale)}` : ''}
              {refreshLoading ? (
                <span className="user-list-inline-loading">
                  <span aria-hidden="true" className="user-list-inline-spinner" />
                  {t('common.refreshingSection')}
                </span>
              ) : null}
            </div>
          </div>
          {pollLoading ? (
            <span className="status-pill tone-neutral">
              <span aria-hidden="true" className="section-refresh-spinner" />
              {t('status.running')}
            </span>
          ) : (
            <span className={`status-pill ${statusTone(fetcher.lastEvent?.status)}`}>{statusLabel(fetcher.lastEvent?.status, locale)}</span>
          )}
        </button>
        <div ref={menuContainerRef} className="fetcher-list-item-menu">
          <div className="fetcher-list-item-actions">
            <button
              aria-label={t('emailAccount.runPollNow')}
              className="icon-button fetcher-run-button"
              disabled={pollLoading}
              onClick={() => onRunPoll(fetcher)}
              title={t('emailAccount.runPollNow')}
              type="button"
            >
              {pollLoading ? (
                <span aria-hidden="true" className="section-refresh-spinner" />
              ) : (
                <svg aria-hidden="true" className="fetcher-run-icon" viewBox="0 0 16 16">
                  <path d="M4 3.5a.75.75 0 0 1 1.16-.63l7 4.5a.75.75 0 0 1 0 1.26l-7 4.5A.75.75 0 0 1 4 12.5z" fill="currentColor" />
                </svg>
              )}
            </button>
            <button aria-label={t('emailAccounts.actions')} className="icon-button fetcher-menu-button" onClick={() => setMenuOpen((current) => !current)} ref={menuButtonRef} title={t('emailAccounts.actions')} type="button">
              <span aria-hidden="true" className="menu-icon-hamburger">
                <span />
                <span />
                <span />
              </span>
            </button>
          </div>
          {menuOpen ? (
            <div className="fetcher-menu" data-placement={menuPlacement} ref={menuPanelRef} style={menuStyle}>
              <button className="secondary" disabled={pollLoading} onClick={() => { onRunPoll(fetcher); setMenuOpen(false) }} type="button">{t('emailAccount.runPollNow')}</button>
              <button className="secondary" onClick={() => { onConfigurePolling(fetcher); setMenuOpen(false) }} type="button">{t('emailAccount.pollerSettings')}</button>
              {fetcher.canEdit ? <button className="secondary" onClick={() => { onEdit(fetcher); setMenuOpen(false) }} type="button">{t('emailAccount.edit')}</button> : null}
              {canConnectOAuth ? (
                <button
                  className="secondary"
                  disabled={connectLoading}
                  onClick={() => { handleConnectOAuth(fetcher.bridgeId, fetcher.oauthProvider); setMenuOpen(false) }}
                  type="button"
                >
                  {oauthConnected
                    ? t(fetcher.oauthProvider === 'GOOGLE' ? 'emailAccount.reconnectGoogle' : 'emailAccount.reconnectMicrosoft')
                    : t(fetcher.oauthProvider === 'GOOGLE' ? 'emailAccount.connectGoogle' : 'emailAccount.connectMicrosoft')}
                </button>
              ) : null}
              {fetcher.canDelete ? <button className="danger" disabled={deleteLoading} onClick={() => { onDelete(fetcher.bridgeId); setMenuOpen(false) }} type="button">{t('emailAccount.delete')}</button> : null}
            </div>
          ) : null}
        </div>
      </div>

      {expanded ? (
        <div className="fetcher-list-item-details">
          {refreshLoading ? (
            <div className="section-refresh-indicator" role="status">
              <span aria-hidden="true" className="section-refresh-spinner" />
              {t('common.refreshingSection')}
            </div>
          ) : null}
          {isEnvManaged ? <div className="muted-box">{t('emailAccounts.envManagedNote')}</div> : null}
          <dl className="bridge-card-config">
            <div><dt>{t('emailAccount.provider')}</dt><dd>{inferProviderLabel(fetcher, locale, t)}</dd></div>
            <div><dt>{t('emailAccount.host')}</dt><dd>{fetcher.host}:{fetcher.port}</dd></div>
            <div><dt>{t('emailAccount.tls')}</dt><dd>{fetcher.tls ? t('emailAccount.tlsRequired') : t('emailAccount.tlsOff')}</dd></div>
            <div><dt>{t('emailAccount.tokenStorage')}</dt><dd>{tokenStorageLabel(fetcher.tokenStorageMode, locale)}</dd></div>
            <div><dt>{t('emailAccount.totalImported')}</dt><dd>{fetcher.totalImportedMessages}</dd></div>
            <div><dt>{t('emailAccount.lastImport')}</dt><dd>{formatDate(fetcher.lastImportedAt, locale)}</dd></div>
            <div><dt>{t('emailAccount.folder')}</dt><dd>{fetcher.folder || 'INBOX'}</dd></div>
            <div><dt>{t('emailAccount.customLabel')}</dt><dd>{fetcher.customLabel || t('users.notSet')}</dd></div>
            {fetcher.authMethod === 'OAUTH2' ? (
              <div><dt>{t('emailAccount.oauthConnected')}</dt><dd>{oauthConnected ? t('common.yes') : t('common.no')}</dd></div>
            ) : null}
            <div><dt>{t('emailAccount.pollingEnabled')}</dt><dd>{fetcher.effectivePollEnabled ? t('common.yes') : t('common.no')}</dd></div>
            <div><dt>{t('emailAccount.effectivePollInterval')}</dt><dd>{fetcher.effectivePollInterval}</dd></div>
            <div><dt>{t('emailAccount.effectiveFetchWindow')}</dt><dd>{fetcher.effectiveFetchWindow}</dd></div>
            <div><dt>{t('emailAccount.nextPollAt')}</dt><dd>{formatDate(fetcher.pollingState?.nextPollAt, locale)}</dd></div>
            <div><dt>{t('emailAccount.cooldownUntil')}</dt><dd>{formatDate(fetcher.pollingState?.cooldownUntil, locale)}</dd></div>
            <div><dt>{t('emailAccount.consecutiveFailures')}</dt><dd>{fetcher.pollingState?.consecutiveFailures || 0}</dd></div>
          </dl>
          {fetcher.pollingState?.lastFailureReason ? (
            <div className="muted-box">
              <strong>{t('emailAccount.lastFailureReason')}</strong><br />
              {formatPollError(fetcher.pollingState.lastFailureReason, locale)}
            </div>
          ) : null}
          {fetcher.lastEvent ? (
            <div className="event-box">
              <div className="section-copy">{t('emailAccount.viaTrigger', { time: formatDate(fetcher.lastEvent.finishedAt, locale), trigger: triggerLabel(fetcher.lastEvent.trigger, locale) })}</div>
              <div className="section-copy">{t('emailAccount.results', { fetched: fetcher.lastEvent.fetched, imported: fetcher.lastEvent.imported, duplicates: fetcher.lastEvent.duplicates, spamJunkSuffix: '' })}</div>
              {fetcher.lastEvent.error ? (
                <div className="bridge-card-error-block">
                  <div className="bridge-card-error">{formatPollError(fetcher.lastEvent.error, locale)}</div>
                  <CopyButton copiedLabel={t('common.copied')} label={t('common.copyError')} text={formatPollError(fetcher.lastEvent.error, locale)} />
                </div>
              ) : null}
            </div>
          ) : (
            <div className="muted-box">{t('emailAccount.noPollActivity')}</div>
          )}
          <Suspense fallback={<div className="muted-box">{t('common.refreshingSection')}</div>}>
            <PollingStatisticsSection
              collapsed={statsCollapsed}
              copy={t('pollingStats.sourceCopy')}
              customRangeLoader={onLoadCustomRange ? (range) => onLoadCustomRange(fetcher, range) : null}
              onCollapseToggle={() => setStatsCollapsed((current) => !current)}
              id={null}
              sectionLoading={statsLoading}
              showCollapseToggle={true}
              stats={stats}
              t={t}
              title={t('pollingStats.sourceTitle', { bridgeId: fetcher.bridgeId })}
              variant="source"
            />
          </Suspense>
        </div>
      ) : null}
    </article>
  )
}

export default EmailAccountListItem
