import { computeWorkspaceDropTargetIndex } from './workspaceDrag'

function createWindow(top, height) {
  return {
    getBoundingClientRect: () => ({
      top,
      height,
      bottom: top + height
    })
  }
}

describe('workspaceDrag', () => {
  it('returns insertion slots based on section midpoints instead of raw hovered indexes', () => {
    const windows = [
      createWindow(0, 100),
      createWindow(120, 100),
      createWindow(240, 100),
      createWindow(360, 100)
    ]

    expect(computeWorkspaceDropTargetIndex(windows, 40, 0)).toBe(0)
    expect(computeWorkspaceDropTargetIndex(windows, 150, 0)).toBe(1)
    expect(computeWorkspaceDropTargetIndex(windows, 190, 0)).toBe(2)
    expect(computeWorkspaceDropTargetIndex(windows, 330, 0)).toBe(3)
    expect(computeWorkspaceDropTargetIndex(windows, 455, 0)).toBe(4)
  })

  it('supports moving the first section into the second position and the last section into the previous position', () => {
    const windows = [
      createWindow(0, 100),
      createWindow(120, 100),
      createWindow(240, 100)
    ]

    expect(computeWorkspaceDropTargetIndex(windows, 190, 0)).toBe(2)
    expect(computeWorkspaceDropTargetIndex(windows, 150, 2)).toBe(1)
  })

  it('falls back to the existing target when no pointer position is available', () => {
    expect(computeWorkspaceDropTargetIndex([], undefined, 2)).toBe(2)
  })
})
