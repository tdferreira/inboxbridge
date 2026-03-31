export function computeWorkspaceDropTargetIndex(windows, clientY, fallbackIndex = 0) {
  if (typeof clientY !== 'number' || !Array.isArray(windows) || !windows.length) {
    return fallbackIndex
  }

  for (let index = 0; index < windows.length; index += 1) {
    const bounds = windows[index].getBoundingClientRect()
    const top = bounds.top ?? 0
    const height = bounds.height ?? 0
    const midpoint = top + (height / 2)
    if (clientY < midpoint) {
      return index
    }
  }

  return windows.length
}
