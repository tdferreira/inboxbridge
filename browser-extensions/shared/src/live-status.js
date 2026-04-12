/**
 * Applies a live poll snapshot to the cached extension status so the badge and
 * popup can show an active run immediately while the durable status catches up.
 */
export function mergeLivePollIntoStatus(status, poll) {
  if (!status) {
    return status
  }
  const runningSourceIds = new Set(
    (poll?.sources || [])
      .filter((source) => source?.state === 'RUNNING')
      .map((source) => source.sourceId)
  )
  return {
    ...status,
    poll: {
      ...(status.poll || {}),
      activeSourceId: poll?.activeSourceId || null,
      canRun: !poll?.running,
      running: Boolean(poll?.running),
      startedAt: poll?.startedAt || null,
      state: poll?.state || (poll?.running ? 'RUNNING' : 'IDLE'),
      updatedAt: poll?.updatedAt || null
    },
    sources: (status.sources || []).map((source) => {
      if (!runningSourceIds.has(source.sourceId)) {
        return source
      }
      return {
        ...source,
        status: 'RUNNING'
      }
    })
  }
}

export function shouldRefreshStatusFromLiveEvent(event) {
  return [
    'poll-run-finished',
    'poll-source-finished',
    'session-revoked'
  ].includes(event?.type)
}

export function shouldOverlayLiveStatus(event) {
  return [
    'poll-snapshot',
    'poll-run-started',
    'poll-run-pausing',
    'poll-run-paused',
    'poll-run-resumed',
    'poll-run-stopping',
    'poll-source-started',
    'poll-source-progress'
  ].includes(event?.type)
}
