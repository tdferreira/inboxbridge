function toBase64Utf8(value) {
  return window.btoa(unescape(encodeURIComponent(value)))
}

function fromBase64Utf8(value) {
  return decodeURIComponent(escape(window.atob(value)))
}

function hexToBytes(hex) {
  if (!hex || hex.length % 2 !== 0) {
    throw new Error('Invalid ALTCHA hex value')
  }
  const pairs = hex.match(/.{1,2}/g) || []
  return new Uint8Array(pairs.map((pair) => parseInt(pair, 16)))
}

function bytesToHex(bytes) {
  return Array.from(bytes).map((value) => value.toString(16).padStart(2, '0')).join('')
}

function concatBytes(...parts) {
  const totalLength = parts.reduce((sum, part) => sum + part.length, 0)
  const output = new Uint8Array(totalLength)
  let offset = 0
  for (const part of parts) {
    output.set(part, offset)
    offset += part.length
  }
  return output
}

function counterBytes(counter) {
  const bytes = new Uint8Array(4)
  bytes[0] = (counter >>> 24) & 0xff
  bytes[1] = (counter >>> 16) & 0xff
  bytes[2] = (counter >>> 8) & 0xff
  bytes[3] = counter & 0xff
  return bytes
}

function startsWithPrefix(buffer, prefix) {
  if (prefix.length > buffer.length) {
    return false
  }
  for (let index = 0; index < prefix.length; index += 1) {
    if (buffer[index] !== prefix[index]) {
      return false
    }
  }
  return true
}

async function shaDigest(algorithm, data) {
  const digest = await window.crypto.subtle.digest(algorithm, data)
  return new Uint8Array(digest)
}

function normalizeAlgorithm(algorithm) {
  if (!algorithm) {
    return ''
  }
  return algorithm.toUpperCase()
}

function pbkdf2HashName(algorithm) {
  return algorithm.endsWith('SHA-512') ? 'SHA-512' : algorithm.endsWith('SHA-384') ? 'SHA-384' : 'SHA-256'
}

function shaHashName(algorithm) {
  return algorithm === 'SHA-512' ? 'SHA-512' : algorithm === 'SHA-384' ? 'SHA-384' : 'SHA-256'
}

async function deriveKey(parameters, counter) {
  const normalizedAlgorithm = normalizeAlgorithm(parameters.algorithm)
  const nonceBytes = hexToBytes(parameters.nonce)
  const saltBytes = hexToBytes(parameters.salt)
  const passwordBytes = concatBytes(nonceBytes, counterBytes(counter))
  const keyLength = Number(parameters.keyLength || 32)
  const cost = Math.max(1, Number(parameters.cost || 1))

  if (normalizedAlgorithm.startsWith('PBKDF2/')) {
    const importedKey = await window.crypto.subtle.importKey(
      'raw',
      passwordBytes,
      'PBKDF2',
      false,
      ['deriveBits']
    )
    const derivedBits = await window.crypto.subtle.deriveBits(
      {
        name: 'PBKDF2',
        salt: saltBytes,
        iterations: cost,
        hash: pbkdf2HashName(normalizedAlgorithm)
      },
      importedKey,
      keyLength * 8
    )
    return new Uint8Array(derivedBits)
  }

  if (normalizedAlgorithm === 'SHA-256' || normalizedAlgorithm === 'SHA-384' || normalizedAlgorithm === 'SHA-512') {
    let derived = null
    for (let iteration = 0; iteration < cost; iteration += 1) {
      const input = iteration === 0 ? concatBytes(saltBytes, passwordBytes) : derived
      derived = await shaDigest(shaHashName(normalizedAlgorithm), input)
    }
    return derived.slice(0, keyLength)
  }

  throw new Error(`Unsupported ALTCHA algorithm: ${parameters.algorithm}`)
}

export async function solveAltchaChallenge(altchaChallenge) {
  const parameters = altchaChallenge?.parameters
  const cost = Number(parameters?.cost || 0)
  const keyLength = Number(parameters?.keyLength || 0)
  if (!parameters?.algorithm || !parameters?.nonce || !parameters?.salt || !parameters?.keyPrefix || !Number.isFinite(cost) || cost < 1 || !Number.isFinite(keyLength) || keyLength < 1) {
    throw new Error('Invalid ALTCHA challenge')
  }

  const keyPrefixBytes = hexToBytes(parameters.keyPrefix)
  const startedAt = window.performance?.now?.() ?? Date.now()

  for (let counter = 0; ; counter += 1) {
    const derivedKeyBytes = await deriveKey(parameters, counter)
    if (startsWithPrefix(derivedKeyBytes, keyPrefixBytes)) {
      const finishedAt = window.performance?.now?.() ?? Date.now()
      return toBase64Utf8(JSON.stringify({
        challenge: {
          parameters,
          signature: altchaChallenge.signature || null
        },
        solution: {
          counter,
          derivedKey: bytesToHex(derivedKeyBytes),
          time: Math.max(0, Math.round(finishedAt - startedAt))
        }
      }))
    }
    if (counter > 0 && counter % 10 === 0) {
      await new Promise((resolve) => window.setTimeout(resolve, 0))
    }
  }
}

export function decodeAltchaPayload(payload) {
  return JSON.parse(fromBase64Utf8(payload))
}
