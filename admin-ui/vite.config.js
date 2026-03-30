import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    chunkSizeWarningLimit: 550,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('/src/lib/i18n.js')) {
            return 'i18n'
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
    setupFiles: './src/test/setup.js'
  }
})
