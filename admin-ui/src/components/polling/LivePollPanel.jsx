import './LivePollPanel.css'
import SectionCard from '../common/SectionCard'
import { formatLiveProgressLabel, formatLiveProgressSummary, hasDeterminateLiveProgress, liveProgressPercent } from '../../lib/livePollProgress'

function formatCounts(source) {
  if (source.state === 'QUEUED' || source.state === 'RETRY_QUEUED') {
    return null
  }
  return `Fetched ${source.fetched}, imported ${source.imported}, duplicates ${source.duplicates}${source.error ? `, error: ${source.error}` : ''}`
}

function showQueuePosition(source) {
  return (source.state === 'QUEUED' || source.state === 'RETRY_QUEUED') && Number.isInteger(source.position) && source.position > 0
}

function LivePollPanel({
  livePoll,
  locale = 'en',
  onMoveNext,
  onPause,
  onResume,
  onRetry,
  onStop,
  showOwners = false,
  t = (key) => key
}) {
  if (!livePoll?.running) {
    return null
  }

  const canControl = livePoll.viewerCanControl
  const state = livePoll.state || 'RUNNING'
  const isPaused = state === 'PAUSED' || state === 'PAUSING'
  const runningSources = (livePoll.sources || []).filter((source) => source.state === 'RUNNING')
  const runningSourceCopy = runningSources.length === 0
    ? ''
    : runningSources.length === 1
      ? ` · Active source: ${runningSources[0].label}`
      : ` · Active sources: ${runningSources.map((source) => source.label).join(', ')}`
  const copy = `State: ${state}${runningSourceCopy}${livePoll.ownerUsername ? ` · Triggered by ${livePoll.ownerUsername}` : ''}`
  const actions = canControl ? (
    <>
      {isPaused ? (
        <button className="secondary" onClick={onResume} type="button">{t('remote.resume')}</button>
      ) : (
        <button className="secondary" onClick={onPause} type="button">{t('remote.pause')}</button>
      )}
      <button className="danger" onClick={onStop} type="button">{t('remote.stop')}</button>
    </>
  ) : null

  return (
    <SectionCard actions={actions ? <div className="live-poll-panel-actions">{actions}</div> : null} className="live-poll-panel" copy={copy} title="Live Poll Progress">
      <div className="live-poll-source-list">
        {(livePoll.sources || []).map((source) => (
          <article key={source.sourceId} className={`live-poll-source live-poll-source-${String(source.state || '').toLowerCase()}`}>
            <div className="live-poll-source-main">
              <div className="live-poll-source-copy">
                <strong>{source.label || source.sourceId}</strong>
                <div className="section-copy">
                  {source.sourceId}
                  {showOwners && source.ownerUsername ? ` · Owner ${source.ownerUsername}` : ''}
                  {showQueuePosition(source) ? ` · Queue ${source.position}` : ''}
                </div>
                {hasDeterminateLiveProgress(source) ? (
                  <div className="live-poll-source-progress" role="progressbar" aria-label={formatLiveProgressLabel(source, t)} aria-valuemin={0} aria-valuemax={source.totalMessages} aria-valuenow={source.processedMessages}>
                    <div className="section-copy live-poll-source-progress-copy">
                      {formatLiveProgressSummary(source, locale, t)}
                    </div>
                    <div className="live-poll-source-progress-bar">
                      <span style={{ width: `${liveProgressPercent(source)}%` }} />
                    </div>
                  </div>
                ) : null}
                {formatCounts(source) ? <div className="section-copy">{formatCounts(source)}</div> : null}
              </div>
              <span className="status-pill tone-neutral">{source.state}</span>
            </div>
            {canControl && source.actionable ? (
              <div className="live-poll-source-actions">
                {(source.state === 'QUEUED' || source.state === 'RETRY_QUEUED') && source.position > 1 ? (
                  <button className="secondary" onClick={() => onMoveNext(source.sourceId)} type="button">{t('remote.moveNext')}</button>
                ) : null}
                {(source.state === 'FAILED' || source.state === 'COMPLETED' || source.state === 'STOPPED') ? (
                  <button className="secondary" onClick={() => onRetry(source.sourceId)} type="button">{t('remote.retry')}</button>
                ) : null}
              </div>
            ) : null}
          </article>
        ))}
      </div>
    </SectionCard>
  )
}

export default LivePollPanel
