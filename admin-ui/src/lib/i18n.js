import english from './i18n/locales/en.js'

export const languageOptions = Object.freeze([
  'en',
  'fr',
  'de',
  'pt-PT',
  'pt-BR',
  'es'
])

const localeModuleLoaders = import.meta.glob('./i18n/locales/*.js', { import: 'default' })
const loadedTranslations = { en: english }
const pendingLocaleLoads = new Map()

if (import.meta.env.MODE === 'test') {
  const eagerLocaleModules = import.meta.glob('./i18n/locales/*.js', { eager: true, import: 'default' })
  Object.entries(eagerLocaleModules).forEach(([modulePath, dictionary]) => {
    loadedTranslations[localeFromModulePath(modulePath)] = dictionary
  })
}

export const translationCatalog = loadedTranslations

function localeFromModulePath(modulePath) {
  return modulePath.match(/\/([^/]+)\.js$/)?.[1] || 'en'
}

function localeModulePath(locale) {
  return `./i18n/locales/${locale}.js`
}

export function normalizeLocale(locale) {
  if (!locale) return 'en'
  const localeValue = String(locale)
  const exact = languageOptions.find((candidate) => candidate.toLowerCase() === localeValue.toLowerCase())
  if (exact) return exact
  if (localeValue.toLowerCase().startsWith('pt-br')) return 'pt-BR'
  if (localeValue.toLowerCase().startsWith('pt')) return 'pt-PT'
  if (localeValue.toLowerCase().startsWith('fr')) return 'fr'
  if (localeValue.toLowerCase().startsWith('de')) return 'de'
  if (localeValue.toLowerCase().startsWith('es')) return 'es'
  return 'en'
}

function interpolate(template, params = {}) {
  return template.replace(/\{(\w+)\}/g, (_, key) => params[key] ?? `{${key}}`)
}

export function isLocaleLoaded(locale) {
  return Boolean(loadedTranslations[normalizeLocale(locale)])
}

/**
 * Loads an additional locale catalog on demand. Production preloads only the
 * active locale before boot, while tests eagerly hydrate every locale so the
 * existing synchronous translation assertions remain stable.
 */
export async function ensureLocaleLoaded(locale) {
  const normalized = normalizeLocale(locale)
  if (loadedTranslations[normalized]) {
    return loadedTranslations[normalized]
  }
  if (pendingLocaleLoads.has(normalized)) {
    return pendingLocaleLoads.get(normalized)
  }

  const loader = localeModuleLoaders[localeModulePath(normalized)]
  if (!loader) {
    return english
  }

  const loadPromise = loader()
    .then((dictionary) => {
      loadedTranslations[normalized] = dictionary
      pendingLocaleLoads.delete(normalized)
      return dictionary
    })
    .catch((error) => {
      pendingLocaleLoads.delete(normalized)
      throw error
    })

  pendingLocaleLoads.set(normalized, loadPromise)
  return loadPromise
}

export function translate(locale, key, params) {
  const normalized = normalizeLocale(locale)
  const value = loadedTranslations[normalized]?.[key] || english[key] || key
  return interpolate(value, params)
}
