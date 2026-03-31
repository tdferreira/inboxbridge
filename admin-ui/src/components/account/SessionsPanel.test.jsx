import { fireEvent, render, screen } from '@testing-library/react'
import SessionsPanel from './SessionsPanel'

describe('SessionsPanel', () => {
  it('renders active and recent sessions and exposes revoke actions', () => {
    const onRevokeOtherSessions = vi.fn()
    const onRevokeSession = vi.fn()

    render(
      <SessionsPanel
        activeSessions={[
          { id: 1, sessionType: 'BROWSER', current: true, ipAddress: '203.0.113.10', locationLabel: null, loginMethod: 'PASSWORD', lastSeenAt: '2026-03-30T12:05:00Z', expiresAt: '2026-03-30T13:00:00Z' },
          { id: 2, sessionType: 'REMOTE', current: false, ipAddress: '203.0.113.11', locationLabel: null, loginMethod: 'PASSKEY', lastSeenAt: '2026-03-30T11:05:00Z', expiresAt: '2026-03-30T13:00:00Z' }
        ]}
        geoIpConfigured={false}
        locale="en"
        onRevokeOtherSessions={onRevokeOtherSessions}
        onRevokeSession={onRevokeSession}
        recentLogins={[
          { id: 1, sessionType: 'BROWSER', current: true, active: true, ipAddress: '203.0.113.10', locationLabel: null, loginMethod: 'PASSWORD', createdAt: '2026-03-30T12:00:00Z', lastSeenAt: '2026-03-30T12:05:00Z' }
        ]}
        revokeLoadingId={null}
        revokeOthersLoading={false}
        t={(key, params = {}) => params.value ? `${key}:${params.value}` : key}
      />
    )

    expect(screen.getByText('sessions.activeTitle')).toBeInTheDocument()
    expect(screen.getByText('sessions.recentTitle')).toBeInTheDocument()
    expect(screen.getByText('sessions.locationNotice')).toBeInTheDocument()
    expect(screen.getByText((value) => value.includes('sessions.sessionKind:sessions.kindRemote'))).toBeInTheDocument()

    fireEvent.click(screen.getByText('sessions.revokeOthers'))
    fireEvent.click(screen.getByText('sessions.revoke'))

    expect(onRevokeOtherSessions).toHaveBeenCalledTimes(1)
    expect(onRevokeSession).toHaveBeenCalledWith(expect.objectContaining({ id: 2, sessionType: 'REMOTE' }))
  })

  it('hides the setup notice when geo-ip lookups are configured', () => {
    render(
      <SessionsPanel
        activeSessions={[]}
        geoIpConfigured
        locale="en"
        onRevokeOtherSessions={vi.fn()}
        onRevokeSession={vi.fn()}
        recentLogins={[]}
        revokeLoadingId={null}
        revokeOthersLoading={false}
        t={(key, params = {}) => params.value ? `${key}:${params.value}` : key}
      />
    )

    expect(screen.queryByText('sessions.locationNotice')).not.toBeInTheDocument()
  })
})
