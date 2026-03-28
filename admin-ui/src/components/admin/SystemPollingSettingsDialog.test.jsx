import { fireEvent, render, screen } from '@testing-library/react'
import SystemPollingSettingsDialog from './SystemPollingSettingsDialog'
import { translate } from '../../lib/i18n'

describe('SystemPollingSettingsDialog', () => {
  it('renders the edit form and forwards changes', () => {
    let form = {
      pollEnabledMode: 'DEFAULT',
      pollIntervalOverride: '',
      fetchWindowOverride: '',
      manualTriggerLimitCountOverride: '',
      manualTriggerLimitWindowSecondsOverride: ''
    }

    render(
      <SystemPollingSettingsDialog
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
          effectiveFetchWindow: 25,
          defaultManualTriggerLimitCount: 5,
          manualTriggerLimitCountOverride: null,
          effectiveManualTriggerLimitCount: 5,
          defaultManualTriggerLimitWindowSeconds: 60,
          manualTriggerLimitWindowSecondsOverride: null,
          effectiveManualTriggerLimitWindowSeconds: 60
        }}
        pollingSettingsForm={form}
        pollingSettingsLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.change(screen.getByLabelText(/Polling Mode/), { target: { value: 'DISABLED' } })
    fireEvent.change(screen.getByLabelText(/Poll Interval Override/), { target: { value: '10m' } })
    fireEvent.change(screen.getByLabelText(/Fetch Window Override/), { target: { value: '25' } })
    fireEvent.change(screen.getByLabelText(/Manual Run Limit/), { target: { value: '7' } })
    fireEvent.change(screen.getByLabelText(/Manual Limit Window/), { target: { value: '90' } })

    expect(form.pollEnabledMode).toBe('DISABLED')
    expect(form.pollIntervalOverride).toBe('10m')
    expect(form.fetchWindowOverride).toBe('25')
    expect(form.manualTriggerLimitCountOverride).toBe('7')
    expect(form.manualTriggerLimitWindowSecondsOverride).toBe('90')
  })
})
