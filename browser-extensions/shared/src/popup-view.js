/**
 * Derives the popup presentation model from the extension status payload so the
 * popup renderer stays small and can be unit tested without a browser runtime.
 */
export function disconnectedView(message, translate = (key) => key) {
  return {
    attentionCount: '',
    connectionCopy: message,
    errorSources: [],
    healthy: true,
    metrics: {
      duplicates: '-',
      errors: '-',
      fetched: '-',
      imported: '-'
    },
    runPollDisabled: true,
    statusLabel: translate('popup.disconnected'),
    statusTone: 'neutral',
    updatedText: translate('popup.noStatus')
  }
}

/**
 * Converts the backend extension status payload into compact popup text,
 * metrics, and attention-state values.
 */
export function deriveStatusView(serverUrl, status, { now = Date.now(), translate = (key, params) => key } = {}) {
  const userLabel = status.user?.displayName || status.user?.username || 'InboxBridge'
  const host = new URL(serverUrl).host
  const errorSources = (status.sources || []).filter((source) => source.needsAttention).slice(0, 3)
  const running = Boolean(status.poll?.running)
  const errorCount = Number(status.summary?.errorSourceCount || 0)
  const statusTone = running ? 'info' : (errorCount > 0 ? 'error' : 'success')
  const statusLabel = running ? translate('popup.running') : (errorCount > 0 ? translate('popup.hasErrors') : translate('popup.healthy'))

  return {
    attentionCount: errorSources.length
      ? translate(errorSources.length === 1 ? 'popup.sourceCount' : 'popup.sourceCountPlural', { count: errorSources.length })
      : '',
    connectionCopy: translate('popup.connectedTo', { userLabel, host }),
    errorSources,
    healthy: !errorSources.length,
    metrics: {
      duplicates: String(status.summary?.lastCompletedRun?.duplicates ?? 0),
      errors: String(status.summary?.lastCompletedRun?.errors ?? errorCount),
      fetched: String(status.summary?.lastCompletedRun?.fetched ?? 0),
      imported: String(status.summary?.lastCompletedRun?.imported ?? 0)
    },
    runPollDisabled: running || !status.poll?.canRun,
    statusLabel,
    statusTone,
    updatedText: status.summary?.lastCompletedRunAt
      ? translate('popup.lastCompleted', { value: formatRelative(status.summary.lastCompletedRunAt, now, translate) })
      : translate('popup.noCompletedRuns')
  }
}

export function formatRelative(value, now = Date.now(), translate = (key, params) => key) {
  const date = new Date(value)
  const diffMs = now - date.getTime()
  const diffMinutes = Math.round(diffMs / 60000)
  if (diffMinutes < 1) {
    return translate('popup.justNow')
  }
  if (diffMinutes < 60) {
    return translate('popup.minutesAgo', { count: diffMinutes })
  }
  const diffHours = Math.round(diffMinutes / 60)
  if (diffHours < 24) {
    return translate('popup.hoursAgo', { count: diffHours })
  }
  return date.toLocaleString()
}

export function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}
