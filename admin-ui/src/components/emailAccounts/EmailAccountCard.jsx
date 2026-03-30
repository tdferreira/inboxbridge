import { authMethodLabel, formatDate, formatPollError, oauthProviderLabel, protocolLabel, statusLabel, statusTone, tokenStorageLabel, triggerLabel } from '../../lib/formatters'
import CopyButton from '../common/CopyButton'
import LoadingButton from '../common/LoadingButton'
import './EmailAccountCard.css'

function EmailAccountCard({
  emailAccount,
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
  const emailAccountId = emailAccount.emailAccountId || emailAccount.id
  const resolvedConnectLabel = connectLabel || t('emailAccount.connectMicrosoft')
  const resolvedEditLabel = editLabel || t('emailAccount.edit')

  return (
    <article className="surface-card email-account-card">
      <div className="email-account-card-topline">
        <div>
          <h2>{emailAccountId}</h2>
          <p className="section-copy">
            {t('emailAccount.summaryLine', {
              authMethod: authMethodLabel(emailAccount.authMethod, locale),
              oauthProvider: emailAccount.oauthProvider !== 'NONE' ? ` / ${oauthProviderLabel(emailAccount.oauthProvider, locale)}` : '',
              protocol: protocolLabel(emailAccount.protocol, locale)
            })}
          </p>
        </div>
        <span className={`status-pill ${statusTone(emailAccount.lastEvent?.status)}`}>{statusLabel(emailAccount.lastEvent?.status, locale)}</span>
      </div>

      <dl className="email-account-card-config">
        <div><dt>{t('emailAccount.host')}</dt><dd>{emailAccount.host}:{emailAccount.port}</dd></div>
        <div><dt>{t('emailAccount.tls')}</dt><dd>{emailAccount.tls ? t('emailAccount.tlsRequired') : t('emailAccount.tlsOff')}</dd></div>
        <div><dt>{t('emailAccount.tokenStorage')}</dt><dd>{tokenStorageLabel(emailAccount.tokenStorageMode, locale)}</dd></div>
        <div><dt>{t('emailAccount.totalImported')}</dt><dd>{emailAccount.totalImportedMessages}</dd></div>
        <div><dt>{t('emailAccount.lastImport')}</dt><dd>{formatDate(emailAccount.lastImportedAt, locale)}</dd></div>
        <div><dt>{t('emailAccount.folder')}</dt><dd>{emailAccount.folder || 'INBOX'}</dd></div>
      </dl>

      {emailAccount.lastEvent ? (
        <div className="event-box">
          <div className="section-copy">{t('emailAccount.viaTrigger', { time: formatDate(emailAccount.lastEvent.finishedAt, locale), trigger: triggerLabel(emailAccount.lastEvent.trigger, locale) })}</div>
          <div className="section-copy">{t('emailAccount.results', { fetched: emailAccount.lastEvent.fetched, imported: emailAccount.lastEvent.imported, duplicates: emailAccount.lastEvent.duplicates, spamJunkSuffix: '' })}</div>
          {emailAccount.lastEvent.error ? (
            <div className="email-account-card-error-block">
              <div className="email-account-card-error">{formatPollError(emailAccount.lastEvent.error, locale)}</div>
              <CopyButton copiedLabel={t('common.copied')} label={t('common.copyError')} text={formatPollError(emailAccount.lastEvent.error, locale)} />
            </div>
          ) : null}
        </div>
      ) : (
        <div className="muted-box">{t('emailAccount.noPollActivity')}</div>
      )}

      <div className="action-row">
        {showEdit ? <button className="secondary" type="button" onClick={() => onEdit(emailAccount)}>{resolvedEditLabel}</button> : null}
        {emailAccount.oauthProvider === 'MICROSOFT' && emailAccount.authMethod === 'OAUTH2' ? (
          <LoadingButton className="secondary" isLoading={connectLoading} loadingLabel={t('emailAccount.startingMicrosoftOAuth')} onClick={() => onConnectMicrosoft(emailAccountId)} type="button">
            {resolvedConnectLabel}
          </LoadingButton>
        ) : null}
        {showDelete ? (
          <LoadingButton className="danger" isLoading={deleteLoading} loadingLabel={t('emailAccount.deleteLoading')} onClick={() => onDelete(emailAccountId)} type="button">
            {t('emailAccount.delete')}
          </LoadingButton>
        ) : null}
      </div>
    </article>
  )
}

export default EmailAccountCard
