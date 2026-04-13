import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

function createStorageMock() {
  const store = new Map()

  return {
    clear() {
      store.clear()
    },
    getItem(key) {
      return store.has(key) ? store.get(key) : null
    },
    key(index) {
      return Array.from(store.keys())[index] ?? null
    },
    removeItem(key) {
      store.delete(key)
    },
    setItem(key, value) {
      store.set(String(key), String(value))
    },
    get length() {
      return store.size
    }
  }
}

Object.defineProperty(window, 'localStorage', {
  configurable: true,
  value: createStorageMock()
})

if (!navigator.clipboard) {
  Object.assign(navigator, {
    clipboard: {
      writeText: () => Promise.resolve()
    }
  })
}

if (!window.ResizeObserver) {
  window.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
