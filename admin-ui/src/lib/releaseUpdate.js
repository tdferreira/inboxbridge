const RELEASE_ENDPOINT = 'https://api.github.com/repos/tdferreira/inboxbridge/releases/latest'
const RELEASE_CACHE_KEY = 'inboxbridge.releaseUpdate'
export const RELEASE_CACHE_TTL_MS = 24 * 60 * 60 * 1000

export function isNewerRelease(currentVersion, candidateVersion) {
  const current = parseVersion(currentVersion)
  const candidate = parseVersion(candidateVersion)
  if (!current || !candidate) return false
  return candidate.some((part, index) => part !== current[index] && part > current[index])
}

export async function checkForReleaseUpdate(currentVersion, fetcher = window.fetch.bind(window)) {
  const cached = readCache()
  if (cached) return toUpdate(currentVersion, cached)

  try {
    const response = await fetcher(RELEASE_ENDPOINT, { headers: { Accept: 'application/vnd.github+json' } })
    if (!response.ok) return null
    const release = await response.json()
    const latestVersion = String(release?.tag_name || '').replace(/^v/, '')
    const releaseUrl = String(release?.html_url || '')
    if (!parseVersion(latestVersion) || !isTrustedReleaseUrl(releaseUrl)) return null
    const value = { latestVersion, releaseUrl, checkedAt: Date.now() }
    window.localStorage.setItem(RELEASE_CACHE_KEY, JSON.stringify(value))
    return toUpdate(currentVersion, value)
  } catch {
    return null
  }
}

function readCache() {
  try {
    const cached = JSON.parse(window.localStorage.getItem(RELEASE_CACHE_KEY) || 'null')
    if (!cached || Date.now() - cached.checkedAt > RELEASE_CACHE_TTL_MS) return null
    return cached
  } catch {
    return null
  }
}

function toUpdate(currentVersion, release) {
  return isNewerRelease(currentVersion, release.latestVersion)
    ? { currentVersion, ...release }
    : null
}

function parseVersion(value) {
  const match = String(value || '').match(/^v?(\d+)\.(\d+)\.(\d+)$/)
  return match ? match.slice(1).map(Number) : null
}

function isTrustedReleaseUrl(value) {
  return /^https:\/\/github\.com\/tdferreira\/inboxbridge\/releases\/tag\/v\d+\.\d+\.\d+$/.test(value)
}
