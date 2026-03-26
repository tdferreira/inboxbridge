import { useState } from 'react'
import { copyText } from '../../lib/clipboard'
import './CopyButton.css'

/**
 * One-click clipboard helper for API payloads and other diagnostic text.
 */
function CopyButton({ copiedLabel = 'Copied', label = 'Copy', text }) {
  const [copied, setCopied] = useState(false)

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
    <button className="copy-button" type="button" onClick={handleCopy}>
      {copied ? copiedLabel : label}
    </button>
  )
}

export default CopyButton
