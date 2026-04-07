import { formatBytes } from '@/lib/formatters'

export function processedMessagesForLiveSource(source) {
  if (!source) {
    return 0
  }
  if (Number.isFinite(source.processedMessages)) {
    return Math.max(0, source.processedMessages)
  }
  return Math.max(0, source.fetched || 0)
}

export function hasLiveByteProgress(source) {
  return hasDeterminateLiveProgress(source)
    && Number.isFinite(source?.totalBytes)
    && source.totalBytes > 0
}

export function formatLiveByteProgress(source, locale, t) {
  if (!hasLiveByteProgress(source)) {
    return ''
  }
  return t('remote.processingBytesProgress', {
    processed: formatBytes(Math.max(0, source.processedBytes || 0), locale),
    total: formatBytes(source.totalBytes, locale)
  })
}

export function hasDeterminateLiveProgress(source) {
  return String(source?.state || '').toUpperCase() === 'RUNNING'
    && Number.isFinite(source?.totalMessages)
    && source.totalMessages > 0
}

export function isLiveSourceFinalizing(source) {
  if (!hasDeterminateLiveProgress(source)) {
    return false
  }
  const processedMessages = processedMessagesForLiveSource(source)
  if (processedMessages < source.totalMessages) {
    return false
  }
  if (Number.isFinite(source?.totalBytes) && source.totalBytes > 0) {
    return Math.max(0, source.processedBytes || 0) >= source.totalBytes
  }
  return true
}

export function liveProgressPercent(source) {
  if (!hasDeterminateLiveProgress(source)) {
    return 0
  }
  const processed = processedMessagesForLiveSource(source)
  return Math.max(0, Math.min(100, (processed / source.totalMessages) * 100))
}

export function formatLiveProgressLabel(source, t) {
  if (!hasDeterminateLiveProgress(source)) {
    return ''
  }
  if (isLiveSourceFinalizing(source)) {
    return t('remote.finalizingProgress', {
      total: source.totalMessages
    })
  }
  return t('remote.processingProgress', {
    processed: processedMessagesForLiveSource(source),
    total: source.totalMessages
  })
}

export function formatLiveProgressSummary(source, locale, t) {
  const countLabel = formatLiveProgressLabel(source, t)
  if (!countLabel) {
    return ''
  }
  if (!hasLiveByteProgress(source)) {
    return countLabel
  }
  if (isLiveSourceFinalizing(source)) {
    return t('remote.finalizingProgressWithSize', {
      total: source.totalMessages,
      totalBytes: formatBytes(source.totalBytes, locale)
    })
  }
  return t('remote.processingProgressWithSize', {
    processed: processedMessagesForLiveSource(source),
    total: source.totalMessages,
    processedBytes: formatBytes(Math.max(0, source.processedBytes || 0), locale),
    totalBytes: formatBytes(source.totalBytes, locale)
  })
}
