import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { authMethodLabel, formatDate, oauthProviderLabel, protocolLabel, statusLabel, statusTone, tokenStorageLabel, triggerLabel } from '../../lib/formatters'
import { resolveFloatingMenuPosition } from '../../lib/floatingMenu'
import CopyButton from '../common/CopyButton'
import './BridgeCard.css'
import './FetcherListItem.css'

function FetcherListItem({
  connectLoading = false,
  deleteLoading = false,
  fetcher,
  locale,
  onConfigurePolling,
  onConnectMicrosoft,
  onDelete,
  onEdit,
  onRunPoll,
  onToggleExpand,
  pollLoading = false,
  refreshLoading = false,
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
              {isEnvManaged ? <span className="status-pill tone-neutral">{t('bridges.envManagedBadge')}</span> : null}
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
          <button aria-label={t('bridges.actions')} className="secondary fetcher-menu-button" onClick={() => setMenuOpen((current) => !current)} ref={menuButtonRef} title={t('bridges.actions')} type="button">
            {t('bridges.actionsIcon')}
          </button>
          {menuOpen ? (
            <div className="fetcher-menu" data-placement={menuPlacement} ref={menuPanelRef} style={menuStyle}>
              <button className="secondary" disabled={pollLoading} onClick={() => { onRunPoll(fetcher.bridgeId); setMenuOpen(false) }} type="button">{t('bridge.runPollNow')}</button>
              <button className="secondary" onClick={() => { onConfigurePolling(fetcher); setMenuOpen(false) }} type="button">{t('bridge.pollerSettings')}</button>
              {fetcher.canEdit ? <button className="secondary" onClick={() => { onEdit(fetcher); setMenuOpen(false) }} type="button">{t('bridge.edit')}</button> : null}
              {fetcher.canConnectMicrosoft ? <button className="secondary" disabled={connectLoading} onClick={() => { onConnectMicrosoft(fetcher.bridgeId); setMenuOpen(false) }} type="button">{oauthConnected ? t('bridge.reconnectMicrosoft') : t('bridge.connectMicrosoft')}</button> : null}
              {fetcher.canDelete ? <button className="danger" disabled={deleteLoading} onClick={() => { onDelete(fetcher.bridgeId); setMenuOpen(false) }} type="button">{t('bridge.delete')}</button> : null}
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
          {isEnvManaged ? <div className="muted-box">{t('bridges.envManagedNote')}</div> : null}
          <dl className="bridge-card-config">
            <div><dt>{t('bridge.host')}</dt><dd>{fetcher.host}:{fetcher.port}</dd></div>
            <div><dt>{t('bridge.tls')}</dt><dd>{fetcher.tls ? t('bridge.tlsRequired') : t('bridge.tlsOff')}</dd></div>
            <div><dt>{t('bridge.tokenStorage')}</dt><dd>{tokenStorageLabel(fetcher.tokenStorageMode, locale)}</dd></div>
            <div><dt>{t('bridge.totalImported')}</dt><dd>{fetcher.totalImportedMessages}</dd></div>
            <div><dt>{t('bridge.lastImport')}</dt><dd>{formatDate(fetcher.lastImportedAt, locale)}</dd></div>
            <div><dt>{t('bridges.folder')}</dt><dd>{fetcher.folder || 'INBOX'}</dd></div>
            <div><dt>{t('bridges.customLabel')}</dt><dd>{fetcher.customLabel || t('users.notSet')}</dd></div>
            {fetcher.authMethod === 'OAUTH2' ? (
              <div><dt>{t('bridge.oauthConnected')}</dt><dd>{oauthConnected ? t('common.yes') : t('common.no')}</dd></div>
            ) : null}
            <div><dt>{t('bridge.pollingEnabled')}</dt><dd>{fetcher.effectivePollEnabled ? t('common.yes') : t('common.no')}</dd></div>
            <div><dt>{t('bridge.effectivePollInterval')}</dt><dd>{fetcher.effectivePollInterval}</dd></div>
            <div><dt>{t('bridge.effectiveFetchWindow')}</dt><dd>{fetcher.effectiveFetchWindow}</dd></div>
            <div><dt>{t('bridge.nextPollAt')}</dt><dd>{formatDate(fetcher.pollingState?.nextPollAt, locale)}</dd></div>
            <div><dt>{t('bridge.cooldownUntil')}</dt><dd>{formatDate(fetcher.pollingState?.cooldownUntil, locale)}</dd></div>
            <div><dt>{t('bridge.consecutiveFailures')}</dt><dd>{fetcher.pollingState?.consecutiveFailures || 0}</dd></div>
          </dl>
          {fetcher.pollingState?.lastFailureReason ? (
            <div className="muted-box">
              <strong>{t('bridge.lastFailureReason')}</strong><br />
              {fetcher.pollingState.lastFailureReason}
            </div>
          ) : null}
          {fetcher.lastEvent ? (
            <div className="event-box">
              <div className="section-copy">{t('bridge.viaTrigger', { time: formatDate(fetcher.lastEvent.finishedAt, locale), trigger: triggerLabel(fetcher.lastEvent.trigger, locale) })}</div>
              <div className="section-copy">{t('bridge.results', { fetched: fetcher.lastEvent.fetched, imported: fetcher.lastEvent.imported, duplicates: fetcher.lastEvent.duplicates, spamJunkSuffix: '' })}</div>
              {fetcher.lastEvent.error ? (
                <div className="bridge-card-error-block">
                  <div className="bridge-card-error">{fetcher.lastEvent.error}</div>
                  <CopyButton copiedLabel={t('common.copied')} label={t('common.copyError')} text={fetcher.lastEvent.error} />
                </div>
              ) : null}
            </div>
          ) : (
            <div className="muted-box">{t('bridge.noPollActivity')}</div>
          )}
        </div>
      ) : null}
    </article>
  )
}

export default FetcherListItem
