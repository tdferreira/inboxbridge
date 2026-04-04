import { Suspense, lazy, useEffect, useState } from 'react'
import { authMethodLabel, effectiveEmailAccountStatus, formatDate, formatImportedSizeSummary, formatPollError, formatPollExecutionSummary, oauthProviderLabel, protocolLabel, statusLabel, statusTone, tokenStorageLabel } from '../../lib/formatters'
import { buildSourceEmailAccountTargetId } from '../../lib/sectionTargets'
import { formatLiveProgressLabel, formatLiveProgressSummary, hasDeterminateLiveProgress, liveProgressPercent } from '../../lib/livePollProgress'
import CopyButton from '../common/CopyButton'
import FloatingActionMenu from '../common/FloatingActionMenu'
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

function describePostPollAction(fetcher, t) {
  switch (fetcher.postPollAction) {
    case 'DELETE':
      return t('emailAccount.postPollAction.delete')
    case 'MOVE':
      return t('emailAccount.postPollAction.moveToFolder', { folder: fetcher.postPollTargetFolder || t('users.notSet') })
    default:
      return t('emailAccount.postPollAction.none')
  }
}

function EmailAccountListItem({
  connectLoading = false,
  deleteLoading = false,
  fetcher,
  liveSource = null,
  livePollRunning = false,
  locale,
  onConfigurePolling,
  onConnectOAuth,
  onConnectMicrosoft,
  onDelete,
  onEdit,
  onLoadCustomRange,
  onRunPoll,
  onToggleEnabled,
  onToggleExpand,
  pollLoading = false,
  refreshLoading = false,
  stats = null,
  statsLoading = false,
  toggleEnabledLoading = false,
  viewerUsername = null,
  t
}) {
  const [expanded, setExpanded] = useState(false)
  const isEnvManaged = fetcher.managementSource === 'ENVIRONMENT'
  const oauthConnected = fetcher.authMethod === 'OAUTH2' && fetcher.oauthConnected === true
  const canConnectOAuth = fetcher.canConnectOAuth ?? fetcher.canConnectMicrosoft ?? false
  const handleConnectOAuth = onConnectOAuth || ((emailAccountId) => onConnectMicrosoft?.(emailAccountId))
  const statsAvailable = hasMeaningfulStats(stats)
  const effectiveStatus = effectiveEmailAccountStatus(fetcher)
  const pollActionDisabled = pollLoading || livePollRunning || fetcher.enabled === false || fetcher.canRunPoll === false
  const liveProgressLabel = formatLiveProgressLabel(liveSource, t)
  const liveProgressSummary = formatLiveProgressSummary(liveSource, locale, t)
  const showInlineRunningProgress = hasDeterminateLiveProgress(liveSource)
  const [statsCollapsed, setStatsCollapsed] = useState(!statsAvailable)

  useEffect(() => {
    setStatsCollapsed(!statsAvailable)
  }, [fetcher.emailAccountId, statsAvailable])

  function toggleExpanded() {
    setExpanded((current) => {
      const next = !current
      onToggleExpand?.(fetcher, next)
      return next
    })
  }

  return (
    <article className="surface-card fetcher-list-item" id={buildSourceEmailAccountTargetId(fetcher.emailAccountId)} tabIndex="-1">
      <div className="fetcher-list-item-summary">
        <button className="fetcher-list-item-main" onClick={toggleExpanded} type="button">
          <div>
            <div className="fetcher-list-item-title-row">
              <strong>{fetcher.customLabel || fetcher.emailAccountId}</strong>
              {isEnvManaged ? <span className="status-pill tone-neutral">{t('emailAccounts.envManagedBadge')}</span> : null}
            </div>
            <div className="section-copy">
              {fetcher.emailAccountId} · {protocolLabel(fetcher.protocol, locale)} · {fetcher.host}:{fetcher.port} · {authMethodLabel(fetcher.authMethod, locale)}{fetcher.oauthProvider !== 'NONE' ? ` / ${oauthProviderLabel(fetcher.oauthProvider, locale)}` : ''}
              {refreshLoading ? (
                <span className="user-list-inline-loading">
                  <span aria-hidden="true" className="user-list-inline-spinner" />
                  {t('common.refreshingSection')}
                </span>
              ) : null}
            </div>
          </div>
          {showInlineRunningProgress ? (
            <span
              aria-label={liveProgressLabel}
              aria-valuemax={liveSource.totalMessages}
              aria-valuemin={0}
              aria-valuenow={liveSource.processedMessages}
              className="status-pill tone-neutral status-pill-progress fetcher-running-status"
              role="progressbar"
            >
              <span className="status-pill-progress-fill" style={{ width: `${liveProgressPercent(liveSource)}%` }} />
              <span className="status-pill-progress-copy">{liveProgressSummary}</span>
            </span>
          ) : pollLoading ? (
            <span className="status-pill tone-neutral status-pill-progress status-pill-progress-indeterminate fetcher-running-status">
              <span className="status-pill-progress-fill" />
              <span className="status-pill-progress-copy">
              <span aria-hidden="true" className="section-refresh-spinner" />
              {t('status.running')}
              </span>
            </span>
          ) : (
            <span className={`status-pill ${statusTone(effectiveStatus)}`}>{statusLabel(effectiveStatus, locale)}</span>
          )}
        </button>
        <div className="fetcher-list-item-menu">
          <div className="fetcher-list-item-actions">
            <button
              aria-label={t('emailAccount.runPollNow')}
              className="icon-button fetcher-run-button"
              disabled={pollActionDisabled}
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
            <FloatingActionMenu
              buttonLabel={t('emailAccounts.actions')}
              menuContent={({ closeMenu }) => (
                <>
                  <button className="secondary" disabled={pollActionDisabled} onClick={() => { onRunPoll(fetcher); closeMenu() }} type="button">{t('emailAccount.runPollNow')}</button>
                  <button className="secondary" onClick={() => { onConfigurePolling(fetcher); closeMenu() }} type="button">{t('emailAccount.pollerSettings')}</button>
                  {fetcher.canEdit ? <button className="secondary" onClick={() => { onEdit(fetcher); closeMenu() }} type="button">{t('emailAccount.edit')}</button> : null}
                  {fetcher.canEdit ? (
                    <button
                      className="secondary"
                      disabled={toggleEnabledLoading}
                      onClick={() => { onToggleEnabled?.(fetcher); closeMenu() }}
                      type="button"
                    >
                      {fetcher.enabled === false ? t('emailAccount.enableAction') : t('emailAccount.disableAction')}
                    </button>
                  ) : null}
                  {canConnectOAuth ? (
                    <button
                      className="secondary"
                      disabled={connectLoading}
                      onClick={() => { handleConnectOAuth(fetcher.emailAccountId, fetcher.oauthProvider); closeMenu() }}
                      type="button"
                    >
                      {oauthConnected
                        ? t(fetcher.oauthProvider === 'GOOGLE' ? 'emailAccount.reconnectGoogle' : 'emailAccount.reconnectMicrosoft')
                        : t(fetcher.oauthProvider === 'GOOGLE' ? 'emailAccount.connectGoogle' : 'emailAccount.connectMicrosoft')}
                    </button>
                  ) : null}
                  {fetcher.canDelete ? <button className="danger" disabled={deleteLoading} onClick={() => { onDelete(fetcher.emailAccountId); closeMenu() }} type="button">{t('emailAccount.delete')}</button> : null}
                </>
              )}
            />
          </div>
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
          <dl className="email-account-card-config">
            <div><dt>{t('emailAccount.provider')}</dt><dd>{inferProviderLabel(fetcher, locale, t)}</dd></div>
            <div><dt>{t('emailAccount.host')}</dt><dd>{fetcher.host}:{fetcher.port}</dd></div>
            <div><dt>{t('emailAccount.tls')}</dt><dd>{fetcher.tls ? t('emailAccount.tlsRequired') : t('emailAccount.tlsOff')}</dd></div>
            <div><dt>{t('emailAccount.tokenStorage')}</dt><dd>{tokenStorageLabel(fetcher.tokenStorageMode, locale)}</dd></div>
            <div><dt>{t('emailAccount.totalImported')}</dt><dd>{fetcher.totalImportedMessages}</dd></div>
            <div><dt>{t('emailAccount.lastImport')}</dt><dd>{formatDate(fetcher.lastImportedAt, locale)}</dd></div>
            <div><dt>{t('emailAccount.folder')}</dt><dd>{fetcher.folder || 'INBOX'}</dd></div>
            <div><dt>{t('emailAccount.customLabel')}</dt><dd>{fetcher.customLabel || t('users.notSet')}</dd></div>
            <div><dt>{t('emailAccount.markReadAfterPoll')}</dt><dd>{fetcher.markReadAfterPoll ? t('common.yes') : t('common.no')}</dd></div>
            <div><dt>{t('emailAccount.postPollAction')}</dt><dd>{describePostPollAction(fetcher, t)}</dd></div>
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
              <div className="section-copy">{formatPollExecutionSummary(fetcher.lastEvent, locale, viewerUsername)}</div>
              <div className="email-account-last-result-pills">
                <span className="status-pill tone-neutral">{t('remote.fetched')}: {fetcher.lastEvent.fetched}</span>
                <span className="status-pill tone-neutral">{t('remote.imported')}: {fetcher.lastEvent.imported}</span>
                <span className="status-pill tone-neutral">{t('remote.duplicates')}: {fetcher.lastEvent.duplicates}</span>
                {formatImportedSizeSummary(fetcher.lastEvent, locale) ? (
                  <span className="status-pill tone-neutral">{formatImportedSizeSummary(fetcher.lastEvent, locale)}</span>
                ) : null}
              </div>
              {fetcher.lastEvent.spamJunkMessageCount > 0 ? (
                <div className="section-copy">{t('bridge.spamJunkSummary', { spamJunkCount: fetcher.lastEvent.spamJunkMessageCount })}</div>
              ) : null}
              {fetcher.lastEvent.error ? (
                <div className="email-account-card-error-block">
                  <div className="email-account-card-error">{formatPollError(fetcher.lastEvent.error, locale)}</div>
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
              title={t('pollingStats.sourceTitle', { emailAccountId: fetcher.emailAccountId })}
              variant="source"
            />
          </Suspense>
        </div>
      ) : null}
    </article>
  )
}

export default EmailAccountListItem
