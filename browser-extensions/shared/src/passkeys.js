function base64UrlToBuffer(base64Url) {
  const padding = '='.repeat((4 - (base64Url.length % 4 || 4)) % 4)
  const base64 = `${base64Url}${padding}`.replace(/-/g, '+').replace(/_/g, '/')
  const binary = globalThis.atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  return bytes.buffer
}

function bufferToBase64Url(buffer) {
  const bytes = buffer instanceof ArrayBuffer ? new Uint8Array(buffer) : new Uint8Array(buffer.buffer)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return globalThis.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function mapCredentialDescriptors(descriptors = []) {
  return descriptors.map((descriptor) => ({
    ...descriptor,
    id: base64UrlToBuffer(descriptor.id)
  }))
}

function normalizePublicKeyOptions(publicKeyJson) {
  const parsed = JSON.parse(publicKeyJson)
  const publicKey = parsed?.publicKey ?? parsed
  if (!publicKey?.challenge) {
    throw new Error('The passkey options returned by InboxBridge are invalid.')
  }
  return publicKey
}

export function parseGetOptions(publicKeyJson) {
  const publicKey = normalizePublicKeyOptions(publicKeyJson)
  return {
    ...publicKey,
    challenge: base64UrlToBuffer(publicKey.challenge),
    allowCredentials: mapCredentialDescriptors(publicKey.allowCredentials)
  }
}

export function serializeCredential(credential) {
  const response = credential.response
  const serialized = {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: bufferToBase64Url(response.clientDataJSON)
    }
  }

  if (response.authenticatorData) {
    serialized.response.authenticatorData = bufferToBase64Url(response.authenticatorData)
  }
  if (response.signature) {
    serialized.response.signature = bufferToBase64Url(response.signature)
  }
  if (response.userHandle) {
    serialized.response.userHandle = bufferToBase64Url(response.userHandle)
  }
  if (typeof credential.getClientExtensionResults === 'function') {
    serialized.clientExtensionResults = credential.getClientExtensionResults()
  }

  return JSON.stringify(serialized)
}
