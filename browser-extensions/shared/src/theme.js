export const THEME_FOLLOW_USER = 'user'
export const THEME_SYSTEM = 'system'
export const THEME_LIGHT_GREEN = 'light-green'
export const THEME_LIGHT_BLUE = 'light-blue'
export const THEME_DARK_GREEN = 'dark-green'
export const THEME_DARK_BLUE = 'dark-blue'

const VALID_THEMES = new Set([
  THEME_FOLLOW_USER,
  THEME_SYSTEM,
  THEME_LIGHT_GREEN,
  THEME_LIGHT_BLUE,
  THEME_DARK_GREEN,
  THEME_DARK_BLUE,
  'light',
  'dark'
])

export function normalizeThemePreference(value) {
  if (value === 'light') {
    return THEME_LIGHT_GREEN
  }
  if (value === 'dark') {
    return THEME_DARK_BLUE
  }
  return VALID_THEMES.has(value) ? value : THEME_SYSTEM
}

export function resolveThemePreference(value, userThemeMode = 'SYSTEM') {
  const theme = normalizeThemePreference(value)
  if (theme !== THEME_FOLLOW_USER) {
    return resolveThemeDescriptor(theme)
  }
  return resolveThemeDescriptor(mapUserThemeMode(userThemeMode))
}

export function applyThemePreference(targetDocument, value, userThemeMode = 'SYSTEM') {
  if (!targetDocument?.documentElement) {
    return
  }
  const resolved = resolveThemePreference(value, userThemeMode)
  targetDocument.documentElement.dataset.theme = resolved.mode
  targetDocument.documentElement.dataset.themeVariant = resolved.variant
}

function mapUserThemeMode(userThemeMode) {
  const normalizedUserTheme = String(userThemeMode || '').trim().toUpperCase()
  switch (normalizedUserTheme) {
    case 'LIGHT':
    case 'LIGHT_GREEN':
      return THEME_LIGHT_GREEN
    case 'LIGHT_BLUE':
      return THEME_LIGHT_BLUE
    case 'DARK':
    case 'DARK_BLUE':
      return THEME_DARK_BLUE
    case 'DARK_GREEN':
      return THEME_DARK_GREEN
    default:
      return THEME_SYSTEM
  }
}

function resolveThemeDescriptor(theme) {
  switch (theme) {
    case THEME_LIGHT_GREEN:
      return { mode: 'light', variant: 'green' }
    case THEME_LIGHT_BLUE:
      return { mode: 'light', variant: 'blue' }
    case THEME_DARK_GREEN:
      return { mode: 'dark', variant: 'green' }
    case THEME_DARK_BLUE:
      return { mode: 'dark', variant: 'blue' }
    default:
      return { mode: 'system', variant: 'blue' }
  }
}
