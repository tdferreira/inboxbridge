export const THEME_MODE_SYSTEM = 'SYSTEM'
export const THEME_MODE_LIGHT_GREEN = 'LIGHT_GREEN'
export const THEME_MODE_LIGHT_BLUE = 'LIGHT_BLUE'
export const THEME_MODE_DARK_GREEN = 'DARK_GREEN'
export const THEME_MODE_DARK_BLUE = 'DARK_BLUE'

const STORAGE_KEY = 'inboxbridge.themeMode'

export function normalizeThemeMode(value) {
  const normalized = String(value || '').trim().toUpperCase()
  if (normalized === 'LIGHT') {
    return THEME_MODE_LIGHT_GREEN
  }
  if (normalized === 'DARK') {
    return THEME_MODE_DARK_BLUE
  }
  if ([THEME_MODE_LIGHT_GREEN, THEME_MODE_LIGHT_BLUE, THEME_MODE_DARK_GREEN, THEME_MODE_DARK_BLUE].includes(normalized)) {
    return normalized
  }
  return THEME_MODE_SYSTEM
}

export function detectSystemTheme() {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return 'light'
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function resolveEffectiveTheme(themeMode) {
  const normalized = normalizeThemeMode(themeMode)
  if (normalized.startsWith('LIGHT_')) {
    return 'light'
  }
  if (normalized.startsWith('DARK_')) {
    return 'dark'
  }
  return detectSystemTheme()
}

export function resolveThemeVariant(themeMode) {
  const normalized = normalizeThemeMode(themeMode)
  if (normalized.endsWith('_BLUE')) {
    return 'blue'
  }
  if (normalized.endsWith('_GREEN')) {
    return 'green'
  }
  return resolveEffectiveTheme(normalized) === 'dark' ? 'blue' : 'green'
}

export function applyDocumentTheme(themeMode, target = document) {
  if (!target?.documentElement) {
    return resolveEffectiveTheme(themeMode)
  }
  const normalized = normalizeThemeMode(themeMode)
  const effectiveTheme = resolveEffectiveTheme(normalized)
  const themeVariant = resolveThemeVariant(normalized)
  target.documentElement.dataset.themeMode = normalized.toLowerCase()
  target.documentElement.dataset.theme = effectiveTheme
  target.documentElement.dataset.themeVariant = themeVariant
  return effectiveTheme
}

export function readStoredThemePreference() {
  if (typeof window === 'undefined') {
    return { themeMode: THEME_MODE_SYSTEM }
  }
  return {
    themeMode: normalizeThemeMode(window.localStorage.getItem(STORAGE_KEY))
  }
}

export function writeStoredThemePreference(preference) {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(STORAGE_KEY, normalizeThemeMode(preference?.themeMode))
}
