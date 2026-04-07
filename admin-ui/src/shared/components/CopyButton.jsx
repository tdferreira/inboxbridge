import { useState } from 'react'
import { copyText } from '@/lib/clipboard'
import './CopyButton.css'

/**
 * One-click clipboard helper for API payloads and other diagnostic text.
 */
function CopyButton({ copiedLabel = 'Copied', label = 'Copy', text }) {
  const [copied, setCopied] = useState(false)
  const accessibleLabel = copied ? copiedLabel : label

  async function handleCopy() {
    try {
      await copyText(text)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1500)
    } catch {
      setCopied(false)
    }
  }

  return (
    <button
      aria-label={accessibleLabel}
      className="copy-button"
      onClick={handleCopy}
      title={accessibleLabel}
      type="button"
    >
      {copied ? (
        <svg aria-hidden="true" className="copy-button-icon" viewBox="0 0 16 16">
          <path d="M6.2 11.7 2.9 8.4l1.1-1.1 2.2 2.2 5.8-5.8 1.1 1.1z" fill="currentColor" />
        </svg>
      ) : (
        <svg aria-hidden="true" className="copy-button-icon" viewBox="0 0 16 16">
          <path d="M5 2.5A1.5 1.5 0 0 1 6.5 1h5A1.5 1.5 0 0 1 13 2.5v7A1.5 1.5 0 0 1 11.5 11h-5A1.5 1.5 0 0 1 5 9.5zM6.5 2a.5.5 0 0 0-.5.5v7a.5.5 0 0 0 .5.5h5a.5.5 0 0 0 .5-.5v-7a.5.5 0 0 0-.5-.5z" fill="currentColor" />
          <path d="M3 5.5V12a2 2 0 0 0 2 2h5.5v-1H5a1 1 0 0 1-1-1V5.5z" fill="currentColor" />
        </svg>
      )}
    </button>
  )
}

export default CopyButton
