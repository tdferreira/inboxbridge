import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import RemoteApp from './RemoteApp'
import { applyRemotePwaMetadata } from './lib/remotePwaMetadata'
import './styles.css'

const useRemoteApp = typeof window !== 'undefined' && window.location.pathname.startsWith('/remote')

if (useRemoteApp) {
  applyRemotePwaMetadata(document)
}

if (useRemoteApp && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/service-worker.js').catch(() => {})
  })
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {useRemoteApp ? <RemoteApp /> : <App />}
  </React.StrictMode>
)
