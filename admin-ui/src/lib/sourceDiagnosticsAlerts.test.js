import { buildSourceDiagnosticsAlerts } from './sourceDiagnosticsAlerts'

describe('buildSourceDiagnosticsAlerts', () => {
  it('flags disconnected IMAP IDLE watchers, cooldown loops, and widened throttles', () => {
    const alerts = buildSourceDiagnosticsAlerts({
      fetchMode: 'IDLE',
      pollingState: {
        cooldownUntil: '2026-04-06T10:15:00Z',
        consecutiveFailures: 4
      },
      diagnostics: {
        idleWatches: [
          { folderName: 'INBOX', status: 'CONNECTED' },
          { folderName: 'Projects/2026', status: 'DISCONNECTED' }
        ],
        sourceThrottle: { adaptiveMultiplier: 3 },
        destinationThrottle: { adaptiveMultiplier: 4 }
      }
    }, { now: Date.parse('2026-04-06T10:10:00Z') })

    expect(alerts).toEqual([
      { code: 'idleDisconnected', params: { count: 1, folders: 'Projects/2026' } },
      { code: 'cooldownLoop', params: { count: 4 } },
      { code: 'sourceThrottle', params: { multiplier: 3 } },
      { code: 'destinationThrottle', params: { multiplier: 4 } }
    ])
  })

  it('skips cooldown alerts once the cooldown window has passed', () => {
    const alerts = buildSourceDiagnosticsAlerts({
      fetchMode: 'POLLING',
      pollingState: {
        cooldownUntil: '2026-04-06T10:15:00Z',
        consecutiveFailures: 5
      }
    }, { now: Date.parse('2026-04-06T10:16:00Z') })

    expect(alerts).toEqual([])
  })

  it('does not treat tracking or connected idle watchers as disconnected', () => {
    const alerts = buildSourceDiagnosticsAlerts({
      fetchMode: 'IDLE',
      diagnostics: {
        idleWatches: [
          { folderName: 'INBOX', status: 'TRACKING' },
          { folderName: 'Projects/2026', status: 'CONNECTED' }
        ],
        sourceThrottle: { adaptiveMultiplier: 2 },
        destinationThrottle: { adaptiveMultiplier: 2 }
      }
    })

    expect(alerts).toEqual([])
  })
})

