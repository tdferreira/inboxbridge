import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import SessionsPanel from './SessionsPanel'
import { DATE_FORMAT_YMD_24, resetCurrentFormattingDateFormat, setCurrentFormattingDateFormat } from '@/lib/formatters'
import { resetCurrentFormattingTimeZone, setCurrentFormattingTimeZone } from '@/lib/timeZonePreferences'

describe('SessionsPanel', () => {
  afterEach(() => {
    resetCurrentFormattingDateFormat()
    resetCurrentFormattingTimeZone()
    vi.unstubAllGlobals()
  })

  it('renders active and recent sessions and exposes revoke actions', async () => {
    const onRevokeOtherSessions = vi.fn()
    const onRevokeSession = vi.fn()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        address: {
          city: 'Lisbon',
          state: 'Lisbon',
          country_code: 'pt'
        }
      })
    }))

    render(
      <SessionsPanel
        activeSessions={[
          { id: 1, sessionType: 'BROWSER', current: true, browserLabel: 'Microsoft Edge', deviceLabel: 'Desktop (Windows)', ipAddress: '203.0.113.10', locationLabel: null, deviceLocationLabel: '38.7223, -9.1393 (±25 m)', deviceLatitude: 38.7223, deviceLongitude: -9.1393, deviceLocationCapturedAt: '2026-03-30T12:03:00Z', loginMethod: 'PASSWORD', lastSeenAt: '2026-03-30T12:05:00Z', expiresAt: '2026-03-30T13:00:00Z' },
          { id: 2, sessionType: 'REMOTE', current: false, browserLabel: 'Safari', deviceLabel: 'Mobile phone (iOS)', ipAddress: '203.0.113.11', locationLabel: null, deviceLocationLabel: null, deviceLocationCapturedAt: null, loginMethod: 'PASSKEY', lastSeenAt: '2026-03-30T11:05:00Z', expiresAt: '2026-03-30T13:00:00Z' }
        ]}
        currentSessionCanRequestDeviceLocation={false}
        currentSessionDeviceLocationError=""
        geoIpConfigured={false}
        locale="en"
        onRequestCurrentDeviceLocation={vi.fn()}
        onRevokeOtherSessions={onRevokeOtherSessions}
        onRevokeSession={onRevokeSession}
        recentLogins={[
          { id: 1, sessionType: 'BROWSER', current: true, active: true, browserLabel: 'Microsoft Edge', deviceLabel: 'Desktop (Windows)', ipAddress: '203.0.113.10', locationLabel: null, deviceLocationLabel: '38.7223, -9.1393 (±25 m)', deviceLatitude: 38.7223, deviceLongitude: -9.1393, deviceLocationCapturedAt: '2026-03-30T12:03:00Z', loginMethod: 'PASSWORD', createdAt: '2026-03-30T12:00:00Z', lastSeenAt: '2026-03-30T12:05:00Z' }
        ]}
        revokeLoadingId={null}
        revokeOthersLoading={false}
        requestCurrentDeviceLocationLoading={false}
        t={(key, params = {}) => params.value ? `${key}:${params.value}` : key}
      />
    )

    expect(screen.getByText('sessions.activeTitle')).toBeInTheDocument()
    expect(screen.getByText('sessions.recentTitle')).toBeInTheDocument()
    expect(screen.getByText('sessions.locationNotice')).toBeInTheDocument()
    expect(screen.getByText((value) => value.includes('sessions.sessionKind:sessions.kindRemote'))).toBeInTheDocument()
    expect(screen.getAllByText((value) => value.includes('sessions.browser:Microsoft Edge')).length).toBeGreaterThan(0)
    expect(screen.getByText((value) => value.includes('sessions.deviceType:Mobile phone (iOS)'))).toBeInTheDocument()
    await waitFor(() => expect(screen.getAllByText('Lisbon, Lisbon, PT')).toHaveLength(2))
    expect(screen.getAllByRole('link', { name: 'sessions.openInMaps' })).toHaveLength(2)

    fireEvent.click(screen.getByText('sessions.revokeOthers'))
    fireEvent.click(screen.getByText('sessions.revoke'))

    expect(onRevokeOtherSessions).toHaveBeenCalledTimes(1)
    expect(onRevokeSession).toHaveBeenCalledWith(expect.objectContaining({ id: 2, sessionType: 'REMOTE' }))

    fireEvent.click(screen.getByRole('button', { name: 'common.collapseSection' }))
    expect(screen.queryByText('sessions.activeTitle')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'common.expandSection' }))
    expect(screen.getByText('sessions.activeTitle')).toBeInTheDocument()
  })

  it('hides the setup notice when geo-ip lookups are configured', () => {
    render(
      <SessionsPanel
        activeSessions={[]}
        currentSessionCanRequestDeviceLocation={false}
        currentSessionDeviceLocationError=""
        geoIpConfigured
        locale="en"
        onRequestCurrentDeviceLocation={vi.fn()}
        onRevokeOtherSessions={vi.fn()}
        onRevokeSession={vi.fn()}
        recentLogins={[]}
        revokeLoadingId={null}
        revokeOthersLoading={false}
        requestCurrentDeviceLocationLoading={false}
        t={(key, params = {}) => params.value ? `${key}:${params.value}` : key}
      />
    )

    expect(screen.queryByText('sessions.locationNotice')).not.toBeInTheDocument()
  })

  it('shows a retry action when the current browser session has not captured a location yet', () => {
    const onRequestCurrentDeviceLocation = vi.fn()

    render(
      <SessionsPanel
        activeSessions={[
          { id: 7, sessionType: 'BROWSER', current: true, browserLabel: 'Chrome', deviceLabel: 'Desktop (Linux)', ipAddress: '203.0.113.12', locationLabel: null, deviceLocationLabel: null, deviceLatitude: null, deviceLongitude: null, deviceLocationCapturedAt: null, loginMethod: 'PASSWORD', lastSeenAt: '2026-03-30T12:05:00Z', expiresAt: '2026-03-30T13:00:00Z' }
        ]}
        currentSessionCanRequestDeviceLocation
        currentSessionDeviceLocationError="deviceLocation.errors.timeout"
        geoIpConfigured
        locale="en"
        onRequestCurrentDeviceLocation={onRequestCurrentDeviceLocation}
        onRevokeOtherSessions={vi.fn()}
        onRevokeSession={vi.fn()}
        recentLogins={[]}
        revokeLoadingId={null}
        revokeOthersLoading={false}
        requestCurrentDeviceLocationLoading={false}
        t={(key, params = {}) => params.value ? `${key}:${params.value}` : key}
      />
    )

    expect(screen.getAllByText((value) => value.includes('sessions.deviceLocationPending'))).toHaveLength(2)
    fireEvent.click(screen.getByRole('button', { name: 'sessions.captureDeviceLocation' }))
    expect(onRequestCurrentDeviceLocation).toHaveBeenCalledTimes(1)
    expect(screen.getByText('deviceLocation.errors.timeout')).toBeInTheDocument()
  })

  it('renders session timestamps with the active manual date format', async () => {
    setCurrentFormattingDateFormat(DATE_FORMAT_YMD_24)
    setCurrentFormattingTimeZone('UTC')

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        address: {
          city: 'Lisbon',
          state: 'Lisbon',
          country_code: 'pt'
        }
      })
    }))

    render(
      <SessionsPanel
        activeSessions={[
          { id: 1, sessionType: 'BROWSER', current: true, browserLabel: 'Microsoft Edge', deviceLabel: 'Desktop (Windows)', ipAddress: '203.0.113.10', locationLabel: null, deviceLocationLabel: '38.7223, -9.1393 (±25 m)', deviceLatitude: 38.7223, deviceLongitude: -9.1393, deviceLocationCapturedAt: '2026-03-30T12:03:00Z', loginMethod: 'PASSWORD', lastSeenAt: '2026-03-30T12:05:00Z', expiresAt: '2026-03-30T13:00:00Z' }
        ]}
        currentSessionCanRequestDeviceLocation={false}
        currentSessionDeviceLocationError=""
        geoIpConfigured
        locale="en"
        onRequestCurrentDeviceLocation={vi.fn()}
        onRevokeOtherSessions={vi.fn()}
        onRevokeSession={vi.fn()}
        recentLogins={[
          { id: 1, sessionType: 'BROWSER', current: true, active: true, browserLabel: 'Microsoft Edge', deviceLabel: 'Desktop (Windows)', ipAddress: '203.0.113.10', locationLabel: null, deviceLocationLabel: '38.7223, -9.1393 (±25 m)', deviceLatitude: 38.7223, deviceLongitude: -9.1393, deviceLocationCapturedAt: '2026-03-30T12:03:00Z', loginMethod: 'PASSWORD', createdAt: '2026-03-30T12:00:00Z', lastSeenAt: '2026-03-30T12:05:00Z' }
        ]}
        revokeLoadingId={null}
        revokeOthersLoading={false}
        requestCurrentDeviceLocationLoading={false}
        t={(key, params = {}) => params.value ? `${key}:${params.value}` : key}
      />
    )

    await waitFor(() => expect(screen.getByText((value) => value.includes('2026-03-30 12:00:00'))).toBeInTheDocument())
    expect(screen.getAllByText((value) => value.includes('2026-03-30 12:03:00')).length).toBeGreaterThan(0)
    expect(screen.getAllByText((value) => value.includes('2026-03-30 12:05:00')).length).toBeGreaterThan(0)
    expect(screen.getByText((value) => value.includes('2026-03-30 13:00:00'))).toBeInTheDocument()
  })
})
