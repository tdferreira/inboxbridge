const DB_NAME = 'inboxbridge-extension-secure-store'
const STORE_NAME = 'crypto-keys'
const KEY_ALIAS = 'extension-auth'

const encoder = new TextEncoder()
const decoder = new TextDecoder()

export async function encryptJson(value) {
  const key = await getOrCreateKey(KEY_ALIAS)
  const iv = globalThis.crypto.getRandomValues(new Uint8Array(12))
  const plaintext = encoder.encode(JSON.stringify(value))
  const ciphertext = await globalThis.crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, plaintext)
  return {
    algorithm: 'AES-GCM',
    iv: bytesToBase64(iv),
    ciphertext: bytesToBase64(new Uint8Array(ciphertext)),
    version: 1
  }
}

export async function decryptJson(payload) {
  if (!payload?.ciphertext || !payload?.iv) {
    return null
  }
  const key = await getOrCreateKey(KEY_ALIAS)
  const plaintext = await globalThis.crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: base64ToBytes(payload.iv) },
    key,
    base64ToBytes(payload.ciphertext)
  )
  return JSON.parse(decoder.decode(plaintext))
}

async function getOrCreateKey(alias) {
  const db = await openDatabase()
  const existing = await idbGet(db, alias)
  if (existing) {
    return existing
  }
  const key = await globalThis.crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  )
  await idbPut(db, alias, key)
  return key
}

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = globalThis.indexedDB.open(DB_NAME, 1)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME)
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error || new Error('Could not open secure extension storage.'))
  })
}

function idbGet(db, key) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, 'readonly')
    const request = transaction.objectStore(STORE_NAME).get(key)
    request.onsuccess = () => resolve(request.result || null)
    request.onerror = () => reject(request.error || new Error('Could not read secure extension storage.'))
  })
}

function idbPut(db, key, value) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, 'readwrite')
    const request = transaction.objectStore(STORE_NAME).put(value, key)
    request.onsuccess = () => resolve()
    request.onerror = () => reject(request.error || new Error('Could not write secure extension storage.'))
  })
}

function bytesToBase64(bytes) {
  return globalThis.btoa(String.fromCharCode(...bytes))
}

function base64ToBytes(value) {
  return Uint8Array.from(globalThis.atob(value), (character) => character.charCodeAt(0))
}
