import LoadingButton from '../common/LoadingButton'
import CollapsibleSection from '../common/CollapsibleSection'
import LivePollPanel from './LivePollPanel'
import './UserPollingSettingsSection.css'

/**
 * Lets each authenticated user override polling cadence for their own
 * database-backed mail fetchers while keeping deployment defaults visible.
 */
function UserPollingSettingsSection({
  collapsed,
  collapseLoading,
  hasFetchers,
  onCollapseToggle,
  onOpenEditor,
  onPausePoll,
  onMovePollSourceNext,
  onResumePoll,
  onRetryPollSource,
  onRunPoll,
  onStopPoll,
  pollingSettings,
  runningPoll = false,
  livePoll = null,
  sectionLoading = false,
  t
}) {
  const livePollRunning = Boolean(livePoll?.running)
  const canControlLivePoll = Boolean(livePollRunning && livePoll?.viewerCanControl)
  const isPaused = livePoll?.state === 'PAUSED' || livePoll?.state === 'PAUSING'

  return (
    <CollapsibleSection
      actions={
        <>
          {canControlLivePoll ? (
            <>
              {isPaused ? (
                <LoadingButton className="secondary" onClick={onResumePoll} type="button">
                  Resume
                </LoadingButton>
              ) : (
                <LoadingButton className="secondary" onClick={onPausePoll} type="button">
                  Pause
                </LoadingButton>
              )}
              <LoadingButton className="danger" onClick={onStopPoll} type="button">
                Stop
              </LoadingButton>
            </>
          ) : null}
          <LoadingButton className="secondary" onClick={onOpenEditor} type="button">
            {t('userPolling.edit')}
          </LoadingButton>
          <LoadingButton
            className="primary"
            disabled={!hasFetchers}
            isLoading={runningPoll}
            loadingLabel={t('userPolling.runPollLoading')}
            onClick={onRunPoll}
            type="button"
          >
            {t('userPolling.runPoll')}
          </LoadingButton>
        </>
      }
      className="user-polling-section"
      collapsed={collapsed}
      collapseLoading={collapseLoading}
      copy={t('userPolling.copy')}
      id="user-polling-section"
      onCollapseToggle={onCollapseToggle}
      sectionLoading={sectionLoading}
      t={t}
      title={t('userPolling.title')}
    >
      {pollingSettings ? (
        <>
          <LivePollPanel
            livePoll={livePoll}
            onMoveNext={onMovePollSourceNext}
            onPause={onPausePoll}
            onResume={onResumePoll}
            onRetry={onRetryPollSource}
            onStop={onStopPoll}
          />
          {!hasFetchers ? (
            <div className="muted-box user-polling-empty-state">{t('userPolling.noFetchers')}</div>
          ) : null}
          <div className="muted-box full user-polling-summary">
            {t('userPolling.effectivePolling', { value: pollingSettings.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
            {t('userPolling.effectiveInterval', { value: pollingSettings.effectivePollInterval })}<br />
            {t('userPolling.effectiveFetchWindow', { value: pollingSettings.effectiveFetchWindow })}
          </div>
        </>
      ) : null}
    </CollapsibleSection>
  )
}

export default UserPollingSettingsSection
