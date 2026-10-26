import { fireEvent, render, screen } from '@testing-library/react'
import EmailAccountPollingDialog from './EmailAccountPollingDialog'
import { translate } from '@/lib/i18n'

describe('EmailAccountPollingDialog', () => {
  it.each([undefined, 'ENABLED'])('renders inherited safety independently of the current override (%s)', (mode) => {
    render(<EmailAccountPollingDialog
      fetcher={{ emailAccountId: 'idle-source', fetchMode: 'IDLE' }}
      form={{ idleSafetyPollingMode: mode, baseIdleSafetyPollingEnabled: mode === undefined }}
      onChange={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} onSave={vi.fn()}
      t={(key, params) => translate('en', key, params)}
    />)
    expect(screen.getByText('Inherit default', { exact: false })).toHaveTextContent(mode === undefined ? 'Yes' : 'No')
  })

  it('renders translated polling settings labels in portuguese', () => {
    render(
      <EmailAccountPollingDialog
        fetcher={{ emailAccountId: 'outlook-main', customLabel: '' }}
        form={{
          pollEnabledMode: 'DEFAULT',
          pollIntervalOverride: '',
          fetchWindowOverride: '',
          basePollEnabled: true,
          basePollInterval: '5m',
          baseFetchWindow: 50,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          isDirty: false
        }}
        loading={false}
        onChange={vi.fn()}
        onClose={vi.fn()}
        onReset={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('outlook-main')).toBeInTheDocument()
    expect(screen.getByText('Modo de polling')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar definições de verificação' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Usar predefinições herdadas' })).toBeInTheDocument()
  })

  it('marks the form dirty when fields change through the parent handler', () => {
    const onChange = vi.fn()

    render(
      <EmailAccountPollingDialog
        fetcher={{ emailAccountId: 'fetcher-1', customLabel: '' }}
        form={{
          pollEnabledMode: 'DEFAULT',
          pollIntervalOverride: '',
          fetchWindowOverride: '',
          basePollEnabled: true,
          basePollInterval: '5m',
          baseFetchWindow: 50,
          effectivePollEnabled: true,
          effectivePollInterval: '5m',
          effectiveFetchWindow: 50,
          isDirty: false
        }}
        loading={false}
        onChange={onChange}
        onClose={vi.fn()}
        onReset={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ENABLED' } })

    expect(onChange).toHaveBeenCalled()
  })

  it('edits an IDLE safety override and supports returning to inherited defaults', () => {
    const onChange = vi.fn()
    render(<EmailAccountPollingDialog
      fetcher={{ emailAccountId: 'idle-source', fetchMode: 'IDLE' }}
      form={{ pollEnabledMode: 'DEFAULT', idleSafetyPollingMode: 'DEFAULT', baseIdleSafetyPollingEnabled: false, pollIntervalOverride: '', fetchWindowOverride: '' }}
      onChange={onChange} onClose={vi.fn()} onReset={vi.fn()} onSave={vi.fn()}
      t={(key, params) => translate('en', key, params)}
    />)
    const policy = screen.getByRole('combobox', { name: /Periodic safety polling/ })
    expect(policy).toHaveTextContent('Inherit defaults (No)')
    fireEvent.change(screen.getByRole('textbox', { name: /^Renewal interval/ }), { target: { value: '10m' } })
    expect(onChange.mock.lastCall[0]({})).toEqual({ idleRenewalIntervalOverride: '10m', isDirty: true })
    fireEvent.change(screen.getByRole('textbox', { name: /Safety polling deadline/ }), { target: { value: '15m' } })
    expect(onChange.mock.lastCall[0]({})).toEqual({ idleSafetyPollIntervalOverride: '15m', isDirty: true })
    fireEvent.change(policy, { target: { value: 'ENABLED' } })
    expect(onChange.mock.lastCall[0]({})).toEqual({ idleSafetyPollingMode: 'ENABLED', isDirty: true })
    fireEvent.change(policy, { target: { value: 'DEFAULT' } })
    expect(onChange.mock.lastCall[0]({})).toEqual({ idleSafetyPollingMode: 'DEFAULT', isDirty: true })
  })

  it('documents fetch window backfill behavior in the source help copy', () => {
    expect(translate('en', 'sourcePolling.fetchWindowHelp')).toContain('does not page backward across older mail automatically')
    expect(translate('en', 'sourcePolling.fetchWindowHelp')).toContain('temporarily raise the window')
  })
})
