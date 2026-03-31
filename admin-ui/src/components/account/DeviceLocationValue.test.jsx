import { render, screen, waitFor } from '@testing-library/react'
import DeviceLocationValue from './DeviceLocationValue'

describe('DeviceLocationValue', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows a guessed place label and maps link when coordinates are available', async () => {
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
      <DeviceLocationValue
        fallbackLabel="38.72230, -9.13930"
        latitude={38.7223}
        locale="en"
        longitude={-9.1393}
        t={(key) => key}
      />
    )

    await waitFor(() => expect(screen.getByText('Lisbon, Lisbon, PT')).toBeInTheDocument())
    expect(screen.getByRole('link', { name: 'sessions.openInMaps' })).toHaveAttribute(
      'href',
      'https://www.google.com/maps/search/?api=1&query=38.7223%2C-9.1393'
    )
  })

  it('falls back to the stored label when coordinates are unavailable', () => {
    render(
      <DeviceLocationValue
        fallbackLabel="Shared from device"
        latitude={null}
        locale="en"
        longitude={null}
        t={(key) => key}
      />
    )

    expect(screen.getByText('Shared from device')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'sessions.openInMaps' })).not.toBeInTheDocument()
  })
})
