export function buildHttpsUrl(locationLike) {
  if (!locationLike || locationLike.protocol !== 'http:') {
    return null
  }
  const host = locationLike.host || locationLike.hostname || ''
  return `https://${host}${locationLike.pathname || '/'}${locationLike.search || ''}${locationLike.hash || ''}`
}

export function enforceHttpsIfNeeded(locationLike = window.location) {
  if (typeof navigator !== 'undefined' && /jsdom/i.test(navigator.userAgent || '')) {
    return false
  }
  const nextUrl = buildHttpsUrl(locationLike)
  if (!nextUrl || typeof locationLike?.replace !== 'function') {
    return false
  }
  locationLike.replace(nextUrl)
  return true
}
