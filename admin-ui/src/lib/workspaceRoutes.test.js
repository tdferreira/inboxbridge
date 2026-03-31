import { buildWorkspacePath, canonicalWorkspacePath, resolveWorkspaceRoute } from './workspaceRoutes'

describe('workspaceRoutes', () => {
  it('resolves root, localized user slugs, and localized admin slugs', () => {
    expect(resolveWorkspaceRoute('/')).toEqual({ workspace: 'user', kind: 'root' })
    expect(resolveWorkspaceRoute('/utilizador')).toEqual({ workspace: 'user', kind: 'user' })
    expect(resolveWorkspaceRoute('/administracao')).toEqual({ workspace: 'admin', kind: 'admin' })
  })

  it('builds locale-aware paths while keeping root as the default user path', () => {
    expect(buildWorkspacePath('pt-PT', 'user')).toBe('/')
    expect(buildWorkspacePath('pt-PT', 'user', { explicitUserRoute: true })).toBe('/')
    expect(buildWorkspacePath('pt-PT', 'admin')).toBe('/administracao')
  })

  it('canonicalizes explicit routes per locale and collapses non-admin user routes to root', () => {
    expect(canonicalWorkspacePath('/user', 'pt-PT', true)).toBe('/')
    expect(canonicalWorkspacePath('/admin', 'pt-PT', true)).toBe('/administracao')
    expect(canonicalWorkspacePath('/utilizador', 'pt-PT', false)).toBe('/')
    expect(canonicalWorkspacePath('/', 'pt-PT', false)).toBeNull()
  })
})
