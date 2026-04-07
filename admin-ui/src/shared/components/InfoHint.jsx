import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import './InfoHint.css'

function InfoHint({ text }) {
  const anchorRef = useRef(null)
  const tooltipRef = useRef(null)
  const [visible, setVisible] = useState(false)
  const [tooltipStyle, setTooltipStyle] = useState({ left: 0, top: 0 })

  useLayoutEffect(() => {
    if (!visible || !anchorRef.current || !tooltipRef.current) {
      return
    }

    function updatePosition() {
      const anchorRect = anchorRef.current.getBoundingClientRect()
      const tooltipRect = tooltipRef.current.getBoundingClientRect()
      const margin = 12
      const preferredTop = anchorRect.top - tooltipRect.height - 10
      const top = preferredTop >= margin
        ? preferredTop
        : Math.min(window.innerHeight - tooltipRect.height - margin, anchorRect.bottom + 10)
      const centeredLeft = anchorRect.left + (anchorRect.width / 2) - (tooltipRect.width / 2)
      const left = Math.max(margin, Math.min(window.innerWidth - tooltipRect.width - margin, centeredLeft))

      setTooltipStyle({ left, top })
    }

    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [visible, text])

  useEffect(() => {
    if (!visible) {
      return
    }

    function hideOnEscape(event) {
      if (event.key === 'Escape') {
        setVisible(false)
      }
    }

    document.addEventListener('keydown', hideOnEscape)
    return () => {
      document.removeEventListener('keydown', hideOnEscape)
    }
  }, [visible])

  return (
    <>
      <span
        aria-label={text}
        className="info-hint"
        onBlur={() => setVisible(false)}
        onFocus={() => setVisible(true)}
        onMouseEnter={() => setVisible(true)}
        onMouseLeave={() => setVisible(false)}
        ref={anchorRef}
        role="note"
        tabIndex="0"
      >
        <span aria-hidden="true" className="info-hint-icon">i</span>
      </span>
      {visible ? createPortal(
        <span
          className="info-hint-tooltip info-hint-tooltip-floating"
          ref={tooltipRef}
          style={{ left: `${tooltipStyle.left}px`, top: `${tooltipStyle.top}px` }}
        >
          {text}
        </span>,
        document.body
      ) : null}
    </>
  )
}

export default InfoHint
