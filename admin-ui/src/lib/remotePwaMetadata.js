export function applyRemotePwaMetadata(doc = document) {
  if (!doc?.head) {
    return
  }

  ensureHeadTag(doc, 'link', 'data-inboxbridge-remote-manifest', {
    rel: 'manifest',
    href: '/remote.webmanifest'
  })
  ensureHeadTag(doc, 'meta', 'data-inboxbridge-theme-color', {
    name: 'theme-color',
    content: '#1d5fbf'
  })
  ensureHeadTag(doc, 'meta', 'data-inboxbridge-mobile-web-app-capable', {
    name: 'mobile-web-app-capable',
    content: 'yes'
  })
  ensureHeadTag(doc, 'meta', 'data-inboxbridge-apple-mobile-web-app-capable', {
    name: 'apple-mobile-web-app-capable',
    content: 'yes'
  })
  ensureHeadTag(doc, 'meta', 'data-inboxbridge-apple-mobile-web-app-title', {
    name: 'apple-mobile-web-app-title',
    content: 'InboxBridge Go'
  })
}

function ensureHeadTag(doc, tagName, markerAttribute, attributes) {
  let node = doc.head.querySelector(`[${markerAttribute}]`)
  if (!node) {
    node = doc.createElement(tagName)
    node.setAttribute(markerAttribute, 'true')
    doc.head.appendChild(node)
  }

  Object.entries(attributes).forEach(([key, value]) => {
    node.setAttribute(key, value)
  })
}
