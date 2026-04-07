import LoadingButton from '@/shared/components/LoadingButton'
import CollapsibleSection from '@/shared/components/CollapsibleSection'
import DurationValue from '@/shared/components/DurationValue'
import LivePollPanel from '@/features/polling/components/LivePollPanel'
import './SystemDashboardSection.css'

/**
 * Admin-only system view for poller health, overrides, and manual poll
 * controls.
 */
function SystemDashboardSection({
  collapsed,
  collapseLoading,
  dashboard,
  livePoll,
  onCollapseToggle,
  onMovePollSourceNext,
  onOpenEditor,
  onPausePoll,
  onResumePoll,
  onRetryPollSource,
  onRunPoll,
  onStopPoll,
  runningPoll,
  sectionLoading = false,
  t,
  locale
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
                  {t('remote.resume')}
                </LoadingButton>
              ) : (
                <LoadingButton className="secondary" onClick={onPausePoll} type="button">
                  {t('remote.pause')}
                </LoadingButton>
              )}
              <LoadingButton className="danger" onClick={onStopPoll} type="button">
                {t('remote.stop')}
              </LoadingButton>
            </>
          ) : null}
          <LoadingButton className="secondary" onClick={onOpenEditor} type="button">
            {t('system.edit')}
          </LoadingButton>
          <LoadingButton className="primary" disabled={livePollRunning} isLoading={runningPoll} loadingLabel={t('system.runPollLoading')} onClick={onRunPoll}>
            {t('system.runPoll')}
          </LoadingButton>
        </>
      }
      className="system-dashboard-section"
      collapsed={collapsed}
      collapseLoading={collapseLoading}
      copy={t('system.copy')}
      id="system-dashboard-section"
      onCollapseToggle={onCollapseToggle}
      sectionLoading={sectionLoading}
      t={t}
      title={t('system.title')}
    >
      {dashboard ? (
        <>
          <LivePollPanel
            livePoll={livePoll}
            locale={locale}
            onMoveNext={onMovePollSourceNext}
            onPause={onPausePoll}
            onResume={onResumePoll}
            onRetry={onRetryPollSource}
            onStop={onStopPoll}
            showOwners
            t={t}
          />
          <div className="muted-box full user-polling-summary">
            {t('system.effectivePolling', { value: dashboard.polling.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
            {t('system.effectiveInterval', { value: dashboard.polling.effectivePollInterval })}<br />
            {t('system.effectiveFetchWindow', { value: dashboard.polling.effectiveFetchWindow })}<br />
            {t('system.sourceHostMinSpacing')}: <DurationValue locale={locale} value={dashboard.polling.effectiveSourceHostMinSpacing} /><br />
            {t('system.sourceHostMaxConcurrency')}: {dashboard.polling.effectiveSourceHostMaxConcurrency}<br />
            {t('system.destinationProviderMinSpacing')}: <DurationValue locale={locale} value={dashboard.polling.effectiveDestinationProviderMinSpacing} /><br />
            {t('system.destinationProviderMaxConcurrency')}: {dashboard.polling.effectiveDestinationProviderMaxConcurrency}<br />
            {t('system.throttleLeaseTtl')}: <DurationValue locale={locale} value={dashboard.polling.effectiveThrottleLeaseTtl} /><br />
            {t('system.adaptiveThrottleMaxMultiplier')}: {dashboard.polling.effectiveAdaptiveThrottleMaxMultiplier}<br />
            {t('system.successJitterRatio')}: {dashboard.polling.effectiveSuccessJitterRatio}<br />
            {t('system.maxSuccessJitter')}: <DurationValue locale={locale} value={dashboard.polling.effectiveMaxSuccessJitter} />
          </div>
        </>
      ) : null}
    </CollapsibleSection>
  )
}

export default SystemDashboardSection
