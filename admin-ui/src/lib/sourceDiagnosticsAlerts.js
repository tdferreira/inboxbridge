function isFutureInstant(value, now) {
  if (!value) return false
  const parsed = Date.parse(value)
  if (Number.isNaN(parsed)) return false
  return parsed > now
}

/**
 * Derives concise operator-facing alert badges from the richer source
 * diagnostics payload and the source's latest polling state.
 */
export function buildSourceDiagnosticsAlerts(fetcher, options = {}) {
  if (!fetcher) {
    return []
  }

  const diagnostics = fetcher.diagnostics || null
  const pollingState = fetcher.pollingState || null
  const now = options.now ?? Date.now()
  const alerts = []

  if (fetcher.fetchMode === 'IDLE' && diagnostics) {
    const disconnectedFolders = (diagnostics.idleWatches || [])
      .filter((watch) => watch?.status === 'DISCONNECTED')
      .map((watch) => watch.folderName)
      .filter(Boolean)
    if (disconnectedFolders.length > 0) {
      alerts.push({
        code: 'idleDisconnected',
        params: {
          count: disconnectedFolders.length,
          folders: disconnectedFolders.join(', ')
        }
      })
    }
  }

  if (pollingState?.consecutiveFailures >= 3 && isFutureInstant(pollingState.cooldownUntil, now)) {
    alerts.push({
      code: 'cooldownLoop',
      params: { count: pollingState.consecutiveFailures }
    })
  }

  if ((diagnostics?.sourceThrottle?.adaptiveMultiplier || 1) >= 3) {
    alerts.push({
      code: 'sourceThrottle',
      params: { multiplier: diagnostics.sourceThrottle.adaptiveMultiplier }
    })
  }

  if ((diagnostics?.destinationThrottle?.adaptiveMultiplier || 1) >= 3) {
    alerts.push({
      code: 'destinationThrottle',
      params: { multiplier: diagnostics.destinationThrottle.adaptiveMultiplier }
    })
  }

  return alerts
}

