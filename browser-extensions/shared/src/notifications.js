/**
 * Normalizes browser-notification payloads so per-browser wrappers can safely
 * inject required defaults without letting undefined caller fields erase them.
 */
export function withBrowserNotificationDefaults(options, defaults) {
  const normalizedOptions = {
    ...(options || {})
  }
  const normalizedDefaults = {
    ...(defaults || {})
  }

  for (const [key, value] of Object.entries(normalizedOptions)) {
    if (value === undefined || value === null || value === '') {
      delete normalizedOptions[key]
    }
  }

  return {
    ...normalizedDefaults,
    ...normalizedOptions
  }
}
