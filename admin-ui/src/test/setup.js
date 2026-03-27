import '@testing-library/jest-dom/vitest'

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
