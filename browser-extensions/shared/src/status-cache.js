import { storageGet, storageSet } from './browser.js'

const STATUS_CACHE_KEY = 'inboxbridgeExtensionStatus'

export async function loadCachedStatus() {
  const stored = await storageGet(STATUS_CACHE_KEY)
  return stored?.[STATUS_CACHE_KEY] || null
}

export async function saveCachedStatus(status) {
  await storageSet({
    [STATUS_CACHE_KEY]: {
      fetchedAt: Date.now(),
      status
    }
  })
}
