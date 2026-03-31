function toBase64Utf8(value) {
  return window.btoa(unescape(encodeURIComponent(value)))
}

function fromBase64Utf8(value) {
  return decodeURIComponent(escape(window.atob(value)))
}

function hexToBytes(hex) {
  const pairs = hex.match(/.{1,2}/g) || []
  return new Uint8Array(pairs.map((pair) => parseInt(pair, 16)))
}

function bytesToHex(bytes) {
  return Array.from(bytes).map((value) => value.toString(16).padStart(2, '0')).join('')
}

async function digestHex(algorithm, value) {
  const normalized = algorithm.toUpperCase()
  const subtleAlgorithm = normalized === 'SHA-1' ? 'SHA-1' : normalized === 'SHA-512' ? 'SHA-512' : 'SHA-256'
  const bytes = new TextEncoder().encode(value)
  const digest = await window.crypto.subtle.digest(subtleAlgorithm, bytes)
  return bytesToHex(new Uint8Array(digest))
}

export async function solveAltchaChallenge(altchaChallenge) {
  const maxNumber = Number(altchaChallenge?.maxNumber || 0)
  if (!altchaChallenge?.challenge || !altchaChallenge?.salt || !altchaChallenge?.algorithm || !Number.isFinite(maxNumber) || maxNumber < 1) {
    throw new Error('Invalid ALTCHA challenge')
  }
  for (let number = 0; number <= maxNumber; number += 1) {
    const digest = await digestHex(altchaChallenge.algorithm, `${altchaChallenge.salt}${number}`)
    if (digest === altchaChallenge.challenge) {
      return toBase64Utf8(JSON.stringify({
        algorithm: altchaChallenge.algorithm,
        challenge: altchaChallenge.challenge,
        number,
        salt: altchaChallenge.salt,
        signature: altchaChallenge.signature
      }))
    }
  }
  throw new Error('Unable to solve ALTCHA challenge')
}

export function decodeAltchaPayload(payload) {
  return JSON.parse(fromBase64Utf8(payload))
}
