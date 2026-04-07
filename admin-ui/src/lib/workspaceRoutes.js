import { normalizeLocale } from '@/lib/i18n'

export const WORKSPACE_ROUTE_SEGMENTS = Object.freeze({
  en: Object.freeze({ user: 'user', admin: 'admin' }),
  fr: Object.freeze({ user: 'utilisateur', admin: 'administration' }),
  de: Object.freeze({ user: 'benutzer', admin: 'verwaltung' }),
  'pt-PT': Object.freeze({ user: 'utilizador', admin: 'administracao' }),
  'pt-BR': Object.freeze({ user: 'usuario', admin: 'administracao' }),
  es: Object.freeze({ user: 'usuario', admin: 'administracion' })
})

const WORKSPACE_ROUTE_ALIASES = Object.freeze(Object.entries(WORKSPACE_ROUTE_SEGMENTS).reduce((aliases, [, segments]) => {
  Object.entries(segments).forEach(([workspace, segment]) => {
    if (!aliases[workspace]) {
      aliases[workspace] = new Set()
    }
    aliases[workspace].add(segment)
  })
  return aliases
}, {}))

function decodePathSegment(segment) {
  if (!segment) {
    return ''
  }
  try {
    return decodeURIComponent(segment)
  } catch {
    return segment
  }
}

function normalizePathSegment(segment) {
  return decodePathSegment(segment).trim().toLowerCase()
}

export function resolveWorkspaceRoute(pathname) {
  const path = String(pathname || '/').trim() || '/'
  const [segment = ''] = path.split('/').filter(Boolean)

  if (!segment) {
    return { workspace: 'user', kind: 'root' }
  }

  const normalizedSegment = normalizePathSegment(segment)
  if (WORKSPACE_ROUTE_ALIASES.user?.has(normalizedSegment)) {
    return { workspace: 'user', kind: 'user' }
  }
  if (WORKSPACE_ROUTE_ALIASES.admin?.has(normalizedSegment)) {
    return { workspace: 'admin', kind: 'admin' }
  }

  return { workspace: 'user', kind: 'unknown' }
}

export function buildWorkspacePath(locale, workspace, options = {}) {
  const normalizedLocale = normalizeLocale(locale)

  if (workspace !== 'admin') {
    return '/'
  }

  const segments = WORKSPACE_ROUTE_SEGMENTS[normalizedLocale] || WORKSPACE_ROUTE_SEGMENTS.en
  return `/${segments[workspace === 'admin' ? 'admin' : 'user']}`
}

export function canonicalWorkspacePath(pathname, locale, isAdmin) {
  const route = resolveWorkspaceRoute(pathname)

  if (!isAdmin) {
    return route.kind === 'root' ? null : '/'
  }

  if (route.kind === 'root') {
    return null
  }
  if (route.kind === 'admin') {
    return buildWorkspacePath(locale, 'admin')
  }
  if (route.kind === 'user') {
    return '/'
  }

  return '/'
}
