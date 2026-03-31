export const geoIpProviderCatalog = {
  IPWHOIS: {
    id: 'IPWHOIS',
    label: 'IPwho.is',
    requiresToken: false,
    docsUrl: 'https://ipwho.is/',
    termsUrl: 'https://ipwho.is/'
  },
  IPAPI_CO: {
    id: 'IPAPI_CO',
    label: 'ipapi.co',
    requiresToken: false,
    docsUrl: 'https://ipapi.co/',
    termsUrl: 'https://ipapi.co/'
  },
  IP_API: {
    id: 'IP_API',
    label: 'ip-api',
    requiresToken: false,
    docsUrl: 'https://ip-api.com/docs/api:json',
    termsUrl: 'https://members.ip-api.com/legal'
  },
  IPINFO_LITE: {
    id: 'IPINFO_LITE',
    label: 'IPinfo Lite',
    requiresToken: true,
    tokenField: 'ipinfoLiteToken',
    docsUrl: 'https://support.ipinfo.io/hc/en-us/articles/34121895556242-Legacy-Free-API-vs-IPinfo-Lite',
    termsUrl: 'https://ipinfo.io/terms-of-service'
  }
}

export function parseProviderList(value) {
  if (!value) {
    return []
  }
  return value
    .split(',')
    .map((item) => item.trim().toUpperCase())
    .filter(Boolean)
}

export function providerLabel(providerId) {
  return geoIpProviderCatalog[providerId]?.label || providerId
}
