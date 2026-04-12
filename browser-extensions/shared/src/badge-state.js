/**
 * Resolves the toolbar badge presentation from the cached/live extension
 * status so the background worker can stay as a thin transport layer.
 */
export function badgeStateForStatus(status, errorMessage = null) {
  if (errorMessage) {
    return {
      color: '#5b6475',
      text: '?',
      title: `InboxBridge: ${errorMessage}`
    }
  }
  if (!status) {
    return {
      clear: true,
      title: 'InboxBridge'
    }
  }
  if (status.poll?.running) {
    return {
      clear: true,
      title: 'InboxBridge: polling running'
    }
  }
  const errorCount = Number(status.summary?.errorSourceCount || 0)
  if (errorCount > 0) {
    return {
      color: '#cf222e',
      text: '!',
      title: `InboxBridge: ${errorCount} source${errorCount === 1 ? '' : 's'} need attention`
    }
  }
  return {
    clear: true,
    title: 'InboxBridge: healthy'
  }
}
