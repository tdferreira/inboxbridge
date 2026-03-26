import { formatDate, statusTone, tokenStorageLabel } from '../../lib/formatters'
import CopyButton from '../common/CopyButton'
import LoadingButton from '../common/LoadingButton'
import './BridgeCard.css'

function BridgeCard({
  bridge,
  connectLabel = 'Connect Microsoft OAuth',
  editLabel = 'Edit',
  connectLoading = false,
  deleteLoading = false,
  onConnectMicrosoft,
  onDelete,
  onEdit,
  showDelete = false,
  showEdit = false
}) {
  const bridgeId = bridge.bridgeId || bridge.id

  return (
    <article className="surface-card bridge-card">
      <div className="bridge-card-topline">
        <div>
          <h2>{bridgeId}</h2>
          <p className="section-copy">{bridge.protocol} via {bridge.authMethod}{bridge.oauthProvider !== 'NONE' ? ` / ${bridge.oauthProvider}` : ''}</p>
        </div>
        <span className={`status-pill ${statusTone(bridge.lastEvent?.status)}`}>{bridge.lastEvent?.status || 'NOT RUN'}</span>
      </div>

      <dl className="bridge-card-config">
        <div><dt>Host</dt><dd>{bridge.host}:{bridge.port}</dd></div>
        <div><dt>TLS</dt><dd>{bridge.tls ? 'Required' : 'Off'}</dd></div>
        <div><dt>Token storage</dt><dd>{tokenStorageLabel(bridge.tokenStorageMode)}</dd></div>
        <div><dt>Total imported</dt><dd>{bridge.totalImportedMessages}</dd></div>
        <div><dt>Last import</dt><dd>{formatDate(bridge.lastImportedAt)}</dd></div>
        <div><dt>Folder</dt><dd>{bridge.folder || 'INBOX'}</dd></div>
      </dl>

      {bridge.lastEvent ? (
        <div className="event-box">
          <div className="section-copy">{formatDate(bridge.lastEvent.finishedAt)} via {bridge.lastEvent.trigger}</div>
          <div className="section-copy">Fetched {bridge.lastEvent.fetched}, imported {bridge.lastEvent.imported}, duplicates {bridge.lastEvent.duplicates}</div>
          {bridge.lastEvent.error ? (
            <div className="bridge-card-error-block">
              <div className="bridge-card-error">{bridge.lastEvent.error}</div>
              <CopyButton label="Copy Error" text={bridge.lastEvent.error} />
            </div>
          ) : null}
        </div>
      ) : (
        <div className="muted-box">No poll activity recorded yet.</div>
      )}

      <div className="action-row">
        {showEdit ? <button className="secondary" type="button" onClick={() => onEdit(bridge)}>{editLabel}</button> : null}
        {bridge.oauthProvider === 'MICROSOFT' && bridge.authMethod === 'OAUTH2' ? (
          <LoadingButton className="secondary" isLoading={connectLoading} loadingLabel="Starting Microsoft OAuth…" onClick={() => onConnectMicrosoft(bridgeId)} type="button">
            {connectLabel}
          </LoadingButton>
        ) : null}
        {showDelete ? (
          <LoadingButton className="danger" isLoading={deleteLoading} loadingLabel="Deleting Bridge…" onClick={() => onDelete(bridgeId)} type="button">
            Delete
          </LoadingButton>
        ) : null}
      </div>
    </article>
  )
}

export default BridgeCard
