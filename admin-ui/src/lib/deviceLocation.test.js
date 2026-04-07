import { buildMapsUrl, guessDeviceLocationLabel } from './deviceLocation'

describe('deviceLocation', () => {
  it('builds a Google Maps search URL', () => {
    expect(buildMapsUrl(38.7223, -9.1393)).toBe(
      'https://www.google.com/maps/search/?api=1&query=38.7223%2C-9.1393'
    )
  })

  it('formats a friendly label from reverse-geocode results', async () => {
    vi.spyOn(window, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({
        address: {
          city: 'Lisbon',
          country: 'Portugal'
        }
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    )

    await expect(guessDeviceLocationLabel(38.72, -9.13)).resolves.toBe('Lisbon, Portugal')
  })
})
