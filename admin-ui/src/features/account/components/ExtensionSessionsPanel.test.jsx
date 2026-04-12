import { act, fireEvent, render, screen } from '@testing-library/react'
import ExtensionSessionsPanel from './ExtensionSessionsPanel'
import { DATE_FORMAT_YMD_24, resetCurrentFormattingDateFormat, setCurrentFormattingDateFormat } from '@/lib/formatters'
import { resetCurrentFormattingTimeZone, setCurrentFormattingTimeZone } from '@/lib/timeZonePreferences'

describe('ExtensionSessionsPanel', () => {
  afterEach(() => {
    resetCurrentFormattingDateFormat()
    resetCurrentFormattingTimeZone()
    vi.unstubAllGlobals()
  })

  it('creates and revokes extension sessions while showing the latest token for copy', async () => {
    const onClearLatestCreatedSession = vi.fn()
    const onCreateSession = vi.fn().mockResolvedValue(undefined)
    const onRevokeSession = vi.fn()

    render(
      <ExtensionSessionsPanel
        createLoading={false}
        latestCreatedSession={{
          id: 11,
          label: 'Desktop',
          token: 'ibx_secret_token'
        }}
        locale="en"
        onClearLatestCreatedSession={onClearLatestCreatedSession}
        onCreateSession={onCreateSession}
        onRevokeSession={onRevokeSession}
        revokeLoadingId={null}
        sessions={[
          {
            id: 12,
            label: 'Firefox profile',
            browserFamily: 'firefox',
            extensionVersion: '0.1.0',
            tokenPrefix: 'ibx_firefox',
            createdAt: '2026-04-12T10:00:00Z',
            lastUsedAt: null,
            revokedAt: null
          }
        ]}
        t={(key, params = {}) => params.value ? `${key}:${params.value}` : key}
      />
    )

    expect(screen.getByText('extensionSessions.latestTokenTitle')).toBeInTheDocument()
    expect(screen.getByText('ibx_secret_token')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('extensionSessions.label'), {
      target: { value: 'Work laptop' }
    })
    await act(async () => {
      fireEvent.submit(screen.getByRole('button', { name: 'extensionSessions.create' }).form)
    })

    expect(onCreateSession).toHaveBeenCalledWith(expect.objectContaining({
      browserFamily: expect.any(String),
      extensionVersion: 'manual-bootstrap',
      label: 'Work laptop'
    }))

    fireEvent.click(screen.getByText('extensionSessions.revoke'))
    expect(onRevokeSession).toHaveBeenCalledWith(expect.objectContaining({ id: 12 }))

    fireEvent.click(screen.getByText('common.dismissNotification'))
    expect(onClearLatestCreatedSession).toHaveBeenCalledTimes(1)
  })

  it('formats extension timestamps with the active manual date format', () => {
    setCurrentFormattingDateFormat(DATE_FORMAT_YMD_24)
    setCurrentFormattingTimeZone('UTC')

    render(
      <ExtensionSessionsPanel
        createLoading={false}
        latestCreatedSession={null}
        locale="en"
        onClearLatestCreatedSession={vi.fn()}
        onCreateSession={vi.fn()}
        onRevokeSession={vi.fn()}
        revokeLoadingId={null}
        sessions={[
          {
            id: 42,
            label: 'Edge',
            browserFamily: 'edge',
            extensionVersion: '0.2.0',
            tokenPrefix: 'ibx_edge',
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
