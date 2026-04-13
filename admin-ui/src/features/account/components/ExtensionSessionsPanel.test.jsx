import { fireEvent, render, screen } from '@testing-library/react'
import ExtensionSessionsPanel from './ExtensionSessionsPanel'
import { DATE_FORMAT_YMD_24, resetCurrentFormattingDateFormat, setCurrentFormattingDateFormat } from '@/lib/formatters'
import { resetCurrentFormattingTimeZone, setCurrentFormattingTimeZone } from '@/lib/timeZonePreferences'

describe('ExtensionSessionsPanel', () => {
  afterEach(() => {
    resetCurrentFormattingDateFormat()
    resetCurrentFormattingTimeZone()
  })

  it('revokes one or all extension sessions and supports collapsing the card', () => {
    const onRevokeAllSessions = vi.fn()
    const onRevokeSession = vi.fn()

    render(
      <ExtensionSessionsPanel
        locale="en"
        onRevokeAllSessions={onRevokeAllSessions}
        onRevokeSession={onRevokeSession}
        revokeAllLoading={false}
        revokeLoadingId={null}
        sessions={[
          {
            id: 12,
            label: 'Firefox profile',
            browserFamily: 'firefox',
            extensionVersion: '0.1.0',
            createdAt: '2026-04-12T10:00:00Z',
            lastUsedAt: null,
            revokedAt: null
          }
        ]}
        t={(key, params = {}) => params.value ? `${key}:${params.value}` : key}
      />
    )

    expect(screen.getByText('extensionSessions.activeTitle')).toBeInTheDocument()

    fireEvent.click(screen.getByText('extensionSessions.revokeAll'))
    expect(onRevokeAllSessions).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByText('extensionSessions.revoke'))
    expect(onRevokeSession).toHaveBeenCalledWith(expect.objectContaining({ id: 12 }))

    fireEvent.click(screen.getByRole('button', { name: 'common.collapseSection' }))
    expect(screen.queryByText('extensionSessions.activeTitle')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'common.expandSection' }))
    expect(screen.getByText('extensionSessions.activeTitle')).toBeInTheDocument()
  })

  it('formats extension timestamps with the active manual date format', () => {
    setCurrentFormattingDateFormat(DATE_FORMAT_YMD_24)
    setCurrentFormattingTimeZone('UTC')

    render(
      <ExtensionSessionsPanel
        locale="en"
        onRevokeAllSessions={vi.fn()}
        onRevokeSession={vi.fn()}
        revokeAllLoading={false}
        revokeLoadingId={null}
        sessions={[
          {
            id: 42,
            label: 'Edge',
            browserFamily: 'edge',
            extensionVersion: '0.2.0',
            createdAt: '2026-04-12T10:00:00Z',
            lastUsedAt: '2026-04-12T10:05:00Z',
            revokedAt: '2026-04-12T10:15:00Z'
          }
        ]}
        t={(key, params = {}) => params.value ? `${key}:${params.value}` : key}
      />
    )

    expect(screen.getByText((value) => value.includes('2026-04-12 10:00:00'))).toBeInTheDocument()
    expect(screen.getByText((value) => value.includes('2026-04-12 10:05:00'))).toBeInTheDocument()
    expect(screen.getByText((value) => value.includes('2026-04-12 10:15:00'))).toBeInTheDocument()
  })
})
