const reverseGeocodeCache = new Map()

function roundedCoordinate(value) {
  return Number(value).toFixed(5)
}

function coordinateKey(latitude, longitude, locale) {
  return `${roundedCoordinate(latitude)}:${roundedCoordinate(longitude)}:${locale || 'en'}`
}

function cityLike(address = {}) {
  return address.city || address.town || address.village || address.municipality || address.county || address.state_district || ''
}

function regionLike(address = {}) {
  return address.state || address.region || address.province || ''
}

function countryLike(address = {}) {
  return address.country_code ? String(address.country_code).toUpperCase() : (address.country || '')
}

function fallbackLabel(latitude, longitude) {
  return `${roundedCoordinate(latitude)}, ${roundedCoordinate(longitude)}`
}

export function buildMapsUrl(latitude, longitude) {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return ''
  }
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${latitude},${longitude}`)}`
}

export async function guessDeviceLocationLabel(latitude, longitude, locale) {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return ''
  }
  const cacheKey = coordinateKey(latitude, longitude, locale)
  if (reverseGeocodeCache.has(cacheKey)) {
    return reverseGeocodeCache.get(cacheKey)
  }

  const url = new URL('https://nominatim.openstreetmap.org/reverse')
  url.searchParams.set('format', 'jsonv2')
  url.searchParams.set('lat', String(latitude))
  url.searchParams.set('lon', String(longitude))
  url.searchParams.set('zoom', '10')
  url.searchParams.set('addressdetails', '1')
  if (locale) {
    url.searchParams.set('accept-language', locale)
  }

  try {
    const response = await fetch(url.toString(), {
      headers: {
        Accept: 'application/json'
      }
    })
    if (!response.ok) {
      throw new Error(`Reverse geocode failed (${response.status})`)
    }
    const payload = await response.json()
    const primary = cityLike(payload?.address)
    const secondary = regionLike(payload?.address)
    const country = countryLike(payload?.address)
    const guess = [primary, secondary, country].filter(Boolean).join(', ') || payload?.display_name || fallbackLabel(latitude, longitude)
    reverseGeocodeCache.set(cacheKey, guess)
    return guess
  } catch {
    const fallback = fallbackLabel(latitude, longitude)
    reverseGeocodeCache.set(cacheKey, fallback)
    return fallback
  }
}
