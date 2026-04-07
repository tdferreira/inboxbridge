import { useEffect, useState } from 'react'
import ButtonLink from '@/shared/components/ButtonLink'
import { buildMapsUrl, guessDeviceLocationLabel } from '@/lib/deviceLocation'

function DeviceLocationValue({
  fallbackLabel = '',
  latitude,
  longitude,
  locale,
  placeholderLabel = '',
  t
}) {
  const [guessedLabel, setGuessedLabel] = useState('')

  useEffect(() => {
    let cancelled = false
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      setGuessedLabel('')
      return () => {
        cancelled = true
      }
    }
    guessDeviceLocationLabel(latitude, longitude, locale).then((value) => {
      if (!cancelled) {
        setGuessedLabel(value)
      }
    }).catch(() => {
      if (!cancelled) {
        setGuessedLabel('')
      }
    })
    return () => {
      cancelled = true
    }
  }, [latitude, longitude, locale])

  const mapsUrl = buildMapsUrl(latitude, longitude)
  const label = guessedLabel || fallbackLabel || placeholderLabel || t('sessions.deviceLocationUnavailable')

  return (
    <span className="session-device-location">
      <span>{label}</span>
      {mapsUrl ? (
        <ButtonLink
          aria-label={t('sessions.openInMaps')}
          className="session-device-location-link"
          href={mapsUrl}
          rel="noreferrer"
          target="_blank"
          tone="secondary"
        >
          {t('sessions.openInMaps')}
        </ButtonLink>
      ) : null}
    </span>
  )
}

export default DeviceLocationValue
