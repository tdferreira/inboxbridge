import { useState } from 'react'
import CollapsibleSection from '../common/CollapsibleSection'
import ImportTimelineChart from '../common/ImportTimelineChart'
import { detectScheduledRunAnomaly } from '../../lib/pollingStatsAlerts'
import { formatDate } from '../../lib/formatters'
import { getCurrentFormattingTimeZone } from '../../lib/timeZonePreferences'
import { translate } from '../../lib/i18n'
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

function formatAnomalyTime(value, locale, timeZone) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  try {
    return new Intl.DateTimeFormat(locale, {
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
      timeZone
    }).format(date)
  } catch {
    return new Intl.DateTimeFormat(locale, {
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23'
    }).format(date)
  }
}

function hasValidTimestamp(value) {
  return Number.isFinite(value) && !Number.isNaN(new Date(value).getTime())
}

function safeFormatWindowDate(value, locale, timeZone, fallback = '') {
  if (!hasValidTimestamp(value)) {
    return fallback
  }
  const formatted = formatDate(value, locale, timeZone)
  if (!formatted || formatted === translate(locale, 'common.unavailable') || formatted === translate(locale, 'common.never')) {
    return fallback
  }
  return formatted
}

function formatAnomalyBucketLabel(label, locale, timeZone) {
  if (!label) return ''
  const text = String(label).trim()
  const isIsoLikeDate = /^\d{4}-\d{2}-\d{2}(?:T.*)?$/.test(text)
  if (!isIsoLikeDate) {
    return ''
  }
  const formatted = formatDate(text, locale, timeZone)
  if (!formatted || formatted === translate(locale, 'common.unavailable') || formatted === translate(locale, 'common.never')) {
    return ''
  }
  return formatted
}

function formatAnomalyWindow(scheduledRunAnomaly, locale, t) {
  if (!scheduledRunAnomaly) return { text: '', variant: 'point' }

  const timeZone = getCurrentFormattingTimeZone()
  const startTime = hasValidTimestamp(scheduledRunAnomaly.startOccurredAt)
    ? formatAnomalyTime(scheduledRunAnomaly.startOccurredAt, locale, timeZone)
    : scheduledRunAnomaly.startBucketLabel
  const endTime = hasValidTimestamp(scheduledRunAnomaly.endOccurredAt)
    ? formatAnomalyTime(scheduledRunAnomaly.endOccurredAt, locale, timeZone)
    : scheduledRunAnomaly.endBucketLabel
  const formattedStartBucketLabel = formatAnomalyBucketLabel(
    scheduledRunAnomaly.startBucketLabel || scheduledRunAnomaly.bucketLabel,
    locale,
    timeZone
  )
  const formattedEndBucketLabel = formatAnomalyBucketLabel(
    scheduledRunAnomaly.endBucketLabel,
    locale,
    timeZone
  )
  const fallbackWindowLabel = scheduledRunAnomaly.startBucketLabel || scheduledRunAnomaly.bucketLabel || ''

  if (
    hasValidTimestamp(scheduledRunAnomaly.startOccurredAt)
    && hasValidTimestamp(scheduledRunAnomaly.endOccurredAt)
    && scheduledRunAnomaly.startOccurredAt === scheduledRunAnomaly.endOccurredAt
  ) {
    const relativeDay = ['today', 'yesterday'].includes(scheduledRunAnomaly.rangeKey)
      ? scheduledRunAnomaly.rangeKey
      : null
    if (relativeDay) {
      return {
        text: t('pollingStats.dayAtTime', {
          day: t(`pollingStats.${relativeDay}`),
          time: startTime
        }),
        variant: 'point'
      }
    }
    return {
      text: formatDate(scheduledRunAnomaly.startOccurredAt, locale, timeZone),
      variant: 'point'
    }
  }

  if (hasValidTimestamp(scheduledRunAnomaly.startOccurredAt) && hasValidTimestamp(scheduledRunAnomaly.endOccurredAt)) {
    const startRelativeDay = ['today', 'yesterday'].includes(scheduledRunAnomaly.rangeKey)
      ? scheduledRunAnomaly.rangeKey
      : null
    const endRelativeDay = startRelativeDay

    if (startRelativeDay && startRelativeDay === endRelativeDay) {
      return {
        text: t('pollingStats.dayFromUntil', {
          day: t(`pollingStats.${startRelativeDay}`),
          end: endTime,
          start: startTime
        }),
        variant: 'point'
      }
    }

    const formattedStart = safeFormatWindowDate(
      scheduledRunAnomaly.startOccurredAt,
      locale,
      timeZone,
      formattedStartBucketLabel || scheduledRunAnomaly.startBucketLabel || fallbackWindowLabel
    )
    const formattedEnd = safeFormatWindowDate(
      scheduledRunAnomaly.endOccurredAt,
      locale,
      timeZone,
      formattedEndBucketLabel || scheduledRunAnomaly.endBucketLabel || formattedStart
    )

    if (formattedStart && formattedEnd && formattedStart !== formattedEnd) {
      return {
        text: t('pollingStats.fromUntil', {
          end: formattedEnd,
          start: formattedStart
        }),
        variant: 'range',
        start: formattedStart,
        end: formattedEnd
      }
    }

    return {
      text: formattedStart || formattedEnd || fallbackWindowLabel,
      variant: 'point'
    }
  }

  if (formattedStartBucketLabel && formattedEndBucketLabel && formattedStartBucketLabel !== formattedEndBucketLabel) {
    return {
      text: t('pollingStats.fromUntil', {
        end: formattedEndBucketLabel,
        start: formattedStartBucketLabel
      }),
      variant: 'range',
      start: formattedStartBucketLabel,
      end: formattedEndBucketLabel
    }
  }

  return {
    text: fallbackWindowLabel,
    variant: 'point'
  }
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
  sourceFetchMode = 'POLLING',
  variant = 'user',
  t
}) {
  const [runChartFocusToken, setRunChartFocusToken] = useState(0)
  const [runChartScrollFocusToken, setRunChartScrollFocusToken] = useState(0)
  const hasIdleRuns = (stats?.idleRuns || 0) > 0 || Object.keys(stats?.idleRunTimelines || {}).length > 0
  const importSeries = [
    { key: 'imports', label: t('pollingStats.seriesImports'), timelines: stats?.importTimelines || {} },
    { key: 'duplicates', label: t('pollingStats.seriesDuplicates'), timelines: stats?.duplicateTimelines || {} },
    { key: 'errors', label: t('pollingStats.seriesErrors'), timelines: stats?.errorTimelines || {} }
  ]
  const isSourceVariant = variant === 'source'
  const sourceUsesIdle = isSourceVariant && sourceFetchMode === 'IDLE'
  const showIdleRunSeries = sourceUsesIdle || hasIdleRuns
  const runSeries = showIdleRunSeries
    ? [
        { key: 'manualRuns', label: t('pollingStats.manualRuns'), timelines: stats?.manualRunTimelines || {} },
        { key: 'idleRuns', label: t('pollingStats.idleRuns'), timelines: stats?.idleRunTimelines || {} },
        { key: 'scheduledRuns', label: t('pollingStats.scheduledRuns'), timelines: stats?.scheduledRunTimelines || {} }
      ]
    : [
        { key: 'manualRuns', label: t('pollingStats.manualRuns'), timelines: stats?.manualRunTimelines || {} },
        { key: 'scheduledRuns', label: t('pollingStats.scheduledRuns'), timelines: stats?.scheduledRunTimelines || {} }
      ]
  const scheduledRunAnomaly = detectScheduledRunAnomaly(stats, scheduledRunAlertInterval, scheduledRunAlertSourceCount)
  const scheduledRunAnomalyWindow = formatAnomalyWindow(scheduledRunAnomaly, locale, t)
  const scheduledRunAnomalyMessage = scheduledRunAnomaly
    ? t(
        scheduledRunAnomalyWindow.variant === 'range'
          ? 'pollingStats.runAnomalyCopyRange'
          : 'pollingStats.runAnomalyCopy',
        {
          window: scheduledRunAnomalyWindow.text,
          start: scheduledRunAnomalyWindow.start,
          end: scheduledRunAnomalyWindow.end,
          observed: scheduledRunAnomaly.observedRuns,
          expected: scheduledRunAnomaly.expectedRunsPerHour,
          interval: scheduledRunAlertInterval,
          sourceCount: scheduledRunAnomaly.sourceCount
        }
      )
    : ''
  const runAnomalyMarkers = scheduledRunAnomaly
    ? [{
        bucketLabel: scheduledRunAnomaly.startBucketLabel || scheduledRunAnomaly.bucketLabel,
      rangeKey: scheduledRunAnomaly.rangeKey,
      endBucketLabel: scheduledRunAnomaly.endBucketLabel,
        message: scheduledRunAnomalyMessage,
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
                {scheduledRunAnomalyMessage}
              </div>
              <button
                className="secondary ghost"
                onClick={() => {
                  setRunChartFocusToken((current) => current + 1)
                  setRunChartScrollFocusToken((current) => current + 1)
                }}
                type="button"
              >
                {t('pollingStats.showAnomalyOnChart')}
              </button>
            </div>
          ) : null}
          {isSourceVariant ? (
            <div className="system-dashboard-summary">
              <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.totalImportedMessages')}</span><strong>{stats.totalImportedMessages}</strong></article>
              <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.errorPolls')}</span><strong>{stats.errorPolls ?? 0}</strong></article>
              <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.manualRuns')}</span><strong>{stats.manualRuns ?? 0}</strong></article>
              <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.scheduledRuns')}</span><strong>{stats.scheduledRuns ?? 0}</strong></article>
              {showIdleRunSeries ? <article className="surface-card metric-card"><span className="metric-label">{t('pollingStats.idleRuns')}</span><strong>{stats.idleRuns ?? 0}</strong></article> : null}
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
            focusChartToken={runChartScrollFocusToken}
            focusedRangeKey={scheduledRunAnomaly?.rangeKey || null}
            focusedRangeToken={runChartFocusToken}
            customRangeLoader={customRangeLoader}
            chartStateKey={id ? `${id}:runs` : `${title}:runs`}
            locale={locale}
            series={runSeries}
            t={t}
            title={sourceUsesIdle ? t('pollingStats.sourceActivityTitle') : t('pollingStats.runActivityTitle')}
          />
          {showIdleRunSeries ? (
            <div className="section-copy">{t('pollingStats.idleRunsHelp')}</div>
          ) : null}

          <div className="polling-statistics-grid">
            <article className="surface-card polling-statistics-card">
              <div className="polling-statistics-card-title">{t('pollingStats.runBreakdown')}</div>
              <div className="polling-statistics-breakdown">
                <div><span>{t('pollingStats.manualRuns')}</span><strong>{stats.manualRuns}</strong></div>
                <div><span>{t('pollingStats.scheduledRuns')}</span><strong>{stats.scheduledRuns}</strong></div>
                {showIdleRunSeries ? <div><span>{t('pollingStats.idleRuns')}</span><strong>{stats.idleRuns ?? 0}</strong></div> : null}
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
