import React from 'react'
import ReactDOM from 'react-dom/client'
import App from '@/app/App'
import RemoteApp from '@/app/RemoteApp'
import { ensureLocaleLoaded, normalizeLocale } from '@/lib/i18n'
import { applyRemotePwaMetadata } from '@/lib/remotePwaMetadata'
import '@/theme/global.css'

const useRemoteApp = typeof window !== 'undefined' && window.location.pathname.startsWith('/remote')

if (useRemoteApp) {
  applyRemotePwaMetadata(document)
}

if (useRemoteApp && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/service-worker.js').catch(() => {})
  })
}

const initialLocale = normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language)

void ensureLocaleLoaded(initialLocale).finally(() => {
  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      {useRemoteApp ? <RemoteApp /> : <App />}
    </React.StrictMode>
  )
})
