import { authMethodLabel, formatDate, oauthProviderLabel, protocolLabel, statusLabel, statusTone, tokenStorageLabel, triggerLabel } from '../../lib/formatters'
import CopyButton from '../common/CopyButton'
import LoadingButton from '../common/LoadingButton'
import './BridgeCard.css'

function BridgeCard({
  bridge,
  connectLabel,
  editLabel,
  connectLoading = false,
  deleteLoading = false,
  locale = 'en',
  onConnectMicrosoft,
  onDelete,
  onEdit,
  showDelete = false,
  showEdit = false,
  t
}) {
  const bridgeId = bridge.bridgeId || bridge.id
  const resolvedConnectLabel = connectLabel || t('bridge.connectMicrosoft')
  const resolvedEditLabel = editLabel || t('bridge.edit')

  return (
    <article className="surface-card bridge-card">
      <div className="bridge-card-topline">
        <div>
          <h2>{bridgeId}</h2>
          <p className="section-copy">
            {t('bridge.summaryLine', {
              authMethod: authMethodLabel(bridge.authMethod, locale),
              oauthProvider: bridge.oauthProvider !== 'NONE' ? ` / ${oauthProviderLabel(bridge.oauthProvider, locale)}` : '',
              protocol: protocolLabel(bridge.protocol, locale)
            })}
          </p>
        </div>
        <span className={`status-pill ${statusTone(bridge.lastEvent?.status)}`}>{statusLabel(bridge.lastEvent?.status, locale)}</span>
      </div>

      <dl className="bridge-card-config">
        <div><dt>{t('bridge.host')}</dt><dd>{bridge.host}:{bridge.port}</dd></div>
        <div><dt>{t('bridge.tls')}</dt><dd>{bridge.tls ? t('bridge.tlsRequired') : t('bridge.tlsOff')}</dd></div>
        <div><dt>{t('bridge.tokenStorage')}</dt><dd>{tokenStorageLabel(bridge.tokenStorageMode, locale)}</dd></div>
        <div><dt>{t('bridge.totalImported')}</dt><dd>{bridge.totalImportedMessages}</dd></div>
        <div><dt>{t('bridge.lastImport')}</dt><dd>{formatDate(bridge.lastImportedAt, locale)}</dd></div>
        <div><dt>{t('bridge.folder')}</dt><dd>{bridge.folder || 'INBOX'}</dd></div>
      </dl>

      {bridge.lastEvent ? (
        <div className="event-box">
          <div className="section-copy">{t('bridge.viaTrigger', { time: formatDate(bridge.lastEvent.finishedAt, locale), trigger: triggerLabel(bridge.lastEvent.trigger, locale) })}</div>
          <div className="section-copy">{t('bridge.results', { fetched: bridge.lastEvent.fetched, imported: bridge.lastEvent.imported, duplicates: bridge.lastEvent.duplicates })}</div>
          {bridge.lastEvent.error ? (
            <div className="bridge-card-error-block">
              <div className="bridge-card-error">{bridge.lastEvent.error}</div>
              <CopyButton copiedLabel={t('common.copied')} label={t('common.copyError')} text={bridge.lastEvent.error} />
            </div>
          ) : null}
        </div>
      ) : (
        <div className="muted-box">{t('bridge.noPollActivity')}</div>
      )}

      <div className="action-row">
        {showEdit ? <button className="secondary" type="button" onClick={() => onEdit(bridge)}>{resolvedEditLabel}</button> : null}
        {bridge.oauthProvider === 'MICROSOFT' && bridge.authMethod === 'OAUTH2' ? (
          <LoadingButton className="secondary" isLoading={connectLoading} loadingLabel={t('bridge.startingMicrosoftOAuth')} onClick={() => onConnectMicrosoft(bridgeId)} type="button">
            {resolvedConnectLabel}
          </LoadingButton>
        ) : null}
        {showDelete ? (
          <LoadingButton className="danger" isLoading={deleteLoading} loadingLabel={t('bridge.deleteLoading')} onClick={() => onDelete(bridgeId)} type="button">
            {t('bridge.delete')}
          </LoadingButton>
        ) : null}
      </div>
    </article>
  )
}

export default BridgeCard
