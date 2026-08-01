import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const packageVersion = JSON.parse(readFileSync(new URL('./package.json', import.meta.url))).version

export default defineConfig({
  define: {
    __INBOXBRIDGE_VERSION__: JSON.stringify(packageVersion)
  },
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    chunkSizeWarningLimit: 550,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('/src/lib/i18n/locales/')) {
            const localeName = id.split('/').pop()?.replace('.js', '') || 'locale'
            return `i18n-${localeName}`
          }
          if (id.includes('/recharts/')) {
            return 'charts'
          }
          if (id.includes('/react-router-dom/')) {
            return 'router'
          }
          if (id.includes('/react-dom/') || id.includes('/react/')) {
            return 'react-vendor'
          }
          return undefined
        }
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      exclude: [
        'dist/**',
        'src/test/**'
      ]
    }
  }
})
