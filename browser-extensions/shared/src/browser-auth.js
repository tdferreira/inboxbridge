function bytesToBase64Url(bytes) {
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return globalThis.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function randomVerifier(length = 32) {
  const bytes = new Uint8Array(length)
  globalThis.crypto.getRandomValues(bytes)
  return bytesToBase64Url(bytes)
}

async function s256(value) {
  const payload = new TextEncoder().encode(value)
  const digest = await globalThis.crypto.subtle.digest('SHA-256', payload)
  return bytesToBase64Url(new Uint8Array(digest))
}

export async function createBrowserAuthPkcePair() {
  const codeVerifier = randomVerifier()
  const codeChallenge = await s256(codeVerifier)
  return {
    codeChallenge,
    codeChallengeMethod: 'S256',
    codeVerifier
  }
}

export async function completeBrowserSignIn({
  browserFamily,
  extensionVersion,
  label,
  openWindow,
  pollIntervalMs = 1000,
  redeemExtensionBrowserAuth,
  serverUrl,
  sleep = (ms) => new Promise((resolve) => globalThis.setTimeout(resolve, ms)),
  startExtensionBrowserAuth,
  timeoutMs = 120_000
}) {
  const pkce = await createBrowserAuthPkcePair()
  const started = await startExtensionBrowserAuth(serverUrl, {
    ...pkce,
    browserFamily,
    extensionVersion,
    label
  })
  await openWindow(started.browserUrl)
  const deadline = Date.now() + timeoutMs
  while (Date.now() <= deadline) {
    const response = await redeemExtensionBrowserAuth(serverUrl, {
      codeVerifier: pkce.codeVerifier,
      requestId: started.requestId
    })
    if (response?.status === 'AUTHENTICATED' && response.session) {
      return response.session
    }
    if (response?.status === 'EXPIRED') {
      throw new Error('The InboxBridge browser sign-in expired. Start the extension sign-in again.')
    }
    await sleep(pollIntervalMs)
  }
  throw new Error('InboxBridge did not finish the browser sign-in in time. Please try again.')
}
