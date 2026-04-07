import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { resolveFloatingMenuPosition } from '@/lib/floatingMenu'

/**
 * Shared anchored action menu used by compact list-row menus. It owns
 * open/close behavior, outside-click handling, keyboard dismissal, and
 * viewport-aware placement relative to the trigger button.
 */
function FloatingActionMenu({
  buttonClassName = 'icon-button fetcher-menu-button',
  buttonLabel,
  className = '',
  menuClassName = 'fetcher-menu',
  menuContent,
  title
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuStyle, setMenuStyle] = useState(null)
  const [menuPlacement, setMenuPlacement] = useState('bottom')
  const containerRef = useRef(null)
  const menuPanelRef = useRef(null)
  const menuButtonRef = useRef(null)

  useEffect(() => {
    if (!menuOpen) {
      return undefined
    }

    function closeOnPointerDown(event) {
      if (!containerRef.current || containerRef.current.contains(event.target)) {
        return
      }
      setMenuOpen(false)
    }

    function closeOnEscape(event) {
      if (event.key === 'Escape') {
        setMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', closeOnPointerDown)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnPointerDown)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [menuOpen])

  useLayoutEffect(() => {
    if (!menuOpen || !menuPanelRef.current || !menuButtonRef.current) {
      return undefined
    }

    function updatePosition() {
      const anchorRect = menuButtonRef.current.getBoundingClientRect()
      const margin = 12
      const hasMeasuredAnchor = anchorRect.width > 0 || anchorRect.height > 0

      if (hasMeasuredAnchor && (
        anchorRect.bottom < margin ||
        anchorRect.top > window.innerHeight - margin ||
        anchorRect.right < margin ||
        anchorRect.left > window.innerWidth - margin
      )) {
        setMenuOpen(false)
        return
      }

      const next = resolveFloatingMenuPosition(
        anchorRect,
        menuPanelRef.current.getBoundingClientRect(),
        window.innerWidth,
        window.innerHeight
      )

      setMenuPlacement(next.placement)
      setMenuStyle({
        left: `${next.left}px`,
        top: `${next.top}px`
      })
    }

    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [menuOpen])

  return (
    <div ref={containerRef} className={className}>
      <button
        aria-label={buttonLabel}
        className={buttonClassName}
        onClick={() => setMenuOpen((current) => !current)}
        ref={menuButtonRef}
        title={title || buttonLabel}
        type="button"
      >
        <span aria-hidden="true" className="menu-icon-hamburger">
          <span />
          <span />
          <span />
        </span>
      </button>
      {menuOpen ? (
        <div className={menuClassName} data-placement={menuPlacement} ref={menuPanelRef} style={menuStyle}>
          {menuContent?.({
            closeMenu: () => setMenuOpen(false)
          })}
        </div>
      ) : null}
    </div>
  )
}

export default FloatingActionMenu
