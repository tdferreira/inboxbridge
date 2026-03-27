export function resolveFloatingMenuPosition(anchorRect, menuRect, viewportWidth, viewportHeight, offset = 8, margin = 12) {
  const menuWidth = menuRect?.width || 220
  const menuHeight = menuRect?.height || 0
  const spaceBelow = viewportHeight - anchorRect.bottom - margin
  const spaceAbove = anchorRect.top - margin
  const placement = menuHeight > 0 && spaceBelow < menuHeight && spaceAbove > spaceBelow ? 'top' : 'bottom'

  const unclampedLeft = anchorRect.right - menuWidth
  const left = Math.min(
    Math.max(unclampedLeft, margin),
    Math.max(margin, viewportWidth - menuWidth - margin)
  )

  let top = placement === 'top'
    ? anchorRect.top - menuHeight - offset
    : anchorRect.bottom + offset

  top = Math.min(
    Math.max(top, margin),
    Math.max(margin, viewportHeight - menuHeight - margin)
  )

  return { left, placement, top }
}
