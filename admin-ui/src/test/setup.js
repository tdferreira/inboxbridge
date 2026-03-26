import '@testing-library/jest-dom/vitest'

if (!navigator.clipboard) {
  Object.assign(navigator, {
    clipboard: {
      writeText: () => Promise.resolve()
    }
  })
}
