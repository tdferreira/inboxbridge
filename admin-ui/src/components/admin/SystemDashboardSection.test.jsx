import { fireEvent, render, screen } from '@testing-library/react'
import SystemDashboardSection from './SystemDashboardSection'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('SystemDashboardSection', () => {
  it('renders polling settings controls and submits override actions', () => {
    const onSavePollingSettings = vi.fn((event) => event.preventDefault())
    const onResetPollingSettings = vi.fn()
    let form = {
      pollEnabledMode: 'DEFAULT',
      pollIntervalOverride: '',
      fetchWindowOverride: ''
    }

    const { rerender } = renderUi()

    fireEvent.change(screen.getByLabelText(/Polling Mode/), { target: { value: 'DISABLED' } })
    fireEvent.change(screen.getByLabelText(/Poll Interval Override/), { target: { value: '2m' } })
    fireEvent.change(screen.getByLabelText(/Fetch Window Override/), { target: { value: '25' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Save Poll Settings' }).closest('form'))
    fireEvent.click(screen.getByRole('button', { name: 'Use Environment Defaults' }))

    expect(onSavePollingSettings).toHaveBeenCalledTimes(1)
    expect(onResetPollingSettings).toHaveBeenCalledTimes(1)
    expect(form.pollEnabledMode).toBe('DISABLED')
    expect(form.pollIntervalOverride).toBe('2m')
    expect(form.fetchWindowOverride).toBe('25')

    function renderUi() {
      return render(
        <SystemDashboardSection
          collapsed={false}
          collapseLoading={false}
          dashboard={{
            overall: {
              configuredSources: 1,
              totalImportedMessages: 4,
              sourcesWithErrors: 0,
              pollInterval: '2m',
              fetchWindow: 25
            },
            polling: {
              defaultPollEnabled: true,
              pollEnabledOverride: false,
              effectivePollEnabled: false,
              defaultPollInterval: '5m',
              pollIntervalOverride: '2m',
              effectivePollInterval: '2m',
              defaultFetchWindow: 50,
              fetchWindowOverride: 25,
              effectiveFetchWindow: 25
            },
            destination: { tokenStorageMode: 'DATABASE' },
            bridges: [],
            recentEvents: []
          }}
          onCollapseToggle={vi.fn()}
          onPollingFormChange={(updater) => {
            form = typeof updater === 'function' ? updater(form) : updater
            rerenderUi()
          }}
          onResetPollingSettings={onResetPollingSettings}
          onRunPoll={vi.fn()}
          onSavePollingSettings={onSavePollingSettings}
          pollingSettingsForm={form}
          pollingSettingsLoading={false}
          runningPoll={false}
          locale="en"
          t={t}
        />
      )
    }

    function rerenderUi() {
      rerender(
        <SystemDashboardSection
          collapsed={false}
          collapseLoading={false}
          dashboard={{
            overall: {
              configuredSources: 1,
              totalImportedMessages: 4,
              sourcesWithErrors: 0,
              pollInterval: '2m',
              fetchWindow: 25
            },
            polling: {
              defaultPollEnabled: true,
              pollEnabledOverride: false,
              effectivePollEnabled: false,
              defaultPollInterval: '5m',
              pollIntervalOverride: '2m',
              effectivePollInterval: '2m',
              defaultFetchWindow: 50,
              fetchWindowOverride: 25,
              effectiveFetchWindow: 25
            },
            destination: { tokenStorageMode: 'DATABASE' },
            bridges: [],
            recentEvents: []
          }}
          onCollapseToggle={vi.fn()}
          onPollingFormChange={(updater) => {
            form = typeof updater === 'function' ? updater(form) : updater
            rerenderUi()
          }}
          onResetPollingSettings={onResetPollingSettings}
          onRunPoll={vi.fn()}
          onSavePollingSettings={onSavePollingSettings}
          pollingSettingsForm={form}
          pollingSettingsLoading={false}
          runningPoll={false}
          locale="en"
          t={t}
        />
      )
    }
  })
})
