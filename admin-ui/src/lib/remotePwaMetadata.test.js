import { describe, expect, it } from 'vitest'
import { applyRemotePwaMetadata } from './remotePwaMetadata'

describe('applyRemotePwaMetadata', () => {
  it('adds the remote manifest and install-related metadata to the document head', () => {
    applyRemotePwaMetadata(document)

    expect(document.head.querySelector('link[rel="manifest"]')).toHaveAttribute('href', '/remote.webmanifest')
    expect(document.head.querySelector('meta[name="theme-color"]')).toHaveAttribute('content', '#1d5fbf')
    expect(document.head.querySelector('meta[name="mobile-web-app-capable"]')).toHaveAttribute('content', 'yes')
    expect(document.head.querySelector('meta[name="apple-mobile-web-app-capable"]')).toHaveAttribute('content', 'yes')
    expect(document.head.querySelector('meta[name="apple-mobile-web-app-title"]')).toHaveAttribute('content', 'InboxBridge Go')
  })

  it('reuses existing metadata nodes instead of duplicating them', () => {
    applyRemotePwaMetadata(document)
    applyRemotePwaMetadata(document)

    expect(document.head.querySelectorAll('link[rel="manifest"]')).toHaveLength(1)
    expect(document.head.querySelectorAll('meta[name="theme-color"]')).toHaveLength(1)
    expect(document.head.querySelectorAll('meta[name="mobile-web-app-capable"]')).toHaveLength(1)
    expect(document.head.querySelectorAll('meta[name="apple-mobile-web-app-capable"]')).toHaveLength(1)
    expect(document.head.querySelectorAll('meta[name="apple-mobile-web-app-title"]')).toHaveLength(1)
  })
})
