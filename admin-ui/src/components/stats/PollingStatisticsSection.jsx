import CollapsibleSection from '../common/CollapsibleSection'
import ImportTimelineChart from '../common/ImportTimelineChart'
import { detectScheduledRunAnomaly } from '../../lib/pollingStatsAlerts'
import './PollingStatisticsSection.css'

function formatDuration(milliseconds, t) {
  if (!milliseconds) return t('pollingStats.notEnoughData')
  if (milliseconds < 1000) return `${milliseconds} ms`
  const seconds = milliseconds / 1000
  if (seconds < 60) return `${seconds.toFixed(seconds >= 10 ? 0 : 1)} s`
  const minutes = seconds / 60
  return `${minutes.toFixed(minutes >= 10 ? 0 : 1)} min`
}

function translateProviderLabel(entry, t) {
  const providerKey = String(entry?.key || '').trim().toLowerCase()
  const providerLabelKeys = {
    gmail: 'emailAccount.providerGmail',
    microsoft: 'emailAccount.providerOutlook',
    outlook: 'emailAccount.providerOutlook',
    yahoo: 'emailAccount.providerYahoo',
    'yahoo-mail': 'emailAccount.providerYahoo',
    proton: 'emailAccount.providerProton',
    'proton-bridge': 'emailAccount.providerProton',
    'generic-imap': 'emailAccount.providerGenericImap',
    'generic-pop3': 'emailAccount.providerGenericPop3'
  }
  return providerLabelKeys[providerKey] ? t(providerLabelKeys[providerKey]) : entry?.label
}

function PollingStatisticsSection({
  attentionActive = false,
  collapsed = false,
  collapseLoading = false,
  customRangeLoader = null,
  id,
  locale = 'en',
  scheduledRunAlertInterval = null,
  scheduledRunAlertSourceCount = 0,
  title,
  copy,
  onCollapseToggle,
  stats,
  sectionLoading = false,
  showCollapseToggle = true,
  variant = 'user',
  t
}) {
  const importSeries = [
    { key: 'imports', label: t('pollingStats.seriesImports'), timelines: stats?.importTimelines || {} },
    { key: 'duplicates', label: t('pollingStats.seriesDuplicates'), timelines: stats?.duplicateTimelines || {} },
    { key: 'errors', label: t('pollingStats.seriesErrors'), timelines: stats?.errorTimelines || {} }
  ]
  const runSeries = [
    { key: 'manualRuns', label: t('pollingStats.manualRuns'), timelines: stats?.manualRunTimelines || {} },
    { key: 'scheduledRuns', label: t('pollingStats.scheduledRuns'), timelines: stats?.scheduledRunTimelines || {} }
  ]
  const isSourceVariant = variant === 'source'
  const scheduledRunAnomaly = detectScheduledRunAnomaly(stats, scheduledRunAlertInterval, scheduledRunAlertSourceCount)
  const runAnomalyMarkers = scheduledRunAnomaly
    ? [{
        bucketLabel: scheduledRunAnomaly.bucketLabel,
        message: t('pollingStats.runAnomalyCopy', {
          bucket: scheduledRunAnomaly.bucketLabel,
          observed: scheduledRunAnomaly.observedRuns,
          expected: scheduledRunAnomaly.expectedRunsPerHour,
          interval: scheduledRunAlertInterval,
          sourceCount: scheduledRunAnomaly.sourceCount
        }),
        seriesKey: 'scheduledRuns'
      }]
    : []
  const sectionClassName = `polling-statistics-section${scheduledRunAnomaly?.warningVisible ? ' polling-statistics-section-alerting' : ''}${attentionActive && scheduledRunAnomaly?.warningVisible ? ' polling-statistics-section-attention' : ''}`

  return (
    <CollapsibleSection
      className={sectionClassName}
      collapsed={collapsed}
      collapseLoading={collapseLoading}
      copy={copy}
      id={id}
      onCollapseToggle={onCollapseToggle}
      sectionLoading={sectionLoading}
      showCollapseToggle={showCollapseToggle}
      t={t}
      title={title}
    >
      {stats ? (
        <>
          {scheduledRunAnomaly?.warningVisible ? (
            <div className="polling-statistics-alert" role="alert">
              <strong>{t('pollingStats.runAnomalyTitle')}</strong>
              <div>
                {t('pollingStats.runAnomalyCopy', {
                  bucket: scheduledRunAnomaly.bucketLabel,
                  observed: scheduledRunAnomaly.observedRuns,
                  expected: scheduledRunAnomaly.expectedRunsPerHour,
                  interval: scheduledRunAlertInterval,
                  sourceCount: scheduledRunAnomaly.sourceCount
                })}
              </div>
            </div>
          ) : null}
          {isSourceVariant ? (
            <div className="system-dashboard-summary">
              <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.totalImportedMessages')}</span><strong>{stats.totalImportedMessages}</strong></article>
              <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.errorPolls')}</span><strong>{stats.errorPolls ?? 0}</strong></article>
              <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.manualRuns')}</span><strong>{stats.manualRuns ?? 0}</strong></article>
              <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.scheduledRuns')}</span><strong>{stats.scheduledRuns ?? 0}</strong></article>
            </div>
          ) : (
            <>
              <div className="system-dashboard-summary">
                <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.totalImportedMessages')}</span><strong>{stats.totalImportedMessages}</strong></article>
                <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.configuredMailFetchers')}</span><strong>{stats.configuredMailFetchers}</strong></article>
                <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.enabledMailFetchers')}</span><strong>{stats.enabledMailFetchers}</strong></article>
                <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.sourcesWithErrors')}</span><strong>{stats.sourcesWithErrors}</strong></article>
              </div>

              <div className="system-dashboard-summary">
                <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.activeMailFetchers')}</span><strong>{stats.health?.activeMailFetchers ?? 0}</strong></article>
                <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.coolingDownMailFetchers')}</span><strong>{stats.health?.coolingDownMailFetchers ?? 0}</strong></article>
                <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.failingMailFetchers')}</span><strong>{stats.health?.failingMailFetchers ?? 0}</strong></article>
                <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.disabledMailFetchers')}</span><strong>{stats.health?.disabledMailFetchers ?? 0}</strong></article>
              </div>
            </>
          )}

          <ImportTimelineChart
            customRangeLoader={customRangeLoader}
            chartStateKey={id ? `${id}:activity` : `${title}:activity`}
            locale={locale}
            series={importSeries}
            t={t}
            title={t('pollingStats.activityTitle')}
          />

          <ImportTimelineChart
            anomalyMarkers={runAnomalyMarkers}
            customRangeLoader={customRangeLoader}
            chartStateKey={id ? `${id}:runs` : `${title}:runs`}
            locale={locale}
            series={runSeries}
            t={t}
            title={t('pollingStats.runActivityTitle')}
          />

          <div className="polling-statistics-grid">
            <article className="surface-card polling-statistics-card">
              <div className="polling-statistics-card-title">{t('pollingStats.runBreakdown')}</div>
              <div className="polling-statistics-breakdown">
                <div><span>{t('pollingStats.manualRuns')}</span><strong>{stats.manualRuns}</strong></div>
                <div><span>{t('pollingStats.scheduledRuns')}</span><strong>{stats.scheduledRuns}</strong></div>
                <div><span>{t('pollingStats.averagePollDuration')}</span><strong>{formatDuration(stats.averagePollDurationMillis, t)}</strong></div>
                {isSourceVariant ? <div><span>{t('pollingStats.errorPolls')}</span><strong>{stats.errorPolls ?? 0}</strong></div> : null}
              </div>
            </article>

            {!isSourceVariant ? (
              <article className="surface-card polling-statistics-card">
                <div className="polling-statistics-card-title">{t('pollingStats.providerBreakdown')}</div>
                {stats.providerBreakdown?.length ? (
                  <div className="polling-statistics-breakdown">
                    {stats.providerBreakdown.map((entry) => (
                      <div key={entry.key}>
                        <span>{translateProviderLabel(entry, t)}</span>
                        <strong>{entry.count}</strong>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="muted-box">{t('pollingStats.noBreakdown')}</div>
                )}
              </article>
            ) : null}
          </div>
        </>
      ) : (
        <div className="muted-box">{t('pollingStats.notEnoughData')}</div>
      )}
    </CollapsibleSection>
  )
}

export default PollingStatisticsSection
