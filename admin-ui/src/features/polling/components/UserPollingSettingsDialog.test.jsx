import { fireEvent, render, screen } from '@testing-library/react'
import UserPollingSettingsDialog from './UserPollingSettingsDialog'
import { translate } from '@/lib/i18n'

describe('UserPollingSettingsDialog', () => {
  it('renders the edit form and forwards changes', () => {
    let form = {
      pollEnabledMode: 'DEFAULT',
      pollIntervalOverride: '',
      fetchWindowOverride: ''
    }

    render(
      <UserPollingSettingsDialog
        isDirty={false}
        onClose={vi.fn()}
        onPollingFormChange={(updater) => {
          form = typeof updater === 'function' ? updater(form) : updater
        }}
        onResetPollingSettings={vi.fn()}
        onSavePollingSettings={vi.fn((event) => event.preventDefault())}
        pollingSettings={{
          defaultPollEnabled: true,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          effectivePollInterval: '2m',
          defaultFetchWindow: 50,
          effectiveFetchWindow: 50
        }}
        pollingSettingsForm={form}
        pollingSettingsLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.change(screen.getByLabelText(/Polling Mode/), { target: { value: 'DISABLED' } })
    fireEvent.change(screen.getByLabelText(/Poll Interval Override/), { target: { value: '10m' } })
    fireEvent.change(screen.getByLabelText(/Fetch Window Override/), { target: { value: '25' } })

    expect(form.pollEnabledMode).toBe('DISABLED')
    expect(form.pollIntervalOverride).toBe('10m')
    expect(form.fetchWindowOverride).toBe('25')
  })

  it('disables the poll interval override when polling is disabled', () => {
    render(
      <UserPollingSettingsDialog
        isDirty={false}
        onClose={vi.fn()}
        onPollingFormChange={vi.fn()}
        onResetPollingSettings={vi.fn()}
        onSavePollingSettings={vi.fn((event) => event.preventDefault())}
        pollingSettings={{
          defaultPollEnabled: true,
          effectivePollEnabled: false,
          defaultPollInterval: '5m',
          effectivePollInterval: 'disabled',
          defaultFetchWindow: 50,
          effectiveFetchWindow: 50
        }}
        pollingSettingsForm={{
          pollEnabledMode: 'DISABLED',
          pollIntervalOverride: '',
          fetchWindowOverride: ''
        }}
        pollingSettingsLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByLabelText(/Poll Interval Override/)).toBeDisabled()
  })

  it('renders grouped scheduler and effective-value sections', () => {
    render(
      <UserPollingSettingsDialog
        isDirty={false}
        onClose={vi.fn()}
        onPollingFormChange={vi.fn()}
        onResetPollingSettings={vi.fn()}
        onSavePollingSettings={vi.fn((event) => event.preventDefault())}
        pollingSettings={{
          defaultPollEnabled: true,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          effectivePollInterval: '2m',
          defaultFetchWindow: 50,
          effectiveFetchWindow: 50
        }}
        pollingSettingsForm={{
          pollEnabledMode: 'DEFAULT',
          pollIntervalOverride: '',
          fetchWindowOverride: ''
        }}
        pollingSettingsLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('My Polling Defaults')).toBeInTheDocument()
    expect(screen.getByText('Effective Values')).toBeInTheDocument()
  })

  it('documents fetch window backfill behavior in the help copy', () => {
    expect(translate('en', 'userPolling.fetchWindowHelp')).toContain('does not page backward across older mail automatically')
    expect(translate('en', 'userPolling.fetchWindowHelp')).toContain('temporarily raise the window')
  })
})
