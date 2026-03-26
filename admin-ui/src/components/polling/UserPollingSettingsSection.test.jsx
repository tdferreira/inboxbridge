import { fireEvent, render, screen } from '@testing-library/react'
import UserPollingSettingsSection from './UserPollingSettingsSection'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('UserPollingSettingsSection', () => {
  it('renders user polling controls and forwards override edits', () => {
    let form = {
      pollEnabledMode: 'DEFAULT',
      pollIntervalOverride: '',
      fetchWindowOverride: ''
    }

    render(
      <UserPollingSettingsSection
        collapsed={false}
        collapseLoading={false}
        hasFetchers
        onCollapseToggle={vi.fn()}
        onPollingFormChange={(updater) => {
          form = typeof updater === 'function' ? updater(form) : updater
        }}
        onResetPollingSettings={vi.fn()}
        onSavePollingSettings={vi.fn((event) => event.preventDefault())}
        pollingSettings={{
          defaultPollEnabled: true,
          pollEnabledOverride: null,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          pollIntervalOverride: null,
          effectivePollInterval: '5m',
          defaultFetchWindow: 50,
          fetchWindowOverride: null,
          effectiveFetchWindow: 50
        }}
        pollingSettingsForm={form}
        pollingSettingsLoading={false}
        t={t}
      />
    )

    fireEvent.change(screen.getByLabelText(/Polling Mode/), { target: { value: 'DISABLED' } })
    fireEvent.change(screen.getByLabelText(/Poll Interval Override/), { target: { value: '2m' } })
    fireEvent.change(screen.getByLabelText(/Fetch Window Override/), { target: { value: '20' } })

    expect(form.pollEnabledMode).toBe('DISABLED')
    expect(form.pollIntervalOverride).toBe('2m')
    expect(form.fetchWindowOverride).toBe('20')
  })
})
