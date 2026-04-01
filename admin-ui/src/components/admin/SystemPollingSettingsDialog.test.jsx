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
      manualTriggerLimitWindowSecondsOverride: '',
      sourceHostMinSpacingOverride: '',
      sourceHostMaxConcurrencyOverride: '',
      destinationProviderMinSpacingOverride: '',
      destinationProviderMaxConcurrencyOverride: '',
      throttleLeaseTtlOverride: '',
      adaptiveThrottleMaxMultiplierOverride: '',
      successJitterRatioOverride: '',
      maxSuccessJitterOverride: ''
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
          effectiveManualTriggerLimitWindowSeconds: 60,
          defaultSourceHostMinSpacing: 'PT1S',
          sourceHostMinSpacingOverride: null,
          effectiveSourceHostMinSpacing: 'PT1S',
          defaultSourceHostMaxConcurrency: 2,
          sourceHostMaxConcurrencyOverride: null,
          effectiveSourceHostMaxConcurrency: 2,
          defaultDestinationProviderMinSpacing: 'PT0.25S',
          destinationProviderMinSpacingOverride: null,
          effectiveDestinationProviderMinSpacing: 'PT0.25S',
          defaultDestinationProviderMaxConcurrency: 1,
          destinationProviderMaxConcurrencyOverride: null,
          effectiveDestinationProviderMaxConcurrency: 1,
          defaultThrottleLeaseTtl: 'PT2M',
          throttleLeaseTtlOverride: null,
          effectiveThrottleLeaseTtl: 'PT2M',
          defaultAdaptiveThrottleMaxMultiplier: 6,
          adaptiveThrottleMaxMultiplierOverride: null,
          effectiveAdaptiveThrottleMaxMultiplier: 6,
          defaultSuccessJitterRatio: 0.2,
          successJitterRatioOverride: null,
          effectiveSuccessJitterRatio: 0.2,
          defaultMaxSuccessJitter: 'PT30S',
          maxSuccessJitterOverride: null,
          effectiveMaxSuccessJitter: 'PT30S'
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
    fireEvent.change(screen.getByLabelText(/Source Host Minimum Spacing/), { target: { value: 'PT2S' } })
    fireEvent.change(screen.getByLabelText(/Source Host Max Concurrency/), { target: { value: '4' } })
    fireEvent.change(screen.getByLabelText(/Destination Provider Minimum Spacing/), { target: { value: 'PT1S' } })
    fireEvent.change(screen.getByLabelText(/Destination Provider Max Concurrency/), { target: { value: '2' } })
    fireEvent.change(screen.getByLabelText(/Throttle Lease TTL/), { target: { value: 'PT3M' } })
    fireEvent.change(screen.getByLabelText(/Adaptive Throttle Max Multiplier/), { target: { value: '8' } })
    fireEvent.change(screen.getByLabelText(/Success Jitter Ratio/), { target: { value: '0.35' } })
    fireEvent.change(screen.getByLabelText(/Maximum Success Jitter/), { target: { value: 'PT45S' } })

    expect(form.pollEnabledMode).toBe('DISABLED')
    expect(form.pollIntervalOverride).toBe('10m')
    expect(form.fetchWindowOverride).toBe('25')
    expect(form.manualTriggerLimitCountOverride).toBe('7')
    expect(form.manualTriggerLimitWindowSecondsOverride).toBe('90')
    expect(form.sourceHostMinSpacingOverride).toBe('PT2S')
    expect(form.sourceHostMaxConcurrencyOverride).toBe('4')
    expect(form.destinationProviderMinSpacingOverride).toBe('PT1S')
    expect(form.destinationProviderMaxConcurrencyOverride).toBe('2')
    expect(form.throttleLeaseTtlOverride).toBe('PT3M')
    expect(form.adaptiveThrottleMaxMultiplierOverride).toBe('8')
    expect(form.successJitterRatioOverride).toBe('0.35')
    expect(form.maxSuccessJitterOverride).toBe('PT45S')
  })

  it('disables the poll interval override when polling is disabled', () => {
    render(
      <SystemPollingSettingsDialog
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
          effectiveFetchWindow: 25,
          defaultManualTriggerLimitCount: 5,
          effectiveManualTriggerLimitCount: 5,
          defaultManualTriggerLimitWindowSeconds: 60,
          effectiveManualTriggerLimitWindowSeconds: 60,
          defaultSourceHostMinSpacing: 'PT1S',
          effectiveSourceHostMinSpacing: 'PT1S',
          defaultSourceHostMaxConcurrency: 2,
          effectiveSourceHostMaxConcurrency: 2,
          defaultDestinationProviderMinSpacing: 'PT0.25S',
          effectiveDestinationProviderMinSpacing: 'PT0.25S',
          defaultDestinationProviderMaxConcurrency: 1,
          effectiveDestinationProviderMaxConcurrency: 1,
          defaultThrottleLeaseTtl: 'PT2M',
          effectiveThrottleLeaseTtl: 'PT2M',
          defaultAdaptiveThrottleMaxMultiplier: 6,
          effectiveAdaptiveThrottleMaxMultiplier: 6,
          defaultSuccessJitterRatio: 0.2,
          effectiveSuccessJitterRatio: 0.2,
          defaultMaxSuccessJitter: 'PT30S',
          effectiveMaxSuccessJitter: 'PT30S'
        }}
        pollingSettingsForm={{
          pollEnabledMode: 'DISABLED',
          pollIntervalOverride: '',
          fetchWindowOverride: '',
          manualTriggerLimitCountOverride: '',
          manualTriggerLimitWindowSecondsOverride: '',
          sourceHostMinSpacingOverride: '',
          sourceHostMaxConcurrencyOverride: '',
          destinationProviderMinSpacingOverride: '',
          destinationProviderMaxConcurrencyOverride: '',
          throttleLeaseTtlOverride: '',
          adaptiveThrottleMaxMultiplierOverride: '',
          successJitterRatioOverride: '',
          maxSuccessJitterOverride: ''
        }}
        pollingSettingsLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByLabelText(/Poll Interval Override/)).toBeDisabled()
  })

  it('documents fetch window backfill behavior in the help copy', () => {
    expect(translate('en', 'system.fetchWindowHelp')).toContain('does not page backward across older mail automatically')
    expect(translate('en', 'system.fetchWindowHelp')).toContain('temporarily raise the window')
  })

  it('renders clearer throttle format guidance with units and examples', () => {
    render(
      <SystemPollingSettingsDialog
        isDirty={false}
        onClose={vi.fn()}
        onPollingFormChange={vi.fn()}
        onResetPollingSettings={vi.fn()}
        onSavePollingSettings={vi.fn((event) => event.preventDefault())}
        pollingSettings={{
          defaultPollEnabled: true,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          effectivePollInterval: '5m',
          defaultFetchWindow: 50,
          effectiveFetchWindow: 50,
          defaultManualTriggerLimitCount: 5,
          effectiveManualTriggerLimitCount: 5,
          defaultManualTriggerLimitWindowSeconds: 60,
          effectiveManualTriggerLimitWindowSeconds: 60,
          defaultSourceHostMinSpacing: 'PT1S',
          effectiveSourceHostMinSpacing: 'PT1S',
          defaultSourceHostMaxConcurrency: 2,
          effectiveSourceHostMaxConcurrency: 2,
          defaultDestinationProviderMinSpacing: 'PT0.25S',
          effectiveDestinationProviderMinSpacing: 'PT0.25S',
          defaultDestinationProviderMaxConcurrency: 1,
          effectiveDestinationProviderMaxConcurrency: 1,
          defaultThrottleLeaseTtl: 'PT2M',
          effectiveThrottleLeaseTtl: 'PT2M',
          defaultAdaptiveThrottleMaxMultiplier: 6,
          effectiveAdaptiveThrottleMaxMultiplier: 6,
          defaultSuccessJitterRatio: 0.2,
          effectiveSuccessJitterRatio: 0.2,
          defaultMaxSuccessJitter: 'PT30S',
          effectiveMaxSuccessJitter: 'PT30S'
        }}
        pollingSettingsForm={{
          pollEnabledMode: 'DEFAULT',
          pollIntervalOverride: '',
          fetchWindowOverride: '',
          manualTriggerLimitCountOverride: '',
          manualTriggerLimitWindowSecondsOverride: '',
          sourceHostMinSpacingOverride: '',
          sourceHostMaxConcurrencyOverride: '',
          destinationProviderMinSpacingOverride: '',
          destinationProviderMaxConcurrencyOverride: '',
          throttleLeaseTtlOverride: '',
          adaptiveThrottleMaxMultiplierOverride: '',
          successJitterRatioOverride: '',
          maxSuccessJitterOverride: ''
        }}
        pollingSettingsLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText(/Fractional seconds must use ISO-8601/i)).toBeInTheDocument()
    expect(screen.getByText(/PT0.25S for 250 milliseconds/i)).toBeInTheDocument()
    expect(screen.getByText(/Decimal from 0 to 1/i)).toBeInTheDocument()
  })

  it('organizes the form into grouped admin subsections', () => {
    render(
      <SystemPollingSettingsDialog
        isDirty={false}
        onClose={vi.fn()}
        onPollingFormChange={vi.fn()}
        onResetPollingSettings={vi.fn()}
        onSavePollingSettings={vi.fn((event) => event.preventDefault())}
        pollingSettings={{
          defaultPollEnabled: true,
          effectivePollEnabled: true,
          defaultPollInterval: '5m',
          effectivePollInterval: '5m',
          defaultFetchWindow: 50,
          effectiveFetchWindow: 50,
          defaultManualTriggerLimitCount: 5,
          effectiveManualTriggerLimitCount: 5,
          defaultManualTriggerLimitWindowSeconds: 60,
          effectiveManualTriggerLimitWindowSeconds: 60,
          defaultSourceHostMinSpacing: 'PT1S',
          effectiveSourceHostMinSpacing: 'PT1S',
          defaultSourceHostMaxConcurrency: 2,
          effectiveSourceHostMaxConcurrency: 2,
          defaultDestinationProviderMinSpacing: 'PT0.25S',
          effectiveDestinationProviderMinSpacing: 'PT0.25S',
          defaultDestinationProviderMaxConcurrency: 1,
          effectiveDestinationProviderMaxConcurrency: 1,
          defaultThrottleLeaseTtl: 'PT2M',
          effectiveThrottleLeaseTtl: 'PT2M',
          defaultAdaptiveThrottleMaxMultiplier: 6,
          effectiveAdaptiveThrottleMaxMultiplier: 6,
          defaultSuccessJitterRatio: 0.2,
          effectiveSuccessJitterRatio: 0.2,
          defaultMaxSuccessJitter: 'PT30S',
          effectiveMaxSuccessJitter: 'PT30S'
        }}
        pollingSettingsForm={{
          pollEnabledMode: 'DEFAULT',
          pollIntervalOverride: '',
          fetchWindowOverride: '',
          manualTriggerLimitCountOverride: '',
          manualTriggerLimitWindowSecondsOverride: '',
          sourceHostMinSpacingOverride: '',
          sourceHostMaxConcurrencyOverride: '',
          destinationProviderMinSpacingOverride: '',
          destinationProviderMaxConcurrencyOverride: '',
          throttleLeaseTtlOverride: '',
          adaptiveThrottleMaxMultiplierOverride: '',
          successJitterRatioOverride: '',
          maxSuccessJitterOverride: ''
        }}
        pollingSettingsLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Scheduler Defaults')).toBeInTheDocument()
    expect(screen.getByText('Manual Runs')).toBeInTheDocument()
    expect(screen.getByText('Source Mailbox Pacing')).toBeInTheDocument()
    expect(screen.getByText('Destination Delivery Pacing')).toBeInTheDocument()
    expect(screen.getByText('Adaptive Recovery and Jitter')).toBeInTheDocument()
    expect(screen.getByText('Effective Values')).toBeInTheDocument()
  })
})
