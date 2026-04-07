import { fireEvent, render, screen } from '@testing-library/react'
import DeviceLocationPrompt from './DeviceLocationPrompt'
import { translate } from '@/lib/i18n'

describe('DeviceLocationPrompt', () => {
  const t = (key, params) => translate('en', key, params)

  it('renders success and error states and wires the actions', () => {
    const onDismiss = vi.fn()
    const onRequestLocation = vi.fn()

    render(
      <DeviceLocationPrompt
        error="Location unavailable"
        onDismiss={onDismiss}
        onRequestLocation={onRequestLocation}
        success="Location saved"
        t={t}
      />
    )

    expect(screen.getByText('Location saved')).toBeInTheDocument()
    expect(screen.getByText('Location unavailable')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Share Device Location' }))
    fireEvent.click(screen.getByRole('button', { name: 'Not now' }))

    expect(onRequestLocation).toHaveBeenCalledTimes(1)
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })
})
