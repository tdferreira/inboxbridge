import { useState } from 'react'
import { formatDate, statusTone, tokenStorageLabel } from '../../lib/formatters'
import CopyButton from '../common/CopyButton'
import './BridgeCard.css'
import './FetcherListItem.css'

function FetcherListItem({
  connectLoading = false,
  deleteLoading = false,
  fetcher,
  locale,
  onConnectMicrosoft,
  onDelete,
  onEdit,
  t
}) {
  const [expanded, setExpanded] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const isEnvManaged = fetcher.managementSource === 'ENVIRONMENT'

  return (
    <article className="surface-card fetcher-list-item">
      <div className="fetcher-list-item-summary">
        <button className="fetcher-list-item-main" onClick={() => setExpanded((current) => !current)} type="button">
          <div>
            <div className="fetcher-list-item-title-row">
              <strong>{fetcher.customLabel || fetcher.bridgeId}</strong>
              {isEnvManaged ? <span className="status-pill tone-neutral">{t('bridges.envManagedBadge')}</span> : null}
            </div>
            <div className="section-copy">
              {fetcher.bridgeId} · {fetcher.protocol} · {fetcher.host}:{fetcher.port} · {fetcher.authMethod}{fetcher.oauthProvider !== 'NONE' ? ` / ${fetcher.oauthProvider}` : ''}
            </div>
          </div>
          <span className={`status-pill ${statusTone(fetcher.lastEvent?.status)}`}>{fetcher.lastEvent?.status || t('bridge.notRun')}</span>
        </button>
        <div className="fetcher-list-item-menu">
          <button className="secondary fetcher-menu-button" onClick={() => setMenuOpen((current) => !current)} title={t('bridges.actions')} type="button">
            ...
          </button>
          {menuOpen ? (
            <div className="fetcher-menu">
              <button className="secondary" onClick={() => { setExpanded((current) => !current); setMenuOpen(false) }} type="button">
                {expanded ? t('common.collapseSection') : t('common.expandSection')}
              </button>
              {fetcher.canEdit ? <button className="secondary" onClick={() => { onEdit(fetcher); setMenuOpen(false) }} type="button">{t('bridge.edit')}</button> : null}
              {fetcher.canConnectMicrosoft ? <button className="secondary" disabled={connectLoading} onClick={() => { onConnectMicrosoft(fetcher.bridgeId); setMenuOpen(false) }} type="button">{t('bridge.connectMicrosoft')}</button> : null}
              {fetcher.canDelete ? <button className="danger" disabled={deleteLoading} onClick={() => { onDelete(fetcher.bridgeId); setMenuOpen(false) }} type="button">{t('bridge.delete')}</button> : null}
            </div>
          ) : null}
        </div>
      </div>

      {expanded ? (
        <div className="fetcher-list-item-details">
          {isEnvManaged ? <div className="muted-box">{t('bridges.envManagedNote')}</div> : null}
          <dl className="bridge-card-config">
            <div><dt>{t('bridge.host')}</dt><dd>{fetcher.host}:{fetcher.port}</dd></div>
            <div><dt>{t('bridge.tls')}</dt><dd>{fetcher.tls ? t('bridge.tlsRequired') : t('bridge.tlsOff')}</dd></div>
            <div><dt>{t('bridge.tokenStorage')}</dt><dd>{tokenStorageLabel(fetcher.tokenStorageMode, locale)}</dd></div>
            <div><dt>{t('bridge.totalImported')}</dt><dd>{fetcher.totalImportedMessages}</dd></div>
            <div><dt>{t('bridge.lastImport')}</dt><dd>{formatDate(fetcher.lastImportedAt, locale)}</dd></div>
            <div><dt>{t('bridges.folder')}</dt><dd>{fetcher.folder || 'INBOX'}</dd></div>
            <div><dt>{t('bridges.customLabel')}</dt><dd>{fetcher.customLabel || t('users.notSet')}</dd></div>
          </dl>
          {fetcher.lastEvent ? (
            <div className="event-box">
              <div className="section-copy">{t('bridge.viaTrigger', { time: formatDate(fetcher.lastEvent.finishedAt, locale), trigger: fetcher.lastEvent.trigger })}</div>
              <div className="section-copy">{t('bridge.results', { fetched: fetcher.lastEvent.fetched, imported: fetcher.lastEvent.imported, duplicates: fetcher.lastEvent.duplicates })}</div>
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
