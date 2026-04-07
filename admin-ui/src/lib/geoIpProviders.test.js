import { geoIpProviderCatalog, parseProviderList, providerLabel } from './geoIpProviders'

describe('geoIpProviders', () => {
  it('parses providers and resolves display labels', () => {
    expect(parseProviderList('IPWHOIS, IP_API')).toEqual(['IPWHOIS', 'IP_API'])
    expect(providerLabel('IPAPI_CO')).toBe('ipapi.co')
  })

  it('exposes provider metadata for the admin UI', () => {
    expect(Object.values(geoIpProviderCatalog)).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ id: 'IPWHOIS' }),
        expect.objectContaining({ id: 'IP_API' })
      ])
    )
  })
})
