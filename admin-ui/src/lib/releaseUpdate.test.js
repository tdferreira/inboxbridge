import { beforeEach, describe, expect, it, vi } from 'vitest'
import { checkForReleaseUpdate, isNewerRelease, RELEASE_CACHE_TTL_MS } from './releaseUpdate'

describe('release updates', () => {
  beforeEach(() => window.localStorage.clear())

  it('compares stable semantic versions', () => {
    expect(isNewerRelease('0.8.3', '0.8.4')).toBe(true)
    expect(isNewerRelease('0.8.3', '0.9.0')).toBe(true)
    expect(isNewerRelease('0.8.3', '0.8.3')).toBe(false)
    expect(isNewerRelease('0.8.3', '0.8.2')).toBe(false)
  })

  it('caches release checks for one day', () => {
    expect(RELEASE_CACHE_TTL_MS).toBe(24 * 60 * 60 * 1000)
  })

  it('returns only a trusted newer GitHub release', async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        tag_name: 'v0.8.4',
        html_url: 'https://github.com/tdferreira/inboxbridge/releases/tag/v0.8.4'
      })
    })

    await expect(checkForReleaseUpdate('0.8.3', fetcher)).resolves.toMatchObject({
      currentVersion: '0.8.3',
      latestVersion: '0.8.4'
    })
  })
})
