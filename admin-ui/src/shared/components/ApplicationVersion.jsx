import { useEffect, useState } from 'react'
import { checkForReleaseUpdate } from '@/lib/releaseUpdate'

const applicationVersion = __INBOXBRIDGE_VERSION__

export { applicationVersion }

/** Shows the version embedded in the deployed admin UI bundle. */
function ApplicationVersion({ className = '', t }) {
  const [releaseUpdate, setReleaseUpdate] = useState(null)

  useEffect(() => {
    if (import.meta.env.MODE === 'test') return undefined
    let cancelled = false
    void checkForReleaseUpdate(applicationVersion).then((update) => {
      if (!cancelled) setReleaseUpdate(update)
    })
    return () => { cancelled = true }
  }, [])

  return (
    <p className={`application-version ${className}`.trim()}>
      {t('app.version', { version: applicationVersion })}
      {releaseUpdate ? (
        <a aria-label={t('app.updateAvailable', { version: releaseUpdate.latestVersion })} className="application-version-update" href={releaseUpdate.releaseUrl} rel="noreferrer" target="_blank" title={t('app.updateAvailable', { version: releaseUpdate.latestVersion })}>
          !
        </a>
      ) : null}
    </p>
  )
}

export default ApplicationVersion
